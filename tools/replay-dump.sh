#!/usr/bin/env bash
# Replay (.bc21) -> text. Compiles tools/replaydump/ReplayDump.java on demand
# (cached by source hash) against the staged engine jar.
#   tools/replay-dump.sh <replay.bc21> [flags]     (see ReplayDump.java header)
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$REPO/tools/lib.sh"
CP="$(engine_cp)"
# BENCHMARK.md rule 2: a locked bot's games may not be reviewed. Enforced here rather than
# left to the reader, because the reader forgot (2026-09-20).
if [ "${BENCH_TIER_OVERRIDE:-0}" != 1 ] && [ $# -ge 1 ] && [ -f "$1" ]; then
  "$REPO/tools/tier-check.sh" "$1" || exit 3
fi
SHA="$(sha1sum "$REPO/tools/replaydump/ReplayDump.java" | cut -c1-12)"
OUT="$REPO/build/replaydump-$SHA"
if [ ! -f "$OUT/replaydump/ReplayDump.class" ]; then
  mkdir -p "$OUT"; javac -nowarn -d "$OUT" -cp "$CP" "$REPO/tools/replaydump/ReplayDump.java"
fi
# 256m was not enough for a long game: a 19 MB compressed replay (rzhan11 on FiveOfHearts,
# 2026-09-20) decompresses past that and the dumper died with OutOfMemoryError, which aborted
# the whole block study. Override with DUMP_XMX on a small box.
exec java -Xmx"${DUMP_XMX:-1g}" -cp "$OUT:$CP" replaydump.ReplayDump "$@"
