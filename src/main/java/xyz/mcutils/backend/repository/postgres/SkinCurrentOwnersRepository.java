package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.SkinCurrentOwnersRow;

public interface SkinCurrentOwnersRepository extends JpaRepository<SkinCurrentOwnersRow, Long> {
    /**
     * Moves every pending delta into the counts in one statement, in skin id order. Concurrent folds
     * cannot double count: the second blocks on the deleted delta rows and then finds none.
     *
     * @return the number of skins whose count changed
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        WITH folded AS (
            DELETE FROM skin_owner_deltas RETURNING skin_id, delta
        ),
        net AS (
            SELECT skin_id, SUM(delta) AS delta
            FROM folded
            GROUP BY skin_id
            HAVING SUM(delta) <> 0
            ORDER BY skin_id
        )
        INSERT INTO skin_current_owners AS o (skin_id, owners)
        SELECT skin_id, delta FROM net
        ON CONFLICT (skin_id) DO UPDATE SET owners = o.owners + EXCLUDED.owners
    """)
    int foldDeltas();
}
