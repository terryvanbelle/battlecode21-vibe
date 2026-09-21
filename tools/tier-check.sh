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
# Only a gauntlet replay carries an opponent in its name: <opponent>__<map>__bot<side>.bc21.
# A diagnostic replay (diag-i44.bc21) has no "__" and is not about a benchmark bot at all; treating
# its whole filename as an opponent name made the guard refuse our own diagnostics (2026-09-21).
case "$f" in *__*__bot?.bc21) ;; *) exit 0 ;; esac
opp="${f%%__*}"
case "$opp" in *.*) ;; *) exit 0 ;; esac          # our own snapshots and archetypes are unrestricted
# Fail CLOSED if the roster is missing. An opponent name with a dot is a benchmark bot, and
# a rule file that is simply absent must never read as permission (the VM had no BENCHMARK.md
# at all when this guard was first written).
if [ ! -f "$REPO/BENCHMARK.md" ]; then
  echo "!! BENCHMARK.md is missing, so $opp's tier cannot be checked. Refusing." >&2
  echo "!! Run tools/vm-sync.sh, or set BENCH_TIER_OVERRIDE=1 with the owner's say-so." >&2
  exit 3
fi
row="$(grep -F "| \`$opp\` |" "$REPO/BENCHMARK.md" 2>/dev/null | head -1 || true)"
if [ -z "$row" ]; then
  echo "!! $opp is not in the BENCHMARK.md roster, so its tier is unknown. Refusing." >&2
  exit 3
fi
tier="$(printf '%s' "$row" | awk -F'|' '{gsub(/ /,"",$6); print $6}')"
if [ "$tier" = "locked" ]; then
  pct="$(printf '%s' "$row" | awk -F'|' '{gsub(/ /,"",$4); print $4}')"
  echo "!! $opp is tier 'locked' (last win rate ${pct}%). BENCHMARK.md rule 2 forbids" >&2
  echo "!! reviewing its games -- replay, trace, log, board or per-game reason. Score only." >&2
  echo "!! Set BENCH_TIER_OVERRIDE=1 only with the project owner's explicit say-so." >&2
  exit 3
fi
exit 0
