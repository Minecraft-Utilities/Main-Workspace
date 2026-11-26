package xyz.mcutils.backend.common;

import com.google.common.util.concurrent.RateLimiter;

import java.time.Duration;

/**
 * A request rate that halves when the upstream pushes back and creeps back up to a ceiling while
 * requests succeed. Shared by every caller of one upstream pool so a burst of rejections slows
 * them all down together.
 */
@SuppressWarnings("UnstableApiUsage")
public class AdaptiveRateLimiter {
    private static final double MIN_REQUESTS_PER_SECOND = 1;
    private static final double RATE_INCREMENT = 0.05;
    private static final long SLOW_DOWN_COOLDOWN_NANOS = Duration.ofSeconds(2).toNanos();

    private final RateLimiter limiter;
    private final double maxRequestsPerSecond;
    private final Object rateLock = new Object();
    private long lastSlowDownNanos = System.nanoTime() - SLOW_DOWN_COOLDOWN_NANOS;

    public AdaptiveRateLimiter(double maxRequestsPerSecond) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.limiter = RateLimiter.create(Math.max(MIN_REQUESTS_PER_SECOND, maxRequestsPerSecond / 4));
    }

    public void acquire() {
        this.limiter.acquire();
    }

    public double getRate() {
        return this.limiter.getRate();
    }

    /**
     * One burst of rejections hits many in-flight requests at once, so only the first within the
     * cooldown halves the rate; otherwise a single burst would collapse it to the floor.
     */
    public void slowDown() {
        synchronized (this.rateLock) {
            long nowNanos = System.nanoTime();
            if (nowNanos - this.lastSlowDownNanos < SLOW_DOWN_COOLDOWN_NANOS) {
                return;
            }
            this.lastSlowDownNanos = nowNanos;
            this.limiter.setRate(Math.max(MIN_REQUESTS_PER_SECOND, this.limiter.getRate() / 2));
        }
    }

    public void speedUp() {
        synchronized (this.rateLock) {
            this.limiter.setRate(Math.min(this.maxRequestsPerSecond, this.limiter.getRate() + RATE_INCREMENT));
        }
    }
}
