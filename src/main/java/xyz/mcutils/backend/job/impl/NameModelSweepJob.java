package xyz.mcutils.backend.job.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;
import xyz.mcutils.backend.common.NameModel;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobProvider;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.metric.impl.player.NameSweepChecksMetric;
import xyz.mcutils.backend.repository.postgres.NameModelRepository;
import xyz.mcutils.backend.service.NameResolveService;

import java.time.Duration;
import java.util.List;

/**
 * Sweeps the usernames of the configured lengths from most to least likely according to a
 * character model of the tracked names, for lengths too long to enumerate.
 *
 * <p>Real names are heavily clustered, so a candidate budget covers most of the names that exist
 * at a small fraction of the cost of a full enumeration. When a length's budget is spent its
 * ranking restarts to pick up names claimed since.
 *
 * <p>All lengths draw from one rate limiter, so the model sweeps together stay within a single
 * request budget however many lengths are configured.
 */
@Component
@Slf4j
public class NameModelSweepJob implements JobProvider {
    private static final int BATCH_SIZE = 1000;
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(1);

    private final NameModelRepository nameModelRepository;
    private final NameResolveService nameResolveService;
    private final List<Integer> lengths;
    private final long candidatesPerLength;
    private final AdaptiveRateLimiter rateLimiter;

    public NameModelSweepJob(
            NameModelRepository nameModelRepository,
            NameResolveService nameResolveService,
            @Value("${mc-utils.name-sweep.model.lengths:}") List<Integer> lengths,
            @Value("${mc-utils.name-sweep.model.candidates-per-length:1000000000}") long candidatesPerLength,
            @Value("${mc-utils.name-sweep.model.requests-per-second:150}") double requestsPerSecond
    ) {
        this.nameModelRepository = nameModelRepository;
        this.nameResolveService = nameResolveService;
        this.lengths = lengths;
        this.candidatesPerLength = candidatesPerLength;
        this.rateLimiter = new AdaptiveRateLimiter(requestsPerSecond);
    }

    @Override
    public List<Job> jobs() {
        return this.lengths.stream().<Job>map(ModelSweep::new).toList();
    }

    /**
     * Sweeps one name length, one batch per cycle, resuming from the persisted cursor.
     */
    private class ModelSweep implements Job {
        private final int length;
        private NameModel model;
        private NameModel.Cursor cursor;
        private long consumed;
        private int passes;

        ModelSweep(int length) {
            this.length = length;
        }

        @Override
        public String name() {
            return "name-model-sweep-" + this.length;
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
            if (this.model == null) {
                load();
            }

            int limit = (int) Math.min(BATCH_SIZE, candidatesPerLength - this.consumed);
            NameModel.Page page = this.model.next(this.cursor, limit);
            nameResolveService.resolve(page.names(), NameSweepChecksMetric.Source.MODEL, this.length, rateLimiter, context);
            if (!context.isRunning()) {
                return JobResult.IDLE;
            }

            this.cursor = page.next();
            this.consumed += page.names().size();
            if (page.exhausted() || this.consumed >= candidatesPerLength) {
                this.cursor = this.model.start();
                this.consumed = 0;
                this.passes++;
                log.info("Name model sweep for length {} finished pass {}", this.length, this.passes);
            }
            nameModelRepository.saveProgress(this.length, new NameModelRepository.Progress(this.cursor, this.consumed, this.passes));
            return JobResult.WORKED;
        }

        private void load() {
            NameModelRepository.StoredModel stored = nameModelRepository.findModel(this.length).orElseGet(this::train);
            this.model = stored.model();
            NameModelRepository.Progress progress = nameModelRepository.findProgress(this.length)
                    .orElse(new NameModelRepository.Progress(this.model.start(), 0, 0));
            this.cursor = progress.cursor();
            this.consumed = progress.consumed();
            this.passes = progress.passes();
            log.info("Name model sweep for length {} starting at {}/{} candidates (pass {}, model trained on {} names)",
                    this.length, this.consumed, candidatesPerLength, this.passes + 1, stored.trainedOn());
        }

        private NameModelRepository.StoredModel train() {
            log.info("Training name model for length {}", this.length);
            NameModel.Trainer trainer = new NameModel.Trainer(this.length);
            long trainedOn = nameModelRepository.train(this.length, trainer);
            NameModel trained = trainer.build();
            nameModelRepository.saveModel(this.length, trained, trainedOn);
            return new NameModelRepository.StoredModel(trained, trainedOn);
        }
    }
}
