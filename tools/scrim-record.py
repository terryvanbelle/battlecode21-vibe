#!/usr/bin/env python3
"""Append a scrimmage block's games to progress/scrims.csv (one row per game, in play order).
   tools/scrim-record.py gauntlet/<run>-scrim-bot --label g_iter5
Columns: run,seq,build,opponent,map,side,result,rounds,reason. Idempotent per run id."""
import csv, os, sys, argparse
ap = argparse.ArgumentParser(); ap.add_argument('run'); ap.add_argument('--label', required=True)
a = ap.parse_args()
repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = os.path.join(repo, 'progress', 'scrims.csv'); run = os.path.basename(a.run.rstrip('/'))
rows = []
if os.path.exists(out):
    rows = list(csv.DictReader(open(out)))
if any(r['run'] == run for r in rows):
    print('already recorded', run); sys.exit(0)
raw = os.path.join(a.run, 'results.raw')   # play order, not sorted
new = []
for i, line in enumerate(open(raw)):
    f = line.rstrip('\n').split(',')
    if len(f) < 7: continue
    opp, m, side, w, rnd, res, reason = f[:7]
    if res not in ('win', 'loss'): continue   # unknown/dud: not a scrimmage result
    new.append(dict(run=run, seq=i, build=a.label, opponent=opp, map=m, side=side, result=res, rounds=rnd, reason=reason[:40]))
hdr = ['run', 'seq', 'build', 'opponent', 'map', 'side', 'result', 'rounds', 'reason']
wr = not os.path.exists(out)
with open(out, 'a', newline='') as fh:
    w = csv.DictWriter(fh, fieldnames=hdr)
    if wr: w.writeheader()
    for r in new: w.writerow(r)
print(f"recorded {len(new)} games from {run} as {a.label}")
