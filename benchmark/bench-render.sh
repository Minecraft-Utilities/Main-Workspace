#!/usr/bin/env bash
# Benchmarks skin rendering on the current mcutils_bench (no clone).
# Usage: benchmark/bench-render.sh [seconds=40] [concurrency=16]   LABEL result prefix (default render)
set -euo pipefail
cd "$(dirname "$0")"
DB=mcutils_bench; STAMP=$(date +%Y%m%d-%H%M%S); LABEL="${LABEL:-render}"
mkdir -p results work
docker exec mcutils-bench-redis-1 redis-cli -n 1 FLUSHDB >/dev/null
if ! curl -s localhost:9200/stats >/dev/null; then
  nohup bun stub/mojang-stub.ts --skins 19800000 >work/stub.log 2>&1 &
  until curl -s localhost:9200/stats >/dev/null; do sleep 0.5; done
fi
timeout 900 ./run-app.sh "$DB" -Dmc-utils.player-refresh.mojang-rate-limit=1 >"work/app-$STAMP.log" 2>&1 &
APP_PID=$!
until grep -q "Started Main" "work/app-$STAMP.log" 2>/dev/null; do sleep 1; done
docker exec -i mcutils-bench-postgres-1 psql -U mcutils -d "$DB" -At -F $'\t' \
  -c "SELECT id, username FROM players TABLESAMPLE SYSTEM (0.01) LIMIT 3000" >work/read-ids.txt
OUT="results/$LABEL-$STAMP.txt"
{ echo "== render benchmark $STAMP"; bun render-mix.ts "${1:-40}" "${2:-16}"; } | tee "$OUT"
pkill -TERM -f '[M]inecraft-Utilities.jar' || true
wait "$APP_PID" 2>/dev/null || true
