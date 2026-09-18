#!/usr/bin/env bash
# Time the same deterministic match under different engine/JVM flags. The 2021 engine is deterministic,
# so every variant plays an identical game and the only difference is overhead.
#   tools/bench-flags.sh [map] [teamA] [teamB]
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$REPO/tools/lib.sh"
MAP="${1:-Gridlock}"; TA="${2:-g_iter4}"; TB="${3:-g_iter3}"
OUT="$REPO/build/bench-classes"; rm -rf "$OUT"; compile_src "$REPO/src" "$OUT" >/dev/null 2>&1
run () {   # run <label> <extra jvm args...> -- <extra -D args...>
  local label="$1"; shift; local jvm=() dee=()
  while [ "$1" != "--" ]; do jvm+=("$1"); shift; done; shift; dee=("$@")
  local rep; rep="$(mktemp -u /tmp/bench-XXXX.bc21)"
  local t0 t1; t0=$(date +%s.%N)
  java "${jvm[@]}" -Dbc.server.mode=headless -Dbc.server.map-path="$ENGINE_DIR/maps" -Dbc.game.map-path="$ENGINE_DIR/maps" \
    -Dbc.server.robot-player-to-system-out=false -Dbc.server.debug=false -Dbc.engine.debug-methods=false \
    -Dbc.engine.enable-profiler=false -Dbc.game.team-a="$TA" -Dbc.game.team-b="$TB" \
    -Dbc.game.team-a.url="$OUT" -Dbc.game.team-b.url="$OUT" -Dbc.game.maps="$MAP" \
    "${dee[@]}" -cp "$(engine_cp)" battlecode.server.Main -c=- > /tmp/bench-out.txt 2>&1 || true
  t1=$(date +%s.%N)
  local rounds; rounds=$(sed -n 's/.*wins (round \([0-9]*\)).*/\1/p' /tmp/bench-out.txt | tail -1)
  printf '%-34s %7.1f s   rounds=%s  replay=%s\n' "$label" "$(echo "$t1 - $t0" | bc)" "${rounds:-?}" "$(du -h "$rep" 2>/dev/null | cut -f1 || echo none)"
  rm -f "$rep"
}
REP=/tmp/bench-keep.bc21
echo "map=$MAP  $TA vs $TB"
run "current (SerialGC 512m, ind=on)"  -Xmx512m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=512m -- -Dbc.engine.show-indicators=true  -Dbc.server.save-file=$REP
run "indicators off"                   -Xmx512m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=512m -- -Dbc.engine.show-indicators=false -Dbc.server.save-file=$REP
run "indicators off, no replay"        -Xmx512m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=512m -- -Dbc.engine.show-indicators=false
run "G1 2g, ind off"                   -Xmx2g   -XX:+UseG1GC     -XX:ReservedCodeCacheSize=512m -- -Dbc.engine.show-indicators=false -Dbc.server.save-file=$REP
run "Parallel 1g, ind off"             -Xmx1g   -XX:+UseParallelGC -XX:ReservedCodeCacheSize=512m -- -Dbc.engine.show-indicators=false -Dbc.server.save-file=$REP
run "SerialGC 1g, ind off, C2 only"    -Xmx1g   -XX:+UseSerialGC -XX:ReservedCodeCacheSize=512m -XX:-TieredCompilation -- -Dbc.engine.show-indicators=false -Dbc.server.save-file=$REP
rm -f $REP
