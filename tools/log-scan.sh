#!/usr/bin/env bash
# Dump every @tag log line of OUR side for every game of a block into <run>/logs/<game>.log
# (side from the file name), four games at a time. Runs on the VM:
#   tools/vm-run.sh logs-<run> 'tools/log-scan.sh gauntlet/<run>'
# then fetch <run>/logs.tar.gz and grep at leisure -- one pass instead of one dump per question.
set -u
run=$1
cd "$(dirname "$0")/.."
mkdir -p "$run/logs"
one() {
    f=$1; b=$(basename "$f" .bc21); side=${b##*bot}
    case "$f" in */losses/*) res=LOSS;; *) res=WIN;; esac
    tools/replay-dump.sh "$f" --logs '@' --logs-team "$side" 2>/dev/null | grep -a ' LOG \[' > "$run/logs/${res}__${b}.log"
    echo "$b $(wc -l < "$run/logs/${res}__${b}.log") lines"
}
export -f one; export run
ls "$run"/losses/*.bc21 "$run"/replays/*.bc21 2>/dev/null | xargs -P 4 -I{} bash -c 'one "$@"' _ {}
tar czf "$run/logs.tar.gz" -C "$run" logs && echo "wrote $run/logs.tar.gz"
