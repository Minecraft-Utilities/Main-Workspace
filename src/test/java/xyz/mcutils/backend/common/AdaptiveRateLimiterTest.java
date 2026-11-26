package xyz.mcutils.backend.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveRateLimiterTest {
    @Test
    void startsAtAQuarterOfTheCeiling() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter("test-start", 100);

        assertEquals(25, limiter.getRate(), 1e-6);
    }

    @Test
    void slowDownCutsAFifthAndOnlyOncePerBurst() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter("test-slow", 100);

        limiter.slowDown();
        limiter.slowDown();

        assertEquals(20, limiter.getRate(), 1e-6);
    }

    @Test
    void speedUpNeverPassesTheCeiling() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter("test-fast", 10);

        for (int i = 0; i < 1000; i++) {
            limiter.speedUp();
        }

        assertEquals(10, limiter.getRate(), 1e-6);
    }

    @Test
    void registersByName() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter("test-registered", 10);

        assertSame(limiter, AdaptiveRateLimiter.registered().get("test-registered"));
        assertTrue(AdaptiveRateLimiter.registered().containsKey("test-registered"));
    }
}
