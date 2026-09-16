#!/usr/bin/env bash
# Stop battlecode-dev when nothing is running on it (it is shared with other projects: check first).
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$REPO/tools/vm.sh"; ensure_vm
n=$(vm_games); [ "$n" = 0 ] || { echo "!! $n game(s) in flight on $VM; not stopping"; exit 1; }
gcloud compute instances stop "$VM" --zone="$ZONE" --project="$PROJECT" && rm -f "$VM_IP_CACHE"
