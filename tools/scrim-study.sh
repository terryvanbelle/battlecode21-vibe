#!/usr/bin/env bash
# Mine a scrimmage block for hypotheses. The games are already paid for; this is where the next
# candidate comes from (user rule, 2026-09-19, PROMPTS 38).
#   tools/scrim-study.sh gauntlet/<run>-scrim-bot [sample]
# For every loss replay: per-round team aggregates at r200/400/600 and the spawn sizes both sides
# build at r300. Writes <run>/study.tsv and prints a per-opponent summary of how we lose.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN="${1:?usage: scrim-study.sh <run-dir> [sample]}"; SAMPLE="${2:-0}"
OUT="$RUN/study.tsv"
printf 'opp\tmap\tround\tus_ec\tus_ecInf\tus_sla\tus_muc\tus_pol\tus_exp\tus_buff\tus_unitInf\tth_ec\tth_ecInf\tth_sla\tth_muc\tth_pol\tth_exp\tth_buff\tth_unitInf\n' > "$OUT"
n=0
for f in "$RUN"/losses/*.bc21; do
  [ -e "$f" ] || continue
  n=$((n+1)); [ "$SAMPLE" -gt 0 ] && [ "$n" -gt "$SAMPLE" ] && break
  b=$(basename "$f" .bc21); opp=${b%%__*}; rest=${b#*__}; map=${rest%%__*}; side=${b##*bot}
  U=$side; T=$([ "$side" = A ] && echo B || echo A)
  nice -n 10 "$REPO/tools/replay-dump.sh" "$f" --metrics 2>/dev/null | awk -F, -v U="$U" -v T="$T" -v opp="$opp" -v map="$map" '
    NR==1{for(i=1;i<=NF;i++)h[$i]=i; next}
    $1==200||$1==400||$1==600 {printf "%s\t%s\t%s", opp, map, $1;
      for (k=1;k<=9;k++) { split("ecs ecInf sla muc pol exposes buff unitInf", c, " ") }
      printf "\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s", $h[U"_ecs"],$h[U"_ecInf"],$h[U"_sla"],$h[U"_muc"],$h[U"_pol"],$h[U"_exposes"],$h[U"_buff"],$h[U"_unitInf"];
      printf "\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", $h[T"_ecs"],$h[T"_ecInf"],$h[T"_sla"],$h[T"_muc"],$h[T"_pol"],$h[T"_exposes"],$h[T"_buff"],$h[T"_unitInf"]; }' >> "$OUT"
  echo "  studied $b" >&2
done
echo "wrote $OUT ($(( $(wc -l < "$OUT") - 1 )) rows from $n replays)"
"$REPO/tools/scrim-study.py" "$OUT"
