#!/usr/bin/env bash
# Benchmarks GET /capes/{id} and /skins/{id} (id-mix.ts) against the idle seed. Requires target/Minecraft-Utilities.jar.
# Usage: benchmark/bench-ids.sh [seconds=40] [concurrency=8] [-Dproperty=value ...]
set -euo pipefail
cd "$(dirname "$0")"
SECONDS_TO_RUN="${1:-40}"; CONC="${2:-8}"; shift 2 || true
DB=mcutils_bench
mkdir -p work
pg() { docker exec -i mcutils-bench-postgres-1 psql -U mcutils -d "$DB" -At "$@"; }
if [[ "${NO_CLONE:-0}" != 1 ]]; then
  docker exec mcutils-bench-postgres-1 psql -U mcutils -d postgres -q \
    -c "DROP DATABASE IF EXISTS $DB" -c "CREATE DATABASE $DB TEMPLATE mcutils_bench_seed_idle STRATEGY file_copy"
fi
if ! curl -s localhost:9200/stats >/dev/null; then
  nohup bun stub/mojang-stub.ts --skins 19800000 >work/stub.log 2>&1 &
  until curl -s localhost:9200/stats >/dev/null; do sleep 0.5; done
fi
docker exec mcutils-bench-redis-1 redis-cli -n 1 FLUSHDB >/dev/null
APP_LOG="work/app-ids-$(date +%s).log"
timeout 1200 ./run-app.sh "$DB" -Dmc-utils.player-refresh.mojang-rate-limit=1 "$@" >"$APP_LOG" 2>&1 &
APP_PID=$!
until grep -q "Started Main" "$APP_LOG" 2>/dev/null; do sleep 1; done
pg -c "SELECT id FROM capes" >work/cape-ids.txt
pg -c "(SELECT id FROM skins ORDER BY unique_owners DESC LIMIT 40) UNION ALL (SELECT id FROM skins TABLESAMPLE SYSTEM (0.001) LIMIT 160)" >work/skin-ids.txt
bun id-mix.ts "$SECONDS_TO_RUN" "$CONC"
pkill -TERM -f '[M]inecraft-Utilities.jar' || true
wait "$APP_PID" 2>/dev/null || true
