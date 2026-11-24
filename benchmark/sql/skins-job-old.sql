-- SkinRepository.rebuildTrendingHeat as it was before the skin_trending table
SELECT pg_current_wal_lsn() AS l0 \gset
\timing on
WITH agg AS MATERIALIZED (
    SELECT psa.skin_id, COUNT(*)::int AS trending_heat
    FROM player_skin_adoptions psa JOIN skins skin ON skin.id = psa.skin_id
    WHERE psa.last_equipped_at >= NOW() - :'window'::interval AND skin.texture_id NOT IN ('v1','v2','v3','v4','v5')
    GROUP BY psa.skin_id),
apply_scores AS (UPDATE skins s SET trending_heat = a.trending_heat FROM agg a
    WHERE s.id = a.skin_id AND s.trending_heat IS DISTINCT FROM a.trending_heat RETURNING s.id)
UPDATE skins s SET trending_heat = 0 WHERE s.trending_heat > 0 AND NOT EXISTS (SELECT 1 FROM agg a WHERE a.skin_id = s.id);
\timing off
SELECT 'WAL_MB', round(pg_wal_lsn_diff(pg_current_wal_lsn(), :'l0') / 1e6, 1);
