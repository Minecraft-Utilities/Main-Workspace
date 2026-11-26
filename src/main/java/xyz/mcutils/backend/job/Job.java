package xyz.mcutils.backend.job;

import java.time.Duration;

/**
 * A background unit of work. Any Spring bean implementing this, or any {@link JobProvider}, is
 * started by {@link JobRunner}, which owns the loop, scheduling, shutdown, error backoff and the
 * {@code background_job_*} metrics. A job only implements one cycle of its work.
 *
 * <p>A job is switched off with {@code mc-utils.jobs.<name>.enabled: false}.
 */
public interface Job {

    /**
     * Stable identifier used in logs, metrics and config. Lowercase and dash-separated.
     */
    String name();

    JobTrigger trigger();

    /**
     * Runs one cycle. An exception fails the cycle, is logged and counted, and for continuous jobs
     * is followed by {@link #errorBackoff()}.
     */
    JobResult run(JobContext context) throws Exception;

    default Duration errorBackoff() {
        return Duration.ofSeconds(5);
    }

    /**
     * A job whose cycle is a single task, for work that has no idle state.
     */
    static Job scheduled(String name, JobTrigger trigger, ThrowingRunnable task) {
        return new Job() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public JobTrigger trigger() {
                return trigger;
            }

            @Override
            public JobResult run(JobContext context) throws Exception {
                task.run();
                return JobResult.WORKED;
            }
        };
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
