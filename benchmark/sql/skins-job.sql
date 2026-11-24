-- SkinTrendingRepository.rebuild; :window is the adoption lookback (7 days in production)
SELECT pg_current_wal_lsn() AS l0 \gset
\timing on
WITH agg AS MATERIALIZED (
    SELECT skin_id, COUNT(*)::int AS heat FROM player_skin_adoptions
    WHERE last_equipped_at >= NOW() - :'window'::interval
      AND skin_id NOT IN (SELECT id FROM skins WHERE texture_id IN ('v1','v2','v3','v4','v5'))
    GROUP BY skin_id),
upserted AS (INSERT INTO skin_trending AS t (skin_id, heat) SELECT skin_id, heat FROM agg
             ON CONFLICT (skin_id) DO UPDATE SET heat = EXCLUDED.heat WHERE t.heat <> EXCLUDED.heat)
DELETE FROM skin_trending t WHERE NOT EXISTS (SELECT 1 FROM agg a WHERE a.skin_id = t.skin_id);
\timing off
SELECT 'WAL_MB', round(pg_wal_lsn_diff(pg_current_wal_lsn(), :'l0') / 1e6, 1), (SELECT count(*) FROM skin_trending);
