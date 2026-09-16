#!/usr/bin/env bash
# Start a detached run on battlecode-dev after syncing the repo tree. Prints the remote log path.
#   tools/vm-run.sh <log-name> '<command line run in ~/projects/vibe/2021>'
#   tools/vm-run.sh scan1 'MAXJOBS=6 OPPONENTS="a.b c.d" MAPS=maptestsmall TAG=scan1 tools/gauntlet.sh'
# Follow with tools/vm-tail.sh <log-name>; fetch results with tools/vm-collect.sh <run-id>.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$REPO/tools/vm.sh"
NAME="${1:?log name}"; CMD="${2:?command}"
"$REPO/tools/vm-sync.sh" >/dev/null
gssh "cd ~/$REMOTE_REPO && setsid nohup bash -c $(printf '%q' "$CMD") > gauntlet/$NAME.log 2>&1 < /dev/null & disown; sleep 1; echo started; head -c 300 gauntlet/$NAME.log"
echo "remote log: ~/$REMOTE_REPO/gauntlet/$NAME.log"
