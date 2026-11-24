#!/usr/bin/env bash
# Runs the backend against the bench infra and the Mojang stub.
# Usage: benchmark/run-app.sh [database] [-Dproperty=value ...]
#
# Overrides are passed as -D system properties, not program arguments: Main starts without a web
# server whenever it receives program arguments, and the JVM then exits after about a minute.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/benchmark/work"
DB="${1:-mcutils_bench}"
shift || true
STUB="${MOJANG_STUB_URL:-http://localhost:9200}"

exec /usr/lib/jvm/java-25-openjdk/bin/java \
  -Xms4g -Xmx8g \
  -Dserver.port=8080 \
  -Dspring.datasource.url="jdbc:postgresql://localhost:5434/${DB}" \
  -Dspring.datasource.username=mcutils \
  -Dspring.datasource.password=mcutils \
  -Dmc-utils.redis.port=6381 \
  -Dmc-utils.s3.endpoint=http://localhost:9100 \
  -Dmc-utils.s3.accessKey=bench \
  -Dmc-utils.s3.secretKey=bench \
  -Dmc-utils.mojang.session-server-url="$STUB" \
  -Dmc-utils.mojang.api-url="$STUB" \
  -Dmc-utils.mojang.textures-url="$STUB" \
  "$@" \
  -jar "$ROOT/target/Minecraft-Utilities.jar"
