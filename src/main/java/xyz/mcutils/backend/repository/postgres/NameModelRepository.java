package xyz.mcutils.backend.repository.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import xyz.mcutils.backend.common.NameModel;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Access to the trained username models and the sweep cursors that point into them.
 */
@Repository
public class NameModelRepository {
    public record Progress(NameModel.Cursor cursor, long consumed, int passes) {}

    public record StoredModel(NameModel model, long trainedOn) {}

    private static final int TRAINING_PARTITIONS = 16;
    private static final int TRAINING_PARALLELISM = 4;

    private final JdbcClient jdbc;

    public NameModelRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Counts, in the database, which character follows each three-character context across the
     * tracked names of {@code length}, so only the aggregate crosses the wire.
     *
     * <p>The table is split into {@link #TRAINING_PARTITIONS} uuid ranges that are aggregated
     * concurrently, at most {@link #TRAINING_PARALLELISM} at a time, so training uses several cores
     * without taking the connection pool from the API.
     *
     * @return the number of names the model was trained on
     */
    public long train(int length, NameModel.Trainer trainer) {
        long[] names = {0};
        try (ExecutorService pool = Executors.newFixedThreadPool(TRAINING_PARALLELISM)) {
            List<Future<?>> partitions = new ArrayList<>();
            for (int partition = 0; partition < TRAINING_PARTITIONS; partition++) {
                int index = partition;
                partitions.add(pool.submit(() -> trainPartition(length, index, trainer, names)));
            }
            for (Future<?> partition : partitions) {
                partition.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while training the name model", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to train the name model", e.getCause());
        }
        return names[0];
    }

    private void trainPartition(int length, int partition, NameModel.Trainer trainer, long[] names) {
        String low = Integer.toHexString(partition) + "0000000-0000-0000-0000-000000000000";
        String high = partition + 1 < TRAINING_PARTITIONS
                ? Integer.toHexString(partition + 1) + "0000000-0000-0000-0000-000000000000"
                : null;
        this.jdbc.sql("""
                        SELECT substr(padded, i, 3) AS context, substr(padded, i + 3, 1) AS symbol, count(*) AS n
                        FROM (
                            SELECT '^^^' || lower(username) AS padded
                            FROM players
                            WHERE id >= CAST(:low AS uuid) AND (CAST(:high AS uuid) IS NULL OR id < CAST(:high AS uuid))
                              AND char_length(username) = :length AND username ~ '^[A-Za-z0-9_]+$'
                        ) trained
                        CROSS JOIN generate_series(1, :length) AS i
                        GROUP BY 1, 2
                        """)
                .param("length", length)
                .param("low", low)
                .param("high", high, Types.VARCHAR)
                .query(rs -> {
                    String context = rs.getString("context");
                    long count = rs.getLong("n");
                    synchronized (trainer) {
                        trainer.add(context, rs.getString("symbol").charAt(0), count);
                        if (context.equals("^^^")) {
                            names[0] += count;
                        }
                    }
                });
    }

    public Optional<StoredModel> findModel(int length) {
        return this.jdbc.sql("SELECT model, trained_on FROM name_model WHERE length = :length")
                .param("length", length)
                .query((rs, row) -> new StoredModel(NameModel.fromBytes(rs.getBytes("model")), rs.getLong("trained_on")))
                .optional();
    }

    public void saveModel(int length, NameModel model, long trainedOn) {
        this.jdbc.sql("""
                        INSERT INTO name_model (length, model, trained_on)
                        VALUES (:length, :model, :trainedOn)
                        ON CONFLICT (length) DO NOTHING
                        """)
                .param("length", length)
                .param("model", model.toBytes())
                .param("trainedOn", trainedOn)
                .update();
    }

    public Optional<Progress> findProgress(int length) {
        return this.jdbc.sql("SELECT cost, last_name, consumed, passes FROM name_model_progress WHERE length = :length")
                .param("length", length)
                .query((rs, row) -> new Progress(
                        new NameModel.Cursor(rs.getInt("cost"), rs.getString("last_name")),
                        rs.getLong("consumed"),
                        rs.getInt("passes")))
                .optional();
    }

    public void saveProgress(int length, Progress progress) {
        this.jdbc.sql("""
                        INSERT INTO name_model_progress (length, cost, last_name, consumed, passes)
                        VALUES (:length, :cost, :lastName, :consumed, :passes)
                        ON CONFLICT (length) DO UPDATE SET cost = EXCLUDED.cost, last_name = EXCLUDED.last_name,
                            consumed = EXCLUDED.consumed, passes = EXCLUDED.passes
                        """)
                .param("length", length)
                .param("cost", progress.cursor().cost())
                .param("lastName", progress.cursor().lastName())
                .param("consumed", progress.consumed())
                .param("passes", progress.passes())
                .update();
    }
}
