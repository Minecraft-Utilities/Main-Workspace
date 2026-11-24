package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.metric.impl.player.PlayerRefreshMetric;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.repository.postgres.PlayerRefreshRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRefreshRepository.DuePlayer;
import xyz.mcutils.backend.repository.postgres.PlayerRefreshRepository.ScheduleUpdate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class PlayerRefreshService {
    private static final Duration CHUNK_TIMEOUT = Duration.ofMinutes(10);
    private final int refreshChunkSize;
    private final int concurrentFetches;
    private final MojangRateLimiter mojangRateLimiter;
    private final MojangService mojangService;
    private final PlayerService playerService;
    private final PlayerRefreshRepository playerRefreshRepository;
    private final ExecutorService refreshExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlayerRefreshService(
            MojangService mojangService,
            PlayerService playerService,
            PlayerRefreshRepository playerRefreshRepository,
            MojangRateLimiter mojangRateLimiter,
            @Value("${mc-utils.player-refresh.chunk-size:2500}") int refreshChunkSize,
            @Value("${mc-utils.player-refresh.concurrent-fetches:200}") int concurrentFetches
    ) {
        this.refreshChunkSize = refreshChunkSize;
        this.concurrentFetches = concurrentFetches;
        this.mojangRateLimiter = mojangRateLimiter;
        this.mojangService = mojangService;
        this.playerService = playerService;
        this.playerRefreshRepository = playerRefreshRepository;
        this.refreshExecutor = Executors.newFixedThreadPool(
                concurrentFetches,
                Thread.ofPlatform().name("player-refresh-worker-", 0).factory()
        );
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        running.set(false);
        refreshExecutor.shutdownNow();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        log.info("Starting player background refresh (chunk size {}, Mojang rate limit configured via mc-utils.player-refresh.mojang-rate-limit)",
                refreshChunkSize);
        Thread.ofPlatform().daemon(true).name("player-refresh").start(() -> {
            while (running.get()) {
                try {
                    Instant now = Instant.now();
                    List<DuePlayer> playerRows = this.playerRefreshRepository.claimDue(now, refreshChunkSize);
                    if (playerRows.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(10));
                        continue;
                    }
                    log.info("Player refresh claimed {} players", playerRows.size());
                    refreshChunk(playerRows);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Player refresh loop error, retrying in 5s", e);
                    try {
                        Thread.sleep(Duration.ofSeconds(5));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void refreshChunk(List<DuePlayer> playerRows) {
        long chunkStart = System.currentTimeMillis();
        List<UUID> failedIds = Collections.synchronizedList(new ArrayList<>());
        Map<String, AtomicInteger> failureReasons = new ConcurrentHashMap<>();
        AtomicInteger persisted = new AtomicInteger();
        Collection<ScheduleUpdate> unchanged = new ConcurrentLinkedQueue<>();
        Set<UUID> finishedIds = ConcurrentHashMap.newKeySet();
        Semaphore inflightFetches = new Semaphore(concurrentFetches);
        List<CompletableFuture<Void>> futures = new ArrayList<>(playerRows.size());

        for (DuePlayer playerRow : playerRows) {
            if (!running.get()) {
                return;
            }
            futures.add(CompletableFuture.runAsync(
                    () -> refreshPlayer(playerRow, failedIds, failureReasons, persisted, unchanged, finishedIds, inflightFetches),
                    refreshExecutor
            ));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .orTimeout(CHUNK_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            log.error(
                    "Player refresh chunk timed out or failed for {} players after {}ms",
                    playerRows.size(),
                    System.currentTimeMillis() - chunkStart,
                    e
            );
        }

        try {
            this.playerService.persistUnchangedRefreshes(unchanged);
        } catch (Exception e) {
            log.error("Failed to write {} unchanged player schedules", unchanged.size(), e);
            persisted.addAndGet(-unchanged.size());
            unchanged.forEach(schedule -> failedIds.add(schedule.playerId()));
        }
        // Claiming writes nothing, so players the chunk abandoned (timeout) would be due again at once.
        finishedIds.addAll(failedIds);
        for (DuePlayer player : playerRows) {
            if (!finishedIds.contains(player.id())) {
                failedIds.add(player.id());
            }
        }
        if (!failedIds.isEmpty()) {
            this.playerService.bumpRefreshFailures(failedIds);
        }
        int persistedCount = persisted.get();
        if (persistedCount > 0) {
            MetricService.getMetric(PlayerRefreshMetric.class).recordPersist(persistedCount);
        }
        long durationMs = System.currentTimeMillis() - chunkStart;
        if (persistedCount > 0 || !failedIds.isEmpty()) {
            log.info(
                    "Player refresh chunk finished: persisted={}, failed={}, total={}, {}ms{}",
                    persistedCount,
                    failedIds.size(),
                    playerRows.size(),
                    durationMs,
                    formatFailureReasons(failureReasons)
            );
        } else {
            log.warn(
                    "Player refresh chunk finished with zero outcomes for {} players in {}ms",
                    playerRows.size(),
                    durationMs
            );
        }
    }

    private static String formatFailureReasons(Map<String, AtomicInteger> failureReasons) {
        if (failureReasons.isEmpty()) {
            return "";
        }
        StringBuilder summary = new StringBuilder(", reasons={");
        boolean first = true;
        for (Map.Entry<String, AtomicInteger> entry : failureReasons.entrySet()) {
            if (!first) {
                summary.append(", ");
            }
            summary.append(entry.getKey()).append('=').append(entry.getValue().get());
            first = false;
        }
        return summary.append('}').toString();
    }

    private static void recordFailure(Map<String, AtomicInteger> failureReasons, String reason) {
        failureReasons.computeIfAbsent(reason, ignored -> new AtomicInteger()).incrementAndGet();
    }

    private void refreshPlayer(
            DuePlayer playerRow,
            List<UUID> failedIds,
            Map<String, AtomicInteger> failureReasons,
            AtomicInteger persisted,
            Collection<ScheduleUpdate> unchanged,
            Set<UUID> finishedIds,
            Semaphore inflightFetches
    ) {
        if (!running.get()) {
            return;
        }
        try {
            inflightFetches.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failedIds.add(playerRow.id());
            return;
        }
        try {
            mojangRateLimiter.acquire();
            PlayerService.PlayerUpdate update = fetchProfile(playerRow, failedIds, failureReasons);
            if (update == null) {
                return;
            }
            if (this.playerService.persistPlayerRefresh(update, unchanged)) {
                finishedIds.add(playerRow.id());
                persisted.incrementAndGet();
            }
        } finally {
            inflightFetches.release();
        }
    }

    private PlayerService.PlayerUpdate fetchProfile(
            DuePlayer playerRow,
            List<UUID> failedIds,
            Map<String, AtomicInteger> failureReasons
    ) {
        if (!running.get()) {
            return null;
        }
        MetricService.getMetric(PlayerRefreshMetric.class).recordMojangLookup();
        try {
            MojangProfileToken token = this.mojangService.getProfile(playerRow.id().toString());
            if (token == null) {
                failedIds.add(playerRow.id());
                recordFailure(failureReasons, "null_profile");
                return null;
            }
            return new PlayerService.PlayerUpdate(playerRow, token);
        } catch (RateLimitException e) {
            failedIds.add(playerRow.id());
            recordFailure(failureReasons, "rate_limited");
            return null;
        } catch (ResourceAccessException e) {
            failedIds.add(playerRow.id());
            recordFailure(failureReasons, "timeout");
            return null;
        } catch (Exception e) {
            log.debug("Mojang profile lookup failed for {}: {}", playerRow.id(), e.toString());
            failedIds.add(playerRow.id());
            recordFailure(failureReasons, e.getClass().getSimpleName());
            return null;
        }
    }
}
