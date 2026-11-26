package xyz.mcutils.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;

/**
 * Single shared rate limiter for all outbound Mojang profile traffic (background player
 * refresh, submit queue, and on-demand refreshes). The configured rate is a ceiling: callers
 * report rate limits and successes so the shared rate backs off and recovers together.
 */
@Service
public class MojangRateLimiter {

    private final AdaptiveRateLimiter rateLimiter;

    public MojangRateLimiter(@Value("${mc-utils.player-refresh.mojang-rate-limit:600}") double rateLimit) {
        this.rateLimiter = new AdaptiveRateLimiter("mojang-profiles", rateLimit);
    }

    /**
     * Blocks until a permit is available.
     */
    public void acquire() {
        this.rateLimiter.acquire();
    }

    public void onRateLimited() {
        this.rateLimiter.slowDown();
    }

    public void onSuccess() {
        this.rateLimiter.speedUp();
    }
}
