package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.metric.impl.player.NameSweepChecksMetric;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.repository.postgres.NameSweepRepository;

import java.time.Duration;
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
 * Enumerates every username of the configured lengths and resolves the ones no tracked player holds
 * through Mojang's bulk name lookup, to discover accounts that are not tracked yet.
 *
 * <p>Each length runs its own loop over a persisted cursor. The first pass runs at the initial
 * rate; once a pass completes the cursor wraps and the length keeps sweeping at the lower
 * continuous rate, so names claimed since the last pass are picked up.
 */
@Service
@Slf4j
public class NameSweepService {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";
    private static final int BATCH_SIZE = 1000;
    private static final int CHUNK_SIZE = 10;
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(1);
    private static final Duration WAIT_POLL = Duration.ofSeconds(1);

    private final NameSweepRepository nameSweepRepository;
    private final MojangService mojangService;
    private final PlayerService playerService;
    private final PlayerSubmitService playerSubmitService;
    private final boolean enabled;
    private final List<Integer> lengths;
    private final double initialRequestsPerSecond;
    private final double continuousRequestsPerSecond;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public NameSweepService(
            NameSweepRepository nameSweepRepository,
            MojangService mojangService,
            PlayerService playerService,
            PlayerSubmitService playerSubmitService,
            @Value("${mc-utils.name-sweep.enabled:true}") boolean enabled,
            @Value("${mc-utils.name-sweep.lengths:4,5}") List<Integer> lengths,
            @Value("${mc-utils.name-sweep.initial-requests-per-second:50}") double initialRequestsPerSecond,
            @Value("${mc-utils.name-sweep.continuous-requests-per-second:10}") double continuousRequestsPerSecond
    ) {
        this.nameSweepRepository = nameSweepRepository;
        this.mojangService = mojangService;
        this.playerService = playerService;
        this.playerSubmitService = playerSubmitService;
        this.enabled = enabled;
        this.lengths = lengths;
        this.initialRequestsPerSecond = initialRequestsPerSecond;
        this.continuousRequestsPerSecond = continuousRequestsPerSecond;
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        this.running.set(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSweeps() {
        if (!this.enabled) {
            log.info("Name sweep is disabled");
            return;
        }
        for (int length : this.lengths) {
            Main.EXECUTOR.submit(() -> runLoop(length));
        }
    }

    static long totalNames(int length) {
        return (long) Math.pow(ALPHABET.length(), length);
    }

    static String nameAt(long index, int length) {
        char[] name = new char[length];
        long remaining = index;
        for (int i = length - 1; i >= 0; i--) {
            name[i] = ALPHABET.charAt((int) (remaining % ALPHABET.length()));
            remaining /= ALPHABET.length();
        }
        return new String(name);
    }

    private void runLoop(int length) {
        long total = totalNames(length);
        NameSweepRepository.Progress progress = this.nameSweepRepository.findProgress(length)
                .orElse(new NameSweepRepository.Progress(0, 0));
        long next = progress.nextIndex();
        int passes = progress.passes();
        AdaptiveRateLimiter rateLimiter = newRateLimiter(passes);
        log.info("Name sweep for length {} starting at {}/{} (pass {})", length, next, total, passes + 1);

        while (this.running.get()) {
            try {
                long end = Math.min(next + BATCH_SIZE, total);
                sweepBatch(length, next, end, rateLimiter);
                if (!this.running.get()) {
                    break;
                }
                next = end;
                if (next >= total) {
                    next = 0;
                    passes++;
                    rateLimiter = newRateLimiter(passes);
                    log.info("Name sweep for length {} finished pass {}, continuing at {} req/s", length, passes, this.continuousRequestsPerSecond);
                }
                this.nameSweepRepository.saveProgress(length, next, passes);
            } catch (Exception e) {
                if (!this.running.get()) {
                    break;
                }
                log.error("Name sweep loop error for length {}, continuing", length, e);
                sleep(FAILURE_BACKOFF);
            }
        }
    }

    private AdaptiveRateLimiter newRateLimiter(int passes) {
        return new AdaptiveRateLimiter(passes == 0 ? this.initialRequestsPerSecond : this.continuousRequestsPerSecond);
    }

    /**
     * Returns only once every chunk of the batch has been looked up, so the cursor never advances
     * past a name that was not checked.
     */
    private void sweepBatch(int length, long start, long end, AdaptiveRateLimiter rateLimiter) {
        List<String> names = new ArrayList<>();
        for (long index = start; index < end; index++) {
            names.add(nameAt(index, length));
        }
        List<String> untracked = this.nameSweepRepository.selectUntracked(names);

        List<List<String>> pending = new ArrayList<>();
        for (int offset = 0; offset < untracked.size(); offset += CHUNK_SIZE) {
            pending.add(List.copyOf(untracked.subList(offset, Math.min(offset + CHUNK_SIZE, untracked.size()))));
        }

        while (!pending.isEmpty() && this.running.get()) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (List<String> chunk : pending) {
                futures.add(Main.EXECUTOR.submit(() -> resolveChunk(length, chunk, rateLimiter)));
            }
            List<List<String>> failed = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                if (!awaitSucceeded(futures.get(i))) {
                    failed.add(pending.get(i));
                }
            }
            pending = failed;
            if (!pending.isEmpty()) {
                record(length, NameSweepChecksMetric.Outcome.FAILED, pending.size() * (long) CHUNK_SIZE);
                sleep(FAILURE_BACKOFF);
            }
        }
    }

    private boolean awaitSucceeded(Future<Boolean> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Name sweep chunk failed: {}", e.getCause() == null ? e.toString() : e.getCause().toString());
            return false;
        }
    }

    private boolean resolveChunk(int length, List<String> chunk, AdaptiveRateLimiter rateLimiter) {
        try {
            rateLimiter.acquire();
            List<MojangUsernameToUuidToken> tokens = this.mojangService.getUuidsFromUsernames(chunk);
            rateLimiter.speedUp();

            Map<String, UUID> resolved = new LinkedHashMap<>();
            for (MojangUsernameToUuidToken token : tokens) {
                resolved.put(token.username().toLowerCase(Locale.ROOT), UUIDUtils.parseUuid(token.uuid()));
            }
            long unclaimed = chunk.stream().filter(name -> !resolved.containsKey(name)).count();

            List<UUID> ids = List.copyOf(resolved.values());
            Set<UUID> existing = ids.isEmpty() ? Set.of() : this.playerService.getExistingPlayerIds(ids);
            List<UUID> newPlayers = ids.stream().filter(id -> !existing.contains(id)).toList();
            int enqueued = newPlayers.isEmpty() ? 0 : this.playerSubmitService.submitPlayers(newPlayers.stream().map(UUID::toString).toList(), null);

            record(length, NameSweepChecksMetric.Outcome.UNCLAIMED, unclaimed);
            record(length, NameSweepChecksMetric.Outcome.ALREADY_TRACKED, existing.size());
            record(length, NameSweepChecksMetric.Outcome.NEW_PLAYER, enqueued);
            record(length, NameSweepChecksMetric.Outcome.ALREADY_QUEUED, newPlayers.size() - enqueued);
            return true;
        } catch (RateLimitException e) {
            rateLimiter.slowDown();
            return false;
        }
    }

    private void record(int length, NameSweepChecksMetric.Outcome outcome, long count) {
        MetricService.getMetric(NameSweepChecksMetric.class).inc(length, outcome, count);
    }

    private void sleep(Duration duration) {
        long deadline = System.nanoTime() + duration.toNanos();
        while (this.running.get() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(WAIT_POLL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
