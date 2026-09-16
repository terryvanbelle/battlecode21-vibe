#!/usr/bin/env python3
"""Pick the standing gauntlet roster from scan results.

    tools/gauntlet-select.py gauntlet/<run>/results.csv [more results files...]
        [--lo 0.2] [--hi 0.5] [--min-games 2] [--write tools/roster.txt]

Aggregates wins/losses per opponent over every file given (unknown/dud games
are ignored), prints a tier table, and writes the roster: opponents whose win
rate for our bot lies in [lo, hi] -- "slightly better than us", the band the
scrimmage ladder would put us against. Opponents with too few decided games
are listed as UNRESOLVED so the next scan can add games for them.

Tiers:  locked  < lo         (no replay may be opened until we pass 20%)
        roster  lo..hi       (the standing gauntlet)
        peer    > hi         (we beat them; kept for regression checks)
"""
import argparse, csv, sys, os
from collections import defaultdict

ap = argparse.ArgumentParser()
ap.add_argument('files', nargs='+')
ap.add_argument('--lo', type=float, default=0.2)
ap.add_argument('--hi', type=float, default=0.5)
ap.add_argument('--min-games', type=int, default=2)
ap.add_argument('--write', default=None)
a = ap.parse_args()

w = defaultdict(int); l = defaultdict(int); other = defaultdict(int)
for f in a.files:
    with open(f) as fh:
        for row in csv.reader(fh):
            if len(row) < 6 or row[0] == 'opponent': continue
            opp, res = row[0], row[5]
            if res == 'win': w[opp] += 1
            elif res == 'loss': l[opp] += 1
            else: other[opp] += 1

rows = []
for opp in sorted(set(w) | set(l) | set(other)):
    n = w[opp] + l[opp]
    rate = w[opp] / n if n else None
    if n < a.min_games: tier = 'unresolved'
    elif rate < a.lo: tier = 'locked'
    elif rate <= a.hi: tier = 'roster'
    else: tier = 'peer'
    rows.append((opp, w[opp], l[opp], other[opp], rate, tier))

rows.sort(key=lambda r: (-1 if r[4] is None else r[4], r[0]))
print(f"{'opponent':40} {'W':>3} {'L':>3} {'?':>3} {'rate':>6}  tier")
for opp, ww, ll, oo, rate, tier in rows:
    print(f"{opp:40} {ww:3} {ll:3} {oo:3} {('-' if rate is None else f'{rate:.2f}'):>6}  {tier}")
roster = [r[0] for r in rows if r[5] == 'roster']
print(f"\nroster ({len(roster)}): {' '.join(roster)}")
unres = [r[0] for r in rows if r[5] == 'unresolved']
if unres: print(f"unresolved ({len(unres)}): {' '.join(unres)}")
if a.write:
    os.makedirs(os.path.dirname(a.write) or '.', exist_ok=True)
    with open(a.write, 'w') as fh: fh.write('\n'.join(roster) + ('\n' if roster else ''))
    print(f"wrote {a.write}")
