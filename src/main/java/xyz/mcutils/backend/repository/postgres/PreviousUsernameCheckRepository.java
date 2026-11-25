package xyz.mcutils.backend.repository.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Set-based access to {@code previous_username_checks} for the background resolver. Names are
 * stored lowercased because Mojang usernames are case-insensitive.
 */
@Repository
public class PreviousUsernameCheckRepository {
    private static final Duration FIRST_RETRY_DELAY = Duration.ofDays(30);
    private static final Duration SECOND_RETRY_DELAY = Duration.ofDays(90);

    private final JdbcClient jdbc;

    public PreviousUsernameCheckRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void upsert(String username, Instant dueAt) {
        this.jdbc.sql("""
                        INSERT INTO previous_username_checks (username, due_at)
                        VALUES (lower(:username), :dueAt)
                        ON CONFLICT (username) DO UPDATE SET due_at = EXCLUDED.due_at, attempts = 0
                        """)
                .param("username", username)
                .param("dueAt", timestamp(dueAt))
                .update();
    }

    public List<String> selectDue(Instant now, int limit) {
        return this.jdbc.sql("""
                        SELECT username FROM previous_username_checks
                        WHERE due_at <= :now
                        ORDER BY due_at
                        LIMIT :limit
                        """)
                .param("now", timestamp(now))
                .param("limit", limit)
                .query((rs, row) -> rs.getString("username"))
                .list();
    }

    @Transactional
    public int deleteOwnedByTrackedPlayers(List<String> usernames) {
        if (usernames.isEmpty()) {
            return 0;
        }
        // The LIMIT 1 lateral cannot be flattened into a join, so each name is one probe of
        // idx_players_username_upper. A plain join let the planner pick a scan of the whole players
        // table whenever the expression index had no statistics.
        return this.jdbc.sql("""
                        DELETE FROM previous_username_checks c
                        USING unnest(CAST(:names AS text[])) AS n(username)
                        CROSS JOIN LATERAL (
                            SELECT 1 FROM players p WHERE upper(p.username) = upper(n.username) LIMIT 1
                        ) owned
                        WHERE c.username = n.username
                        """)
                .param("names", usernames.toArray(String[]::new))
                .update();
    }

    /**
     * Only rows that were due at {@code dueBy} are touched, so a name released again while its
     * lookup was in flight keeps the fresh 37-day row written by {@link #upsert}.
     */
    @Transactional
    public void delete(List<String> usernames, Instant dueBy) {
        if (usernames.isEmpty()) {
            return;
        }
        this.jdbc.sql("DELETE FROM previous_username_checks WHERE username = ANY(:names) AND due_at <= :dueBy")
                .param("names", usernames.toArray(String[]::new))
                .param("dueBy", timestamp(dueBy))
                .update();
    }

    /**
     * Gives unclaimed names a longer wait, dropping them after the third attempt. The delete and
     * the update must stay on opposite sides of the {@code attempts} 2 boundary: rows touched by
     * both sub-statements would be modified twice within one command, which Postgres rejects.
     */
    @Transactional
    public void markUnclaimed(List<String> usernames, Instant now) {
        if (usernames.isEmpty()) {
            return;
        }
        this.jdbc.sql("""
                        WITH removed AS (
                            DELETE FROM previous_username_checks
                            WHERE username = ANY(:names) AND attempts >= 2 AND due_at <= :now
                        )
                        UPDATE previous_username_checks
                        SET attempts = attempts + 1,
                            due_at = CASE attempts WHEN 0 THEN :first ELSE :second END
                        WHERE username = ANY(:names) AND attempts < 2 AND due_at <= :now
                        """)
                .param("names", usernames.toArray(String[]::new))
                .param("first", timestamp(now.plus(FIRST_RETRY_DELAY)))
                .param("second", timestamp(now.plus(SECOND_RETRY_DELAY)))
                .param("now", timestamp(now))
                .update();
    }

    @Transactional
    public void postpone(List<String> usernames, Instant until) {
        if (usernames.isEmpty()) {
            return;
        }
        this.jdbc.sql("UPDATE previous_username_checks SET due_at = :until WHERE username = ANY(:names)")
                .param("until", timestamp(until))
                .param("names", usernames.toArray(String[]::new))
                .update();
    }

    public long countDue(Instant now) {
        Long count = this.jdbc.sql("SELECT count(*) FROM previous_username_checks WHERE due_at <= :now")
                .param("now", timestamp(now))
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
