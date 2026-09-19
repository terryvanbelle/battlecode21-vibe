#!/usr/bin/env python3
"""Which measured differences actually separate our wins from our losses?

    tools/correlate.py <run-dir>            # study.tsv + nav.tsv of one block
    tools/correlate.py <run-dir> --round 200

For every metric we track, and for the us-minus-them gap where both sides have it,
reports the point-biserial correlation with the game's outcome and the medians in
wins vs losses. Requires a run studied with wins kept (KEEP_ALL=1).

READ IT CAREFULLY. Correlation at r600 is mostly the *outcome* leaking backwards --
a team that is winning has more ECs because it is winning. Metrics at r200 precede
almost the whole game, so they are the ones worth acting on; the table prints the
rounds separately for exactly that reason, and the r200 column is the one that
should choose the next candidate."""
import csv, os, sys, math, argparse, statistics as st
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from polarity import orient, label
a = argparse.ArgumentParser(); a.add_argument('run'); a.add_argument('--round', type=int, default=0)
o = a.parse_args()
def num(x):
    try: return float(x)
    except: return None
def partial_by_group(rows, key, groupf):
    """Within-group (opponent x map) deviations, so a metric cannot score merely by
    identifying which opponents are weak -- the strongest confound in block data."""
    from collections import defaultdict
    g = defaultdict(list)
    for r in rows:
        v = key(r)
        if v is not None: g[groupf(r)].append((v, 1.0 if r['won'] == '1' else 0.0))
    out = []
    for _, vs in g.items():
        if len(vs) < 2: continue
        mv = sum(v for v, _ in vs)/len(vs); my = sum(y for _, y in vs)/len(vs)
        for v, y in vs: out.append((v - mv, y - my))
    return out

def pointbiserial(pairs):
    xs = [p[0] for p in pairs]; ys = [p[1] for p in pairs]
    n = len(pairs)
    if n < 6 or len(set(ys)) < 2: return None
    mx, my = sum(xs)/n, sum(ys)/n
    sx = math.sqrt(sum((x-mx)**2 for x in xs)/n); sy = math.sqrt(sum((y-my)**2 for y in ys)/n)
    if sx == 0 or sy == 0: return None
    return sum((x-mx)*(y-my) for x, y in pairs)/(n*sx*sy)
def report(path, cols, label):
    if not os.path.exists(path): print(f"  (no {path})"); return
    rows = list(csv.DictReader(open(path), delimiter='\t'))
    if not rows: return
    rounds = sorted({r['round'] for r in rows}) if 'round' in rows[0] else [None]
    for rnd in rounds:
        rs = [r for r in rows if rnd is None or r['round'] == rnd]
        if o.round and rnd is not None and int(rnd) != o.round: continue
        wins = sum(1 for r in rs if r['won'] == '1')
        if wins == 0 or wins == len(rs): 
            print(f"\n== {label}" + (f" r{rnd}" if rnd else "") + f": {len(rs)} games, {wins} wins -- no variation, nothing to correlate")
            continue
        print(f"\n== {label}" + (f" r{rnd}" if rnd else "") + f"  ({len(rs)} games, {wins} wins)")
        print(f"{'metric (higher=better)':30s} {'corr':>7s} {'within':>7s} {'win median':>12s} {'loss median':>12s}")
        out = []
        for c in cols:
            us, th = 'us_'+c, 'th_'+c
            if us not in rs[0]: continue
            for side in ('us', 'gap'):
                if orient(c, 0, side) is None: continue
                name = label(c, side)
                pairs = []
                for r in rs:
                    y = 1.0 if r['won'] == '1' else 0.0
                    if side == 'us': v = num(r[us]); v = None if v is None else orient(c, v, 'us')
                    else:
                        a1, b1 = num(r[us]), num(r[th])
                        v = None if a1 is None or b1 is None else orient(c, a1 - b1, 'gap')
                    if v is not None: pairs.append((v, y))
                c2 = pointbiserial(pairs)
                if c2 is None: continue
                def kf(r, sd=side, u=us, t=th, c=c):
                    if sd == 'us':
                        v = num(r[u]); return None if v is None else orient(c, v, 'us')
                    a2, b2 = num(r[u]), num(r[t])
                    return None if a2 is None or b2 is None else orient(c, a2 - b2, 'gap')
                # stratify by whichever key actually has repeats: opponent (ladder blocks) or map (mirrors)
                nopp = len({r['opp'] for r in rs}); gf = (lambda r: r['opp']) if nopp > 1 else (lambda r: r['map'])
                pw = partial_by_group(rs, kf, gf)
                cw = pointbiserial(pw) if len(pw) >= 6 else None
                w = [p[0] for p in pairs if p[1] == 1.0]; l = [p[0] for p in pairs if p[1] == 0.0]
                out.append((abs(cw if cw is not None else c2), name, c2, cw, st.median(w) if w else float('nan'), st.median(l) if l else float('nan')))
        for _, name, c2, cw, wm, lm in sorted(out, reverse=True)[:14]:
            cws = f"{cw:+7.2f}" if cw is not None else "      -"
            print(f"{name:30s} {c2:+7.2f} {cws} {wm:12.1f} {lm:12.1f}")
run = o.run.rstrip('/')
report(os.path.join(run, 'study.tsv'), ['ec','ecInf','sla','muc','pol','exp','buff','unitInf'], 'economy')
report(os.path.join(run, 'nav.tsv'), ['cov','moves','meanMoves','aba','swamp','firstEC'], 'exploration')
print("\nRead: 'corr' is raw; 'within' removes each opponent-and-map's own average, so it cannot")
print("score merely by identifying weak opponents. Act on r200 -- later rounds are contaminated")
print("by the outcome itself. A correlation is a place to look, not a mechanism: confirm with a")
print("diagnostic game that the mechanism exists and can be moved before spending a test.")
