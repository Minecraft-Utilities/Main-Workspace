package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.CoalescingLoader;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.player.AccountsUpdatedMetric;
import xyz.mcutils.backend.metric.impl.player.PlayerChangesDetectedMetric;
import xyz.mcutils.backend.metric.impl.player.PlayerRefreshMetric;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.history.RecentUsernameChange;
import xyz.mcutils.backend.model.domain.player.history.UsernameHistory;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.postgres.*;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.postgres.PlayerCapeAdoptionRepository;
import xyz.mcutils.backend.repository.postgres.PlayerCreateRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRefreshRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRefreshRepository.DuePlayer;
import xyz.mcutils.backend.repository.postgres.PlayerRefreshRepository.ScheduleUpdate;
import xyz.mcutils.backend.repository.postgres.PlayerMonthlyViewsRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRefreshStateRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.PlayerSkinAdoptionRepository;
import xyz.mcutils.backend.repository.postgres.UsernameChangeEventRepository;
import xyz.mcutils.backend.websocket.WebSocketManager;
import xyz.mcutils.backend.websocket.impl.NameChangeWebSocket;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class PlayerService {
    static final Duration PLAYER_UPDATE_INTERVAL = PlayerRefreshSchedule.BASE_INTERVAL;
    private static final int MAX_PLAYER_SEARCH_RESULTS = 5;
    /**
     * Max concurrent per-player refresh transactions (each uses REQUIRES_NEW + row lock).
     * Keep well below {@code spring.datasource.hikari.maximum-pool-size} to leave connections
     * for API traffic, submit queue, and skin/cape resolution during prepare.
     */
    private final Semaphore persistSemaphore;

    public static PlayerService INSTANCE;
    private final MojangService mojangService;
    private final MojangRateLimiter mojangRateLimiter;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerRepository playerRepository;
    private final UsernameChangeEventRepository usernameChangeEventRepository;
    private final PlayerSkinAdoptionRepository playerSkinAdoptionRepository;
    private final PlayerCapeAdoptionRepository playerCapeAdoptionRepository;
    private final PlayerRefreshRepository playerRefreshRepository;
    private final PlayerRefreshStateRepository playerRefreshStateRepository;
    private final PlayerCreateRepository playerCreateRepository;
    private final PlayerMonthlyViewsRepository playerMonthlyViewsRepository;
    private final PlayerService self;

    private final CoalescingLoader<String, PlayerRow> playerLoader = new CoalescingLoader<>(Runnable::run);

    public PlayerService(MojangService mojangService, MojangRateLimiter mojangRateLimiter, SkinService skinService, CapeService capeService,
                         PlayerRepository playerRepository, UsernameChangeEventRepository usernameChangeEventRepository,
                         PlayerSkinAdoptionRepository playerSkinAdoptionRepository, PlayerCapeAdoptionRepository playerCapeAdoptionRepository,
                         PlayerRefreshRepository playerRefreshRepository, PlayerRefreshStateRepository playerRefreshStateRepository,
                         PlayerMonthlyViewsRepository playerMonthlyViewsRepository, PlayerCreateRepository playerCreateRepository,
                         @Lazy PlayerService self,
                         @Value("${mc-utils.player-refresh.concurrent-persists:64}") int concurrentPersists) {
        this.mojangService = mojangService;
        this.mojangRateLimiter = mojangRateLimiter;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerRepository = playerRepository;
        this.usernameChangeEventRepository = usernameChangeEventRepository;
        this.playerSkinAdoptionRepository = playerSkinAdoptionRepository;
        this.playerCapeAdoptionRepository = playerCapeAdoptionRepository;
        this.playerRefreshRepository = playerRefreshRepository;
        this.playerRefreshStateRepository = playerRefreshStateRepository;
        this.playerMonthlyViewsRepository = playerMonthlyViewsRepository;
        this.playerCreateRepository = playerCreateRepository;
        this.self = self;
        this.persistSemaphore = new Semaphore(concurrentPersists);
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    public PlayerRow getPlayer(String query) {
        return playerLoader.get(query, () -> {
            boolean isUsername = query.length() <= 16;
            UUID parsedUuid = isUsername ? null : UUIDUtils.parseUuid(query);
            Optional<PlayerRow> optionalPlayerRow = isUsername ? this.playerRepository.findByUsernameIgnoreCase(query) : this.playerRepository.findById(parsedUuid);
            if (optionalPlayerRow.isEmpty()) {
                UUID uuid = parsedUuid;
                if (isUsername) {
                    MojangUsernameToUuidToken mojangUsernameToUuid = this.mojangService.getUuidFromUsername(query);
                    if (mojangUsernameToUuid == null) {
                        throw new NotFoundException("Player with username '%s' was not found".formatted(query));
                    }
                    uuid = UUIDUtils.parseUuid(mojangUsernameToUuid.uuid());
                }
                assert uuid != null;

                MojangProfileToken token = this.mojangService.getProfile(uuid.toString());
                if (token == null) {
                    throw new NotFoundException("Player '%s' was not found".formatted(query));
                }
                return this.self.createPlayer(token);
            }

            PlayerRow playerRow = optionalPlayerRow.get();
            // Only refresh on-demand for viewed players; the background loop owns the long tail.
            // The shared limiter keeps this path inside the same Mojang budget as the background
            // refresh and submit queue.
            PlayerRefreshStateRow refreshState = this.playerRefreshStateRepository.findById(playerRow.getId()).orElse(null);
            if (refreshState != null && refreshState.getNextRefreshAt().isBefore(Instant.now())
                    && this.playerMonthlyViewsRepository.existsById(playerRow.getId())) {
                Main.EXECUTOR.execute(() -> {
                    try {
                        this.mojangRateLimiter.acquire();
                        MojangProfileToken token = this.mojangService.getProfile(playerRow.getId().toString());
                        if (token == null) {
                            this.bumpRefreshFailure(playerRow.getId());
                            return;
                        }
                        this.self.updatePlayer(playerRow, refreshState, token);
                    } catch (Exception e) {
                        this.bumpRefreshFailure(playerRow.getId());
                    }
                });
            }
            return playerRow;
        });
    }

    /**
     * Cache-only player lookup: returns the already-known player row for a username (≤16
     * chars) or a dashed UUID, without any Mojang call and without creating the player. The
     * players table is only ever populated from Mojang-verified profiles, so a match here
     * proves the identity without spending a Mojang lookup. Same single-argument query
     * contract as {@link #getPlayer} (which remains the compatibility entry point); this is
     * its read-only twin.
     */
    @Transactional(readOnly = true)
    public Optional<PlayerRow> getCachedPlayer(String query) {
        if (query.length() <= 16) {
            return this.playerRepository.findByUsernameIgnoreCase(query);
        }
        return this.playerRepository.findById(UUIDUtils.parseUuid(query));
    }

    @Transactional
    public PlayerRow createPlayer(MojangProfileToken token) {
        UUID id = UUIDUtils.parseUuid(token.getId());
        Instant now = Instant.now();

        SkinRow skin = this.skinService.getOrCreateSkin(token.getSkinAndCape().left(), id);
        CapeTextureToken capeToken = token.getSkinAndCape().right();
        CapeRow cape = capeToken != null ? this.capeService.getOrCreateCape(capeToken, id) : null;

        Instant nextRefreshAt = now.plus(PlayerRefreshSchedule.BASE_INTERVAL);

        PlayerRow playerRow = this.playerRepository.save(new PlayerRow(
                id,
                token.getName(),
                token.getLegacy() != null && token.getLegacy(),
                0,
                skin,
                cape,
                now
        ));
        this.playerRefreshStateRepository.save(new PlayerRefreshStateRow(id, nextRefreshAt, 0, now));

        this.playerSkinAdoptionRepository.save(new PlayerSkinAdoptionRow(id, skin.getId(), now, null));
        if (cape != null) {
            this.playerCapeAdoptionRepository.save(new PlayerCapeAdoptionRow(id, cape.getId(), now, null));
        }

        StatisticsService.addTrackedPlayerCount(1);
        return playerRow;
    }

    @Transactional
    public void createPlayers(List<MojangProfileToken> tokens) {
        // Precompute the first owner of each distinct texture, so the per-texture futures below
        // don't each re-scan the token list (O(N) instead of O(distinct x N)).
        Map<String, UUID> firstSkinOwnerByTextureId = new HashMap<>();
        Map<String, UUID> firstCapeOwnerByTextureId = new HashMap<>();
        for (MojangProfileToken token : tokens) {
            SkinTextureToken skin = token.getSkinAndCape().left();
            firstSkinOwnerByTextureId.computeIfAbsent(skin.getTextureId(), _ -> UUIDUtils.parseUuid(token.getId()));
            CapeTextureToken cape = token.getSkinAndCape().right();
            if (cape != null) {
                firstCapeOwnerByTextureId.computeIfAbsent(cape.getTextureId(), _ -> UUIDUtils.parseUuid(token.getId()));
            }
        }

        Map<String, SkinRow> skinsByTextureId = new HashMap<>(this.skinService.findByTextureIds(firstSkinOwnerByTextureId.keySet()));
        Map<String, CapeRow> capesByTextureId = new HashMap<>(this.capeService.findByTextureIds(firstCapeOwnerByTextureId.keySet()));

        // Only textures the bulk lookup did not find need a create, which is the rare case.
        Map<String, CompletableFuture<SkinRow>> skinFutures = tokens.stream()
                .map(t -> t.getSkinAndCape().left())
                .filter(t -> !skinsByTextureId.containsKey(t.getTextureId()))
                .collect(Collectors.toMap(
                        SkinTextureToken::getTextureId,
                        t -> CompletableFuture.supplyAsync(() ->
                                this.skinService.getOrCreateSkinCached(t, firstSkinOwnerByTextureId.get(t.getTextureId())),
                                Main.EXECUTOR),
                        (a, b) -> a));

        Map<String, CompletableFuture<CapeRow>> capeFutures = tokens.stream()
                .map(t -> t.getSkinAndCape().right())
                .filter(t -> t != null && !capesByTextureId.containsKey(t.getTextureId()))
                .collect(Collectors.toMap(
                        CapeTextureToken::getTextureId,
                        t -> CompletableFuture.supplyAsync(() ->
                                this.capeService.getOrCreateCapeCached(t, firstCapeOwnerByTextureId.get(t.getTextureId())),
                                Main.EXECUTOR),
                        (a, b) -> a));

        CompletableFuture.allOf(Stream.concat(skinFutures.values().stream(), capeFutures.values().stream())
                .toArray(CompletableFuture[]::new)).join();
        skinFutures.forEach((textureId, future) -> skinsByTextureId.put(textureId, future.join()));
        capeFutures.forEach((textureId, future) -> capesByTextureId.put(textureId, future.join()));

        List<UUID> ids = tokens.stream().map(t -> UUIDUtils.parseUuid(t.getId())).toList();
        Set<UUID> existingIds = this.playerRepository.findExistingIds(ids);

        List<PlayerRow> playerRows = new ArrayList<>();
        List<PlayerRefreshStateRow> refreshStates = new ArrayList<>();
        List<PlayerSkinAdoptionRow> skinAdoptions = new ArrayList<>();
        List<PlayerCapeAdoptionRow> capeAdoptions = new ArrayList<>();

        Instant now = Instant.now();
        List<MojangProfileToken> byId = tokens.stream().sorted(Comparator.comparing(t -> UUIDUtils.parseUuid(t.getId()).toString())).toList();
        for (MojangProfileToken token : byId) {
            UUID id = UUIDUtils.parseUuid(token.getId());
            if (existingIds.contains(id)) {
                continue;
            }
            SkinRow skin = skinsByTextureId.get(token.getSkinAndCape().left().getTextureId());
            CapeTextureToken capeToken = token.getSkinAndCape().right();
            CapeRow cape = capeToken != null ? capesByTextureId.get(capeToken.getTextureId()) : null;

            skinAdoptions.add(new PlayerSkinAdoptionRow(id, skin.getId(), now, null));
            if (cape != null) {
                capeAdoptions.add(new PlayerCapeAdoptionRow(id, cape.getId(), now, null));
            }
            playerRows.add(new PlayerRow(id, token.getName(), token.getLegacy() != null && token.getLegacy(),
                    0, skin, cape, now));
            refreshStates.add(new PlayerRefreshStateRow(id, now.plus(PlayerRefreshSchedule.BASE_INTERVAL), 0, now));
        }

        if (playerRows.isEmpty()) {
            return;
        }

        int inserted = this.playerCreateRepository.insertAll(playerRows, refreshStates, skinAdoptions, capeAdoptions);
        StatisticsService.addTrackedPlayerCount(inserted);
    }

    /**
     * Prepares and persists a single player refresh. Used by the background refresh pipeline.
     * <p>
     * A refresh that found nothing to change never touches the player row: its new schedule is
     * appended to {@code unchanged} for the caller to write in one batch via
     * {@link #persistUnchangedRefreshes}. A detected username change is pushed by the refreshing
     * thread as soon as it is committed, rather than collected and sent once a whole chunk finished.
     *
     * @return whether the refresh was persisted or queued for the batch write
     */
    public boolean persistPlayerRefresh(PlayerUpdate playerUpdate, Collection<ScheduleUpdate> unchanged) {
        UUID playerId = playerUpdate.player().id();
        try {
            PreparedPlayerUpdate prepared = preparePlayerUpdate(playerUpdate);
            if (!prepared.changesRow()) {
                unchanged.add(scheduleRefresh(prepared.player(), false, Instant.now()));
                return true;
            }
            UsernameChangeEventRow usernameChangeEvent;
            this.persistSemaphore.acquire();
            try {
                usernameChangeEvent = this.self.persistPlayerUpdate(prepared);
            } finally {
                this.persistSemaphore.release();
            }
            broadcastUsernameChange(usernameChangeEvent);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while refreshing player {}", playerId);
            this.bumpRefreshFailure(playerId);
            return false;
        } catch (Exception e) {
            log.warn("Failed to refresh player {}: {}", playerId, e.toString());
            log.debug("Failed to refresh player {}", playerId, e);
            this.bumpRefreshFailure(playerId);
            return false;
        }
    }

    /**
     * Writes the schedules of refreshes that found no change, in one statement.
     */
    public void persistUnchangedRefreshes(Collection<ScheduleUpdate> schedules) {
        if (schedules.isEmpty()) {
            return;
        }
        this.playerRefreshRepository.recordSchedules(schedules);
        MetricService.getMetric(AccountsUpdatedMetric.class).inc(schedules.size());
    }

    public void updatePlayer(PlayerRow playerRow, PlayerRefreshStateRow refreshState, MojangProfileToken token) {
        CapeRow cape = playerRow.getCape();
        DuePlayer player = new DuePlayer(
                playerRow.getId(),
                playerRow.getUsername(),
                playerRow.isLegacyAccount(),
                playerRow.getSkin().getTextureId(),
                cape != null ? cape.getTextureId() : null,
                refreshState.getChangeVelocity(),
                refreshState.getLastUpdated(),
                this.getMonthlyViews(playerRow.getId())
        );
        List<ScheduleUpdate> unchanged = new ArrayList<>(1);
        this.persistPlayerRefresh(new PlayerUpdate(player, token), unchanged);
        this.persistUnchangedRefreshes(unchanged);
    }

    public long getMonthlyViews(UUID playerId) {
        return this.playerMonthlyViewsRepository.findById(playerId).map(PlayerMonthlyViewsRow::getViews).orElse(0L);
    }

    @Transactional(readOnly = true)
    public PlayerRefreshRepository.PlayerStats getPlayerStats(UUID playerId) {
        return this.playerRefreshRepository.loadStats(playerId)
                .orElseThrow(() -> new IllegalStateException("Player %s has no refresh state".formatted(playerId)));
    }

    private ScheduleUpdate scheduleRefresh(DuePlayer player, boolean hadChanges, Instant now) {
        double velocity = PlayerRefreshSchedule.updateVelocity(player.changeVelocity(), player.lastUpdated(), now, hadChanges);
        Duration interval = PlayerRefreshSchedule.nextRefreshInterval(hadChanges, velocity, player.monthlyViews());
        MetricService.getMetric(PlayerRefreshMetric.class).recordInterval(interval);
        return new ScheduleUpdate(player.id(), now.plus(interval), velocity, now);
    }

    /**
     * Pushes one committed username change to every client on the name-change WebSocket.
     *
     * @param usernameChangeEvent the change to push, or null when the refresh found no change
     */
    void broadcastUsernameChange(UsernameChangeEventRow usernameChangeEvent) {
        if (usernameChangeEvent == null) {
            return;
        }
        WebSocketManager.getWebsocket(NameChangeWebSocket.class).sendMessageToAll(new RecentUsernameChange(
                usernameChangeEvent.getPlayerId(),
                usernameChangeEvent.getNewUsername(),
                usernameChangeEvent.getPreviousUsername(),
                usernameChangeEvent.getTimestamp()
        ));
    }

    /**
     * Resolves skin/cape rows without holding a player row lock.
     */
    private PreparedPlayerUpdate preparePlayerUpdate(PlayerUpdate playerUpdate) {
        DuePlayer snapshot = playerUpdate.player();
        MojangProfileToken token = playerUpdate.token();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        SkinTextureToken skinToken = skinAndCape != null ? skinAndCape.left() : null;
        CapeTextureToken capeToken = skinAndCape != null ? skinAndCape.right() : null;

        SkinRow newSkin = null;
        if (skinToken != null && !snapshot.skinTextureId().equals(skinToken.getTextureId())) {
            newSkin = this.skinService.getOrCreateSkinCached(skinToken, snapshot.id());
        }

        // A profile without a textures payload (null skinAndCape) means Mojang returned no skin/cape
        // data at all; keep the stored assets unchanged instead of treating them as removed.
        String newCapeTextureId = capeToken != null ? capeToken.getTextureId() : null;
        boolean capeChanged = skinAndCape != null && !Objects.equals(snapshot.capeTextureId(), newCapeTextureId);
        CapeRow newCape = null;
        if (capeChanged && capeToken != null) {
            newCape = this.capeService.getOrCreateCapeCached(capeToken, snapshot.id());
        }

        boolean changesRow = newSkin != null || capeChanged
                || !snapshot.username().equals(token.getName())
                || snapshot.legacyAccount() != token.isLegacy();
        return new PreparedPlayerUpdate(snapshot, token, newSkin, newCape, capeChanged, changesRow);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UsernameChangeEventRow persistPlayerUpdate(PreparedPlayerUpdate prepared) {
        Instant now = Instant.now();
        Optional<PlayerRow> locked = this.playerRepository.findByIdForUpdate(prepared.player().id());
        if (locked.isEmpty()) {
            return null;
        }

        PlayerRow playerRow = locked.get();
        MojangProfileToken token = prepared.token();
        int changeCount = 0;
        Long equippedSkinId = null;
        Long equippedCapeId = null;

        if (playerRow.isLegacyAccount() != token.isLegacy()) {
            playerRow.setLegacyAccount(token.isLegacy());
        }

        if (prepared.newSkin() != null && !playerRow.getSkin().getTextureId().equals(prepared.newSkin().getTextureId())) {
            playerRow.setSkin(prepared.newSkin());
            equippedSkinId = prepared.newSkin().getId();
            changeCount++;
            MetricService.getMetric(PlayerChangesDetectedMetric.class).record(PlayerChangesDetectedMetric.SKIN);
        }

        if (prepared.capeChanged()) {
            String currentCapeTextureId = playerRow.getCape() != null ? playerRow.getCape().getTextureId() : null;
            String targetCapeTextureId = prepared.newCape() != null ? prepared.newCape().getTextureId() : null;
            if (!Objects.equals(currentCapeTextureId, targetCapeTextureId)) {
                playerRow.setCape(prepared.newCape());
                if (prepared.newCape() != null) {
                    equippedCapeId = prepared.newCape().getId();
                }
                changeCount++;
                MetricService.getMetric(PlayerChangesDetectedMetric.class).record(PlayerChangesDetectedMetric.CAPE);
            }
        }

        UsernameChangeEventRow usernameChangeEventRow = null;
        String previousUsername = playerRow.getUsername();
        if (!previousUsername.equals(token.getName())) {
            playerRow.setUsername(token.getName());
            usernameChangeEventRow = new UsernameChangeEventRow(playerRow.getId(), token.getName(), previousUsername, now);
            changeCount++;
            MetricService.getMetric(PlayerChangesDetectedMetric.class).record(PlayerChangesDetectedMetric.USERNAME);
        }

        boolean hadChanges = changeCount > 0;
        this.playerRefreshRepository.recordSchedules(List.of(scheduleRefresh(prepared.player(), hadChanges, now)));
        if (equippedSkinId != null) {
            recordSkinEquips(List.of(new PlayerSkinAdoptionRow(playerRow.getId(), equippedSkinId, now, now)), now);
        }
        if (equippedCapeId != null) {
            recordCapeEquips(List.of(new PlayerCapeAdoptionRow(playerRow.getId(), equippedCapeId, now, now)), now);
        }
        if (usernameChangeEventRow != null) {
            this.usernameChangeEventRepository.save(usernameChangeEventRow);
            StatisticsService.addNameChangesCount(1);
        }

        MetricService.getMetric(AccountsUpdatedMetric.class).inc(1);
        return usernameChangeEventRow;
    }

    private record PreparedPlayerUpdate(
            DuePlayer player,
            MojangProfileToken token,
            SkinRow newSkin,
            CapeRow newCape,
            boolean capeChanged,
            boolean changesRow
    ) {}

    public Set<UsernameHistory> getUsernameHistory(PlayerRow player) {
        List<UsernameChangeEventRow> events = this.usernameChangeEventRepository
                .findByPlayerIdOrderByTimestampDesc(player.getId())
                .stream()
                .filter(row -> row.getPreviousUsername() != null)
                .toList();

        Set<UsernameHistory> history = events.stream()
                .map(row -> new UsernameHistory(row.getNewUsername(), row.getPreviousUsername(), row.getTimestamp()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String initialUsername = events.isEmpty()
                ? player.getUsername()
                : events.getLast().getPreviousUsername();
        history.add(new UsernameHistory(initialUsername, null, player.getFirstSeen()));

        return history;
    }

    public List<RecentUsernameChange> getRecentNameChanges() {
        return this.usernameChangeEventRepository.findRecentNameChanges(PageRequest.of(0, 50)).stream()
                .map(row -> new RecentUsernameChange(row.getPlayerId(), row.getNewUsername(), row.getPreviousUsername(), row.getTimestamp()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Skin> getSkinHistory(PlayerRow player) {
        List<PlayerSkinAdoptionRow> adoptions = this.playerSkinAdoptionRepository.findByPlayerIdOrderByFirstSeenAsc(player.getId());
        return adoptions.stream()
                .filter(adoption -> adoption.getSkin() != null)
                .map(adoption -> {
                    Skin skin = Skin.fromRow(adoption.getSkin());
                    skin.setFirstSeen(adoption.getFirstSeen());
                    return skin;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public Set<VanillaCape> getCapeHistory(PlayerRow player) {
        List<PlayerCapeAdoptionRow> adoptions = this.playerCapeAdoptionRepository.findByPlayerIdOrderByFirstSeenAsc(player.getId());
        return adoptions.stream()
                .filter(adoption -> adoption.getCape() != null)
                .map(adoption -> {
                    VanillaCape cape = VanillaCape.fromRow(adoption.getCape());
                    cape.setFirstSeen(adoption.getFirstSeen());
                    return cape;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<PlayerRow> searchPlayers(String query) {
        return this.playerRepository.searchByUsernamePrefix(query, MAX_PLAYER_SEARCH_RESULTS);
    }

    public List<PlayerRow> getTopSubmittedPlayers(int amount) {
        return this.playerRepository.findAllByOrderBySubmittedUuidsDesc(PageRequest.of(0, amount)).stream().toList();
    }

    public Set<UUID> getExistingPlayerIds(List<UUID> ids) {
        return this.playerRepository.findExistingIds(ids);
    }

    public void incrementSubmittedUuids(UUID playerId, long count) {
        this.playerRepository.incrementSubmittedUuids(playerId, count);
    }

    public void bumpRefreshFailure(UUID playerId) {
        this.playerRefreshRepository.bumpFailures(
                List.of(playerId),
                Instant.now().plus(PlayerRefreshSchedule.FAILURE_BACKOFF)
        );
    }

    public void bumpRefreshFailures(Collection<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return;
        }
        this.playerRefreshRepository.bumpFailures(
                playerIds,
                Instant.now().plus(PlayerRefreshSchedule.FAILURE_BACKOFF)
        );
    }

    private void recordSkinEquips(List<PlayerSkinAdoptionRow> equips, Instant timestamp) {
        if (equips.isEmpty()) {
            return;
        }
        Set<PlayerSkinAdoptionId> ids = equips.stream()
                .map(row -> new PlayerSkinAdoptionId(row.getPlayerId(), row.getSkinId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<PlayerSkinAdoptionId, PlayerSkinAdoptionRow> existing = this.playerSkinAdoptionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(row -> new PlayerSkinAdoptionId(row.getPlayerId(), row.getSkinId()), row -> row));
        List<PlayerSkinAdoptionRow> toSave = new ArrayList<>();
        for (PlayerSkinAdoptionRow equip : equips) {
            PlayerSkinAdoptionId id = new PlayerSkinAdoptionId(equip.getPlayerId(), equip.getSkinId());
            PlayerSkinAdoptionRow row = existing.get(id);
            if (row == null) {
                row = new PlayerSkinAdoptionRow(equip.getPlayerId(), equip.getSkinId(), timestamp, timestamp);
            } else {
                row.setLastEquippedAt(timestamp);
            }
            toSave.add(row);
        }
        this.playerSkinAdoptionRepository.saveAll(toSave);
    }

    private void recordCapeEquips(List<PlayerCapeAdoptionRow> equips, Instant timestamp) {
        if (equips.isEmpty()) {
            return;
        }
        Set<PlayerCapeAdoptionId> ids = equips.stream()
                .map(row -> new PlayerCapeAdoptionId(row.getPlayerId(), row.getCapeId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<PlayerCapeAdoptionId, PlayerCapeAdoptionRow> existing = this.playerCapeAdoptionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(row -> new PlayerCapeAdoptionId(row.getPlayerId(), row.getCapeId()), row -> row));
        List<PlayerCapeAdoptionRow> toSave = new ArrayList<>();
        for (PlayerCapeAdoptionRow equip : equips) {
            PlayerCapeAdoptionId id = new PlayerCapeAdoptionId(equip.getPlayerId(), equip.getCapeId());
            PlayerCapeAdoptionRow row = existing.get(id);
            if (row == null) {
                row = new PlayerCapeAdoptionRow(equip.getPlayerId(), equip.getCapeId(), timestamp, timestamp);
            } else {
                row.setLastEquippedAt(timestamp);
            }
            toSave.add(row);
        }
        this.playerCapeAdoptionRepository.saveAll(toSave);
    }

    public record PlayerUpdate(DuePlayer player, MojangProfileToken token) {}
}
