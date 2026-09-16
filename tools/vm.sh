#!/usr/bin/env bash
# Shared helpers for running games on the battlecode-dev VM (sourced, not run).
# claude-driver runs only the Claude session; EVERY game runs on the VM.
# The VM mirrors the driver's layout, so tools/lib.sh works unchanged there:
#   ~/jdk/jdk8u504-b01, ~/projects/vibe/2021 (this repo: src tools test engine),
#   ~/projects/vibe/bc21-benchmarks/{_classes,manifest.tsv}
VM=battlecode-dev; ZONE=us-west1-b; PROJECT=tvanbelle-vibecode
REMOTE_REPO='projects/vibe/2021'
SSHO=(-i "$HOME/.ssh/google_compute_engine" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null
      -o ConnectTimeout=20 -o ServerAliveInterval=20 -o ServerAliveCountMax=3 -o LogLevel=ERROR)
USER_NAME="${BC_SSH_USER:-$(whoami)}"
VM_IP_CACHE="${TMPDIR:-/tmp}/.bc21-vm-ip-$VM"
vm_ip () { gcloud compute instances describe "$VM" --zone="$ZONE" --project="$PROJECT" --format='value(networkInterfaces[0].accessConfigs[0].natIP)' 2>/dev/null; }
gssh () { ssh "${SSHO[@]}" "$USER_NAME@$IP" "$@"; }
gscp () { scp "${SSHO[@]}" "$@"; }
wait_ssh () { for _ in $(seq 1 40); do gssh true 2>/dev/null && return 0; sleep 8; done; return 1; }
# ensure_vm: sets IP; starts the VM if it is stopped. A cached IP is trusted only if one ssh probe answers.
ensure_vm () {
  if [ -s "$VM_IP_CACHE" ]; then IP="$(cat "$VM_IP_CACHE")"; gssh -o ConnectTimeout=8 true 2>/dev/null && return 0; fi
  local state; state=$(gcloud compute instances describe "$VM" --zone="$ZONE" --project="$PROJECT" --format='value(status)' 2>/dev/null || true)
  [ "$state" = RUNNING ] || { echo "  starting $VM ..." >&2; gcloud compute instances start "$VM" --zone="$ZONE" --project="$PROJECT" >/dev/null; }
  IP="$(vm_ip)"; [ -n "$IP" ] || { echo "!! no external IP for $VM" >&2; return 1; }
  wait_ssh || { echo "!! cannot reach $USER_NAME@$IP" >&2; return 1; }
  printf '%s\n' "$IP" > "$VM_IP_CACHE" 2>/dev/null || true
}
# games in flight on the VM (any project)
vm_games () { gssh 'pgrep -fc "[b]attlecode.server.Main" || true'; }
