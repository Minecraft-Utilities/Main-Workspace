package xyz.mcutils.backend.repository.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.PlayerCapeAdoptionRow;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRefreshStateRow;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.PlayerSkinAdoptionRow;

import java.util.List;

/**
 * Bulk insert of brand-new players. One statement per table: the rows are known to be new, so
 * Hibernate's merge-before-insert existence {@code SELECT} per row would be pure overhead.
 * <p>
 * Callers pass rows sorted by player id so the b-tree inserts of every table walk their indexes in order.
 */
@Repository
public class PlayerCreateRepository {
    private final JdbcClient jdbc;

    public PlayerCreateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return the number of players actually inserted; rows that already existed are skipped. */
    @Transactional
    public int insertAll(List<PlayerRow> players,
                          List<PlayerRefreshStateRow> states,
                          List<PlayerSkinAdoptionRow> skinAdoptions,
                          List<PlayerCapeAdoptionRow> capeAdoptions) {
        int inserted = insertPlayers(players);
        insertStates(states);
        insertSkinAdoptions(skinAdoptions);
        insertCapeAdoptions(capeAdoptions);
        return inserted;
    }

    private int insertPlayers(List<PlayerRow> rows) {
        int n = rows.size();
        String[] ids = new String[n];
        String[] names = new String[n];
        boolean[] legacy = new boolean[n];
        long[] submitted = new long[n];
        long[] skinIds = new long[n];
        Long[] capeIds = new Long[n];
        long[] firstSeen = new long[n];
        for (int i = 0; i < n; i++) {
            PlayerRow row = rows.get(i);
            ids[i] = row.getId().toString();
            names[i] = row.getUsername();
            legacy[i] = row.isLegacyAccount();
            submitted[i] = row.getSubmittedUuids();
            skinIds[i] = row.getSkin().getId();
            capeIds[i] = row.getCape() != null ? row.getCape().getId() : null;
            firstSeen[i] = row.getFirstSeen().toEpochMilli();
        }
        return this.jdbc.sql("""
                        INSERT INTO players (id, username, legacy_account, submitted_uuids, skin_id, cape_id, first_seen)
                        SELECT v.id::uuid, v.username, v.legacy, v.submitted, v.skin_id, v.cape_id, to_timestamp(v.first_seen / 1000.0)
                        FROM unnest(:ids::text[], :names::text[], :legacy::boolean[], :submitted::bigint[],
                                    :skins::bigint[], :capes::bigint[], :seen::bigint[])
                             AS v(id, username, legacy, submitted, skin_id, cape_id, first_seen)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("ids", ids)
                .param("names", names)
                .param("legacy", legacy)
                .param("submitted", submitted)
                .param("skins", skinIds)
                .param("capes", capeIds)
                .param("seen", firstSeen)
                .update();
    }

    private void insertStates(List<PlayerRefreshStateRow> rows) {
        int n = rows.size();
        String[] ids = new String[n];
        long[] next = new long[n];
        double[] counts = new double[n];
        double[] observed = new double[n];
        long[] updated = new long[n];
        for (int i = 0; i < n; i++) {
            PlayerRefreshStateRow row = rows.get(i);
            ids[i] = row.getPlayerId().toString();
            next[i] = row.getNextRefreshAt().toEpochMilli();
            counts[i] = row.getChangeCount();
            observed[i] = row.getObservedDays();
            updated[i] = row.getLastUpdated().toEpochMilli();
        }
        this.jdbc.sql("""
                        INSERT INTO player_refresh_state (player_id, next_refresh_at, change_count, observed_days, last_updated)
                        SELECT v.id::uuid, to_timestamp(v.next / 1000.0), v.counts, v.observed, to_timestamp(v.updated / 1000.0)
                        FROM unnest(:ids::text[], :next::bigint[], :counts::float8[], :observed::float8[], :updated::bigint[])
                             AS v(id, next, counts, observed, updated)
                        ON CONFLICT (player_id) DO NOTHING
                        """)
                .param("ids", ids)
                .param("next", next)
                .param("counts", counts)
                .param("observed", observed)
                .param("updated", updated)
                .update();
    }

    private void insertSkinAdoptions(List<PlayerSkinAdoptionRow> rows) {
        int n = rows.size();
        String[] players = new String[n];
        long[] skins = new long[n];
        long[] firstSeen = new long[n];
        for (int i = 0; i < n; i++) {
            PlayerSkinAdoptionRow row = rows.get(i);
            players[i] = row.getPlayerId().toString();
            skins[i] = row.getSkinId();
            firstSeen[i] = row.getFirstSeen().toEpochMilli();
        }
        this.jdbc.sql("""
                        INSERT INTO player_skin_adoptions (player_id, skin_id, first_seen, last_equipped_at)
                        SELECT v.player_id::uuid, v.skin_id, to_timestamp(v.first_seen / 1000.0), NULL
                        FROM unnest(:players::text[], :skins::bigint[], :seen::bigint[]) AS v(player_id, skin_id, first_seen)
                        ON CONFLICT (player_id, skin_id) DO NOTHING
                        """)
                .param("players", players)
                .param("skins", skins)
                .param("seen", firstSeen)
                .update();
    }

    private void insertCapeAdoptions(List<PlayerCapeAdoptionRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        int n = rows.size();
        String[] players = new String[n];
        long[] capes = new long[n];
        long[] firstSeen = new long[n];
        for (int i = 0; i < n; i++) {
            PlayerCapeAdoptionRow row = rows.get(i);
            players[i] = row.getPlayerId().toString();
            capes[i] = row.getCapeId();
            firstSeen[i] = row.getFirstSeen().toEpochMilli();
        }
        this.jdbc.sql("""
                        INSERT INTO player_cape_adoptions (player_id, cape_id, first_seen, last_equipped_at)
                        SELECT v.player_id::uuid, v.cape_id, to_timestamp(v.first_seen / 1000.0), NULL
                        FROM unnest(:players::text[], :capes::bigint[], :seen::bigint[]) AS v(player_id, cape_id, first_seen)
                        ON CONFLICT (player_id, cape_id) DO NOTHING
                        """)
                .param("players", players)
                .param("capes", capes)
                .param("seen", firstSeen)
                .update();
    }
}
