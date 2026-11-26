package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks the outcome of usernames checked through Mojang's bulk name lookup, by source, name
 * length and outcome. Names already held by a tracked player are skipped before the lookup and
 * not counted.
 *
 * <p>Sources:
 * <ul>
 *   <li>{@code enumeration} – every name of a length, walked by index</li>
 *   <li>{@code model} – names ranked by a character model of real usernames</li>
 * </ul>
 *
 * <p>Outcomes:
 * <ul>
 *   <li>{@code new_player} – the name belongs to an untracked account, which was added to the submit queue</li>
 *   <li>{@code already_queued} – the name belongs to an untracked account that was already in the submit queue</li>
 *   <li>{@code already_tracked} – the name resolved to an account that is already tracked under another name</li>
 *   <li>{@code unclaimed} – Mojang returned no profile for the name</li>
 *   <li>{@code failed} – the lookup chunk failed and will be retried</li>
 * </ul>
 */
public class NameSweepChecksMetric extends Metric<NameSweepChecksMetric.Holder> {
    public enum Source {
        ENUMERATION("enumeration"),
        MODEL("model");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

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

    public NameSweepChecksMetric() {
        super(new Holder(Counter.builder()
                .name("name_sweep_checks_total")
                .help("Total usernames checked against Mojang, by source, name length and outcome")
                .labelNames("source", "length", "outcome")
                .register(MetricService.REGISTRY)));
    }

    public void inc(Source source, int length, Outcome outcome, long n) {
        if (n > 0) {
            getValue().counter.labelValues(source.label(), String.valueOf(length), outcome.label()).inc(n);
        }
    }

    public record Holder(Counter counter) {}
}
