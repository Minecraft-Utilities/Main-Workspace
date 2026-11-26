package xyz.mcutils.backend.job;

import java.time.Duration;

/**
 * When a job's cycles run.
 */
public sealed interface JobTrigger {

    /**
     * Runs cycles back to back; {@code idleDelay} is the wait after a cycle that returned
     * {@link JobResult#IDLE}.
     */
    record Continuous(Duration idleDelay) implements JobTrigger {}

    /**
     * Runs on a Spring cron expression in the system time zone, optionally once at startup too.
     */
    record Cron(String expression, boolean runOnStart) implements JobTrigger {}

    /**
     * Runs {@code interval} after the previous cycle finished, the first after {@code initialDelay}.
     */
    record Periodic(Duration interval, Duration initialDelay) implements JobTrigger {}

    static JobTrigger continuous(Duration idleDelay) {
        return new Continuous(idleDelay);
    }

    static JobTrigger cron(String expression) {
        return new Cron(expression, false);
    }

    static JobTrigger cronAndOnStart(String expression) {
        return new Cron(expression, true);
    }

    static JobTrigger periodic(Duration interval, Duration initialDelay) {
        return new Periodic(interval, initialDelay);
    }
}
