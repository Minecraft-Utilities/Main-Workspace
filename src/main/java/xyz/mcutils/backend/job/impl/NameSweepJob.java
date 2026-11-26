package xyz.mcutils.backend.job.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobProvider;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.metric.impl.player.NameSweepChecksMetric;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.repository.postgres.NameSweepRepository;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.MojangService;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.PlayerSubmitService;

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
 * Enumerates every username of the configured lengths and resolves the ones no tracked player holds
 * through Mojang's bulk name lookup, to discover accounts that are not tracked yet.
 *
 * <p>Each length runs its own loop over a persisted cursor. The first pass runs at the initial
 * rate; once a pass completes the cursor wraps and the length keeps sweeping at the lower
 * continuous rate, so names claimed since the last pass are picked up.
 */
@Component
@Slf4j
public class NameSweepJob implements JobProvider {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";
    private static final int BATCH_SIZE = 1000;
    private static final int CHUNK_SIZE = 10;
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(1);

    private final NameSweepRepository nameSweepRepository;
    private final MojangService mojangService;
    private final PlayerService playerService;
    private final PlayerSubmitService playerSubmitService;
    private final List<Integer> lengths;
    private final double initialRequestsPerSecond;
    private final double continuousRequestsPerSecond;

    public NameSweepJob(
            NameSweepRepository nameSweepRepository,
            MojangService mojangService,
            PlayerService playerService,
            PlayerSubmitService playerSubmitService,
            @Value("${mc-utils.name-sweep.lengths:4,5}") List<Integer> lengths,
            @Value("${mc-utils.name-sweep.initial-requests-per-second:50}") double initialRequestsPerSecond,
            @Value("${mc-utils.name-sweep.continuous-requests-per-second:10}") double continuousRequestsPerSecond
    ) {
        this.nameSweepRepository = nameSweepRepository;
        this.mojangService = mojangService;
        this.playerService = playerService;
        this.playerSubmitService = playerSubmitService;
        this.lengths = lengths;
        this.initialRequestsPerSecond = initialRequestsPerSecond;
        this.continuousRequestsPerSecond = continuousRequestsPerSecond;
    }

    @Override
    public List<Job> jobs() {
        return this.lengths.stream().<Job>map(LengthSweep::new).toList();
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

    /**
     * Sweeps one name length, one batch per cycle, resuming from the persisted cursor.
     */
    private class LengthSweep implements Job {
        private final int length;
        private final long total;
        private long next;
        private int passes;
        private AdaptiveRateLimiter rateLimiter;

        LengthSweep(int length) {
            this.length = length;
            this.total = totalNames(length);
        }

        @Override
        public String name() {
            return "name-sweep-" + this.length;
        }

        @Override
        public JobTrigger trigger() {
            return JobTrigger.continuous(Duration.ZERO);
        }

        @Override
        public Duration errorBackoff() {
            return FAILURE_BACKOFF;
        }

        @Override
        public JobResult run(JobContext context) {
            if (this.rateLimiter == null) {
                NameSweepRepository.Progress progress = nameSweepRepository.findProgress(this.length)
                        .orElse(new NameSweepRepository.Progress(0, 0));
                this.next = progress.nextIndex();
                this.passes = progress.passes();
                this.rateLimiter = newRateLimiter(this.passes);
                log.info("Name sweep for length {} starting at {}/{} (pass {})", this.length, this.next, this.total, this.passes + 1);
            }

            long end = Math.min(this.next + BATCH_SIZE, this.total);
            sweepBatch(this.length, this.next, end, this.rateLimiter, context);
            if (!context.isRunning()) {
                return JobResult.IDLE;
            }
            this.next = end;
            if (this.next >= this.total) {
                this.next = 0;
                this.passes++;
                this.rateLimiter = newRateLimiter(this.passes);
                log.info("Name sweep for length {} finished pass {}, continuing at {} req/s", this.length, this.passes, continuousRequestsPerSecond);
            }
            nameSweepRepository.saveProgress(this.length, this.next, this.passes);
            return JobResult.WORKED;
        }
    }

    private AdaptiveRateLimiter newRateLimiter(int passes) {
        return new AdaptiveRateLimiter(passes == 0 ? this.initialRequestsPerSecond : this.continuousRequestsPerSecond);
    }

    /**
     * Returns only once every chunk of the batch has been looked up, so the cursor never advances
     * past a name that was not checked.
     */
    private void sweepBatch(int length, long start, long end, AdaptiveRateLimiter rateLimiter, JobContext context) {
        List<String> names = new ArrayList<>();
        for (long index = start; index < end; index++) {
            names.add(nameAt(index, length));
        }
        List<String> untracked = this.nameSweepRepository.selectUntracked(names);

        List<List<String>> pending = new ArrayList<>();
        for (int offset = 0; offset < untracked.size(); offset += CHUNK_SIZE) {
            pending.add(List.copyOf(untracked.subList(offset, Math.min(offset + CHUNK_SIZE, untracked.size()))));
        }

        while (!pending.isEmpty() && context.isRunning()) {
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
}
