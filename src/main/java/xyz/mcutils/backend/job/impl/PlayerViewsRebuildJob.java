package xyz.mcutils.backend.job.impl;

import org.springframework.stereotype.Component;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.service.PlayerViewService;

/**
 * Rebuilds the monthly player view snapshots.
 */
@Component
public class PlayerViewsRebuildJob implements Job {
    private final PlayerViewService playerViewService;

    public PlayerViewsRebuildJob(PlayerViewService playerViewService) {
        this.playerViewService = playerViewService;
    }

    @Override
    public String name() {
        return "player-views-rebuild";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.cron("0 0 * * * *");
    }

    @Override
    public JobResult run(JobContext context) {
        this.playerViewService.rebuildMonthlyViews();
        return JobResult.WORKED;
    }
}
