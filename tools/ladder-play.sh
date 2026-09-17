#!/usr/bin/env bash
# Play ladder pairings (external vs external) from a cells file: "teamA teamB map" per line.
#   MAXJOBS=2 tools/ladder-play.sh tools/ladder-cells.txt      # -> gauntlet/ladder/<id>/results.raw (A,B,map,winner,rounds,reason)
# Replays are discarded (these are not our games). Runs on the VM through vm-run.sh.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$REPO/tools/lib.sh"
CELLS="${1:?cells file}"; MAXJOBS="${MAXJOBS:-2}"; ID="$(date +%Y%m%d-%H%M%S)-ladder"; OUT="$REPO/gauntlet/ladder/$ID"; mkdir -p "$OUT"
MANIFEST="$BENCH_CLASSES/../manifest.tsv"
export REPO OUT MANIFEST BENCH_CLASSES ENGINE_DIR
resolve () { awk -F'\t' -v n="$1" '$1==n{print $2" "$3; exit}' "$MANIFEST" | grep . ; }
export -f resolve engine_cp
echo "ladder tick $ID: $(grep -c . "$CELLS") games, jobs=$MAXJOBS"
play () {
  local A="$1" B="$2" MAP="$3"; local PA UA PB UB
  read -r PA UA <<<"$(resolve "$A")" || { echo "$A,$B,$MAP,?,?,unknown $A" >> "$OUT/results.raw"; return; }
  read -r PB UB <<<"$(resolve "$B")" || { echo "$A,$B,$MAP,?,?,unknown $B" >> "$OUT/results.raw"; return; }
  local RP; RP="$(mktemp -u /tmp/ladder-XXXXXX.bc21)"
  local LOG; LOG="$(timeout "${GAME_TIMEOUT:-1500}" java -Xmx512m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=512m \
    -Dbc.server.mode=headless -Dbc.server.map-path="$ENGINE_DIR/maps" -Dbc.game.map-path="$ENGINE_DIR/maps" \
    -Dbc.server.robot-player-to-system-out=false -Dbc.server.debug=false -Dbc.engine.debug-methods=false -Dbc.engine.enable-profiler=false \
    -Dbc.engine.show-indicators=false -Dbc.game.team-a="$PA" -Dbc.game.team-b="$PB" -Dbc.game.team-a.url="$UA" -Dbc.game.team-b.url="$UB" \
    -Dbc.game.maps="$MAP" -Dbc.server.save-file="$RP" -cp "$(engine_cp)" battlecode.server.Main -c=- 2>&1 || true)"
  rm -f "$RP"
  local W R RE
  W=$(printf '%s\n' "$LOG" | sed -n 's/.*(\([AB]\)) wins.*/\1/p' | tail -1); R=$(printf '%s\n' "$LOG" | sed -n 's/.*wins (round \([0-9]*\)).*/\1/p' | tail -1); RE=$(printf '%s\n' "$LOG" | sed -n 's/.*Reason: //p' | tail -1)
  echo "$A,$B,$MAP,${W:-?},${R:-?},${RE:-unknown}" >> "$OUT/results.raw"
  echo "  $A vs $B on $MAP -> ${W:-?} r${R:-?}"
}
export -f play
: > "$OUT/results.raw"
grep . "$CELLS" | xargs -P "$MAXJOBS" -L 1 bash -c 'play "$0" "$1" "$2"'
echo "wrote $OUT/"
