package xyz.mcutils.backend.repository.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Access to {@code name_sweep_progress} and the untracked-name filter used by the name sweep.
 */
@Repository
public class NameSweepRepository {
    public record Progress(long nextIndex, int passes) {}

    private final JdbcClient jdbc;

    public NameSweepRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Progress> findProgress(int length) {
        return this.jdbc.sql("SELECT next_index, passes FROM name_sweep_progress WHERE length = :length")
                .param("length", length)
                .query((rs, row) -> new Progress(rs.getLong("next_index"), rs.getInt("passes")))
                .optional();
    }

    public void saveProgress(int length, long nextIndex, int passes) {
        this.jdbc.sql("""
                        INSERT INTO name_sweep_progress (length, next_index, passes)
                        VALUES (:length, :nextIndex, :passes)
                        ON CONFLICT (length) DO UPDATE SET next_index = EXCLUDED.next_index, passes = EXCLUDED.passes
                        """)
                .param("length", length)
                .param("nextIndex", nextIndex)
                .param("passes", passes)
                .update();
    }

    /**
     * Returns the names no tracked player currently holds. The LIMIT 1 lateral is one probe of
     * idx_players_username_upper per name, which a plain anti-join does not guarantee.
     */
    public List<String> selectUntracked(List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        return this.jdbc.sql("""
                        SELECT n.username FROM unnest(CAST(:names AS text[])) AS n(username)
                        WHERE NOT EXISTS (
                            SELECT 1 FROM players p WHERE upper(p.username) = upper(n.username) LIMIT 1
                        )
                        """)
                .param("names", names.toArray(String[]::new))
                .query((rs, row) -> rs.getString("username"))
                .list();
    }
}
