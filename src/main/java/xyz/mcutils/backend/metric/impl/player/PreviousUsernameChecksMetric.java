package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks the outcome of released usernames checked through Mojang's bulk name lookup.
 *
 * <p>Outcomes:
 * <ul>
 *   <li>{@code new_player} – the name now belongs to an untracked account, which was added to the submit queue</li>
 *   <li>{@code already_queued} – the name belongs to an untracked account that was already in the submit queue</li>
 *   <li>{@code already_tracked} – the name still belongs to a tracked player, or its account is already known</li>
 *   <li>{@code unclaimed} – Mojang returned no profile for the name</li>
 *   <li>{@code failed} – the lookup chunk failed and was postponed</li>
 * </ul>
 */
public class PreviousUsernameChecksMetric extends Metric<PreviousUsernameChecksMetric.Holder> {
    public enum Outcome {
        NEW_PLAYER("new_player"),
        ALREADY_TRACKED("already_tracked"),
        ALREADY_QUEUED("already_queued"),
        UNCLAIMED("unclaimed"),
        FAILED("failed");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public PreviousUsernameChecksMetric() {
        super(new Holder(Counter.builder()
                .name("previous_username_checks_total")
                .help("Total previous usernames checked against Mojang, by outcome")
                .labelNames("outcome")
                .register(MetricService.REGISTRY)));
    }

    public void inc(Outcome outcome, long n) {
        if (n > 0) {
            getValue().counter.labelValues(outcome.label()).inc(n);
        }
    }

    public record Holder(Counter counter) {}
}
