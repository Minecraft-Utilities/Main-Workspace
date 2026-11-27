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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Access to the trained username models and the sweep cursors that point into them.
 */
@Repository
public class NameModelRepository {
    public record Progress(NameModel.Cursor cursor, long consumed, int passes) {}

    public record StoredModel(NameModel model, long trainedOn) {}

    private static final int TRAINING_PARTITIONS = 16;
    private static final int TRAINING_PARALLELISM = 2;

    private final JdbcClient jdbc;
    private final Semaphore trainingPermits = new Semaphore(TRAINING_PARALLELISM);

    public NameModelRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Feeds the tracked names of {@code length} to {@code trainer}.
     *
     * <p>The table is split into {@link #TRAINING_PARTITIONS} uuid ranges that are read
     * concurrently, so no single result holds more than a sixteenth of the names. At most
     * {@link #TRAINING_PARALLELISM} run at once across all lengths, because each holds a pooled
     * connection for minutes and training must not starve the API.
     *
     * @return the number of names the model was trained on
     */
    public long train(int length, NameModel.Trainer trainer) {
        AtomicLong names = new AtomicLong();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> partitions = new ArrayList<>();
            for (int partition = 0; partition < TRAINING_PARTITIONS; partition++) {
                int index = partition;
                partitions.add(pool.submit(() -> {
                    trainPartition(length, index, trainer, names);
                    return null;
                }));
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
        return names.get();
    }

    private void trainPartition(int length, int partition, NameModel.Trainer trainer, AtomicLong names) throws InterruptedException {
        this.trainingPermits.acquire();
        try {
            String low = Integer.toHexString(partition) + "0000000-0000-0000-0000-000000000000";
            String high = partition + 1 < TRAINING_PARTITIONS
                    ? Integer.toHexString(partition + 1) + "0000000-0000-0000-0000-000000000000"
                    : null;
            List<String> usernames = this.jdbc.sql("""
                            SELECT lower(username)
                            FROM players
                            WHERE id >= CAST(:low AS uuid) AND (CAST(:high AS uuid) IS NULL OR id < CAST(:high AS uuid))
                              AND char_length(username) = :length AND username ~ '^[A-Za-z0-9_]+$'
                            """)
                    .param("length", length)
                    .param("low", low)
                    .param("high", high, Types.VARCHAR)
                    .query(String.class)
                    .list();
            for (String username : usernames) {
                trainer.add(username);
            }
            names.addAndGet(usernames.size());
        } finally {
            this.trainingPermits.release();
        }
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
