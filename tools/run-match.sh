#!/usr/bin/env bash
# Play ONE headless game with bare java (no Gradle), print the result line.
#
#   tools/run-match.sh <teamA> <teamB> <map> [replay.bc21] [extra -D flags...]
#
# Team names are package names under build/classes (our own bots) or under
# $BENCH_CLASSES (compiled external bots, see tools/bench-compile.sh). Prints
#   RESULT <winner A|B> <round> <reason>
# and leaves the replay where asked (default matches/<A>-vs-<B>-on-<map>.bc21).
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$REPO/tools/lib.sh"
TA="$1"; TB="$2"; MAP="$3"; REPLAY="${4:-$REPO/matches/$TA-vs-$TB-on-$MAP.bc21}"; shift 3; [ $# -gt 0 ] && shift
mkdir -p "$(dirname "$REPLAY")"
LOG="$(run_game "$TA" "$TB" "$MAP" "$REPLAY" "$@" 2>&1 || true)"
parse_result "$LOG"
