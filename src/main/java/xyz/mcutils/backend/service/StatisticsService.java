package xyz.mcutils.backend.service;

import lombok.Getter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.model.dto.response.StatisticsResponse;
import xyz.mcutils.backend.repository.postgres.CapeRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.SkinRepository;
import xyz.mcutils.backend.repository.postgres.SkinTrendingRepository;
import xyz.mcutils.backend.repository.postgres.UsernameChangeEventRepository;
import xyz.mcutils.backend.websocket.WebSocketManager;
import xyz.mcutils.backend.websocket.impl.StatisticsWebSocket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Getter
public class StatisticsService {

    public static StatisticsService INSTANCE;

    private final PlayerRepository playerRepository;
    private final SkinRepository skinRepository;
    private final SkinTrendingRepository skinTrendingRepository;
    private final CapeRepository capeRepository;
    private final UsernameChangeEventRepository usernameChangeEventRepository;

    private final AtomicLong trackedPlayerCount = new AtomicLong();
    private final AtomicLong trackedSkinCount = new AtomicLong();
    private final AtomicLong trackedCapeCount = new AtomicLong();
    private final AtomicLong trendingSkinCount = new AtomicLong();
    private final AtomicLong nameChangesCount = new AtomicLong();

    private volatile boolean trackedPlayerCountLoaded;
    private volatile boolean trackedSkinCountLoaded;
    private volatile boolean trackedCapeCountLoaded;

    public long getTrackedPlayerCount() {
        return this.trackedPlayerCount.get();
    }

    public long getTrackedSkinCount() {
        return this.trackedSkinCount.get();
    }

    public long getTrackedCapeCount() {
        return this.trackedCapeCount.get();
    }

    public long getTrendingSkinCount() {
        return this.trendingSkinCount.get();
    }

    public long getNameChangesCount() {
        return this.nameChangesCount.get();
    }

    public StatisticsService(PlayerRepository playerRepository, SkinRepository skinRepository, SkinTrendingRepository skinTrendingRepository, CapeRepository capeRepository, UsernameChangeEventRepository usernameChangeEventRepository) {
        this.playerRepository = playerRepository;
        this.skinRepository = skinRepository;
        this.skinTrendingRepository = skinTrendingRepository;
        this.capeRepository = capeRepository;
        this.usernameChangeEventRepository = usernameChangeEventRepository;
        INSTANCE = this;
    }

    public static void addTrackedPlayerCount(long delta) {
        if (delta == 0) {
            return;
        }
        long newValue = INSTANCE.trackedPlayerCount.addAndGet(delta);
        if (newValue % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void addTrackedSkinCount(long delta) {
        if (delta == 0) {
            return;
        }
        long newValue = INSTANCE.trackedSkinCount.addAndGet(delta);
        if (newValue % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void addTrackedCapeCount(long delta) {
        if (delta == 0) {
            return;
        }
        long newValue = INSTANCE.trackedCapeCount.addAndGet(delta);
        if (newValue % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void addNameChangesCount(long delta) {
        if (delta == 0) {
            return;
        }
        INSTANCE.nameChangesCount.addAndGet(delta);
    }

    /**
     * Loads the initial DB-backed counters. Must NOT run during bean creation: repository
     * calls on virtual threads resolve late singletons (transaction machinery, customizers)
     * whose creation needs the singleton lock held by the bean-creation thread — joining on
     * them from {@code @PostConstruct} deadlocked startup, leaving a Hikari connection open
     * until the leak detector fired. Fires once at {@code ApplicationReadyEvent}, when every
     * singleton exists, and never blocks: each count sets its counter when it finishes.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadInitialCounts() {
        CompletableFuture.supplyAsync(this.playerRepository::count, Main.EXECUTOR)
                .thenAccept(count -> {
                    this.trackedPlayerCount.set(count);
                    this.trackedPlayerCountLoaded = true;
                });
        CompletableFuture.supplyAsync(this.skinRepository::count, Main.EXECUTOR)
                .thenAccept(count -> {
                    this.trackedSkinCount.set(count);
                    this.trackedSkinCountLoaded = true;
                });
        CompletableFuture.supplyAsync(this.capeRepository::count, Main.EXECUTOR)
                .thenAccept(count -> {
                    this.trackedCapeCount.set(count);
                    this.trackedCapeCountLoaded = true;
                });
        CompletableFuture.supplyAsync(this.skinTrendingRepository::count, Main.EXECUTOR)
                .thenAccept(this.trendingSkinCount::set);
        CompletableFuture.supplyAsync(this.usernameChangeEventRepository::countNameChanges, Main.EXECUTOR)
                .thenAccept(this.nameChangesCount::set);
    }

    /**
     * Refreshes the cached count of trending skins.
     * Called after the hourly trending-heat rebuild; avoids a per-request COUNT over ~90k+ rows.
     */
    public void refreshTrendingSkinCount() {
        this.trendingSkinCount.set(this.skinTrendingRepository.count());
    }

    /**
     * Updates the statistics for the all connected WebSocket clients.
     */
    public void updateStatistics() {
        ((StatisticsWebSocket) WebSocketManager.getWebsocket(StatisticsWebSocket.class)).updateStatistics();
    }

    /**
     * Gets the statistics for the app.
     *
     * @return the statistics
     */
    public StatisticsResponse getStatistics() {
        return new StatisticsResponse(getTrackedPlayerCount(), getTrackedSkinCount(), getTrackedCapeCount());
    }
}
