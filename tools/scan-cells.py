#!/usr/bin/env python3
"""Cells (opponent map side) still missing for a scan stage, given results already on disk.

  tools/scan-cells.py --opponents "a.b c.d" --maps "m1 m2" [--band] results.csv...

Prints one "opponent map side" line per cell with no decided (win/loss) result in the
files. With --band, only opponents whose decided record over the given maps is neither
0% nor 100% (the ones a further stage can still move) are listed; opponents with no
decided games yet are included.
"""
import argparse, csv
from collections import defaultdict
ap = argparse.ArgumentParser()
ap.add_argument('--opponents', required=True); ap.add_argument('--maps', required=True)
ap.add_argument('--band', action='store_true'); ap.add_argument('--band-maps', default=None,
    help='maps whose record decides band membership (default: --maps)')
ap.add_argument('files', nargs='*')
a = ap.parse_args()
opps = a.opponents.split(); maps = a.maps.split(); bmaps = (a.band_maps or a.maps).split()
done = set(); rec = defaultdict(lambda: [0, 0])
for f in a.files:
    try: fh = open(f)
    except OSError: continue
    for row in csv.reader(fh):
        if len(row) < 6 or row[5] not in ('win', 'loss'): continue
        done.add((row[0], row[1], row[2]))
        if row[1] in bmaps: rec[row[0]][0 if row[5] == 'win' else 1] += 1
for o in opps:
    if a.band:
        w, l = rec[o]
        if w + l > 0 and (w == 0 or l == 0): continue
    for m in maps:
        for s in 'AB':
            if (o, m, s) not in done: print(o, m, s)
