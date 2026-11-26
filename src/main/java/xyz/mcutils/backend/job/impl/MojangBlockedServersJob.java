package xyz.mcutils.backend.job.impl;

import org.springframework.stereotype.Component;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.service.MojangService;

/**
 * Refreshes Mojang's blocked servers list.
 */
@Component
public class MojangBlockedServersJob implements Job {
    private final MojangService mojangService;

    public MojangBlockedServersJob(MojangService mojangService) {
        this.mojangService = mojangService;
    }

    @Override
    public String name() {
        return "mojang-blocked-servers";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.cron("0 0 0 * * *");
    }

    @Override
    public JobResult run(JobContext context) {
        this.mojangService.updateBlockedServers();
        return JobResult.WORKED;
    }
}
