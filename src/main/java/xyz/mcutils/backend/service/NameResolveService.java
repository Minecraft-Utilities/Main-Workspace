package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.job.JobContext;
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

/**
 * Resolves usernames through Mojang's bulk name lookup and queues the accounts no tracked player
 * holds yet for submission.
 */
@Service
@Slf4j
public class NameResolveService {
    private static final int CHUNK_SIZE = 10;
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(1);

    private final NameSweepRepository nameSweepRepository;
    private final MojangService mojangService;
    private final PlayerService playerService;
    private final PlayerSubmitService playerSubmitService;

    public NameResolveService(
            NameSweepRepository nameSweepRepository,
            MojangService mojangService,
            PlayerService playerService,
            PlayerSubmitService playerSubmitService
    ) {
        this.nameSweepRepository = nameSweepRepository;
        this.mojangService = mojangService;
        this.playerService = playerService;
        this.playerSubmitService = playerSubmitService;
    }

    /**
     * Returns only once every chunk of {@code names} has been looked up, so a caller advancing a
     * cursor never moves past a name that was not checked.
     */
    public void resolve(List<String> names, NameSweepChecksMetric.Source source, int length, AdaptiveRateLimiter rateLimiter, JobContext context) {
        List<String> untracked = this.nameSweepRepository.selectUntracked(names);

        List<List<String>> pending = new ArrayList<>();
        for (int offset = 0; offset < untracked.size(); offset += CHUNK_SIZE) {
            pending.add(List.copyOf(untracked.subList(offset, Math.min(offset + CHUNK_SIZE, untracked.size()))));
        }

        while (!pending.isEmpty() && context.isRunning()) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (List<String> chunk : pending) {
                futures.add(Main.EXECUTOR.submit(() -> resolveChunk(source, length, chunk, rateLimiter)));
            }
            List<List<String>> failed = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                if (!awaitSucceeded(futures.get(i))) {
                    failed.add(pending.get(i));
                }
            }
            pending = failed;
            if (!pending.isEmpty()) {
                record(source, length, NameSweepChecksMetric.Outcome.FAILED, pending.size() * (long) CHUNK_SIZE);
                context.sleep(FAILURE_BACKOFF);
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

    private boolean resolveChunk(NameSweepChecksMetric.Source source, int length, List<String> chunk, AdaptiveRateLimiter rateLimiter) {
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

            record(source, length, NameSweepChecksMetric.Outcome.UNCLAIMED, unclaimed);
            record(source, length, NameSweepChecksMetric.Outcome.ALREADY_TRACKED, existing.size());
            record(source, length, NameSweepChecksMetric.Outcome.NEW_PLAYER, enqueued);
            record(source, length, NameSweepChecksMetric.Outcome.ALREADY_QUEUED, newPlayers.size() - enqueued);
            return true;
        } catch (RateLimitException e) {
            rateLimiter.slowDown();
            return false;
        }
    }

    private void record(NameSweepChecksMetric.Source source, int length, NameSweepChecksMetric.Outcome outcome, long count) {
        MetricService.getMetric(NameSweepChecksMetric.class).inc(source, length, outcome, count);
    }
}
