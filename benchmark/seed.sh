#!/usr/bin/env bash
# Seeds a prod-shaped dataset into a database that already has the Flyway schema.
# Usage: benchmark/seed.sh [database] [players]
#   defaults: mcutils_bench_seed, 66000000 (prod scale)
#
# Every row is a pure function of its index, so the Mojang stub (stub/mojang-stub.ts) can answer
# for any seeded player without touching the database:
#   id        = md5('p' || i)::uuid
#   h         = md5(id::text)               -- canonical dashed lowercase uuid
#   username  = 'u' || substr(h, 1, 12)
#   skin k    = 1 + hex(h[1..8]) % skins    -- texture md5('s'||k) || md5('S'||k), slim when k % 4 = 0
#   cape k    = hex(h[9..16]) % 1000 < 143 ? 1 + hex(h[17..24]) % 44 : none
#                                            -- texture md5('c'||k) || md5('C'||k)
set -euo pipefail

DB="${1:-mcutils_bench_seed}"
PLAYERS="${2:-66000000}"
SKINS=$(( PLAYERS * 3 / 10 ))
CAPES=44
CHUNK=2000000
JOBS="${JOBS:-12}"

psql_db() {
  docker exec -i mcutils-bench-postgres-1 psql -U mcutils -d "$DB" -v ON_ERROR_STOP=1 -q -At \
    -v players="$PLAYERS" -v skins="$SKINS" -v chunk="$CHUNK" "$@"
}

START=$(date +%s)
step() { echo "[$(( $(date +%s) - START ))s] $*"; }

# Runs <fn> once per chunk offset below <total>, $JOBS at a time.
parallel_chunks() {
  local total=$1 fn=$2 lo running=0
  for (( lo = 0; lo < total; lo += CHUNK )); do
    "$fn" "$lo" &
    running=$(( running + 1 ))
    if (( running >= JOBS )); then
      wait -n
      running=$(( running - 1 ))
    fi
  done
  wait
}

skins_chunk() {
  psql_db -v lo="$1" <<'SQL'
SET session_replication_role = replica;
SET synchronous_commit = off;
INSERT INTO skins (id, texture_id, model, legacy, unique_owners, first_seen, trending_heat)
SELECT k, md5('s' || k) || md5('S' || k),
       CASE WHEN k % 4 = 0 THEN 'SLIM'::skin_model ELSE 'DEFAULT'::skin_model END,
       false,
       least(1000000, floor(power(random(), -1.0 / 1.5)))::bigint,
       now() - random() * interval '8 years',
       0
FROM generate_series(:lo + 1, least(:lo + :chunk, :skins)) k;
SQL
}

players_chunk() {
  psql_db -v lo="$1" <<'SQL'
SET session_replication_role = replica;
SET synchronous_commit = off;
INSERT INTO players (id, username, legacy_account, submitted_uuids, skin_id, cape_id,
                     last_updated, first_seen, monthly_views, change_velocity, next_refresh_at)
SELECT p.id,
       'u' || substr(p.h, 1, 12),
       false,
       0,
       1 + ('x' || substr(p.h, 1, 8))::bit(32)::bigint % :skins,
       CASE WHEN ('x' || substr(p.h, 9, 8))::bit(32)::bigint % 1000 < 143
            THEN 1 + ('x' || substr(p.h, 17, 8))::bit(32)::bigint % 44 END,
       now() - (p.due_days + 0.1) * interval '1 day',
       now() - random() * interval '8 years',
       0,
       CASE WHEN p.r_hot < 0.0055 THEN 0.5 + 2.9 * power(random(), 2)
            WHEN p.r_hot < 0.4    THEN random() * 0.0005
            ELSE 0 END,
       now() - p.due_days * interval '1 day'
FROM (
    SELECT md5('p' || i)::uuid AS id,
           md5(md5('p' || i)::uuid::text) AS h,
           random() AS r_hot,
           -- prod backlog shape: every player overdue, median ~28 days
           32 - 14 * power(random(), 1.5) AS due_days
    FROM generate_series(:lo + 1, least(:lo + :chunk, :players)) i
) p;
SQL
}

skin_adoptions_chunk() {
  psql_db -v lo="$1" <<'SQL'
SET session_replication_role = replica;
SET synchronous_commit = off;
-- current skin for every player, plus an older skin for 1 in 8
INSERT INTO player_skin_adoptions (player_id, skin_id, first_seen, last_equipped_at)
SELECT md5('p' || i)::uuid,
       1 + ('x' || substr(md5(md5('p' || i)::uuid::text), 1, 8))::bit(32)::bigint % :skins,
       now() - random() * interval '8 years',
       now() - random() * interval '30 days'
FROM generate_series(:lo + 1, least(:lo + :chunk, :players)) i
UNION ALL
SELECT md5('p' || i)::uuid,
       1 + (('x' || substr(md5(md5('p' || i)::uuid::text), 1, 8))::bit(32)::bigint + 7919) % :skins,
       now() - random() * interval '8 years',
       NULL
FROM generate_series(:lo + 1, least(:lo + :chunk, :players)) i
WHERE i % 8 = 0;
SQL
}

cape_adoptions_chunk() {
  psql_db -v lo="$1" <<'SQL'
SET session_replication_role = replica;
SET synchronous_commit = off;
-- current cape for every caped player, plus an extra cape for 1 in 67
INSERT INTO player_cape_adoptions (player_id, cape_id, first_seen, last_equipped_at)
SELECT s.id, s.cape_id, now() - random() * interval '4 years', now() - random() * interval '30 days'
FROM (
    SELECT md5('p' || i)::uuid AS id,
           1 + ('x' || substr(md5(md5('p' || i)::uuid::text), 17, 8))::bit(32)::bigint % 44 AS cape_id,
           ('x' || substr(md5(md5('p' || i)::uuid::text), 9, 8))::bit(32)::bigint % 1000 < 143 AS has_cape,
           i
    FROM generate_series(:lo + 1, least(:lo + :chunk, :players)) i
) s
WHERE s.has_cape
UNION ALL
SELECT s.id, 1 + s.cape_id % 44, now() - random() * interval '4 years', NULL
FROM (
    SELECT md5('p' || i)::uuid AS id,
           1 + ('x' || substr(md5(md5('p' || i)::uuid::text), 17, 8))::bit(32)::bigint % 44 AS cape_id,
           ('x' || substr(md5(md5('p' || i)::uuid::text), 9, 8))::bit(32)::bigint % 1000 < 143 AS has_cape,
           i
    FROM generate_series(:lo + 1, least(:lo + :chunk, :players)) i
) s
WHERE s.i % 67 = 0 AND s.has_cape;
SQL
}

name_events_chunk() {
  psql_db -v lo="$1" <<'SQL'
SET session_replication_role = replica;
SET synchronous_commit = off;
INSERT INTO username_change_events (player_id, new_username, previous_username, timestamp)
SELECT md5('p' || i)::uuid,
       'u' || substr(md5(md5('p' || i)::uuid::text), 1, 12),
       'o' || substr(md5(md5('p' || i)::uuid::text), 13, 10),
       now() - random() * interval '5 years'
FROM generate_series(:lo + 1, least(:lo + :chunk, :players)) i
WHERE i % 55 = 0;
SQL
}

echo "== seeding $DB: $PLAYERS players, $SKINS skins, $CAPES capes"

step "dropping secondary indexes (recreated at the end)"
psql_db <<'SQL'
CREATE TABLE IF NOT EXISTS bench_index_defs (name text PRIMARY KEY, def text NOT NULL);
INSERT INTO bench_index_defs
SELECT i.indexname, i.indexdef FROM pg_indexes i
WHERE i.schemaname = 'public'
  AND i.tablename IN ('players','skins','player_skin_adoptions','player_cape_adoptions','username_change_events','capes')
  AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conname = i.indexname)
ON CONFLICT DO NOTHING;
SELECT format('DROP INDEX IF EXISTS %I', name) FROM bench_index_defs \gexec
SQL

step "capes"
psql_db <<'SQL'
SET session_replication_role = replica;
INSERT INTO capes (id, name, texture_id, unique_owners, first_seen)
SELECT k, 'cape' || k, md5('c' || k) || md5('C' || k), 0, now() - interval '4 years'
FROM generate_series(1, 44) k;
SQL

step "skins"
parallel_chunks "$SKINS" skins_chunk
psql_db -c "SELECT setval('skins_id_seq', $SKINS), setval('capes_id_seq', $CAPES)" >/dev/null

step "players"
parallel_chunks "$PLAYERS" players_chunk

step "skin adoptions"
parallel_chunks "$PLAYERS" skin_adoptions_chunk

step "cape adoptions"
parallel_chunks "$PLAYERS" cape_adoptions_chunk

step "username change events"
parallel_chunks "$PLAYERS" name_events_chunk

# Hibernate allocates ids from username_change_events_seq (pooled, increment 50); it must start past the seeded ids.
psql_db -c "SELECT setval('username_change_events_seq', (SELECT coalesce(max(id), 0) FROM username_change_events) + 100)" >/dev/null

step "recreating indexes"
psql_db -c "SELECT def || ';' FROM bench_index_defs" | grep . | xargs -d '\n' -P 4 -I{} sh -c 'docker exec -i mcutils-bench-postgres-1 psql -U mcutils -d '"$DB"' -v ON_ERROR_STOP=1 -q -c "$1"' _ {}

step "vacuum analyze"
psql_db -c "VACUUM (ANALYZE, FREEZE)"

step "done"
psql_db -c "SELECT relname, n_live_tup, pg_size_pretty(pg_total_relation_size(relid)) FROM pg_stat_user_tables ORDER BY n_live_tup DESC"
