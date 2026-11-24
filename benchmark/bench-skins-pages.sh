#!/usr/bin/env bash
# Times /skins pages at increasing depth against the current mcutils_bench (NO clone).
# Usage: benchmark/bench-skins-pages.sh   LABEL result prefix (default skins-pages)
set -euo pipefail
cd "$(dirname "$0")"
DB=mcutils_bench; STAMP=$(date +%Y%m%d-%H%M%S); LABEL="${LABEL:-skins-pages}"
mkdir -p results work
docker exec mcutils-bench-redis-1 redis-cli -n 1 FLUSHDB >/dev/null
if ! curl -s localhost:9200/stats >/dev/null; then
  nohup bun stub/mojang-stub.ts --skins 19800000 >work/stub.log 2>&1 &
  until curl -s localhost:9200/stats >/dev/null; do sleep 0.5; done
fi
timeout 900 ./run-app.sh "$DB" -Dmc-utils.player-refresh.mojang-rate-limit=1 >"work/app-$STAMP.log" 2>&1 &
APP_PID=$!
until grep -q "Started Main" "work/app-$STAMP.log" 2>/dev/null; do sleep 1; done
OUT="results/$LABEL-$STAMP.txt"
{
  echo "== skins page depth $STAMP"
  for sort in TOP LATEST; do
    for page in 1 100 4000 100000; do
      t=$(curl -s -o /dev/null -w '%{http_code} %{time_total}' "localhost:8080/skins?page=$page&sort=$sort")
      echo "$sort page=$page $t"
    done
  done
} | tee "$OUT"
pkill -TERM -f '[M]inecraft-Utilities.jar' || true
wait "$APP_PID" 2>/dev/null || true
