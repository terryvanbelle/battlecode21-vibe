#!/usr/bin/env bash
# Mine a scrimmage block for hypotheses. The games are already paid for; this is where the next
# candidate comes from (user rule, 2026-09-19, PROMPTS 38).
#   tools/scrim-study.sh gauntlet/<run>-scrim-bot [sample]
# For every loss replay: per-round team aggregates at r200/400/600 and the spawn sizes both sides
# build at r300. Writes <run>/study.tsv and prints a per-opponent summary of how we lose.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN="${1:?usage: scrim-study.sh <run-dir> [sample]}"; SAMPLE="${2:-0}"; BOTNAME="${BOT:-bot}"
OUT="$RUN/study.tsv"
printf 'opp\tmap\twon\tround\tus_ec\tus_ecInf\tus_sla\tus_muc\tus_pol\tus_exp\tus_buff\tus_unitInf\tth_ec\tth_ecInf\tth_sla\tth_muc\tth_pol\tth_exp\tth_buff\tth_unitInf\n' > "$OUT"
NAV="$RUN/nav.tsv"
printf 'opp\tmap\twon\tside\tus_cov\tth_cov\tus_moves\tth_moves\tus_meanMoves\tth_meanMoves\tus_aba\tth_aba\tus_swamp\tth_swamp\tus_firstEC\tth_firstEC\n' > "$NAV"
n=0
for f in "$RUN"/losses/*.bc21 "$RUN"/replays/*.bc21; do
  [ -e "$f" ] || continue
  case "$f" in *) won=$(case "$f" in *"/losses/"*) echo 0;; *) echo 1;; esac);; esac
  n=$((n+1)); [ "$SAMPLE" -gt 0 ] && [ "$n" -gt "$SAMPLE" ] && break
  b=$(basename "$f" .bc21); opp=${b%%__*}; rest=${b#*__}; map=${rest%%__*}; side=${b##*bot}
  U=$side; T=$([ "$side" = A ] && echo B || echo A)
  # exploration (user rule, 2026-09-19, PROMPTS 41): coverage is the share of tiles a team ever stood on
  nice -n 10 "$REPO/tools/replay-dump.sh" "$f" --navstats 2>/dev/null | grep "^  nav " | \
    sed -E 's/^  nav ([^:]+): moves=([0-9]+) aba=[0-9]+ \(([0-9.]+)%\) ontoSwamp=[0-9]+ \(([0-9.]+)%\) coverage=([0-9.]+)%.*firstEnemyECContact=r(-?[0-9]+).*meanMoves=([0-9.]+).*/\1 \2 \3 \4 \5 \6 \7/' | \
    awk -v opp="$opp" -v map="$map" -v side="$side" -v won="$won" -v bot="$BOTNAME" '
      { if (NR==1) { a=$0 } else { b=$0 } }
      END { split(a,x," "); split(b,y," ");
            if (x[1] ~ /^(bot|g_iter)/) { split(a,u," "); split(b,t," ") } else { split(b,u," "); split(a,t," ") }
            printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", opp, map, won, side, u[5], t[5], u[2], t[2], u[7], t[7], u[3], t[3], u[4], t[4], u[6], t[6] }' >> "$NAV"
  nice -n 10 "$REPO/tools/replay-dump.sh" "$f" --metrics 2>/dev/null | awk -F, -v U="$U" -v T="$T" -v opp="$opp" -v map="$map" -v won="$won" '
    NR==1{for(i=1;i<=NF;i++)h[$i]=i; next}
    $1%50==0 && $1>=50 && $1<=700 {printf "%s\t%s\t%s\t%s", opp, map, won, $1;
      for (k=1;k<=9;k++) { split("ecs ecInf sla muc pol exposes buff unitInf", c, " ") }
      printf "\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s", $h[U"_ecs"],$h[U"_ecInf"],$h[U"_sla"],$h[U"_muc"],$h[U"_pol"],$h[U"_exposes"],$h[U"_buff"],$h[U"_unitInf"];
      printf "\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", $h[T"_ecs"],$h[T"_ecInf"],$h[T"_sla"],$h[T"_muc"],$h[T"_pol"],$h[T"_exposes"],$h[T"_buff"],$h[T"_unitInf"]; }' >> "$OUT"
  echo "  studied $b" >&2
done
echo "wrote $OUT ($(( $(wc -l < "$OUT") - 1 )) rows from $n replays)"
"$REPO/tools/scrim-study.py" "$OUT"
"$REPO/tools/scrim-study.py" "$NAV" --nav
