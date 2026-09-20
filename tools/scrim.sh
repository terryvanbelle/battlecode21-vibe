#!/usr/bin/env bash
# Scrimmage block against external bots under contest rules (PROMPTS 25, 2026-09-18):
# the map and the side are drawn at random for every game, opponents rotate (never the same
# one twice in a row, each at most ceil(N/pool) times per block). This is the ONLY way an
# external bot may be played; gauntlet.sh refuses external opponents unless SCRIM=1 (set here).
#   BOT=bot N=24 tools/scrim.sh                 # opponents: 6 rated bots just above us + 2 least-known (tools/roster.txt until 40 games exist)
#   POOL="a.b c.d" N=12 SEED=7 tools/scrim.sh   # explicit pool; SEED for a reproducible draw
# Maps: tools/bc21-maps.txt (the released corpus). Results: gauntlet/<run>-scrim-<BOT>/ ;
# record them with tools/scrim-record.py <run-dir> --label <build> (appends progress/scrims.csv).
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOT="${BOT:-bot}"; N="${N:-24}"; MAXJOBS="${MAXJOBS:-6}"
# Challenge pool: the rated bots nearest above us, plus bots we have never met (tools/elo.py --pool --explore).
# From 2026-09-20 the split is 4 + 4 rather than 6 + 2 (PROMPTS 67): we are 4th of 9 rated with only three
# bots above us, so a pool of 6 "above" was padding itself with bots below us, and at 2 new bots per block
# the 57 unmet ones would have taken 28 blocks to meet. tools/roster.txt is used until 40 games exist.
# Refuse rather than fall back silently. The roster fallback is only legitimate before the ladder has
# 40 games; a MISSING games.csv means the history did not reach this machine, and quietly substituting a
# different set of opponents is how every block from 2026-09-17 to 2026-09-20 challenged the wrong bots.
if [ -z "${POOL:-}" ]; then
  if [ ! -f "$REPO/progress/games.csv" ]; then
    echo "!! progress/games.csv is missing: the ladder history did not reach this machine." >&2
    echo "!! Run tools/vm-sync.sh, or set POOL=\"a.b c.d\" to choose the opponents explicitly." >&2
    exit 4
  fi
  if [ "$(grep -c . "$REPO/progress/games.csv")" -ge 40 ]; then
    POOL="$(python3 "$REPO/tools/elo.py" --pool "${POOLSIZE:-4}" --explore "${EXPLORE:-4}")"
  else
    echo "ladder history has under 40 games: using tools/roster.txt" >&2
    POOL="$(tr '\n' ' ' < "$REPO/tools/roster.txt")"
  fi
fi
SEED="${SEED:-$(date +%s%N | cut -c1-13)}"
CELLS="$(mktemp)"
python3 - "$N" "$SEED" "$POOL" "$(grep -v '^Cow$' "$REPO/tools/bc21-maps.txt" | tr '\n' ' ')" > "$CELLS" <<'PY'
import random, sys, math
n = int(sys.argv[1]); seed = int(sys.argv[2]); pool = sys.argv[3].split(); maps = sys.argv[4].split()
random.seed(seed)
per = math.ceil(n / len(pool)); counts = {o: 0 for o in pool}; last = None
for _ in range(n):
    cands = [o for o in pool if counts[o] < per and o != last] or [o for o in pool if counts[o] < per]
    o = random.choice(cands); counts[o] += 1; last = o
    print(o, random.choice(maps), random.choice("AB"))
PY
echo "scrim block: bot=$BOT n=$N seed=$SEED pool=[$POOL]"
SCRIM=1 KEEP_ALL=1 CELLS="$CELLS" BOT="$BOT" TAG="scrim-$BOT" MAXJOBS="$MAXJOBS" "$REPO/tools/gauntlet.sh"   # KEEP_ALL: wins are studied too (PROMPTS 43)
rm -f "$CELLS"
