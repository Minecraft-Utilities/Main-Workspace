package xyz.mcutils.backend.job.impl;

import org.springframework.stereotype.Component;
import xyz.mcutils.backend.job.Job;
import xyz.mcutils.backend.job.JobContext;
import xyz.mcutils.backend.job.JobResult;
import xyz.mcutils.backend.job.JobTrigger;
import xyz.mcutils.backend.service.MetricService;

import java.time.Duration;

/**
 * Refreshes the in-memory snapshots behind metrics that must not query on the scrape path.
 */
@Component
public class MetricsRefreshJob implements Job {
    private final MetricService metricService;

    public MetricsRefreshJob(MetricService metricService) {
        this.metricService = metricService;
    }

    @Override
    public String name() {
        return "metrics-refresh";
    }

    @Override
    public JobTrigger trigger() {
        return JobTrigger.periodic(Duration.ofSeconds(30), Duration.ofSeconds(10));
    }

    @Override
    public JobResult run(JobContext context) {
        this.metricService.refreshCachedMetrics();
        return JobResult.WORKED;
    }
}
