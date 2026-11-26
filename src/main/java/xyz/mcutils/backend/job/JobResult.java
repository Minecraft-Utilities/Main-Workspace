package xyz.mcutils.backend.job;

/**
 * What a job cycle found to do, which decides whether the runner waits before the next cycle.
 */
public enum JobResult {
    /** The cycle did work, so the next one starts immediately. */
    WORKED,
    /** There was nothing to do, so the runner waits for the trigger's idle delay. */
    IDLE
}
