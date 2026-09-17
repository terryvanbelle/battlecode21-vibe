#!/usr/bin/env bash
# One line per loss replay in a gauntlet run: how and when it was lost, with the instruments' numbers.
#   tools/loss-census.sh gauntlet/<run> > census.tsv      (driver-side; one dump at a time, ~25 s per replay)
# Columns: opp map side rounds reason ecs200 ecs400 ecs800 ecInf200 ecInf400 earned300_us earned300_them
#          units600_us units600_them exposed600 votes_end_us votes_end_them firstEClossRound attacker held
#          speechToEnemy_us speechToFriend_us speechToFriend_them
set -u
RUN="$1"; cd "$(dirname "$0")/.."
printf 'opp\tmap\tside\trounds\treason\tecs200\tecs400\tecs800\tecInf200\tecInf400\tearn300us\tearn300them\tunits600us\tunits600them\texposed600\tvotesUs\tvotesThem\tfirstECloss\tattacker\theld\tspEnemyUs\tspFriendUs\tspFriendThem\n'
for f in "$RUN"/losses/*.bc21; do
  n=$(basename "$f" .bc21); opp=${n%%__*}; rest=${n#*__}; map=${rest%%__*}; side=${n##*bot}
  if [ "$side" = A ]; then U=A; T=B; else U=B; T=A; fi
  m=$(nice -n 10 tools/replay-dump.sh "$f" --metrics 2>/dev/null | awk -F, -v U=$U -v T=$T '
    NR==1{for(i=1;i<=NF;i++)h[$i]=i; next}
    /^#/{split($0,a,"rounds="); rounds=a[2]+0; next}
    {last=$0; lv=$h[U"_votes"]; lt=$h[T"_votes"]}
    $1==200{e2=$h[U"_ecs"]"v"$h[T"_ecs"]; i2=$h[U"_ecInf"]}
    $1==300{eu=$h[U"_spawnInf"]+$h[U"_bidInf"]+$h[U"_ecInf"]; et=$h[T"_spawnInf"]+$h[T"_bidInf"]+$h[T"_ecInf"]}
    $1==400{e4=$h[U"_ecs"]"v"$h[T"_ecs"]; i4=$h[U"_ecInf"]}
    $1==600{u6=$h[U"_pol"]+$h[U"_sla"]+$h[U"_muc"]; t6=$h[T"_pol"]+$h[T"_sla"]+$h[T"_muc"]; x6=$h[T"_exposes"]}
    $1==800{e8=$h[U"_ecs"]"v"$h[T"_ecs"]}
    END{printf "%d\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s", rounds, e2, e4, e8, i2, i4, eu, et, u6, t6, x6, lv, lt}')
  reason=$(grep -F ",$map,$side," "$RUN/results.csv" | grep -F "$opp," | head -1 | awk -F, '{print $7}' | sed -E 's/The winning team won by //; s/ the enemy team//; s/having more //' | cut -c1-14)
  h=$(nice -n 10 tools/replay-dump.sh "$f" --hits 2>/dev/null | grep HIT | grep -v "by g_iter4 " | grep -v "by bot " | grep CONVERTED | head -1 | sed -E 's/.*r([0-9]+) HIT by [^ ]+ conv=([0-9]+).*inf ([0-9]+) ->.*/\1\t\2\t\3/')
  [ -n "$h" ] || h=$(printf '\t\t')
  # speech shares: the first "speeches" line is team A, the second team B (mawk: no 3-arg match, so use sed)
  s=$(nice -n 10 tools/replay-dump.sh "$f" --speeches 2>/dev/null | grep 'speeches ' | sed -E 's/.*toEnemy=[0-9]+ \(([0-9]+)%\).*toFriend=[0-9]+ \(([0-9]+)%\).*/\1 \2/' \
      | awk -v U=$U 'NR==1{a1=$1;a2=$2} NR==2{b1=$1;b2=$2} END{if (U=="A") printf "%s\t%s\t%s", a1, a2, b2; else printf "%s\t%s\t%s", b1, b2, a2}')
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$opp" "$map" "$side" "$m" "$reason" "$h" "$s"
done
