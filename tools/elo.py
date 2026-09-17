#!/usr/bin/env python3
"""The Elo ladder (progress/games.csv): every benchmark bot plus our team 'us', K=32 from 1500.
   tools/elo.py                 # ranking table; also writes progress/ELO.md and progress/elo.png
   tools/elo.py --pool 8        # the challenge pool: the 8 bots ranked just above us (space-separated)
   tools/elo.py --build g_iter4 # per-build record of our scrimmages (Wilson 95%)"""
import argparse, math, os, sys, collections
# the chart needs matplotlib, which lives in tools/.venv: re-exec there when it exists
_venv = os.path.join(os.path.dirname(os.path.abspath(__file__)), '.venv', 'bin', 'python')
if os.path.exists(_venv) and '.venv' not in sys.prefix: os.execv(_venv, [_venv] + sys.argv)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); import elolib
ap = argparse.ArgumentParser(); ap.add_argument('--pool', type=int, default=0); ap.add_argument('--build', default='')
ap.add_argument('--quiet', action='store_true'); a = ap.parse_args()
rows = elolib.load(); R, games, wins, hist = elolib.ratings(rows)
bots = elolib.ladder_bots()
table = sorted([(R[b], b) for b in bots] + [(R['us'], 'us')], key=lambda x: -x[0])
rank = {b: i + 1 for i, (_, b) in enumerate(table)}
if a.pool:
    above = [b for _, b in table if b != 'us' and R[b] >= R['us']]
    pool = above[-a.pool:]                      # the closest ones above us
    if len(pool) < a.pool:                      # not enough above: fill with the closest below
        below = [b for _, b in table if b != 'us' and R[b] < R['us']]
        pool += below[:a.pool - len(pool)]
    print(' '.join(pool)); raise SystemExit
def wilson(w, n, z=1.96):
    if n == 0: return (0, 0, 0)
    p = w / n; d = 1 + z * z / n; c = p + z * z / (2 * n); m = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))
    return (p, (c - m) / d, (c + m) / d)
if a.build:
    v = [(r['winner'] == ('A' if r['teamA'] == 'us:' + a.build else 'B')) for r in rows if 'us:' + a.build in (r['teamA'], r['teamB'])]
    p, lo, hi = wilson(sum(v), len(v)); print(f"{a.build}: {sum(v)}/{len(v)} = {p:.1%} [{lo:.1%}, {hi:.1%}]"); raise SystemExit
lines = [f"# Elo ladder", "", f"{len(rows)} scrimmages recorded; K=32 from 1500; our team is **us** (rank {rank['us']} of {len(table)}, Elo {R['us']:.0f}, {games['us']} games).", "",
         "| rank | bot | Elo | games | W-L |", "|---|---|---|---|---|"]
for i, (r, b) in enumerate(table):
    name = f"**us**" if b == 'us' else b
    lines.append(f"| {i + 1} | {name} | {r:.0f} | {games[b]} | {wins[b]:.0f}-{games[b] - wins[b]:.0f} |")
open(os.path.join(elolib.REPO, 'progress', 'ELO.md'), 'w').write('\n'.join(lines) + '\n')
if not a.quiet: print('\n'.join(lines[2:3] + lines[4:min(len(lines), 4 + 70)]))
try:
    import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
    fig, ax = plt.subplots(figsize=(8, 3.6))
    if hist:
        ax.plot(range(1, len(hist) + 1), [h[1] for h in hist], lw=1.4, color='#1f77b4', label='us')
        # build boundaries
        prev = None; ours = [r for r in rows if 'us' in (elolib.pid(r['teamA']), elolib.pid(r['teamB']))]
        for j, r in enumerate(ours):
            b = r['teamA'] if r['teamA'].startswith('us:') else r['teamB']
            if b != prev: ax.axvline(j + 1, color='#999', lw=0.6, ls=':'); ax.text(j + 1, ax.get_ylim()[0], b[3:], fontsize=7, rotation=90, va='bottom', ha='right'); prev = b
    ax.set_xlabel('our scrimmages'); ax.set_ylabel('Elo'); ax.set_title(f"us: Elo {R['us']:.0f}, rank {rank['us']} of {len(table)}"); ax.grid(alpha=.3)
    fig.tight_layout(); fig.savefig(os.path.join(elolib.REPO, 'progress', 'elo.png'), dpi=120)
except Exception as e: print('plot skipped:', e)
