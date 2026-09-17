#!/usr/bin/env bash
# One headless game from a PRIVATE compile of src/ (build/dev-classes), so it is
# safe to run while a gauntlet owns build/classes.  Same arguments as run-match.sh:
#   tools/run-dev.sh <teamA> <teamB> <map> [replay.bc21] [extra -D flags...]
#   LOG_OUT=game.log tools/run-dev.sh bot x Map r.bc21 -Dbc.server.robot-player-to-system-out=true   # keep the engine stdout (bot @tag lines)
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$REPO/tools/lib.sh"
OUT="$REPO/build/dev-classes"
rm -rf "$OUT"; compile_src "$REPO/src" "$OUT" >&2
team_url () {
  if [ -d "$OUT/$1" ]; then echo "$OUT";
  elif [ -d "$BENCH_CLASSES/$1" ]; then echo "$BENCH_CLASSES";
  else echo "!! no compiled classes for team $1" >&2; return 1; fi
}
TA="$1"; TB="$2"; MAP="$3"; REPLAY="${4:-$REPO/matches/$TA-vs-$TB-on-$MAP.bc21}"; shift 3; [ $# -gt 0 ] && shift
mkdir -p "$(dirname "$REPLAY")"
LOG="$(run_game "$TA" "$TB" "$MAP" "$REPLAY" "$@" 2>&1 || true)"
[ -n "${LOG_OUT:-}" ] && printf '%s\n' "$LOG" > "$LOG_OUT"
parse_result "$LOG"
