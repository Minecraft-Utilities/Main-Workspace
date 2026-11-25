package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Counter for players newly inserted into the database.
 * <p>
 * Rows for players that already existed are not counted, so the value tracks discovery of new
 * accounts rather than submit throughput.
 */
public class PlayersCreatedMetric extends Metric<PlayersCreatedMetric.Holder> {

    public PlayersCreatedMetric() {
        super(new Holder(Counter.builder().name("players_created_total").help("Total number of players newly inserted into the database").register(MetricService.REGISTRY)));
    }

    public void inc(long count) {
        if (count > 0) {
            getValue().counter.inc(count);
        }
    }

    public record Holder(Counter counter) {}
}
