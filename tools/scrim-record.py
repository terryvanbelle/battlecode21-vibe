#!/usr/bin/env python3
"""Record scrimmage results into progress/games.csv (one row per game, in play order; idempotent per run id).
   tools/scrim-record.py gauntlet/<run>-scrim-bot --label g_iter5     # our block: BOT played as 'us:g_iter5'
   tools/scrim-record.py gauntlet/ladder/<id>-ladder                  # a ladder tick (external vs external)
Then tools/elo.py refreshes progress/ELO.md and elo.png."""
import os, sys, argparse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); import elolib
ap = argparse.ArgumentParser(); ap.add_argument('run'); ap.add_argument('--label', default='')
a = ap.parse_args(); run = os.path.basename(a.run.rstrip('/'))
if any(r['run'] == run for r in elolib.load()): print('already recorded', run); sys.exit(0)
new = []
src = os.path.join(a.run, 'results.raw')
if not os.path.exists(src): src = os.path.join(a.run, 'results.csv')   # gauntlet.sh keeps only the sorted csv (same columns, header first)
for i, line in enumerate(open(src)):
    if line.startswith('opponent,'): continue
    f = line.rstrip('\n').split(',')
    if run.endswith('-ladder'):
        if len(f) < 6 or f[3] not in ('A', 'B'): continue
        A, B, m, w, rnd, reason = f[:6]
    else:
        if len(f) < 7 or f[5] not in ('win', 'loss'): continue
        opp, m, side, w, rnd, res, reason = f[:7]; us = 'us:' + (a.label or 'bot')
        A, B = (us, opp) if side == 'A' else (opp, us)
    new.append(dict(run=run, seq=i, teamA=A, teamB=B, map=m, winner=w, rounds=rnd, reason=reason[:40]))
elolib.append(new); print(f"recorded {len(new)} games from {run}")
