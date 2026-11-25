package xyz.mcutils.backend.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Computes per-player refresh intervals from a gamma-Poisson estimate of each player's change rate.
 * <p>
 * Every refresh adds the elapsed time to {@code observedDays} and any detected change to {@code changeCount};
 * both decay with {@link #EVIDENCE_HALF_LIFE_DAYS} so old behaviour fades. The next refresh is scheduled for
 * when the chance of a change since the last check reaches {@link #TARGET_CHANGE_PROBABILITY}, which spends
 * refreshes on players who actually change and leaves the dormant majority at {@link #IDLE_INTERVAL}.
 * <p>
 * Parameters were chosen with {@code benchmark/refresh-sim/sim.py}.
 */
public final class PlayerRefreshSchedule {

    public static final Duration BASE_INTERVAL = Duration.ofHours(3);
    public static final Duration MIN_INTERVAL = Duration.ofMinutes(20);
    public static final Duration IDLE_INTERVAL = Duration.ofDays(180);
    public static final Duration FAILURE_BACKOFF = Duration.ofMinutes(30);
    /**
     * Re-check shortly after a detected change: a player who just changed is far likelier to change again
     * (skin tweaks, name-drop sprees). Self-terminating, as a clean check falls back to the estimated cadence.
     */
    public static final Duration BURST_INTERVAL = Duration.ofHours(6);

    private static final double EVIDENCE_HALF_LIFE_DAYS = 90.0;
    private static final double TARGET_CHANGE_PROBABILITY = 0.1;
    /** Prior of 0.02 changes per 30 days of observation, so an unseen player starts near the idle ceiling. */
    private static final double PRIOR_CHANGES = 0.02;
    private static final double PRIOR_DAYS = 30.0;
    private static final double DECAY_PER_DAY = Math.log(2) / EVIDENCE_HALF_LIFE_DAYS;
    private static final double MILLIS_PER_DAY = 86_400_000.0;
    private static final double LN_NO_CHANGE = -Math.log1p(-TARGET_CHANGE_PROBABILITY);

    /**
     * Minimum decayed change count for a player to join the "hot" refresh tier (claimed before the
     * cold tier, ordered by count descending).
     * <p>
     * MUST stay in sync with the partial index predicates in {@code V52__player_refresh_state.sql} and the SQL
     * literals in {@code PlayerRefreshRepository} (kept as literals so Postgres can use the partial indexes).
     */
    public static final double HOT_CHANGE_THRESHOLD = 0.5;

    private PlayerRefreshSchedule() {
    }

    /**
     * Decayed evidence after a refresh, plus the interval until the next one.
     */
    public record Evidence(double changeCount, double observedDays, Duration nextInterval) {
    }

    public static Evidence update(double changeCount, double observedDays, Instant lastUpdated, Instant now, boolean hadChanges) {
        double elapsedDays = Math.max(0, Duration.between(lastUpdated, now).toMillis() / MILLIS_PER_DAY);
        double decay = Math.exp(-DECAY_PER_DAY * elapsedDays);
        double count = changeCount * decay + (hadChanges ? 1 : 0);
        double observed = observedDays * decay + elapsedDays;
        Duration interval = hadChanges ? BURST_INTERVAL : intervalFor(count, observed);
        return new Evidence(count, observed, interval);
    }

    /**
     * Days until the chance of a change reaches the target, clamped to [{@link #MIN_INTERVAL}, {@link #IDLE_INTERVAL}].
     */
    public static Duration intervalFor(double changeCount, double observedDays) {
        double rate = (PRIOR_CHANGES + changeCount) / (PRIOR_DAYS + observedDays);
        long intervalMs = (long) (LN_NO_CHANGE / rate * MILLIS_PER_DAY);
        return Duration.ofMillis(Math.clamp(intervalMs, MIN_INTERVAL.toMillis(), IDLE_INTERVAL.toMillis()));
    }
}
