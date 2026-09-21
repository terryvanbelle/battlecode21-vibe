#!/usr/bin/env bash
# Dump our EC's @econ counters at one round for every game of a block (our side comes from the file name).
# Runs on the VM: tools/vm-run.sh econ-<run> 'tools/econ-scan.sh gauntlet/<run> 200'
# Output: one TSV line per game: result, opponent, map, side, then the @econ fields of the home centre.
set -u
run=$1; at=${2:-200}
cd "$(dirname "$0")/.."
printf 'result\topp\tmap\tside\tecon\n'
for f in "$run"/losses/*.bc21 "$run"/replays/*.bc21; do
    [ -e "$f" ] || continue
    b=$(basename "$f" .bc21); side=${b##*bot}; opp=${b%%__*}; map=${b#*__}; map=${map%%__*}
    case "$f" in */losses/*) res=LOSS;; *) res=WIN;; esac
    line=$(tools/replay-dump.sh "$f" --logs '@econ' --logs-team "$side" --from $((at-1)) --to $((at+1)) 2>/dev/null | grep -a "@$at\] @econ" | head -1 | sed 's/.*@econ //')
    printf '%s\t%s\t%s\t%s\t%s\n' "$res" "$opp" "$map" "$side" "${line:-none}"
done
