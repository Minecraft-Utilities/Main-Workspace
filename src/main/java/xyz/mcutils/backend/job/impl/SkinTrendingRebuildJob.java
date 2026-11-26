package xyz.mcutils.backend.job.impl;

import org.springframework.stereotype.Component;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.service.SkinService;

/**
 * Rebuilds the skin trending heat.
 */
@Component
public class SkinTrendingRebuildJob implements Job {
    private final SkinService skinService;

    public SkinTrendingRebuildJob(SkinService skinService) {
        this.skinService = skinService;
    }

    @Override
    public String name() {
        return "skin-trending-rebuild";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.cron("0 0 * * * *");
    }

    @Override
    public JobResult run(JobContext context) {
        this.skinService.updateTrendingHeat();
        return JobResult.WORKED;
    }
}
