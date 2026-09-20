#!/usr/bin/env bash
# Push what a run needs to battlecode-dev (tar over ssh; the VM has no rsync).
#   tools/vm-sync.sh          # repo tree (src tools test + engine if missing) and, once, JDK 8 and benchmark classes
#   FULL=1 tools/vm-sync.sh   # re-push engine and benchmark classes too
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; source "$REPO/tools/vm.sh"; ensure_vm
gssh "mkdir -p ~/$REMOTE_REPO ~/projects/vibe/bc21-benchmarks ~/jdk"
if ! gssh "test -x ~/jdk/jdk8u504-b01/bin/java"; then
  echo "pushing JDK 8 ..."; tar -C "$HOME/jdk" -czf - jdk8u504-b01 | gssh "tar -C ~/jdk -xzf -"
fi
if [ "${FULL:-0}" = 1 ] || ! gssh "test -f ~/projects/vibe/bc21-benchmarks/manifest.tsv"; then
  echo "pushing benchmark classes + manifest ..."
  tar -C "$HOME/projects/vibe/bc21-benchmarks" -czf - _classes manifest.tsv | gssh "tar -C ~/projects/vibe/bc21-benchmarks -xzf -"
fi
if [ "${FULL:-0}" = 1 ] || ! gssh "test -f ~/$REMOTE_REPO/engine/engine.jar"; then
  echo "pushing engine ..."; tar -C "$REPO" -czf - engine | gssh "tar -C ~/$REMOTE_REPO -xzf -"
fi
echo "pushing repo tree ..."
# progress/ travels too: tools/scrim.sh picks its challenge pool with tools/elo.py, which reads
# progress/games.csv, and silently falls back to the fixed 8-bot tools/roster.txt when that file has
# fewer than 40 rows. It was never synced, so every ladder block since 2026-09-17 took the fallback
# and the "challenge the bots just above us" rule never actually ran on the VM (found 2026-09-20).
tar -C "$REPO" --exclude='tools/.venv' --exclude='__pycache__' -czf - src tools test progress BENCHMARK.md | gssh "cd ~/$REMOTE_REPO && rm -rf src tools test progress && tar -xzf - && mkdir -p gauntlet matches build"
echo "synced to $USER_NAME@$IP:~/$REMOTE_REPO"
