-- PlayerViewEventRepository.deleteViewedBefore
SELECT pg_current_wal_lsn() AS l0 \gset
\timing on
DELETE FROM player_view_events WHERE viewed_at < now() - interval '30 days';
\timing off
SELECT 'WAL_MB', round(pg_wal_lsn_diff(pg_current_wal_lsn(), :'l0') / 1e6, 1);
