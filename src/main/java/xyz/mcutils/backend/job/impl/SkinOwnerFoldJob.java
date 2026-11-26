package xyz.mcutils.backend.job.impl;

import org.springframework.stereotype.Component;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.repository.postgres.SkinCurrentOwnersRepository;

import java.time.Duration;

/**
 * Folds the pending skin owner deltas written by the player triggers into the owner counts.
 */
@Component
public class SkinOwnerFoldJob implements Job {
    private final SkinCurrentOwnersRepository repository;

    public SkinOwnerFoldJob(SkinCurrentOwnersRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "skin-owner-fold";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.periodic(Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    @Override
    public JobResult run(JobContext context) {
        return this.repository.foldDeltas() > 0 ? JobResult.WORKED : JobResult.IDLE;
    }
}
