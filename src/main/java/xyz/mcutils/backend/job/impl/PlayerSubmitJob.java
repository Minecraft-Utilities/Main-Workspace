package xyz.mcutils.backend.job.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.common.redis.RedisQueue;
import xyz.mcutils.backend.common.redis.RedisQueueFactory;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.metric.impl.player.PlayerSubmitProcessingMetric;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;
import xyz.mcutils.backend.service.MojangService;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.PlayerSubmitService;
import xyz.mcutils.backend.service.PlayerSubmitService.QueueEntry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

/**
 * Drains the player submit queue, fetching each player's profile from Mojang and storing it.
 */
@Component
@Slf4j
public class PlayerSubmitJob implements Job {

    private static final int BATCH_SIZE = 1000;
    /** Must stay at or below mojang-profile http-client max-connections-per-route. */
    private static final int CONCURRENT_FETCHES = 40;
    private static final Duration EMPTY_QUEUE_BLOCK = Duration.ofSeconds(2);

    private final AdaptiveRateLimiter mojangRateLimiter;
    private final RedisQueue submitQueue;
    private final PlayerService playerService;
    private final MojangService mojangService;

    public PlayerSubmitJob(RedisQueueFactory queueFactory, @Lazy PlayerService playerService, @Lazy MojangService mojangService, @Qualifier("submitRateLimiter") AdaptiveRateLimiter mojangRateLimiter) {
        this.submitQueue = queueFactory.getQueue(PlayerSubmitService.QUEUE_NAME);
        this.playerService = playerService;
        this.mojangService = mojangService;
        this.mojangRateLimiter = mojangRateLimiter;
    }

    @Override
    public String name() {
        return "player-submit";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.continuous(Duration.ZERO);
    }

    @Override
    public JobResult run(JobContext context) {
        Optional<List<String>> batch = submitQueue.blockingTakeBatch(BATCH_SIZE, EMPTY_QUEUE_BLOCK);
        if (batch.isEmpty()) {
            return JobResult.IDLE;
        }
        processBatch(batch.get(), context);
        return JobResult.WORKED;
    }

    private void processBatch(List<String> batch, JobContext context) {
        List<QueueEntry> entries = parseEntries(batch);
        if (entries.isEmpty()) {
            return;
        }
        List<QueueEntry> toProcess = filterExisting(entries);
        Map<UUID, Long> submitterCounts = processEntries(toProcess, context);
        submitterCounts.forEach(playerService::incrementSubmittedUuids);
    }

    private List<QueueEntry> parseEntries(List<String> batch) {
        return batch.stream()
                .map(QueueEntry::fromRedisValue)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private List<QueueEntry> filterExisting(List<QueueEntry> entries) {
        Set<UUID> existingIds = playerService.getExistingPlayerIds(entries.stream().map(QueueEntry::playerId).toList());
        if (!existingIds.isEmpty()) {
            List<String> duplicateIds = entries.stream()
                    .filter(e -> existingIds.contains(e.playerId()))
                    .map(e -> e.playerId().toString())
                    .toList();
            submitQueue.removeDedupeKeys(duplicateIds);
        }
        return entries.stream().filter(e -> !existingIds.contains(e.playerId())).toList();
    }

    /**
     * Fetches and stores the entries. Anything popped from the queue that is not settled when this returns, whether
     * by shutdown or a failure, goes back on the queue with its dedupe key intact.
     */
    private Map<UUID, Long> processEntries(List<QueueEntry> toProcess, JobContext context) {
        Set<UUID> settled = new HashSet<>();
        try {
            return fetchAndStore(toProcess, context, settled);
        } finally {
            List<String> unsettled = toProcess.stream()
                    .filter(entry -> !settled.contains(entry.playerId()))
                    .map(QueueEntry::toRedisValue)
                    .toList();
            if (!unsettled.isEmpty()) {
                submitQueue.requeue(unsettled);
            }
        }
    }

    private Map<UUID, Long> fetchAndStore(List<QueueEntry> toProcess, JobContext context, Set<UUID> settled) {
        Queue<FetchResult> fetchResults = new ConcurrentLinkedQueue<>();
        Set<String> idsToRemoveFromQueue = new HashSet<>();
        Map<UUID, Long> submitterCounts = new HashMap<>();
        List<MojangProfileToken> tokensToSave = new ArrayList<>();
        List<FetchResult> successResults = new ArrayList<>();

        for (int offset = 0; offset < toProcess.size(); offset += CONCURRENT_FETCHES) {
            if (!context.isRunning()) {
                break;
            }
            int end = Math.min(offset + CONCURRENT_FETCHES, toProcess.size());
            List<QueueEntry> slice = toProcess.subList(offset, end);
            List<Future<Void>> futures = new ArrayList<>();
            for (QueueEntry entry : slice) {
                mojangRateLimiter.acquire();
                futures.add(Main.EXECUTOR.submit(() -> {
                    fetchResults.add(fetchProfile(entry));
                    return null;
                }));
            }
            FutureUtils.awaitAll(futures, "submit");
        }

        Set<UUID> retryable = new HashSet<>();
        for (FetchResult result : fetchResults) {
            if (result.outcome() == PlayerSubmitProcessingMetric.Outcome.RATE_LIMITED
                    || result.outcome() == PlayerSubmitProcessingMetric.Outcome.TIMED_OUT) {
                retryable.add(result.entry().playerId());
            } else {
                idsToRemoveFromQueue.add(result.entry().playerId().toString());
                if (result.token() != null) {
                    tokensToSave.add(result.token());
                    successResults.add(result);
                }
            }
        }

        if (!tokensToSave.isEmpty()) {
            playerService.createPlayers(tokensToSave);
            for (FetchResult result : successResults) {
                if (result.entry().submittedBy() != null) {
                    submitterCounts.merge(result.entry().submittedBy(), 1L, Long::sum);
                }
            }
        }

        // Retryable entries stay unsettled so the caller requeues them with everything that was never fetched.
        for (FetchResult result : fetchResults) {
            if (!retryable.contains(result.entry().playerId())) {
                settled.add(result.entry().playerId());
            }
        }
        submitQueue.removeDedupeKeys(idsToRemoveFromQueue);
        return submitterCounts;
    }

    private FetchResult fetchProfile(QueueEntry entry) {
        long processStart = System.currentTimeMillis();
        try {
            MojangProfileToken token = mojangService.getProfile(entry.playerId().toString());
            if (token == null) {
                log.warn("Player with uuid '{}' was not found", entry.playerId());
                recordOutcome(PlayerSubmitProcessingMetric.Outcome.NOT_FOUND, processStart);
                return new FetchResult(entry, null, PlayerSubmitProcessingMetric.Outcome.NOT_FOUND, processStart);
            }
            mojangRateLimiter.speedUp();
            recordOutcome(PlayerSubmitProcessingMetric.Outcome.CREATED, processStart);
            return new FetchResult(entry, token, PlayerSubmitProcessingMetric.Outcome.CREATED, processStart);
        } catch (NotFoundException e) {
            log.debug("Player {} not found on Mojang, removing from queue", entry.playerId(), e);
            recordOutcome(PlayerSubmitProcessingMetric.Outcome.NOT_FOUND, processStart);
            return new FetchResult(entry, null, PlayerSubmitProcessingMetric.Outcome.NOT_FOUND, processStart);
        } catch (RateLimitException e) {
            mojangRateLimiter.slowDown();
            // The HTTP layer throws the plain RateLimitException (not the never-thrown
            // MojangAPIRateLimitException subclass); without this catch the popped entry
            // was silently dropped: never requeued, and its dedupe key stayed in the set,
            // permanently blocking future enqueues of that player.
            recordOutcome(PlayerSubmitProcessingMetric.Outcome.RATE_LIMITED, processStart);
            return new FetchResult(entry, null, PlayerSubmitProcessingMetric.Outcome.RATE_LIMITED, processStart);
        } catch (ResourceAccessException e) {
            log.debug("Timed out fetching profile for {}, re-queuing", entry.playerId());
            recordOutcome(PlayerSubmitProcessingMetric.Outcome.TIMED_OUT, processStart);
            return new FetchResult(entry, null, PlayerSubmitProcessingMetric.Outcome.TIMED_OUT, processStart);
        }
    }

    private void recordOutcome(PlayerSubmitProcessingMetric.Outcome outcome, long startMs) {
        MetricService.getMetric(PlayerSubmitProcessingMetric.class).record(outcome, System.currentTimeMillis() - startMs);
    }

    private record FetchResult(QueueEntry entry, MojangProfileToken token, PlayerSubmitProcessingMetric.Outcome outcome, long processStart) {}
}
