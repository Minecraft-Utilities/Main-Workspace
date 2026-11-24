#!/usr/bin/env bash
# Benchmarks the read path (read-mix.ts) against a fresh clone of the seed.
# Usage: benchmark/bench-read.sh [seconds=60] [concurrency=8] [-Dproperty=value ...]
#   NO_CLONE=1  reuse the current mcutils_bench (already migrated) instead of re-cloning
#   SEED_DB  template to clone (default mcutils_bench_seed_idle); LABEL  result name prefix (default read)
set -euo pipefail
cd "$(dirname "$0")"
SECONDS_TO_RUN="${1:-60}"; CONC="${2:-8}"; shift 2 || true
DB=mcutils_bench
SEED_DB="${SEED_DB:-mcutils_bench_seed_idle}"
STAMP=$(date +%Y%m%d-%H%M%S); LABEL="${LABEL:-read}"
mkdir -p results work
APP_LOG="work/app-$STAMP.log"
pg() { docker exec -i mcutils-bench-postgres-1 psql -U mcutils -d "$DB" -At "$@"; }

if [[ "${NO_CLONE:-0}" != 1 ]]; then
  echo "cloning $SEED_DB -> $DB"
  docker exec mcutils-bench-postgres-1 psql -U mcutils -d postgres -q \
    -c "DROP DATABASE IF EXISTS $DB" -c "CREATE DATABASE $DB TEMPLATE $SEED_DB STRATEGY file_copy"
fi
pg -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements" >/dev/null
if ! curl -s localhost:9200/stats >/dev/null; then
  nohup bun stub/mojang-stub.ts --skins 19800000 >work/stub.log 2>&1 &
  until curl -s localhost:9200/stats >/dev/null; do sleep 0.5; done
fi
docker exec mcutils-bench-redis-1 redis-cli -n 1 FLUSHDB >/dev/null

timeout 1200 ./run-app.sh "$DB" -Dmc-utils.player-refresh.mojang-rate-limit=1 "$@" >"$APP_LOG" 2>&1 &
APP_PID=$!
until grep -q "Started Main" "$APP_LOG" 2>/dev/null; do sleep 1; done
# ids are sampled after the app is up so a migration that rewrites players does not affect them
pg -F $'\t' -c "SELECT id, username FROM players TABLESAMPLE SYSTEM (0.01) LIMIT 3000" >work/read-ids.txt
pg -c "SELECT pg_stat_statements_reset()" >/dev/null
OUT="results/$LABEL-$STAMP.txt"
{
  echo "== read benchmark $STAMP (${SECONDS_TO_RUN}s, concurrency $CONC, args: $*)"
  bun read-mix.ts "$SECONDS_TO_RUN" "$CONC"
  echo "-- SQL: $(pg -F ' ' -c "SELECT sum(calls), round(sum(total_exec_time)) FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = '$DB') AND query NOT LIKE '%pg_stat_statements%'") (calls, total ms)"
  echo "-- top SQL by total time"
  pg -F ' | ' -c "SELECT calls, round(total_exec_time) AS total_ms, round(mean_exec_time::numeric, 2) AS mean_ms,
                          left(regexp_replace(query, '\s+', ' ', 'g'), 110) AS query
                   FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = '$DB')
                   ORDER BY total_exec_time DESC LIMIT 10"
} | tee "$OUT"
pkill -TERM -f '[M]inecraft-Utilities.jar' || true
wait "$APP_PID" 2>/dev/null || true
echo "saved $OUT"
