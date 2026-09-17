#!/usr/bin/env python3
"""Contest-style standings from progress/scrims.csv.
Our side is one player, "us", across builds (a new version inherits the rating, as in the real ladder);
every external bot is a player. Elo K=32 from 1500, games in recorded order. Also per-build records
with Wilson 95% intervals, which are what a candidate decision reads.
   tools/elo.py            # standings + per-build records
   tools/elo.py --last 48  # the last 48 games only (a block)"""
import csv, os, sys, math, argparse, collections
ap = argparse.ArgumentParser(); ap.add_argument('--last', type=int, default=0); ap.add_argument('--k', type=float, default=32)
a = ap.parse_args()
repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
rows = list(csv.DictReader(open(os.path.join(repo, 'progress', 'scrims.csv'))))
R = collections.defaultdict(lambda: 1500.0); games = collections.Counter()
def expected(ra, rb): return 1 / (1 + 10 ** ((rb - ra) / 400))
hist = []
for r in rows:
    s = 1.0 if r['result'] == 'win' else 0.0
    e = expected(R['us'], R[r['opponent']])
    R['us'] += a.k * (s - e); R[r['opponent']] += a.k * ((1 - s) - (1 - e))
    games['us'] += 1; games[r['opponent']] += 1; hist.append(R['us'])
def wilson(w, n, z=1.96):
    if n == 0: return (0, 0, 0)
    p = w / n; d = 1 + z * z / n; c = p + z * z / (2 * n); m = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))
    return (p, (c - m) / d, (c + m) / d)
print(f"team Elo (us): {R['us']:.0f} after {games['us']} scrimmages")
print("opponent standings:")
for o, r in sorted(((o, r) for o, r in R.items() if o != 'us'), key=lambda x: -x[1]):
    print(f"  {o:34s} {r:6.0f}  ({games[o]} games)")
sel = rows[-a.last:] if a.last else rows
print("per-build records" + (f" (last {a.last})" if a.last else "") + ":")
byb = collections.defaultdict(list)
for r in sel: byb[r['build']].append(r['result'] == 'win')
for b, v in byb.items():
    p, lo, hi = wilson(sum(v), len(v)); print(f"  {b:12s} {sum(v):3d}/{len(v):<3d} = {p:5.1%}  [{lo:5.1%}, {hi:5.1%}]")
