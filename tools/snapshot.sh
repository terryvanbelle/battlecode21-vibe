#!/usr/bin/env bash
# Freeze src/bot/ as a Gauntlet opponent package: tools/snapshot.sh g_iter3
# Rewrites `package bot` -> `package <name>` (and intra-package imports).
set -euo pipefail
cd "$(dirname "$0")/.."
NAME="${1:?usage: tools/snapshot.sh <package-name> [archetype-int]}"
ARCH="${2:-0}"
case "$NAME" in bot|examplefuncsplayer) echo "refusing to overwrite $NAME" >&2; exit 1;; *[!a-z0-9_]*) echo "name must be [a-z0-9_]+" >&2; exit 1;; esac
DEST="src/$NAME"; [ -e "$DEST" ] && { echo "$DEST already exists" >&2; exit 1; }
mkdir -p "$DEST"
for f in src/bot/*.java; do sed -e "s/^package bot;/package $NAME;/" -e "s/^import bot\./import $NAME./" -e "s/^import static bot\./import static $NAME./" -e "s/ARCHETYPE = 0;/ARCHETYPE = $ARCH;/" "$f" > "$DEST/$(basename "$f")"; done
echo "snapshotted src/bot/ -> $DEST/ ($(ls "$DEST" | wc -l) files)"
