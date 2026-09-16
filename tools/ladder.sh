#!/usr/bin/env bash
# Run the external ladder: bot vs every selected benchmark bot, record history, redraw charts, refresh BENCHMARK.md.
#   tools/ladder.sh                     # the standing roster (tools/roster.txt: the 20-50% tier), quick map set
#   SCAN=1 tools/ladder.sh              # one bot per repo (tools/bench-select.py): the re-tiering scan
#   ALL=1 MAPSET=full tools/ladder.sh   # every package, full corpus
#   LABEL=g_iter3 tools/ladder.sh       # label the history rows with the build that played
# After a scan: tools/gauntlet-select.py gauntlet/<run>/results.csv --write tools/roster.txt
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${ALL:-0}" = 1 ]; then OPP="$(tools/bench-select.py --all)"
elif [ "${SCAN:-0}" = 1 ] || [ ! -s tools/roster.txt ]; then OPP="$(tools/bench-select.py)"
else OPP="$(tr '\n' ' ' < tools/roster.txt)"; fi
LABEL="${LABEL:-bot}"
OUT=$(BOT="${BOT:-bot}" OPPONENTS="$OPP" MAPSET="${MAPSET:-quick}" TAG="${TAG:-ladder}" tools/gauntlet.sh | tee /dev/stderr | sed -n 's#^wrote \(.*\)/$#\1#p')
tools/.venv/bin/python tools/track_history.py "$OUT" --label "$LABEL"
tools/.venv/bin/python tools/plot_history.py
tools/bench-roster.py
