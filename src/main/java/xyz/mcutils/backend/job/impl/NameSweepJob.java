package xyz.mcutils.backend.job.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobProvider;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.metric.impl.player.NameSweepChecksMetric;
import xyz.mcutils.backend.repository.postgres.NameSweepRepository;
import xyz.mcutils.backend.service.NameResolveService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(1);

    private final NameSweepRepository nameSweepRepository;
    private final NameResolveService nameResolveService;
    private final List<Integer> lengths;
    private final double initialRequestsPerSecond;
    private final double continuousRequestsPerSecond;

    public NameSweepJob(
            NameSweepRepository nameSweepRepository,
            NameResolveService nameResolveService,
            @Value("${mc-utils.name-sweep.lengths:4,5}") List<Integer> lengths,
            @Value("${mc-utils.name-sweep.initial-requests-per-second:50}") double initialRequestsPerSecond,
            @Value("${mc-utils.name-sweep.continuous-requests-per-second:10}") double continuousRequestsPerSecond
    ) {
        this.nameSweepRepository = nameSweepRepository;
        this.nameResolveService = nameResolveService;
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

    private void sweepBatch(int length, long start, long end, AdaptiveRateLimiter rateLimiter, JobContext context) {
        List<String> names = new ArrayList<>();
        for (long index = start; index < end; index++) {
            names.add(nameAt(index, length));
        }
        this.nameResolveService.resolve(names, NameSweepChecksMetric.Source.ENUMERATION, length, rateLimiter, context);
    }
}
