# Benchmark environment

Prod-scale data (66M players, 19.8M skins, ~74M skin adoptions, ~9.6M cape adoptions, 1.2M name changes) plus a
Mojang stub, so the backend can be benchmarked without touching prod or Mojang.

| Piece | What |
|---|---|
| `docker-compose.yml` | Postgres 18 (`localhost:5434`, user/pass `mcutils`), Redis (`6381`), S3 mock (`9100`). Persistent; `docker compose up -d`. |
| `seed.sh [db] [players]` | Fills a Flyway-migrated database. About 8 minutes at 66M. Rows are pure functions of their index, so the stub can answer for any player. |
| `stub/mojang-stub.ts` | Bun server: session server, textures and blocked-servers stub on `:9200`. Options: `--latency-ms`, `--error-rate`, `--rename-rate`, `--skin-rate`, `--cape-rate`. |
| `run-app.sh [db] [-Dprop=val...]` | Runs `target/Minecraft-Utilities.jar` against the bench infra. Overrides must be `-D` properties: program arguments switch `Main` to no web server and the JVM exits after about a minute. |
| `bench-refresh.sh [secs] [-Dprop=val...]` | Re-clones `mcutils_bench` from the seed, runs the refresh loop, prints throughput per 5s window and top SQL. Saves to `results/`. |

Databases: `mcutils_bench_seed` is pristine and must never be run against. `mcutils_bench` is the throwaway working copy
(cloning takes about 2 minutes). Build the jar with
`docker run --rm -v "$PWD":/build -v ~/.m2/repository:/root/.m2/repository -w /build maven:3.9.15-eclipse-temurin-25-alpine mvn -q -DskipTests package`.
