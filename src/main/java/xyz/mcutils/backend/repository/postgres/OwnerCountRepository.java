package xyz.mcutils.backend.repository.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the owner count deltas appended by the database triggers. Writers only append, so they
 * never contend; this is the single writer of the counts.
 */
@Repository
public class OwnerCountRepository {
    private final JdbcClient jdbc;

    public OwnerCountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Moves every pending delta into its count in one statement. A concurrent fold cannot double
     * count: it blocks on the deleted delta rows and then finds none.
     *
     * @return the number of distinct counts that changed
     */
    @Transactional
    public int fold() {
        return this.jdbc.sql("""
                WITH folded AS (
                    DELETE FROM counter_deltas RETURNING counter, id, delta
                ),
                net AS MATERIALIZED (
                    SELECT counter, id, SUM(delta) AS delta
                    FROM folded
                    GROUP BY counter, id
                    HAVING SUM(delta) <> 0
                ),
                skin_current AS (
                    INSERT INTO skin_current_owners AS o (skin_id, owners)
                    SELECT id, delta FROM net WHERE counter = 'skin_current' ORDER BY id
                    ON CONFLICT (skin_id) DO UPDATE SET owners = o.owners + EXCLUDED.owners
                ),
                skin_unique AS (
                    UPDATE skins s SET unique_owners = s.unique_owners + net.delta
                    FROM net
                    WHERE net.counter = 'skin_unique' AND s.id = net.id
                ),
                capes AS (
                    SELECT id,
                        COALESCE(SUM(delta) FILTER (WHERE counter = 'cape_unique'), 0) AS unique_delta,
                        COALESCE(SUM(delta) FILTER (WHERE counter = 'cape_current'), 0) AS current_delta
                    FROM net
                    WHERE counter IN ('cape_unique', 'cape_current')
                    GROUP BY id
                ),
                cape_update AS (
                    UPDATE capes c SET unique_owners = c.unique_owners + capes.unique_delta,
                        current_owners = c.current_owners + capes.current_delta
                    FROM capes
                    WHERE c.id = capes.id
                )
                SELECT COUNT(*) FROM net
                """)
                .query(Integer.class)
                .single();
    }
}
