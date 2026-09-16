#!/usr/bin/env bash
# Run the external ladder: bot vs every selected benchmark bot, record history, redraw charts, refresh BENCHMARK.md.
#   tools/ladder.sh                     # one bot per repo (tools/bench-select.py), quick map set
#   ALL=1 MAPSET=full tools/ladder.sh   # every package, full corpus
#   LABEL=g_iter3 tools/ladder.sh       # label the history rows with the build that played
set -euo pipefail
cd "$(dirname "$0")/.."
OPP="$( [ "${ALL:-0}" = 1 ] && tools/bench-select.py --all || tools/bench-select.py )"
LABEL="${LABEL:-bot}"
OUT=$(BOT="${BOT:-bot}" OPPONENTS="$OPP" MAPSET="${MAPSET:-quick}" TAG="${TAG:-ladder}" tools/gauntlet.sh | tee /dev/stderr | sed -n 's#^wrote \(.*\)/$#\1#p')
tools/.venv/bin/python tools/track_history.py "$OUT" --label "$LABEL"
tools/.venv/bin/python tools/plot_history.py
tools/bench-roster.py
