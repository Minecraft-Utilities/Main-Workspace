package xyz.mcutils.backend.job;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

/**
 * Metrics recorded by {@link JobRunner} for every job. The label is {@code name} because {@code job}
 * is the scrape target label and would be renamed on ingestion.
 *
 * <ul>
 *   <li>{@code background_job_cycles_total{name,result}} – cycles by result: {@code worked}, {@code idle} or {@code failed}</li>
 *   <li>{@code background_job_cycle_duration_seconds{name}} – duration of cycles that worked or failed, idle cycles excluded</li>
 *   <li>{@code background_job_last_success_timestamp_seconds{name}} – end of the last cycle that did not fail</li>
 *   <li>{@code background_job_running{name}} – 1 while the job's loop is alive</li>
 * </ul>
 */
public class JobMetric {
    private final Counter cycles;
    private final Histogram duration;
    private final Gauge lastSuccess;
    private final Gauge running;

    public JobMetric(PrometheusRegistry registry) {
        this.cycles = Counter.builder()
                .name("background_job_cycles_total")
                .help("Total background job cycles, by result")
                .labelNames("name", "result")
                .register(registry);
        this.duration = Histogram.builder()
                .name("background_job_cycle_duration_seconds")
                .help("Duration of background job cycles that worked or failed")
                .labelNames("name")
                .classicUpperBounds(0.1, 0.5, 1, 5, 15, 60, 300, 900, 3600)
                .register(registry);
        this.lastSuccess = Gauge.builder()
                .name("background_job_last_success_timestamp_seconds")
                .help("Unix time the last background job cycle that did not fail finished")
                .labelNames("name")
                .register(registry);
        this.running = Gauge.builder()
                .name("background_job_running")
                .help("Whether the background job's loop is alive")
                .labelNames("name")
                .register(registry);
    }

    void recordCycle(String job, JobResult result, double durationSeconds) {
        this.cycles.labelValues(job, result == JobResult.WORKED ? "worked" : "idle").inc();
        if (result == JobResult.WORKED) {
            this.duration.labelValues(job).observe(durationSeconds);
        }
        this.lastSuccess.labelValues(job).set(System.currentTimeMillis() / 1000.0);
    }

    void recordFailure(String job, double durationSeconds) {
        this.cycles.labelValues(job, "failed").inc();
        this.duration.labelValues(job).observe(durationSeconds);
    }

    void setRunning(String job, boolean alive) {
        this.running.labelValues(job).set(alive ? 1 : 0);
    }
}
