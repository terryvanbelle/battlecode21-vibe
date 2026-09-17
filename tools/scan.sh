#!/usr/bin/env bash
# Incremental two-stage tiering scan of external bots.
#   tools/scan.sh                                   # every name-selected benchmark bot
#   OPPONENTS="a.b c.d" tools/scan.sh               # a given list
#   DONE="gauntlet/x/results.csv gauntlet/y/results.csv" tools/scan.sh   # results already played (never replayed)
# Stage 1: every opponent on STAGE1_MAPS (default: maptestsmall Arena Gridlock), both sides,
#          playing only the cells DONE does not already decide.
# Stage 2: opponents whose stage-1 record is neither 0% nor 100% get STAGE2_MAPS
#          (default: Maze Saturn Circles Corridor), both sides, again only the missing cells.
# Then tools/gauntlet-select.py over DONE + both stages writes tools/roster.txt (the 20-50% band).
set -euo pipefail
cd "$(dirname "$0")/.."
OPP="${OPPONENTS:-$(tools/bench-select.py)}"
STAGE1_MAPS="${STAGE1_MAPS:-maptestsmall Arena Gridlock}"; STAGE2_MAPS="${STAGE2_MAPS:-Maze Saturn Circles Corridor}"
MAXJOBS="${MAXJOBS:-5}"; TAG="${TAG:-scan}"; DONE="${DONE:-}"
FILES="$DONE"
C1=$(mktemp); tools/scan-cells.py --opponents "$OPP" --maps "$STAGE1_MAPS" $FILES > "$C1"
echo "scan stage 1: $(grep -c . "$C1") cells over $(echo $OPP | wc -w) opponents on [$STAGE1_MAPS]"
if [ -s "$C1" ]; then
  R1=$(BOT="${BOT:-bot}" CELLS="$C1" MAXJOBS="$MAXJOBS" TAG="$TAG-s1" tools/gauntlet.sh | tee /dev/stderr | sed -n 's#^wrote \(.*\)/$#\1#p')
  FILES="$FILES $R1/results.csv"
fi
C2=$(mktemp); tools/scan-cells.py --opponents "$OPP" --maps "$STAGE2_MAPS" --band --band-maps "$STAGE1_MAPS" $FILES > "$C2"
echo "scan stage 2: $(grep -c . "$C2") cells on [$STAGE2_MAPS] for the in-band opponents"
if [ -s "$C2" ]; then
  R2=$(BOT="${BOT:-bot}" CELLS="$C2" MAXJOBS="$MAXJOBS" TAG="$TAG-s2" tools/gauntlet.sh | tee /dev/stderr | sed -n 's#^wrote \(.*\)/$#\1#p')
  FILES="$FILES $R2/results.csv"
fi
rm -f "$C1" "$C2"
tools/gauntlet-select.py $FILES --write tools/roster.txt
echo "NOTE: on the VM, fetch tools/roster.txt to the driver and commit it before the next vm-run (vm-sync replaces tools/)"
