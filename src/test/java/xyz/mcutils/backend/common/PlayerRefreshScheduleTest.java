package xyz.mcutils.backend.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRefreshScheduleTest {

    private static final Instant BASE = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void zeroEvidenceSitsAtTheIdleEndOfTheRange() {
        // The prior of 0.02 changes per 30 days lands just below the idle clamp rather than on it.
        Duration interval = PlayerRefreshSchedule.intervalFor(0, 0);
        assertTrue(interval.compareTo(Duration.ofDays(150)) >= 0, "interval=" + interval);
        assertTrue(interval.compareTo(PlayerRefreshSchedule.IDLE_INTERVAL) <= 0, "interval=" + interval);
    }

    @Test
    void moreChangesBuyShorterIntervals() {
        Duration never = PlayerRefreshSchedule.intervalFor(0, 0);
        Duration monthly = PlayerRefreshSchedule.intervalFor(1, 30);
        Duration weekly = PlayerRefreshSchedule.intervalFor(4, 30);
        Duration daily = PlayerRefreshSchedule.intervalFor(30, 30);
        assertTrue(monthly.compareTo(never) < 0, "monthly=" + monthly + " never=" + never);
        assertTrue(weekly.compareTo(monthly) < 0, "weekly=" + weekly + " monthly=" + monthly);
        assertTrue(daily.compareTo(weekly) < 0, "daily=" + daily + " weekly=" + weekly);
    }

    @Test
    void intervalStaysWithinBounds() {
        double[][] samples = {{0, 0}, {1, 1}, {30, 0}, {0, 1_000_000}, {1_000, 0}};
        for (double[] sample : samples) {
            Duration interval = PlayerRefreshSchedule.intervalFor(sample[0], sample[1]);
            String label = "count=" + sample[0] + " days=" + sample[1] + " interval=" + interval;
            assertTrue(interval.compareTo(PlayerRefreshSchedule.MIN_INTERVAL) >= 0, label);
            assertTrue(interval.compareTo(PlayerRefreshSchedule.IDLE_INTERVAL) <= 0, label);
        }
    }

    @Test
    void changeBumpsCountAndSchedulesBurst() {
        PlayerRefreshSchedule.Evidence evidence = PlayerRefreshSchedule.update(0, 0, BASE, BASE, true);
        assertEquals(1.0, evidence.changeCount(), 1e-9);
        assertEquals(0.0, evidence.observedDays(), 1e-9);
        assertEquals(PlayerRefreshSchedule.BURST_INTERVAL, evidence.nextInterval());
    }

    @Test
    void decayHalvesBankedEvidenceAndBanksTheElapsedDays() {
        PlayerRefreshSchedule.Evidence evidence =
                PlayerRefreshSchedule.update(2, 4, BASE, BASE.plus(Duration.ofDays(90)), false);
        assertEquals(1.0, evidence.changeCount(), 1e-6);
        assertEquals(92.0, evidence.observedDays(), 1e-6);
        assertEquals(PlayerRefreshSchedule.intervalFor(1, 92), evidence.nextInterval());
    }

    @Test
    void cleanRefreshAddsObservedDays() {
        PlayerRefreshSchedule.Evidence evidence =
                PlayerRefreshSchedule.update(0, 10, BASE, BASE.plus(Duration.ofDays(5)), false);
        assertEquals(0.0, evidence.changeCount(), 1e-9);
        // Five more observed days, minus the decay on the ten already banked.
        assertEquals(14.62, evidence.observedDays(), 0.01);
        assertTrue(evidence.observedDays() > 10);
    }

    @Test
    void hotterPlayersAreRefreshedFarMoreOftenThanIdlePlayers() {
        Duration idle = PlayerRefreshSchedule.intervalFor(0, 0);
        Duration hot = PlayerRefreshSchedule.intervalFor(30, 30);
        assertTrue(idle.dividedBy(hot) >= 50, "idle=" + idle + " hot=" + hot);
        assertTrue(hot.compareTo(PlayerRefreshSchedule.MIN_INTERVAL) >= 0, "hot=" + hot);
    }
}
