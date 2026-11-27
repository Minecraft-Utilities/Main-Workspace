package xyz.mcutils.backend.common.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Needs a Redis; skipped when none answers. Point it at one with {@code -Dredis.test.port=...}
 * (default 6381, the benchmark Redis) and run on a network that can reach it.
 */
class RedisQueueTest {

    private LettuceConnectionFactory producerFactory;
    private LettuceConnectionFactory consumerFactory;
    private RedisTemplate<String, String> producer;
    private RedisTemplate<String, String> consumer;
    private String name;
    private RedisQueue queue;

    @BeforeEach
    void connect() {
        String host = System.getProperty("redis.test.host", "127.0.0.1");
        int port = Integer.getInteger("redis.test.port", 6381);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(2);
        producerFactory = new LettuceConnectionFactory(config);
        producerFactory.afterPropertiesSet();
        consumerFactory = new LettuceConnectionFactory(config);
        consumerFactory.setShareNativeConnection(false);
        consumerFactory.afterPropertiesSet();
        producer = template(producerFactory);
        consumer = template(consumerFactory);
        try {
            producer.opsForValue().get("ping");
        } catch (RuntimeException e) {
            Assumptions.abort("No Redis at " + host + ":" + port);
        }
        name = "test-queue-" + UUID.randomUUID();
        queue = new RedisQueue(producer, consumer, name);
    }

    @AfterEach
    void cleanup() {
        if (producer != null && name != null) {
            producer.delete(List.of(name, name + "-ids"));
        }
        if (producerFactory != null) {
            producerFactory.destroy();
        }
        if (consumerFactory != null) {
            consumerFactory.destroy();
        }
    }

    @Test
    void enqueueSkipsKeysAlreadyQueued() {
        assertEquals(new RedisQueue.EnqueueResult(2, 0), queue.enqueue(List.of(RedisQueue.QueueItem.of("a"), RedisQueue.QueueItem.of("b"))));
        assertEquals(new RedisQueue.EnqueueResult(1, 1), queue.enqueue(List.of(RedisQueue.QueueItem.of("a"), RedisQueue.QueueItem.of("c"))));

        assertEquals(List.of("a", "b", "c"), producer.opsForList().range(name, 0, -1));
        assertEquals(3, queue.size());
    }

    @Test
    void duplicateKeysInOneBatchPushOnce() {
        RedisQueue.EnqueueResult result = queue.enqueue(List.of(new RedisQueue.QueueItem("first", "k"), new RedisQueue.QueueItem("second", "k")));

        assertEquals(new RedisQueue.EnqueueResult(1, 1), result);
        assertEquals(List.of("first"), producer.opsForList().range(name, 0, -1));
    }

    @Test
    void concurrentEnqueuesOfOneKeyPushOnce() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<RedisQueue.EnqueueResult>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                return queue.enqueue(List.of(RedisQueue.QueueItem.of("same")));
            }, pool));
        }
        start.countDown();
        int enqueued = 0;
        for (CompletableFuture<RedisQueue.EnqueueResult> result : results) {
            enqueued += result.get().enqueued();
        }
        pool.shutdown();

        assertEquals(1, enqueued);
        assertEquals(1, queue.size());
    }

    @Test
    void dedupeSetExpiresAndIsRefreshedOnEveryEnqueue() {
        queue.enqueue(List.of(RedisQueue.QueueItem.of("a")));
        Long first = producer.getExpire(name + "-ids");
        assertTrue(first != null && first > 0 && first <= 86400, "ttl was " + first);

        producer.expire(name + "-ids", Duration.ofSeconds(10));
        queue.enqueue(List.of(RedisQueue.QueueItem.of("b")));
        Long refreshed = producer.getExpire(name + "-ids");
        assertTrue(refreshed != null && refreshed > 86000, "ttl was " + refreshed);
    }

    @Test
    void removedDedupeKeyCanBeQueuedAgain() {
        queue.enqueue(List.of(RedisQueue.QueueItem.of("a")));
        queue.removeDedupeKeys(List.of("a"));

        assertEquals(1, queue.enqueue(List.of(RedisQueue.QueueItem.of("a"))).enqueued());
    }

    @Test
    void requeueKeepsTheDedupeKeyOfAnInFlightItem() {
        queue.enqueue(List.of(RedisQueue.QueueItem.of("a")));
        Optional<List<String>> batch = queue.blockingTakeBatch(10, Duration.ofSeconds(1));
        assertEquals(List.of("a"), batch.orElseThrow());

        queue.requeue(batch.get());

        assertEquals(1, queue.size());
        assertEquals(0, queue.enqueue(List.of(RedisQueue.QueueItem.of("a"))).enqueued());
    }

    @Test
    void takeBatchDrainsUpToBatchSizeAndTimesOutWhenEmpty() {
        queue.enqueue(List.of(RedisQueue.QueueItem.of("a"), RedisQueue.QueueItem.of("b"), RedisQueue.QueueItem.of("c")));

        assertEquals(List.of("a", "b"), queue.blockingTakeBatch(2, Duration.ofSeconds(1)).orElseThrow());
        assertEquals(List.of("c"), queue.blockingTakeBatch(2, Duration.ofSeconds(1)).orElseThrow());
        assertTrue(queue.blockingTakeBatch(2, Duration.ofSeconds(1)).isEmpty());
    }

    @Test
    void blockedConsumerDoesNotDelayProducers() throws Exception {
        CompletableFuture<Optional<List<String>>> blocked = CompletableFuture.supplyAsync(() -> queue.blockingTakeBatch(10, Duration.ofSeconds(2)));
        Thread.sleep(300);

        long start = System.nanoTime();
        queue.size();
        queue.enqueue(List.of(RedisQueue.QueueItem.of("late")));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(List.of("late"), blocked.get().orElseThrow());
        assertTrue(elapsedMs < 500, "producer waited " + elapsedMs + "ms behind the blocking consumer");
    }

    private static RedisTemplate<String, String> template(LettuceConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
