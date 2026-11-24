#!/usr/bin/env bash
# Benchmarks the submit consumer (PlayerSubmitService -> PlayerService.createPlayers) on new players.
# Usage: benchmark/bench-submit.sh [players=20000] [-Dproperty=value ...]
#   SEED_DB   template to clone (default mcutils_bench_seed_idle: migrated seed with every refresh pushed 10 years out,
#             so the background refresh cannot compete for the database)
#   LABEL     name prefix for saved results (default: submit)
# New players are md5('p' || i) for i beyond the seeded range, which the Mojang stub answers like any seeded player.
set -euo pipefail
cd "$(dirname "$0")"

PLAYERS="${1:-20000}"
shift || true
DB=mcutils_bench
SEED_DB="${SEED_DB:-mcutils_bench_seed_idle}"
STAMP=$(date +%Y%m%d-%H%M%S)
LABEL="${LABEL:-submit}"
mkdir -p results work
export STAMP LABEL
OUT="results/$LABEL-$STAMP.txt"
APP_LOG="work/app-$STAMP.log"

pg() { docker exec -i mcutils-bench-postgres-1 psql -U mcutils -d "$DB" -At "$@"; }

echo "cloning $SEED_DB -> $DB"
docker exec mcutils-bench-postgres-1 psql -U mcutils -d postgres -q \
  -c "DROP DATABASE IF EXISTS $DB" -c "CREATE DATABASE $DB TEMPLATE $SEED_DB STRATEGY file_copy"
pg -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements" >/dev/null

if ! curl -s localhost:9200/stats >/dev/null; then
  nohup bun stub/mojang-stub.ts --skins 19800000 >work/stub.log 2>&1 &
  until curl -s localhost:9200/stats >/dev/null; do sleep 0.5; done
fi
docker exec mcutils-bench-redis-1 redis-cli -n 1 DEL player-submit-queue player-submit-queue-ids >/dev/null

timeout 900 ./run-app.sh "$DB" -Dmc-utils.player-refresh.mojang-rate-limit=1000000 "$@" >"$APP_LOG" 2>&1 &
APP_PID=$!
until grep -q "Started Main" "$APP_LOG" 2>/dev/null; do sleep 0.5; done
sleep 5

pg -c "SELECT pg_stat_statements_reset()" >/dev/null
BASE_PLAYERS=$(pg -c "SELECT reltuples::bigint FROM pg_class WHERE relname = 'players'")
snapshot() { pg -F ' ' -c "SELECT wal_records, wal_fpi, wal_bytes FROM pg_stat_wal"; }
WAL0=$(snapshot)
START=$(date +%s.%N)
pg -c "SELECT 'RPUSH player-submit-queue ' || md5('p' || i)::uuid FROM generate_series(70000000, 70000000 + $PLAYERS - 1) i" \
  | docker exec -i mcutils-bench-redis-1 redis-cli -n 1 >/dev/null
until [[ "$(docker exec mcutils-bench-redis-1 redis-cli -n 1 LLEN player-submit-queue)" == 0 ]]; do sleep 0.5; done
# the consumer pops a batch before it persists it, so wait for the row count to settle
LAST=-1
while true; do
  NOW=$(pg -c "SELECT count(*) FROM players WHERE first_seen > now() - interval '1 hour' AND id IN (SELECT md5('p' || i)::uuid FROM generate_series(70000000, 70000000 + $PLAYERS - 1) i)")
  [[ "$NOW" == "$LAST" && "$NOW" -gt 0 ]] && break
  LAST=$NOW; sleep 3
done
END=$(date +%s.%N)
WAL1=$(snapshot)
pkill -TERM -f '[M]inecraft-Utilities.jar' || true
wait "$APP_PID" 2>/dev/null || true

{
  echo "== submit benchmark $STAMP ($PLAYERS players, seed $SEED_DB, args: $*)"
  python3 - "$START" "$END" "$LAST" "$PLAYERS" "$WAL0" "$WAL1" <<'PY'
import json, sys, os
start, end, created, players = float(sys.argv[1]), float(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
w0 = [float(x) for x in sys.argv[5].split()]; w1 = [float(x) for x in sys.argv[6].split()]
secs = end - start - 3  # trailing settle poll
d = [b - a for a, b in zip(w0, w1)]
s = {"submitted": players, "created": created, "seconds": round(secs, 1), "players_per_s": round(created / secs, 1),
     "wal_bytes_per_player": round(d[2] / max(created, 1)), "wal_records_per_player": round(d[0] / max(created, 1), 1),
     "wal_fpi_per_player": round(d[1] / max(created, 1), 2), "wal_mb": round(d[2] / 1e6, 1)}
print(json.dumps(s, indent=1))
json.dump(s, open(f"results/{os.environ.get('LABEL', 'submit')}-{os.environ['STAMP']}.json", "w"))
PY
  echo "-- top SQL by total time"
  pg -F ' | ' -c "SELECT calls, round(total_exec_time) AS total_ms, round(mean_exec_time::numeric, 3) AS mean_ms, rows,
                          left(regexp_replace(query, '\s+', ' ', 'g'), 110) AS query
                   FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = '$DB')
                   ORDER BY total_exec_time DESC LIMIT 14"
} | tee "$OUT"
echo "saved $OUT (app log: $APP_LOG)"
