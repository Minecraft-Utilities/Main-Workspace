package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.PlayerMonthlyViewsRow;

import java.util.UUID;

public interface PlayerMonthlyViewsRepository extends JpaRepository<PlayerMonthlyViewsRow, UUID> {
    /**
     * Rebuilds the table from the last 30 days of view events in one pass. Only rows whose count changed
     * are written, and players that dropped out of the window are removed, so the table holds exactly the
     * players with at least one view.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        WITH counts AS MATERIALIZED (
            SELECT player_id, COUNT(*) AS views
            FROM player_view_events
            WHERE viewed_at >= NOW() - INTERVAL '30 days'
            GROUP BY player_id
        ),
        upserted AS (
            INSERT INTO player_monthly_views AS v (player_id, views)
            SELECT player_id, views FROM counts
            ON CONFLICT (player_id) DO UPDATE SET views = EXCLUDED.views
            WHERE v.views <> EXCLUDED.views
        )
        DELETE FROM player_monthly_views v
        WHERE NOT EXISTS (SELECT 1 FROM counts c WHERE c.player_id = v.player_id)
    """)
    void rebuild();
}
