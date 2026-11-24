package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.SkinTrendingRow;

import java.util.Collection;

public interface SkinTrendingRepository extends JpaRepository<SkinTrendingRow, Long> {
    /**
     * Rebuilds the table from adoptions in the last 7 days. Vanilla skins are excluded by id so the
     * adoption scan never has to join {@code skins} per row; only changed scores are written and skins that
     * stopped trending are removed.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        WITH agg AS MATERIALIZED (
            SELECT skin_id, COUNT(*)::int AS heat
            FROM player_skin_adoptions
            WHERE last_equipped_at >= NOW() - INTERVAL '7 days'
              AND skin_id NOT IN (SELECT id FROM skins WHERE texture_id IN (:vanillaTextureIds))
            GROUP BY skin_id
        ),
        upserted AS (
            INSERT INTO skin_trending AS t (skin_id, heat)
            SELECT skin_id, heat FROM agg
            ON CONFLICT (skin_id) DO UPDATE SET heat = EXCLUDED.heat
            WHERE t.heat <> EXCLUDED.heat
        )
        DELETE FROM skin_trending t
        WHERE NOT EXISTS (SELECT 1 FROM agg a WHERE a.skin_id = t.skin_id)
    """)
    void rebuild(@Param("vanillaTextureIds") Collection<String> vanillaTextureIds);
}
