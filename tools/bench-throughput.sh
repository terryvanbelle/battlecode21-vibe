#!/usr/bin/env bash
# What actually matters: games finished per minute when the box is full, not one game's latency.
# Plays the same N-game batch under two JVM flag sets and reports wall time for each.
#   tools/bench-throughput.sh [jobs] [map]
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$REPO/tools/lib.sh"
JOBS="${1:-6}"; MAP="${2:-Gridlock}"
OUT="$REPO/build/bench-classes"; rm -rf "$OUT"; compile_src "$REPO/src" "$OUT" >/dev/null 2>&1
one () {
  local rep; rep="$(mktemp -u /tmp/tp-XXXXXX.bc21)"
  java $JVMFLAGS -Dbc.server.mode=headless -Dbc.server.map-path="$ENGINE_DIR/maps" -Dbc.game.map-path="$ENGINE_DIR/maps" \
    -Dbc.server.robot-player-to-system-out=false -Dbc.server.debug=false -Dbc.engine.debug-methods=false \
    -Dbc.engine.enable-profiler=false -Dbc.engine.show-indicators=false -Dbc.game.team-a=g_iter4 -Dbc.game.team-b=g_iter3 \
    -Dbc.game.team-a.url="$OUT" -Dbc.game.team-b.url="$OUT" -Dbc.game.maps="$1" -Dbc.server.save-file="$rep" \
    -cp "$(engine_cp)" battlecode.server.Main -c=- > /dev/null 2>&1 || true
  rm -f "$rep"
}
export -f one; export ENGINE_DIR JVMFLAGS
batch () {
  local label="$1" flags="$2" map="$3"
  export JVMFLAGS="$flags"
  local t0 t1; t0=$(date +%s%3N)
  seq "$JOBS" | xargs -P "$JOBS" -I{} bash -c "JVMFLAGS='$flags' ENGINE_DIR='$ENGINE_DIR' OUT='$OUT' one '$map'"
  t1=$(date +%s%3N); local ms=$((t1 - t0))
  printf '%-30s %s x %-14s %4d.%03d s wall  (%d ms/game)\n' "$label" "$JOBS" "$map" $((ms/1000)) $((ms%1000)) $((ms/JOBS))
}
export -f batch
echo "throughput: $JOBS parallel games"
batch "SerialGC 512m (current)" "-Xmx512m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=512m" "$MAP"
batch "ParallelGC 1g"           "-Xmx1g -XX:+UseParallelGC -XX:ReservedCodeCacheSize=512m" "$MAP"
batch "SerialGC 512m, 32x32"    "-Xmx512m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=512m" "Arena"
