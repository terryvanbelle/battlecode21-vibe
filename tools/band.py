#!/usr/bin/env python3
"""Draw the scrimmage band (tools/roster.txt) from progress/scrims.csv: opponents that beat us between
50% and 80% of the time over the last N games (i.e. we win 20-50%), among those with at least MIN games.
   tools/band.py                 # print per-opponent records over the last 200 games and the band
   tools/band.py --write         # also write tools/roster.txt
Opponents never scrimmaged keep whatever tools/roster.txt says (the band is only re-drawn from evidence)."""
import csv, os, argparse, collections, math
ap = argparse.ArgumentParser(); ap.add_argument('--last', type=int, default=200); ap.add_argument('--min', type=int, default=8)
ap.add_argument('--lo', type=float, default=0.2); ap.add_argument('--hi', type=float, default=0.5); ap.add_argument('--write', action='store_true')
a = ap.parse_args()
repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
rows = list(csv.DictReader(open(os.path.join(repo, 'progress', 'scrims.csv'))))[-a.last:]
rec = collections.defaultdict(lambda: [0, 0])
for r in rows: rec[r['opponent']][1] += 1; rec[r['opponent']][0] += r['result'] == 'win'
band = []
for o, (w, n) in sorted(rec.items(), key=lambda kv: -kv[1][0] / max(1, kv[1][1])):
    p = w / n; tag = ''
    if n >= a.min and a.lo <= p <= a.hi: tag = 'band'; band.append(o)
    elif n < a.min: tag = f'(< {a.min} games)'
    print(f"  {o:34s} {w:3d}/{n:<3d} {p:5.1%} {tag}")
print('band:', ' '.join(band) or '(none with enough games)')
if a.write and band:
    open(os.path.join(repo, 'tools', 'roster.txt'), 'w').write('\n'.join(band) + '\n'); print('wrote tools/roster.txt')
