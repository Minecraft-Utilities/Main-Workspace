package xyz.mcutils.backend.job.impl;

import org.springframework.stereotype.Component;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.service.MaxMindService;

/**
 * Refreshes the MaxMind IP database.
 */
@Component
public class MaxMindUpdateJob implements Job {
    private final MaxMindService maxMindService;

    public MaxMindUpdateJob(MaxMindService maxMindService) {
        this.maxMindService = maxMindService;
    }

    @Override
    public String name() {
        return "maxmind-update";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.cron("0 0 2 * * *");
    }

    @Override
    public JobResult run(JobContext context) {
        this.maxMindService.scheduledDatabaseUpdate();
        return JobResult.WORKED;
    }
}
