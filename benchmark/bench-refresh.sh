#!/usr/bin/env bash
# Benchmarks the background player refresh against a fresh clone of the seeded database.
# Usage: benchmark/bench-refresh.sh [seconds=180] [-Dproperty=value ...]
#   NO_CLONE=1   reuse the current mcutils_bench database instead of re-cloning the seed
#   LABEL        name prefix for the saved results (default: refresh)
#   STUB_ARGS    extra MojangStub options, e.g. STUB_ARGS="--latency-ms 50"
# Results are printed and saved to benchmark/results/<timestamp>.txt.
set -euo pipefail
cd "$(dirname "$0")"

SECONDS_TO_RUN="${1:-180}"
shift || true
DB=mcutils_bench
SEED_DB="${SEED_DB:-mcutils_bench_seed}"
STAMP=$(date +%Y%m%d-%H%M%S)
LABEL="${LABEL:-refresh}"
export STAMP LABEL
mkdir -p results work
OUT="results/$LABEL-$STAMP.txt"
APP_LOG="work/app-$STAMP.log"

pg() { docker exec -i mcutils-bench-postgres-1 psql -U mcutils -d "${DB_OVERRIDE:-$DB}" -At "$@"; }

if [[ "${NO_CLONE:-0}" != 1 ]]; then
  echo "cloning $SEED_DB -> $DB"
  docker exec mcutils-bench-postgres-1 psql -U mcutils -d postgres -q \
    -c "DROP DATABASE IF EXISTS $DB" -c "CREATE DATABASE $DB TEMPLATE $SEED_DB STRATEGY file_copy"
  pg -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements" >/dev/null
fi

if ! curl -s localhost:9200/stats >/dev/null; then
  # shellcheck disable=SC2086
  nohup bun stub/mojang-stub.ts --skins 19800000 ${STUB_ARGS:-} >work/stub.log 2>&1 &
  until curl -s localhost:9200/stats >/dev/null; do sleep 0.5; done
fi

pg -c "SELECT pg_stat_statements_reset()" >/dev/null
snapshot() {
  pg -F ' ' -c "SELECT w.wal_records, w.wal_fpi, w.wal_bytes,
                       (SELECT coalesce(sum(n_tup_upd), 0) FROM pg_stat_user_tables),
                       (SELECT coalesce(sum(n_tup_hot_upd), 0) FROM pg_stat_user_tables),
                       d.blks_read, d.blks_hit
                FROM pg_stat_wal w, pg_stat_database d WHERE d.datname = '$DB'"
}
STATS_BEFORE=$(snapshot)
BASE=$(curl -s localhost:9200/stats | python3 -c 'import json,sys; print(json.load(sys.stdin)["profileRequests"])')

timeout --signal=TERM "$((SECONDS_TO_RUN + 60))" ./run-app.sh "$DB" \
  -Dmc-utils.player-refresh.mojang-rate-limit=1000000 "$@" >"$APP_LOG" 2>&1 &
APP_PID=$!
until grep -q "Started Main" "$APP_LOG" 2>/dev/null; do sleep 0.5; done

echo "app up, sampling for ${SECONDS_TO_RUN}s"
SAMPLES=()
for (( t = 0; t <= SECONDS_TO_RUN; t += 5 )); do
  SAMPLES+=("$(curl -s localhost:9200/stats | python3 -c 'import json,sys; print(json.load(sys.stdin)["profileRequests"])')")
  sleep 5
done
STATS_AFTER=$(snapshot)
pkill -TERM -f '[M]inecraft-Utilities.jar' || true
wait "$APP_PID" 2>/dev/null || true

{
  echo "== refresh benchmark $STAMP (${SECONDS_TO_RUN}s, args: $*)"
  python3 - "$BASE" "$STATS_BEFORE" "$STATS_AFTER" "$APP_LOG" "$DB" "${SAMPLES[@]}" <<'PY'
import json, re, subprocess, sys
base, before, after, log, db = sys.argv[1:6]
s = [int(x) for x in sys.argv[6:]]
b = [float(x) for x in before.split()]; a = [float(x) for x in after.split()]
d = [y - x for x, y in zip(b, a)]
rates = [(y - x) / 5 for x, y in zip(s, s[1:])]
fetched = s[-1] - s[0]
persisted = sum(int(m) for m in re.findall(r"persisted=(\d+)", open(log).read()))
sql = ("SELECT round(sum(total_exec_time)), sum(calls) FROM pg_stat_statements "
       f"WHERE dbid = (SELECT oid FROM pg_database WHERE datname = '{db}')")
db_ms, calls = subprocess.run(["docker", "exec", "-i", "mcutils-bench-postgres-1", "psql", "-U", "mcutils", "-d", db, "-At", "-F", " ", "-c", sql],
                              capture_output=True, text=True).stdout.split()
secs = 5 * (len(s) - 1)
per = max(persisted, 1)
summary = {
    "refreshed_per_s": round(persisted / secs, 1), "persisted": persisted, "fetched": fetched,
    "peak_per_s": max(rates), "zero_windows": sum(1 for r in rates if r == 0),
    "wal_bytes_per_player": round(d[2] / per), "wal_records_per_player": round(d[0] / per, 2),
    "wal_fpi_per_player": round(d[1] / per, 3), "wal_mb": round(d[2] / 1e6, 1),
    "row_updates_per_player": round(d[3] / per, 2), "hot_update_pct": round(100 * d[4] / max(d[3], 1), 1),
    "db_ms_per_player": round(float(db_ms) / per, 3), "sql_calls_per_player": round(float(calls) / per, 2),
    "block_reads_per_player": round(d[5] / per, 2), "cache_hit_pct": round(100 * d[6] / max(d[5] + d[6], 1), 2),
}
print(json.dumps(summary, indent=1))
print("rate per 5s window:", " ".join(f"{r:.0f}" for r in rates))
import os
json.dump(summary, open(f"results/{os.environ['LABEL']}-{os.environ['STAMP']}.json", "w"))
PY
  echo "-- failures: $(grep -c 'Failed to refresh player' "$APP_LOG") persist failures, $(grep -c 'chunk finished' "$APP_LOG") chunks"
  echo "-- top SQL by total time"
  pg -F ' | ' -c "SELECT calls, round(total_exec_time) AS total_ms, round(mean_exec_time::numeric, 2) AS mean_ms,
                          left(regexp_replace(query, '\s+', ' ', 'g'), 100) AS query
                   FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = '$DB')
                   ORDER BY total_exec_time DESC LIMIT 10"
} | tee "$OUT"
echo "saved $OUT (app log: $APP_LOG)"
