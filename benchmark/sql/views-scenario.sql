-- usage: psql -v viewers=N -v stale=M -f views-scenario.sql
-- N players viewed in the window (3 events each, some older than 30 days); M players carry a stale count.
ALTER TABLE player_view_events DISABLE TRIGGER trg_player_monthly_views;
TRUNCATE player_view_events, player_monthly_views;
CREATE TEMP TABLE sample_players AS
SELECT player_id FROM player_refresh_state TABLESAMPLE SYSTEM (2) LIMIT (:viewers + :stale);
INSERT INTO player_view_events (player_id, ip_address, viewed_at)
SELECT p.player_id, '10.0.0.' || g, now() - random() * interval '40 days'
FROM (SELECT player_id FROM sample_players OFFSET :stale) p, generate_series(1, 3) g;
INSERT INTO player_monthly_views (player_id, views)
SELECT player_id, 1 + (random() * 20)::int FROM (SELECT player_id FROM sample_players LIMIT :stale) s;
INSERT INTO player_monthly_views (player_id, views)
SELECT player_id, 1 FROM (SELECT player_id FROM sample_players OFFSET :stale LIMIT (:viewers / 2)) s;
ALTER TABLE player_view_events ENABLE TRIGGER trg_player_monthly_views;
VACUUM (ANALYZE) player_view_events;
VACUUM (ANALYZE) player_monthly_views;
