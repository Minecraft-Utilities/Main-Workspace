package xyz.mcutils.backend.repository.postgres;

import org.jetbrains.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Set-based access to {@code player_refresh_state} for the background refresh loop. Plain SQL
 * keeps each chunk to a handful of statements and avoids loading entities that are only read.
 * <p>
 * The {@code 0.5} literals MUST stay in sync with
 * {@link xyz.mcutils.backend.service.PlayerRefreshSchedule#HOT_CHANGE_THRESHOLD} and the partial
 * index predicates in {@code V52__player_refresh_state.sql}.
 */
@Repository
public class PlayerRefreshRepository {
    private static final String SELECT_DUE_VIEWED = """
            SELECT s.player_id FROM player_monthly_views v
            JOIN player_refresh_state s ON s.player_id = v.player_id
            WHERE s.next_refresh_at < :now
            ORDER BY v.views DESC, s.next_refresh_at ASC
            LIMIT :limit
            """;
    private static final String SELECT_DUE_BY_CHANGE_COUNT = """
            SELECT s.player_id FROM player_refresh_state s
            WHERE s.next_refresh_at < :now AND %s
              AND NOT EXISTS (SELECT 1 FROM player_monthly_views v WHERE v.player_id = s.player_id)
            ORDER BY %s
            LIMIT :limit
            """;
    private static final String SELECT_DUE_HOT = SELECT_DUE_BY_CHANGE_COUNT.formatted(
            "s.change_count >= 0.5", "s.change_count DESC, s.next_refresh_at ASC");
    private static final String SELECT_DUE_COLD = SELECT_DUE_BY_CHANGE_COUNT.formatted(
            "s.change_count < 0.5", "s.next_refresh_at ASC");

    private static final String LOAD_DUE = """
            SELECT s.player_id, s.change_count, s.observed_days, s.last_updated, COALESCE(v.views, 0) AS monthly_views,
                   p.username, p.legacy_account, sk.texture_id AS skin_texture_id, c.texture_id AS cape_texture_id
            FROM player_refresh_state s
            JOIN players p ON p.id = s.player_id
            JOIN skins sk ON sk.id = p.skin_id
            LEFT JOIN capes c ON c.id = p.cape_id
            LEFT JOIN player_monthly_views v ON v.player_id = s.player_id
            WHERE s.player_id = ANY(:ids::uuid[])
            """;

    private final JdbcClient jdbc;

    public PlayerRefreshRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A claimed player: everything the refresh needs to detect changes, read in one query.
     */
    public record DuePlayer(
            UUID id,
            String username,
            boolean legacyAccount,
            String skinTextureId,
            @Nullable String capeTextureId,
            double changeCount,
            double observedDays,
            Instant lastUpdated,
            long monthlyViews
    ) {}

    public record ScheduleUpdate(UUID playerId, Instant nextRefreshAt, double changeCount, double observedDays, Instant lastUpdated) {}

    /**
     * Selects up to {@code limit} due players (viewed, then change-count-hot, then cold) without writing anything: a claim
     * that rewrote every row would double the index and WAL traffic of the refresh itself.
     * Callers must give every returned player a new schedule (success or failure backoff).
     */
    @Transactional(readOnly = true)
    public List<DuePlayer> claimDue(Instant now, int limit) {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        List<String> ids = new ArrayList<>(selectIds(SELECT_DUE_VIEWED, cutoff, limit));
        for (String tier : List.of(SELECT_DUE_HOT, SELECT_DUE_COLD)) {
            if (ids.size() < limit) {
                ids.addAll(selectIds(tier, cutoff, limit - ids.size()));
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        ids.sort(Comparator.naturalOrder());
        return this.jdbc.sql(LOAD_DUE)
                .param("ids", ids.toArray(String[]::new))
                .query((rs, row) -> new DuePlayer(
                        rs.getObject("player_id", UUID.class),
                        rs.getString("username"),
                        rs.getBoolean("legacy_account"),
                        rs.getString("skin_texture_id"),
                        rs.getString("cape_texture_id"),
                        rs.getDouble("change_count"),
                        rs.getDouble("observed_days"),
                        rs.getObject("last_updated", OffsetDateTime.class).toInstant(),
                        rs.getLong("monthly_views")))
                .list();
    }

    public record PlayerStats(Instant lastUpdated, long monthlyViews) {}

    /** Both profile counters in one round trip; empty when the player has no refresh state. */
    public Optional<PlayerStats> loadStats(UUID playerId) {
        return this.jdbc.sql("""
                        SELECT s.last_updated, COALESCE(v.views, 0) AS views
                        FROM player_refresh_state s
                        LEFT JOIN player_monthly_views v ON v.player_id = s.player_id
                        WHERE s.player_id = :id
                        """)
                .param("id", playerId)
                .query((rs, row) -> new PlayerStats(rs.getObject("last_updated", OffsetDateTime.class).toInstant(), rs.getLong("views")))
                .optional();
    }

    private List<String> selectIds(String sql, OffsetDateTime now, int limit) {
        return this.jdbc.sql(sql)
                .param("now", now)
                .param("limit", limit)
                .query((rs, row) -> rs.getString("player_id"))
                .list();
    }

    /**
     * Writes new schedules for many players in one statement. Rows are applied in id order so
     * overlapping batches take row locks in the same order.
     */
    @Transactional
    public void recordSchedules(Collection<ScheduleUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        List<ScheduleUpdate> sorted = updates.stream()
                .sorted(Comparator.comparing(ScheduleUpdate::playerId))
                .toList();
        String[] ids = new String[sorted.size()];
        long[] next = new long[sorted.size()];
        double[] counts = new double[sorted.size()];
        double[] observed = new double[sorted.size()];
        long[] updated = new long[sorted.size()];
        for (int i = 0; i < ids.length; i++) {
            ScheduleUpdate update = sorted.get(i);
            ids[i] = update.playerId().toString();
            next[i] = update.nextRefreshAt().toEpochMilli();
            counts[i] = update.changeCount();
            observed[i] = update.observedDays();
            updated[i] = update.lastUpdated().toEpochMilli();
        }
        this.jdbc.sql("""
                        UPDATE player_refresh_state s
                        SET next_refresh_at = to_timestamp(v.next / 1000.0),
                            change_count = v.counts,
                            observed_days = v.observed,
                            last_updated = to_timestamp(v.updated / 1000.0)
                        FROM unnest(:ids::text[], :next::bigint[], :counts::float8[], :observed::float8[], :updated::bigint[])
                             AS v(id, next, counts, observed, updated)
                        WHERE s.player_id = v.id::uuid
                        """)
                .param("ids", ids)
                .param("next", next)
                .param("counts", counts)
                .param("observed", observed)
                .param("updated", updated)
                .update();
    }

    @Transactional
    public void bumpFailures(Collection<UUID> playerIds, Instant nextRefreshAt) {
        if (playerIds.isEmpty()) {
            return;
        }
        this.jdbc.sql("UPDATE player_refresh_state SET next_refresh_at = :next WHERE player_id IN (:ids)")
                .param("next", OffsetDateTime.ofInstant(nextRefreshAt, ZoneOffset.UTC))
                .param("ids", playerIds)
                .update();
    }
}
