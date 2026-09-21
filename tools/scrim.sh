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
# Challenge pool: the rated bots nearest above us (tools/elo.py --pool --explore).
# 2026-09-20 (PROMPTS 67): 4 rated + 4 never-played, to widen a thin field.
# 2026-09-21 (PROMPTS 74): exploration OFF -- 8 rated, 0 new -- "hold off on adding any new opponents to
# the ladder until our standing improves". Four blocks of exploration took the rated field from 9 bots to
# 21 and our rank from 4th to 18th, which is the ladder becoming accurate rather than the bot getting
# worse, but it also means consecutive blocks no longer share a field and their win rates cannot be
# chained. A fixed pool fixes both. Set EXPLORE=n to sample new bots again.
# tools/roster.txt is used until 40 games exist.
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
    if [ "${EXPLORE:-0}" = 0 ]; then
      # A FIXED field of the most-played rated bots. "Nearest above us" cannot be used here: our rank
      # fell to 18th of 21 as exploration added bots, and the eight nearest above us are now seven bots
      # we have beaten 6-0 whose ratings rest on six games each. Playing those would raise our Elo
      # without telling us anything. The most-played set spans awesomelemonade at 4/54 to arya-k at
      # 20/36 and does not change between blocks, so win rates finally chain.
      POOL="$(python3 "$REPO/tools/elo.py" --established "${POOLSIZE:-8}")"
    else
      POOL="$(python3 "$REPO/tools/elo.py" --pool "${POOLSIZE:-8}" --explore "$EXPLORE")"
    fi
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
