#!/usr/bin/env bash
# Is this replay's opponent allowed to be reviewed? BENCHMARK.md rule 2 (binding, from the
# project owner): no game against a bot may be reviewed -- any replay, trace, log, board or
# per-game reason -- until we beat it at least 20% of the time. Only the score until then.
#
#   tools/tier-check.sh <replay.bc21>     exit 0 allowed, 3 locked, 0 if not a benchmark game
#
# Added 2026-09-20 after I reviewed six awesomelemonade replays while that bot was listed
# `locked`. The rule said "the discipline is on the reader"; discipline that depends on
# remembering to look is the same silent-failure shape as the other three found that day.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
f="$(basename "${1:?usage: tier-check.sh <replay.bc21>}")"
opp="${f%%__*}"
case "$opp" in *.*) ;; *) exit 0 ;; esac          # our own snapshots and archetypes are unrestricted
row="$(grep -F "| \`$opp\` |" "$REPO/BENCHMARK.md" 2>/dev/null | head -1 || true)"
[ -n "$row" ] || exit 0                            # not in the roster: nothing to enforce
tier="$(printf '%s' "$row" | awk -F'|' '{gsub(/ /,"",$6); print $6}')"
if [ "$tier" = "locked" ]; then
  pct="$(printf '%s' "$row" | awk -F'|' '{gsub(/ /,"",$4); print $4}')"
  echo "!! $opp is tier 'locked' (last win rate ${pct}%). BENCHMARK.md rule 2 forbids" >&2
  echo "!! reviewing its games -- replay, trace, log, board or per-game reason. Score only." >&2
  echo "!! Set BENCH_TIER_OVERRIDE=1 only with the project owner's explicit say-so." >&2
  exit 3
fi
exit 0
