-- per-view write path: dedupe lookup + insert + trigger upsert, 20k views from distinct players (rolled back)
BEGIN;
SELECT pg_current_wal_lsn() AS l0, clock_timestamp() AS t0 \gset
DO $$
DECLARE r record; n int := 0;
BEGIN
  FOR r IN SELECT player_id FROM player_refresh_state TABLESAMPLE SYSTEM (0.2) LIMIT 20000 LOOP
    IF NOT EXISTS (SELECT 1 FROM player_view_events WHERE player_id = r.player_id AND ip_address = '203.0.113.9' AND viewed_at > now() - interval '30 days') THEN
      INSERT INTO player_view_events (player_id, ip_address, viewed_at) VALUES (r.player_id, '203.0.113.9', now());
      n := n + 1;
    END IF;
  END LOOP;
  RAISE NOTICE 'views %', n;
END $$;
SELECT 'RESULT', round(extract(epoch FROM clock_timestamp() - :'t0'::timestamptz)::numeric, 2) AS secs,
       round(pg_wal_lsn_diff(pg_current_wal_lsn(), :'l0') / 1e6, 1) AS wal_mb;
ROLLBACK;
