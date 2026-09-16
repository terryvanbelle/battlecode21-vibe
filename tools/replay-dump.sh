#!/usr/bin/env bash
# Replay (.bc21) -> text. Compiles tools/replaydump/ReplayDump.java on demand
# (cached by source hash) against the staged engine jar.
#   tools/replay-dump.sh <replay.bc21> [flags]     (see ReplayDump.java header)
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$REPO/tools/lib.sh"
CP="$(engine_cp)"
SHA="$(sha1sum "$REPO/tools/replaydump/ReplayDump.java" | cut -c1-12)"
OUT="$REPO/build/replaydump-$SHA"
if [ ! -f "$OUT/replaydump/ReplayDump.class" ]; then
  mkdir -p "$OUT"; javac -nowarn -d "$OUT" -cp "$CP" "$REPO/tools/replaydump/ReplayDump.java"
fi
exec java -Xmx256m -cp "$OUT:$CP" replaydump.ReplayDump "$@"
