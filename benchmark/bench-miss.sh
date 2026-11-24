#!/usr/bin/env bash
# Benchmarks reads of known players while unknown players are created through the Mojang path.
# Usage: benchmark/bench-miss.sh [seconds=30] [miss-clients=16] [hit-clients=4]
#   LABEL result name prefix (default miss); the pool is shrunk to 10 and Mojang answers after 300 ms
#   so connections held across the Mojang call show up as hit latency.
set -euo pipefail
cd "$(dirname "$0")"
SECONDS_TO_RUN="${1:-30}"; MISS="${2:-16}"; HIT="${3:-4}"
DB=mcutils_bench
STAMP=$(date +%Y%m%d-%H%M%S); LABEL="${LABEL:-miss}"
mkdir -p results work
APP_LOG="work/app-$STAMP.log"
pg() { docker exec -i mcutils-bench-postgres-1 psql -U mcutils -d "$DB" -At "$@"; }

pkill -f '[m]ojang-stub.ts' || true
sleep 1
nohup bun stub/mojang-stub.ts --skins 19800000 --latency-ms 300 >work/stub.log 2>&1 &
until curl -s localhost:9200/stats >/dev/null; do sleep 0.5; done
docker exec mcutils-bench-redis-1 redis-cli -n 1 FLUSHDB >/dev/null

timeout 1200 ./run-app.sh "$DB" -Dmc-utils.player-refresh.mojang-rate-limit=1 -Dspring.datasource.hikari.maximum-pool-size=10 >"$APP_LOG" 2>&1 &
APP_PID=$!
until grep -q "Started Main" "$APP_LOG" 2>/dev/null; do sleep 1; done
pg -F $'\t' -c "SELECT id, username FROM players TABLESAMPLE SYSTEM (0.01) LIMIT 3000" >work/read-ids.txt
OUT="results/$LABEL-$STAMP.txt"
{
  echo "== miss benchmark $STAMP (${SECONDS_TO_RUN}s, miss clients $MISS, hit clients $HIT)"
  bun miss-mix.ts "$SECONDS_TO_RUN" "$MISS" "$HIT"
} | tee "$OUT"
pkill -TERM -f '[M]inecraft-Utilities.jar' || true
wait "$APP_PID" 2>/dev/null || true
pkill -f '[m]ojang-stub.ts' || true
echo "saved $OUT"
