#!/usr/bin/env bash
# Mirror match under SPRT: our candidate against our incumbent on random maps and random sides.
# Our own builds, so the contest rule on external bots does not apply -- but random maps, because a
# fixed 12-map set answers a different question than the ladder does.
#   BOT=bot REF=g_iter4 N=200 BATCH=16 tools/mirror.sh
# Plays in batches, runs tools/sprt.py after each, stops at ACCEPT or REJECT. Prints the verdict.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOT="${BOT:-bot}"; REF="${REF:-g_iter4}"; N="${N:-200}"; BATCH="${BATCH:-16}"; MAXJOBS="${MAXJOBS:-6}"
SEED="${SEED:-$(date +%s)}"; TAG="${TAG:-mirror-$BOT-vs-$REF}"
# Own class tree by default: a mirror often runs beside a scrimmage block, and a shared
# build/classes means whichever starts second rebuilds the bot the first one is playing.
CLASSES="${CLASSES:-$REPO/build/mirror-classes}"
MAPS="$(grep -v '^Cow$' "$REPO/tools/bc21-maps.txt" | tr '\n' ' ')"
W=0; L=0; i=0
while [ $((W + L)) -lt "$N" ]; do
  i=$((i + 1))
  CELLS="$(mktemp)"
  python3 - "$BATCH" "$((SEED + i))" "$REF" "$MAPS" > "$CELLS" <<'PY'
import random, sys
n = int(sys.argv[1]); random.seed(int(sys.argv[2])); ref = sys.argv[3]; maps = sys.argv[4].split()
for _ in range(n): print(ref, random.choice(maps), random.choice("AB"))
PY
  # compile once: every batch after the first reuses build/classes (was ~25 s per batch)
  OUT=$(CELLS="$CELLS" CLASSES="$CLASSES" BOT="$BOT" TAG="$TAG-b$i" MAXJOBS="$MAXJOBS" KEEP_ALL="${KEEP_ALL:-0}" SKIP_COMPILE=$([ $i -gt 1 ] && echo 1 || echo 0) "$REPO/tools/gauntlet.sh" | sed -n 's#^wrote \(.*\)/$#\1#p')
  rm -f "$CELLS"
  bw=$(awk -F, '$6=="win"{n++} END{print n+0}' "$OUT/results.csv"); bl=$(awk -F, '$6=="loss"{n++} END{print n+0}' "$OUT/results.csv")
  W=$((W + bw)); L=$((L + bl))
  VERDICT="$(python3 "$REPO/tools/sprt.py" "$W" "$L")"
  echo "batch $i: +$bw -$bl  ==> $VERDICT"
  case "$VERDICT" in *ACCEPT) echo "SPRT_ACCEPT $W-$L"; break;; *REJECT) echo "SPRT_REJECT $W-$L"; break;; esac
done
[ $((W + L)) -lt "$N" ] || echo "SPRT_INCONCLUSIVE $W-$L after $N games"
