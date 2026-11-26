package xyz.mcutils.backend.metric.impl.mojang;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.common.AdaptiveRateLimiter;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Exposes the current request rate of every adaptive rate limiter, so how far a limiter has
 * backed off from its ceiling is visible per upstream pool.
 */
public class RateLimiterRateMetric extends GaugeWithCallbackMetric {
    public RateLimiterRateMetric() {
        super(GaugeWithCallback.builder()
                .name("adaptive_rate_limiter_rate")
                .help("Current requests per second allowed by each adaptive rate limiter")
                .labelNames("limiter")
                .callback(callback -> AdaptiveRateLimiter.registered()
                        .forEach((name, limiter) -> callback.call(limiter.getRate(), name)))
                .register(MetricService.REGISTRY));
    }
}
