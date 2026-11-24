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
        CompletableFuture.supplyAsync(this::loadPlayerCount, Main.EXECUTOR)
                .thenAccept(this.trackedPlayerCount::set);
        CompletableFuture.supplyAsync(this::loadSkinCount, Main.EXECUTOR)
                .thenAccept(this.trackedSkinCount::set);
        CompletableFuture.supplyAsync(this.capeRepository::count, Main.EXECUTOR)
                .thenAccept(this.trackedCapeCount::set);
        CompletableFuture.supplyAsync(this.skinTrendingRepository::count, Main.EXECUTOR)
                .thenAccept(this.trendingSkinCount::set);
        CompletableFuture.supplyAsync(this.usernameChangeEventRepository::countNameChanges, Main.EXECUTOR)
                .thenAccept(this.nameChangesCount::set);
    }

    /**
     * Seeds the player counter from {@code pg_class.reltuples}, falling back to an exact count
     * only when the table has never been analyzed, so startup does not scan every row.
     */
    private long loadPlayerCount() {
        Long estimate = this.playerRepository.estimateRowCount();
        return estimate != null && estimate >= 0 ? estimate : this.playerRepository.count();
    }

    /**
     * Seeds the skin counter the same way as {@link #loadPlayerCount()}.
     */
    private long loadSkinCount() {
        Long estimate = this.skinRepository.estimateRowCount();
        return estimate != null && estimate >= 0 ? estimate : this.skinRepository.count();
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
