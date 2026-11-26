package xyz.mcutils.backend.job.impl;

import org.springframework.stereotype.Component;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.service.ServerRegistryService;

/**
 * Refreshes the server registry.
 */
@Component
public class ServerRegistryUpdateJob implements Job {
    private final ServerRegistryService serverRegistryService;

    public ServerRegistryUpdateJob(ServerRegistryService serverRegistryService) {
        this.serverRegistryService = serverRegistryService;
    }

    @Override
    public String name() {
        return "server-registry-update";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.cronAndOnStart("0 */10 * * * *");
    }

    @Override
    public JobResult run(JobContext context) {
        this.serverRegistryService.updateRegistry();
        return JobResult.WORKED;
    }
}
