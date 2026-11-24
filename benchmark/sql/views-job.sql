-- PlayerMonthlyViewsRepository.rebuild
SELECT pg_current_wal_lsn() AS l0 \gset
\timing on
WITH counts AS MATERIALIZED (SELECT player_id, COUNT(*) AS views FROM player_view_events WHERE viewed_at >= NOW() - INTERVAL '30 days' GROUP BY player_id),
upserted AS (INSERT INTO player_monthly_views AS v (player_id, views) SELECT player_id, views FROM counts
             ON CONFLICT (player_id) DO UPDATE SET views = EXCLUDED.views WHERE v.views <> EXCLUDED.views)
DELETE FROM player_monthly_views v WHERE NOT EXISTS (SELECT 1 FROM counts c WHERE c.player_id = v.player_id);
\timing off
SELECT 'WAL_MB', round(pg_wal_lsn_diff(pg_current_wal_lsn(), :'l0') / 1e6, 1);
