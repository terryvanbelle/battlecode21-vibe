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
MANIFEST="${MANIFEST:-$HOME/projects/vibe/bc21-benchmarks/manifest.tsv}"
# names resolve as in gauntlet.sh: our packages under build/dev-classes, else manifest "name -> package url"
resolve () {
  if [ -d "$OUT/$1" ]; then echo "$1 $OUT"; return; fi
  [ -f "$MANIFEST" ] && awk -F'\t' -v n="$1" '$1==n{print $2" "$3; exit}' "$MANIFEST" | grep . && return
  echo "!! no compiled classes for team $1" >&2; return 1
}
team_url () { echo "${URL_OF[$1]}"; }
NA="$1"; NB="$2"; MAP="$3"; REPLAY="${4:-$REPO/matches/$NA-vs-$NB-on-$MAP.bc21}"; shift 3; [ $# -gt 0 ] && shift
declare -A URL_OF
read -r TA UA <<<"$(resolve "$NA")" || exit 1; read -r TB UB <<<"$(resolve "$NB")" || exit 1
[ -n "$UA" ] && [ -n "$UB" ] || exit 1; URL_OF[$TA]="$UA"; URL_OF[$TB]="$UB"
mkdir -p "$(dirname "$REPLAY")"
LOG="$(run_game "$TA" "$TB" "$MAP" "$REPLAY" "$@" 2>&1 || true)"
[ -n "${LOG_OUT:-}" ] && printf '%s\n' "$LOG" > "$LOG_OUT"
parse_result "$LOG"
