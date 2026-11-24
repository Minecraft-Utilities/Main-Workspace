package xyz.mcutils.backend.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoalescingLoaderTest {
    @Test
    void sameKeyRunsOneLoad() throws Exception {
        CoalescingLoader<String, Integer> loader = new CoalescingLoader<>(Runnable::run);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService callers = Executors.newFixedThreadPool(8)) {
            Future<Integer> first = callers.submit(() -> loader.get("k", () -> {
                loads.incrementAndGet();
                await(release);
                return 1;
            }));
            Thread.sleep(100);
            Future<Integer> second = callers.submit(() -> loader.get("k", () -> {
                loads.incrementAndGet();
                return 2;
            }));
            Thread.sleep(100);
            release.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS));
            assertEquals(1, second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, loads.get());
    }

    @Test
    void blockingLoadDoesNotStallOtherKeys() throws Exception {
        CoalescingLoader<Integer, Integer> loader = new CoalescingLoader<>(Runnable::run);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService callers = Executors.newFixedThreadPool(4)) {
            Future<Integer> blocked = callers.submit(() -> loader.get(0, () -> {
                await(release);
                return 0;
            }));
            Thread.sleep(100);
            for (int key = 1; key <= 64; key++) {
                int k = key;
                assertEquals(k, callers.submit(() -> loader.get(k, () -> k)).get(2, TimeUnit.SECONDS));
            }
            assertTrue(!blocked.isDone());
            release.countDown();
            blocked.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void loaderFailureIsRethrownAndKeyReleased() {
        CoalescingLoader<String, Integer> loader = new CoalescingLoader<>(Runnable::run);
        assertThrows(IllegalArgumentException.class, () -> loader.get("k", () -> {
            throw new IllegalArgumentException("boom");
        }));
        assertEquals(7, loader.get("k", () -> 7));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
