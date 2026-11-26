package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.repository.postgres.PreviousUsernameCheckRepository;
import xyz.mcutils.backend.service.MetricService;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exposes the number of due previous-username checks as a gauge backed by an in-memory snapshot,
 * so scraping /metrics never issues a count query per scrape.
 */
public class PreviousUsernameChecksDueMetric extends GaugeWithCallbackMetric {

    private static final AtomicLong DUE = new AtomicLong();

    private final PreviousUsernameCheckRepository previousUsernameCheckRepository;

    public PreviousUsernameChecksDueMetric(PreviousUsernameCheckRepository previousUsernameCheckRepository) {
        super(GaugeWithCallback.builder()
                .name("previous_username_checks_due")
                .callback(callback -> callback.call(DUE.get()))
                .register(MetricService.REGISTRY));
        this.previousUsernameCheckRepository = previousUsernameCheckRepository;
    }

    /**
     * Refreshes the in-memory snapshot. Called by a scheduler, never from the scrape callback.
     */
    public void refresh() {
        DUE.set(this.previousUsernameCheckRepository.countDue(Instant.now()));
    }
}
