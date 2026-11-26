package xyz.mcutils.backend.job.impl;

import org.springframework.stereotype.Component;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.repository.postgres.OwnerCountRepository;

import java.time.Duration;

/**
 * Folds the pending owner count deltas written by the database triggers into the skin and cape counts.
 */
@Component
public class OwnerCountFoldJob implements Job {
    private final OwnerCountRepository repository;

    public OwnerCountFoldJob(OwnerCountRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "owner-count-fold";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.periodic(Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    @Override
    public JobResult run(JobContext context) {
        return this.repository.fold() > 0 ? JobResult.WORKED : JobResult.IDLE;
    }
}
