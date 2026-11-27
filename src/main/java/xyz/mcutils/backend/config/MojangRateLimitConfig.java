package xyz.mcutils.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;

/**
 * Each stream of outbound Mojang profile traffic has its own limiter, so a burst of rate limits
 * on one does not slow the other down.
 */
@Configuration
public class MojangRateLimitConfig {

    /**
     * Shared by the background player refresh and on-demand refreshes.
     */
    @Bean
    public AdaptiveRateLimiter refreshRateLimiter(@Value("${mc-utils.player-refresh.mojang-rate-limit:600}") double rateLimit) {
        return new AdaptiveRateLimiter("mojang-profiles-refresh", rateLimit);
    }

    @Bean
    public AdaptiveRateLimiter submitRateLimiter(@Value("${mc-utils.player-submit.mojang-rate-limit:600}") double rateLimit) {
        return new AdaptiveRateLimiter("mojang-profiles-submit", rateLimit);
    }
}
