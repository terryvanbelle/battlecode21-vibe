#!/usr/bin/env bash
# Mirror match: the current bot vs a byte-identical copy (package `mirror`, DEBUG off), quick map set unless MAPSET/MAPS given.
#   tools/mirror.sh            # regenerates src/mirror from src/bot every time (a stale mirror is not a null)
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf src/mirror; tools/snapshot.sh mirror 0 >/dev/null
BOT=bot OPPONENTS=mirror MAPSET="${MAPSET:-quick}" TAG="${TAG:-mirror}" tools/gauntlet.sh
