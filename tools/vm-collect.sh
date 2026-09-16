#!/usr/bin/env bash
# tools/vm-collect.sh <run-id>   -- pull gauntlet/<run-id>/ (results, summary, losses, logs) into local gauntlet/
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$REPO/tools/vm.sh"; ensure_vm
RUN="${1:?run id}"; mkdir -p "$REPO/gauntlet"
gssh "cd ~/$REMOTE_REPO/gauntlet && tar -czf - $RUN" | tar -C "$REPO/gauntlet" -xzf -
ls "$REPO/gauntlet/$RUN"; cat "$REPO/gauntlet/$RUN/summary.txt" 2>/dev/null || echo "(no summary yet: run still in flight)"
