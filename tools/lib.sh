#!/usr/bin/env bash
# Shared helpers: JDK, engine classpath, one-game runner, result parsing.
# Source this; do not execute.
REPO="${REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk/jdk8u504-b01}"
export PATH="$JAVA_HOME/bin:$PATH"
ENGINE_DIR="$REPO/engine"
BENCH_CLASSES="${BENCH_CLASSES:-$HOME/projects/vibe/bc21-benchmarks/_classes}"

engine_cp () {
  [ -f "$ENGINE_DIR/engine.jar" ] || { echo "!! no engine; run tools/build-engine.sh" >&2; return 1; }
  local cp="$ENGINE_DIR/engine.jar"
  for j in "$ENGINE_DIR"/lib/*.jar; do cp="$cp:$j"; done
  echo "$cp"
}

# Where a team's classes live: our build/classes, else the benchmark class dir.
team_url () {
  if [ -d "$REPO/build/classes/$1" ]; then echo "$REPO/build/classes";
  elif [ -d "$BENCH_CLASSES/$1" ]; then echo "$BENCH_CLASSES";
  else echo "!! no compiled classes for team $1" >&2; return 1; fi
}

# run_game <teamA> <teamB> <map> <replay> [extra -D flags]  -> engine stdout
run_game () {
  local TA="$1" TB="$2" MAP="$3" REPLAY="$4"; shift 4
  local UA UB; UA="$(team_url "$TA")" || return 1; UB="$(team_url "$TB")" || return 1
  java -Xmx${GAME_XMX:-512m} -XX:+UseSerialGC \
    -Dbc.server.mode=headless -Dbc.server.map-path="$ENGINE_DIR/maps" -Dbc.game.map-path="$ENGINE_DIR/maps" \
    -Dbc.server.robot-player-to-system-out=false -Dbc.server.debug=false \
    -Dbc.engine.debug-methods=false -Dbc.engine.enable-profiler=false \
    -Dbc.engine.show-indicators=true \
    -Dbc.game.team-a="$TA" -Dbc.game.team-b="$TB" \
    -Dbc.game.team-a.url="$UA" -Dbc.game.team-b.url="$UB" \
    -Dbc.game.maps="$MAP" -Dbc.server.save-file="$REPLAY" "$@" \
    -cp "$(engine_cp)" battlecode.server.Main -c=-
}

# parse_result <engine stdout> -> "RESULT <A|B|?> <round|?> <reason>"
parse_result () {
  local LOG="$1" W R RE
  W=$(printf '%s\n' "$LOG"  | sed -n 's/.*(\([AB]\)) wins.*/\1/p' | tail -1)
  R=$(printf '%s\n' "$LOG"  | sed -n 's/.*wins (round \([0-9]*\)).*/\1/p' | tail -1)
  RE=$(printf '%s\n' "$LOG" | sed -n 's/.*Reason: //p' | tail -1)
  printf 'RESULT %s %s %s\n' "${W:-?}" "${R:-?}" "${RE:-?}"
}

# compile_bot <srcdir> <outdir> : javac every .java under srcdir (all packages)
compile_src () {
  local SRC="$1" OUT="$2"; mkdir -p "$OUT"
  local files; files=$(find "$SRC" -name '*.java' | grep -v '/test/' || true)
  [ -n "$files" ] || { echo "!! no java sources under $SRC" >&2; return 1; }
  javac -nowarn -encoding UTF-8 -source 8 -target 8 -d "$OUT" -cp "$(engine_cp)" $files
}
