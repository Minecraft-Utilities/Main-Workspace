#!/usr/bin/env bash
# Usage: index-experiment.sh <label> <rows> [index ...]
# Drops the given indexes inside a transaction, inserts <rows> new skin adoptions (each fires the
# unique_owners trigger) and reports time and WAL, then rolls back so every run starts from the same state.
set -euo pipefail
label=$1; rows=$2; shift 2
drops=""
for i in "$@"; do drops+="DROP INDEX $i;"$'\n'; done
docker exec mcutils-bench-postgres-1 psql -U mcutils -d "${DB:-mcutils_bench}" -c checkpoint >/dev/null
docker exec -i mcutils-bench-postgres-1 psql -U mcutils -d "${DB:-mcutils_bench}" -At <<SQL | grep -E "^(RESULT|ERROR)" | sed "s/^/$label /"
BEGIN;
${SETUP:-}
$drops
SELECT pg_current_wal_lsn() AS l0, clock_timestamp() AS t0 \gset
INSERT INTO player_skin_adoptions (player_id, skin_id, first_seen, last_equipped_at)
SELECT p.player_id, 1 + (random() * 19000000)::bigint, now(), NULL
FROM (SELECT player_id FROM player_refresh_state TABLESAMPLE SYSTEM (1) LIMIT $rows) p
ON CONFLICT DO NOTHING;
SELECT 'RESULT', round(extract(epoch FROM clock_timestamp() - :'t0'::timestamptz)::numeric, 2) AS secs,
       round(pg_wal_lsn_diff(pg_current_wal_lsn(), :'l0') / 1e6, 1) AS wal_mb;
ROLLBACK;
SQL
