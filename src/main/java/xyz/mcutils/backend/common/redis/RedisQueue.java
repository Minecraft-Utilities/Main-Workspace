package xyz.mcutils.backend.common.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed FIFO queue using a list, with a companion set ({@code queueName + "-ids"}) that rejects
 * items whose dedupe key is already queued or in flight.
 * <p>
 * Producers and consumers use separate templates: a blocking pop occupies its connection until it returns, so
 * enqueues and size checks must never share one with it.
 */
public final class RedisQueue {

    /**
     * Without an expiry, an item that is popped but never completed would block its dedupe key forever.
     */
    private static final Duration DEDUPE_TTL = Duration.ofHours(24);

    // SADD decides per item, so two concurrent enqueues of one key cannot both push it.
    private static final RedisScript<Long> ENQUEUE_SCRIPT = new DefaultRedisScript<>("""
            local added = 0
            for i = 2, #ARGV, 2 do
                if redis.call('SADD', KEYS[2], ARGV[i + 1]) == 1 then
                    redis.call('RPUSH', KEYS[1], ARGV[i])
                    added = added + 1
                end
            end
            redis.call('EXPIRE', KEYS[2], ARGV[1])
            return added
            """, Long.class);

    private final RedisTemplate<String, String> producer;
    private final RedisTemplate<String, String> consumer;
    private final String listKey;
    private final String dedupeSetKey;

    public RedisQueue(RedisTemplate<String, String> producer, RedisTemplate<String, String> consumer, String queueName) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.listKey = Objects.requireNonNull(queueName, "queueName");
        this.dedupeSetKey = queueName + "-ids";
    }

    /**
     * Atomically enqueues the items whose dedupe keys are not already queued or in flight.
     */
    public EnqueueResult enqueue(List<QueueItem> items) {
        if (items == null || items.isEmpty()) {
            return EnqueueResult.EMPTY;
        }
        String[] args = new String[1 + items.size() * 2];
        args[0] = String.valueOf(DEDUPE_TTL.toSeconds());
        for (int i = 0; i < items.size(); i++) {
            args[1 + i * 2] = items.get(i).payload();
            args[2 + i * 2] = items.get(i).dedupeKey();
        }
        Long added = producer.execute(ENQUEUE_SCRIPT, List.of(listKey, dedupeSetKey), (Object[]) args);
        int enqueued = added == null ? 0 : added.intValue();
        return new EnqueueResult(enqueued, items.size() - enqueued);
    }

    /**
     * Pushes popped items back without touching the dedupe set: an in-flight item keeps its key until it is
     * completed through {@link #removeDedupeKeys}.
     */
    public void requeue(Collection<String> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        producer.opsForList().rightPushAll(listKey, payloads.toArray(new String[0]));
    }

    public void removeDedupeKeys(Collection<String> dedupeKeys) {
        if (dedupeKeys == null || dedupeKeys.isEmpty()) {
            return;
        }
        producer.opsForSet().remove(dedupeSetKey, dedupeKeys.toArray());
    }

    /**
     * Blocks until an item is available or {@code emptyTimeout} elapses, then drains up to {@code batchSize} items.
     */
    public Optional<List<String>> blockingTakeBatch(int batchSize, Duration emptyTimeout) {
        if (batchSize <= 0) {
            return Optional.empty();
        }
        String first = consumer.opsForList().leftPop(listKey, emptyTimeout.toSeconds(), TimeUnit.SECONDS);
        if (first == null) {
            return Optional.empty();
        }
        List<String> batch = new ArrayList<>(batchSize);
        batch.add(first);
        if (batchSize > 1) {
            List<String> rest = producer.opsForList().leftPop(listKey, batchSize - 1);
            if (rest != null) {
                batch.addAll(rest);
            }
        }
        return Optional.of(batch);
    }

    public long size() {
        Long size = producer.opsForList().size(listKey);
        return size != null ? size : 0L;
    }

    public record QueueItem(String payload, String dedupeKey) {
        public QueueItem {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(dedupeKey, "dedupeKey");
        }

        public static QueueItem of(String payload) {
            return new QueueItem(payload, payload);
        }
    }

    public record EnqueueResult(int enqueued, int alreadyQueued) {
        public static final EnqueueResult EMPTY = new EnqueueResult(0, 0);
    }
}
