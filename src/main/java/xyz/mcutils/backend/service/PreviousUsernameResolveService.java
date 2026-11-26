package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.metric.impl.player.PreviousUsernameChecksMetric;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.repository.postgres.PreviousUsernameCheckRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves released usernames through Mojang's bulk name lookup to discover accounts that are not
 * tracked yet. A name is only checked once Mojang's 37-day hold has passed, so a profile returned
 * for it belongs to a different account than the tracked player that gave the name up.
 */
@Service
@Slf4j
public class PreviousUsernameResolveService {

    private static final int BATCH_SIZE = 2000;
    private static final int CHUNK_SIZE = 10;
    private static final Duration EMPTY_WAIT = Duration.ofMinutes(1);
    private static final Duration FAILURE_BACKOFF = Duration.ofHours(1);
    private static final Duration WAIT_POLL = Duration.ofSeconds(1);

    private final PreviousUsernameCheckRepository previousUsernameCheckRepository;
    private final MojangService mojangService;
    private final PlayerService playerService;
    private final PlayerSubmitService playerSubmitService;
    private final AdaptiveRateLimiter requestRateLimiter;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PreviousUsernameResolveService(
            PreviousUsernameCheckRepository previousUsernameCheckRepository,
            MojangService mojangService,
            PlayerService playerService,
            PlayerSubmitService playerSubmitService,
            @Value("${mc-utils.previous-username.enabled:true}") boolean enabled,
            @Value("${mc-utils.previous-username.max-requests-per-second:50}") double maxRequestsPerSecond
    ) {
        this.previousUsernameCheckRepository = previousUsernameCheckRepository;
        this.mojangService = mojangService;
        this.playerService = playerService;
        this.playerSubmitService = playerSubmitService;
        this.enabled = enabled;
        this.requestRateLimiter = new AdaptiveRateLimiter(maxRequestsPerSecond);
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        this.running.set(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startResolveLoop() {
        if (!this.enabled) {
            log.info("Previous username resolver is disabled");
            return;
        }
        Main.EXECUTOR.submit(this::runLoop);
    }

    public long countDue() {
        return this.previousUsernameCheckRepository.countDue(Instant.now());
    }

    private void runLoop() {
        while (this.running.get()) {
            try {
                if (!processBatch()) {
                    waitForWork();
                }
            } catch (Exception e) {
                if (!this.running.get()) {
                    break;
                }
                log.error("Previous username resolve loop error, continuing", e);
                waitForWork();
            }
        }
    }

    private boolean processBatch() {
        Instant now = Instant.now();
        List<String> due = this.previousUsernameCheckRepository.selectDue(now, BATCH_SIZE);
        if (due.isEmpty()) {
            return false;
        }

        long alreadyTracked = this.previousUsernameCheckRepository.deleteOwnedByTrackedPlayers(due);
        List<String> remaining = alreadyTracked == 0 ? due : this.previousUsernameCheckRepository.selectDue(now, BATCH_SIZE);
        Totals totals = new Totals(alreadyTracked, 0, 0, 0, 0, 0);
        if (this.running.get()) {
            totals = totals.plus(resolveChunks(remaining, now));
        }

        record(PreviousUsernameChecksMetric.Outcome.ALREADY_TRACKED, totals.alreadyTracked());
        record(PreviousUsernameChecksMetric.Outcome.NEW_PLAYER, totals.newPlayers());
        record(PreviousUsernameChecksMetric.Outcome.ALREADY_QUEUED, totals.alreadyQueued());
        record(PreviousUsernameChecksMetric.Outcome.UNCLAIMED, totals.unclaimed());
        record(PreviousUsernameChecksMetric.Outcome.FAILED, totals.failed());
        log.info("Previous username checks: {} due, {} already tracked, {} new players, {} already queued, {} unclaimed, {} failed, {} rate limited, at {} req/s",
                due.size(), totals.alreadyTracked(), totals.newPlayers(), totals.alreadyQueued(), totals.unclaimed(), totals.failed(),
                totals.rateLimited(), String.format(Locale.ROOT, "%.1f", this.requestRateLimiter.getRate()));
        // A failing endpoint fails every chunk, so pausing until the next poll keeps it to one
        // round of requests a minute instead of hammering Mojang and the error-rate panels.
        // Rate limits are backpressure rather than an outage, so they never pause the loop.
        return totals.failed() == totals.rateLimited();
    }

    private Totals resolveChunks(List<String> names, Instant now) {
        List<Future<Totals>> futures = new ArrayList<>();
        for (int offset = 0; offset < names.size(); offset += CHUNK_SIZE) {
            List<String> chunk = List.copyOf(names.subList(offset, Math.min(offset + CHUNK_SIZE, names.size())));
            futures.add(Main.EXECUTOR.submit(() -> {
                try {
                    Totals chunkTotals = resolveChunk(chunk, now);
                    this.requestRateLimiter.speedUp();
                    return chunkTotals;
                } catch (RateLimitException e) {
                    log.debug("Previous username chunk rate limited for {} names, postponing", chunk.size());
                    postpone(chunk, now);
                    this.requestRateLimiter.slowDown();
                    return new Totals(0, 0, 0, 0, chunk.size(), chunk.size());
                } catch (Exception e) {
                    log.warn("Previous username chunk failed for {} names, postponing: {}", chunk.size(), e.toString());
                    postpone(chunk, now);
                    return new Totals(0, 0, 0, 0, chunk.size(), 0);
                }
            }));
        }

        Totals totals = new Totals(0, 0, 0, 0, 0, 0);
        for (Totals chunkTotals : FutureUtils.awaitAll(futures, "previous username resolve")) {
            totals = totals.plus(chunkTotals);
        }
        return totals;
    }

    private Totals resolveChunk(List<String> chunk, Instant now) {
        this.requestRateLimiter.acquire();
        List<MojangUsernameToUuidToken> tokens = this.mojangService.getUuidsFromUsernames(chunk);

        Map<String, UUID> resolved = new LinkedHashMap<>();
        for (MojangUsernameToUuidToken token : tokens) {
            resolved.put(token.username().toLowerCase(Locale.ROOT), UUIDUtils.parseUuid(token.uuid()));
        }

        List<String> unclaimed = chunk.stream().filter(name -> !resolved.containsKey(name)).toList();
        if (!unclaimed.isEmpty()) {
            this.previousUsernameCheckRepository.markUnclaimed(unclaimed, now);
        }

        List<UUID> ids = List.copyOf(resolved.values());
        Set<UUID> existing = ids.isEmpty() ? Set.of() : this.playerService.getExistingPlayerIds(ids);
        List<UUID> newPlayers = ids.stream().filter(id -> !existing.contains(id)).toList();
        int enqueued = newPlayers.isEmpty() ? 0 : this.playerSubmitService.submitPlayers(newPlayers.stream().map(UUID::toString).toList(), null);
        this.previousUsernameCheckRepository.delete(List.copyOf(resolved.keySet()), now);
        return new Totals(existing.size(), enqueued, newPlayers.size() - enqueued, unclaimed.size(), 0, 0);
    }

    private void postpone(List<String> chunk, Instant now) {
        this.previousUsernameCheckRepository.postpone(chunk, now.plus(FAILURE_BACKOFF));
    }

    private void record(PreviousUsernameChecksMetric.Outcome outcome, long count) {
        MetricService.getMetric(PreviousUsernameChecksMetric.class).inc(outcome, count);
    }

    private void waitForWork() {
        long deadline = System.nanoTime() + EMPTY_WAIT.toNanos();
        while (this.running.get() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(WAIT_POLL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private record Totals(long alreadyTracked, long newPlayers, long alreadyQueued, long unclaimed, long failed, long rateLimited) {
        Totals plus(Totals other) {
            return new Totals(
                    this.alreadyTracked + other.alreadyTracked,
                    this.newPlayers + other.newPlayers,
                    this.alreadyQueued + other.alreadyQueued,
                    this.unclaimed + other.unclaimed,
                    this.failed + other.failed,
                    this.rateLimited + other.rateLimited);
        }
    }
}
