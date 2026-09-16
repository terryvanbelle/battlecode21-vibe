#!/usr/bin/env bash
# tools/vm-tail.sh <log-name> [lines]   -- tail of a remote run log, plus games in flight on the VM
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$REPO/tools/vm.sh"; ensure_vm
gssh "tail -n ${2:-15} ~/$REMOTE_REPO/gauntlet/${1:?log name}.log; echo \"[games in flight: \$(pgrep -fc '[b]attlecode.server.Main' || true)  load: \$(cut -d' ' -f1-3 /proc/loadavg)]\""
