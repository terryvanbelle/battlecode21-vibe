#!/usr/bin/env bash
# Run the Gauntlet locally: BOT vs each OPPONENT on each MAP, both sides, in
# parallel, with bare java per game (no Gradle).
#
#   tools/gauntlet.sh                                  # bot vs examplefuncsplayer, all maps
#   BOT=bot OPPONENTS="g_iter1 jmerle.camel_case" tools/gauntlet.sh
#   MAPS="maptestsmall Maze" MAXJOBS=2 tools/gauntlet.sh
#   MAPSET=quick tools/gauntlet.sh                     # the 12-map quick set
#   TAG=h2h-iter3 tools/gauntlet.sh                    # run-id suffix
#
# Opponent names: a package under src/ (ours), or a benchmark name from
# ~/projects/vibe/bc21-benchmarks/manifest.tsv (owner.package). The opponent's
# System.out is silenced (their logs are theirs); ours is kept in the replay.
#
# Output gauntlet/<run-id>/: results.csv (opponent,map,bot_side,winner_side,
# rounds,bot_result,reason), summary.txt, maps.txt, losses/*.bc21, and every
# replay under replays/ (deleted at the end unless KEEP_ALL=1, losses kept).
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$REPO/tools/lib.sh"
BOT="${BOT:-bot}"; OPPONENTS="${OPPONENTS:-examplefuncsplayer}"
MAXJOBS="${MAXJOBS:-2}"; MAPSET="${MAPSET:-full}"; TAG="${TAG:-}"
QUICK_MAPS="maptestsmall Andromeda Arena Blotches Circles Corridor CrossStitch Gridlock Maze Radial Saturn Snowflake"
SCREEN_MAPS="maptestsmall Arena Maze Gridlock"
if [ -n "${MAPS:-}" ]; then :
elif [ "$MAPSET" = quick ]; then MAPS="$QUICK_MAPS"
elif [ "$MAPSET" = screen ]; then MAPS="$SCREEN_MAPS"
else MAPS="$(grep -v '^Cow$' "$REPO/tools/bc21-maps.txt" | tr '\n' ' ')"; fi
MAPS="$(printf '%s ' $MAPS)"

MANIFEST="$BENCH_CLASSES/../manifest.tsv"
resolve () {  # name -> "package url" ; our packages first, then manifest
  local n="$1"
  if [ -d "$REPO/build/classes/$n" ]; then echo "$n $REPO/build/classes"; return; fi
  if [ -f "$MANIFEST" ]; then
    local line; line=$(awk -F'\t' -v n="$n" '$1==n{print $2" "$3; exit}' "$MANIFEST")
    [ -n "$line" ] && { echo "$line"; return; }
  fi
  echo "!! unknown opponent $n" >&2; return 1
}

# compile our sources once, fresh
rm -rf "$REPO/build/classes" && compile_src "$REPO/src" "$REPO/build/classes" || { echo "!! compile failed" >&2; exit 1; }
for o in $BOT $OPPONENTS; do resolve "$o" >/dev/null || exit 1; done

RUN_ID="$(date +%Y%m%d-%H%M%S)${TAG:+-$TAG}"
while ! mkdir -p "$REPO/gauntlet" && mkdir "$REPO/gauntlet/$RUN_ID" 2>/dev/null; do RUN_ID="$(date +%Y%m%d-%H%M%S)-$$"; done
OUT="$REPO/gauntlet/$RUN_ID"; mkdir -p "$OUT/losses" "$OUT/replays"
printf '%s\n' $MAPS > "$OUT/maps.txt"
NG=$(( $(echo $OPPONENTS | wc -w) * $(echo $MAPS | wc -w) * 2 ))
echo "gauntlet $RUN_ID bot=$BOT opponents=[$OPPONENTS] maps=$(echo $MAPS | wc -w) games=$NG jobs=$MAXJOBS"
: > "$OUT/results.raw"

game () {  # opp map side
  local OPP="$1" MAP="$2" SIDE="$3" TA TB UA UB PA PB rb ro
  rb=$(resolve "$BOT"); ro=$(resolve "$OPP")
  PB=${rb%% *}; UB=${rb#* }; PA=${ro%% *}; UA=${ro#* }
  local silence
  if [ "$SIDE" = A ]; then TA=$PB; TB=$PA; UAA=$UB; UBB=$UA; silence=-Dbc.engine.silence-b=true
  else TA=$PA; TB=$PB; UAA=$UA; UBB=$UB; silence=-Dbc.engine.silence-a=true; fi
  local REPLAY="$OUT/replays/${OPP}__${MAP}__bot${SIDE}.bc21"
  local LOG; LOG=$(java -Xmx${GAME_XMX:-512m} -XX:+UseSerialGC \
    -Dbc.server.mode=headless -Dbc.server.map-path="$ENGINE_DIR/maps" -Dbc.game.map-path="$ENGINE_DIR/maps" \
    -Dbc.server.robot-player-to-system-out=false -Dbc.server.debug=false \
    -Dbc.engine.debug-methods=false -Dbc.engine.enable-profiler=false -Dbc.engine.show-indicators=false \
    "$silence" -Dbc.game.team-a="$TA" -Dbc.game.team-b="$TB" \
    -Dbc.game.team-a.url="$UAA" -Dbc.game.team-b.url="$UBB" \
    -Dbc.game.maps="$MAP" -Dbc.server.save-file="$REPLAY" \
    -cp "$(engine_cp)" battlecode.server.Main -c=- 2>&1 || true)
  local R; R=$(parse_result "$LOG")   # RESULT W round reason
  set -- $R; local W="$2" RND="$3"; shift 3; local RE="$*"
  local res; if [ "$W" = "$SIDE" ]; then res=win; elif [ "$W" = "?" ]; then res=unknown; else res=loss; fi
  printf '%s,%s,%s,%s,%s,%s,%s\n' "$OPP" "$MAP" "$SIDE" "$W" "$RND" "$res" "$RE" >> "$OUT/results.raw"
  [ "$res" = win ] && [ "${KEEP_ALL:-0}" != 1 ] && rm -f "$REPLAY"
  [ "$res" = loss ] && mv "$REPLAY" "$OUT/losses/" 2>/dev/null
  printf '  [%3d/%d] %-4s %-28s %-24s r%s\n' "$(wc -l < "$OUT/results.raw")" "$NG" "$res" "$MAP" "$OPP" "$RND"
}
export -f game resolve parse_result engine_cp; export OUT BOT ENGINE_DIR REPO MANIFEST GAME_XMX KEEP_ALL NG BENCH_CLASSES
for OPP in $OPPONENTS; do for MAP in $MAPS; do for SIDE in A B; do echo "$OPP $MAP $SIDE"; done; done; done \
  | xargs -P "$MAXJOBS" -L 1 bash -c 'game "$0" "$1" "$2"'

{ echo "opponent,map,bot_side,winner_side,rounds,bot_result,reason"; sort "$OUT/results.raw"; } > "$OUT/results.csv"
rm -f "$OUT/results.raw"; rmdir "$OUT/replays" 2>/dev/null || true
{
  total=$(($(wc -l < "$OUT/results.csv") - 1)); wins=$(awk -F, 'NR>1&&$6=="win"' "$OUT/results.csv" | wc -l)
  echo "run $RUN_ID bot=$BOT maps=$(echo $MAPS | wc -w)"
  awk -v w="$wins" -v t="$total" 'BEGIN{printf "overall: %d/%d wins (%.1f%%)\n", w, t, (t>0)?100*w/t:0}'
  for OPP in $OPPONENTS; do
    t=$(awk -F, -v o="$OPP" 'NR>1&&$1==o' "$OUT/results.csv" | wc -l); w=$(awk -F, -v o="$OPP" 'NR>1&&$1==o&&$6=="win"' "$OUT/results.csv" | wc -l)
    sa=$(awk -F, -v o="$OPP" 'NR>1&&$1==o&&$3=="A"&&$6=="win"' "$OUT/results.csv" | wc -l); sb=$(awk -F, -v o="$OPP" 'NR>1&&$1==o&&$3=="B"&&$6=="win"' "$OUT/results.csv" | wc -l)
    awk -v o="$OPP" -v w="$w" -v t="$t" -v a="$sa" -v b="$sb" 'BEGIN{printf "  vs %-40s %3d/%-3d (%5.1f%%)  asA=%d asB=%d\n", o, w, t, (t>0)?100*w/t:0, a, b}'
  done
  echo "unknown results: $(awk -F, 'NR>1&&$6=="unknown"' "$OUT/results.csv" | wc -l)"
  echo "reasons: $(awk -F, 'NR>1{print $7}' "$OUT/results.csv" | sort | uniq -c | sort -rn | tr '\n' ';')"
} | tee "$OUT/summary.txt"
echo "wrote $OUT/"
