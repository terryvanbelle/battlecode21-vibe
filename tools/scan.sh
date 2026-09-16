#!/usr/bin/env bash
# Two-stage tiering scan of external bots (memory-bound box: one game at a time).
#   tools/scan.sh                                  # every name-selected benchmark bot
#   OPPONENTS="a.b c.d" tools/scan.sh              # a given list
#   SKIP="gauntlet/<run>/results.csv" tools/scan.sh   # skip opponents already decided in an earlier run
# Stage 1: each opponent plays STAGE1_MAP (default maptestsmall), both sides.
#   2-0 -> peer for now, 0-2 -> locked for now: no more games this scan.
# Stage 2: split (1-1) opponents play STAGE2_MAPS (default Gridlock) both sides.
# Then tools/gauntlet-select.py over every results file (incl. SKIP) writes tools/roster.txt.
set -euo pipefail
cd "$(dirname "$0")/.."
OPP="${OPPONENTS:-$(tools/bench-select.py)}"
STAGE1_MAP="${STAGE1_MAP:-maptestsmall}"; STAGE2_MAPS="${STAGE2_MAPS:-Gridlock}"
MAXJOBS="${MAXJOBS:-1}"; TAG="${TAG:-scan}"
if [ -n "${SKIP:-}" ]; then
  done_opps=$(awk -F, 'NR>1 && ($6=="win"||$6=="loss"){print $1}' $SKIP | sort -u)
  OPP=$(for o in $OPP; do grep -qx "$o" <<<"$done_opps" || echo "$o"; done | tr '\n' ' ')
fi
echo "scan stage 1: $(echo $OPP | wc -w) opponents on $STAGE1_MAP"
R1=$(BOT="${BOT:-bot}" OPPONENTS="$OPP" MAPS="$STAGE1_MAP" MAXJOBS="$MAXJOBS" TAG="$TAG-s1" tools/gauntlet.sh | tee /dev/stderr | sed -n 's#^wrote \(.*\)/$#\1#p')
SPLIT=$(awk -F, 'NR>1 && $6=="win"{w[$1]++} NR>1 && $6=="loss"{l[$1]++} END{for(o in w) if (l[o]>0) print o}' "$R1/results.csv" | tr '\n' ' ')
FILES="$R1/results.csv ${SKIP:-}"
if [ -n "$SPLIT" ]; then
  echo "scan stage 2: split opponents [$SPLIT] on $STAGE2_MAPS"
  R2=$(BOT="${BOT:-bot}" OPPONENTS="$SPLIT" MAPS="$STAGE2_MAPS" MAXJOBS="$MAXJOBS" TAG="$TAG-s2" tools/gauntlet.sh | tee /dev/stderr | sed -n 's#^wrote \(.*\)/$#\1#p')
  FILES="$FILES $R2/results.csv"
fi
tools/gauntlet-select.py $FILES --write tools/roster.txt
