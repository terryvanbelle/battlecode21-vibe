#!/usr/bin/env python3
"""When does each metric start predicting the result?

    tools/onset.py <run-dir> [--threshold 0.30] [--metric ec]

Correlation with the outcome is computed at every sampled round, so a metric's
correlation becomes a curve rather than a number. The round where that curve
first crosses the threshold (and stays across the next sample) is its ONSET.

Why this matters: correlation cannot separate cause from symptom, but *temporal
precedence* is evidence. If coverage starts predicting the result at r100 and
EC count only at r250, coverage is upstream. Metrics are ranked by onset, so the
earliest riser is the first place to look for a lever.

Caveats printed with the output: with n games the noise floor on a correlation
is about 2/sqrt(n) (n=29 -> 0.37), so onsets from a small block are suggestive,
not conclusive; and a metric that is *constant* early (buff, exposures) cannot
have an early onset even if it matters later."""
import csv, os, sys, math, argparse, collections
_venv = os.path.join(os.path.dirname(os.path.abspath(__file__)), '.venv', 'bin', 'python')
if os.path.exists(_venv) and '.venv' not in sys.prefix: os.execv(_venv, [_venv] + sys.argv)
a = argparse.ArgumentParser(); a.add_argument('run'); a.add_argument('--threshold', type=float, default=0.30)
a.add_argument('--metric', default=''); a.add_argument('--plot', default='')
a.add_argument('--snapshot-only', action='store_true', help='skip the running-mean variants')
a.add_argument('--raw', action='store_true', help='also show un-differenced metrics (confounded by map size)')
a.add_argument('--md', default='', help='also write the ranked table as markdown')
o = a.parse_args()
def num(x):
    try: return float(x)
    except: return None
def corr(pairs):
    return _pb(pairs)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from polarity import orient, label
from statlib import pointbiserial as _pb, onset as _onset, within_group as _within
run = o.run.rstrip('/')
rows = list(csv.DictReader(open(os.path.join(run, 'study.tsv')), delimiter='\t'))
if not rows: sys.exit("no study.tsv rows")
rounds = sorted({int(r['round']) for r in rows})
cols = ['ec','ecInf','sla','muc','pol','exp','buff','unitInf','cov','navMoves','navAba','navSwamp']
# every metric is oriented so that higher = better for us (tools/polarity.py),
# hence a positive correlation always means "this being better goes with winning"
# Progressive variants: the running mean of a metric over every sampled round up to r.
# A snapshot at r200 is noisy (a centre taken at r199 counts as much as one held since r60);
# the mean so far measures accumulated advantage, which is closer to what decides a game.
# Built from the samples we already have, so it costs no extra replay passes.
games = collections.defaultdict(dict)          # (opp,map) -> round -> row
for r in rows: games[(r['opp'], r['map'])][int(r['round'])] = r
def running(rkey, side, c, rr):
    """Mean of the oriented value over sampled rounds <= rr for one game."""
    vals = []
    for r2 in rounds:
        if r2 > rr: break
        row = games[rkey].get(r2)
        if row is None: continue
        if side == 'us':
            v = num(row['us_'+c]); v = None if v is None else orient(c, v, 'us')
        else:
            a3, b3 = num(row['us_'+c]), num(row['th_'+c])
            v = None if a3 is None or b3 is None else orient(c, a3 - b3, 'gap')
        if v is not None: vals.append(v)
    return sum(vals)/len(vals) if vals else None

names = []
for c in cols:
    def gap(r, c=c):
        a, b = num(r['us_'+c]), num(r['th_'+c])
        return None if a is None or b is None else orient(c, a - b, 'gap')
    def ours(r, c=c):
        v = num(r['us_'+c]); return None if v is None else orient(c, v, 'us')
    if orient(c, 0, 'gap') is not None: names.append((label(c, 'gap'), gap))
    # Raw (un-differenced) metrics are confounded by the map: on a rich or large board BOTH
    # teams build and move more. Measured at r50 the correlation between our value and theirs
    # is +0.46 to +0.82, so a raw metric largely reports which map we drew. They are reported
    # only with --raw, and never rank the candidate list.
    if o.raw and orient(c, 0, 'us') is not None: names.append((label(c, 'us') + ' [map-confounded]', ours))
    if not o.snapshot_only:
        for side in (('gap', 'us') if o.raw else ('gap',)):
            if orient(c, 0, side) is None: continue
            names.append((label(c, side) + ' ~avg', ('prog', c, side)))
if o.metric: names = [(n, f) for n, f in names if n.startswith(o.metric)]
ngames = len({(r['opp'], r['map']) for r in rows})
print(f"correlation with the result by round  ({ngames} games, noise floor ~{2/math.sqrt(max(1,ngames)):.2f})")
print("every metric is oriented so HIGHER = BETTER FOR US, so a positive correlation is always good\n")
hdr = "metric".ljust(24) + "".join(f"r{rr:<5d}" for rr in rounds) + "  onset"
print(hdr); print("-" * len(hdr))
table = []
for name, f in names:
    curve = []
    for rr in rounds:
        rs = [r for r in rows if int(r['round']) == rr]
        if isinstance(f, tuple):
            _, c0, side0 = f
            pairs = []
            for r in rs:
                v = running((r['opp'], r['map']), side0, c0, rr)
                if v is not None: pairs.append((v, 1.0 if r['won'] == '1' else 0.0))
        else:
            pairs = [(v, 1.0 if r['won'] == '1' else 0.0) for r in rs for v in [f(r)] if v is not None]
        curve.append(corr(pairs))
    onset = None
    for i, c in enumerate(curve):
        if c is not None and abs(c) >= o.threshold and (i + 1 >= len(curve) or (curve[i+1] is not None and abs(curve[i+1]) >= o.threshold * 0.8)):
            onset = rounds[i]; break
    table.append((onset if onset is not None else 10**6, name, curve, onset))
for _, name, curve, onset in sorted(table):
    cells = "".join((f"{c:+5.2f} " if c is not None else "   .  ") for c in curve)
    print(name.ljust(24) + cells + ("  r%d" % onset if onset else "   -"))
if o.plot:
    import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
    fig, ax = plt.subplots(figsize=(9, 5))
    # one curve per distinct metric: plotting both x and "x ~avg" wastes half the panel on
    # near-identical lines and pushes later-onset metrics (coverage, ECs) off the graph entirely
    seen = set(); shown = []
    for t in sorted(table):
        base = t[1].replace(' ~avg', '')
        if t[3] is None or base in seen: continue
        seen.add(base); shown.append(t)
        if len(shown) == 8: break
    if not shown:
        shown = sorted(table, key=lambda t: -max((abs(c) for c in t[2] if c is not None), default=0))[:8]
    cmap = plt.get_cmap('tab10')
    ax.set_xlim(min(rounds), max(rounds))
    for i, (_, name, curve, onset) in enumerate(shown):
        xs = [rr for rr, c in zip(rounds, curve) if c is not None]
        ys = [c for c in curve if c is not None]
        ax.plot(xs, ys, marker='o', ms=3.5, lw=1.8, color=cmap(i), label=name + (f"  (onset r{onset})" if onset else ""))
        if onset: ax.axvline(onset, color=cmap(i), lw=0.8, ls=':', alpha=0.6)
    nf = 2 / math.sqrt(max(1, ngames))
    ax.axhspan(-nf, nf, color='0.85', zorder=0, label=f"noise floor (n={ngames})")
    ax.axhline(0, color='0.4', lw=0.8)
    ax.set_xlabel('round'); ax.set_ylabel('correlation with the result')
    ax.set_title('When each metric starts predicting the result\n(earliest riser is the likeliest cause)')
    ax.grid(alpha=.3); ax.legend(fontsize=8, loc='upper left')
    fig.tight_layout(); fig.savefig(o.plot, dpi=120); print('wrote', o.plot)
if o.md:
    rank = sorted(table)
    with open(o.md, 'w') as fh:
        fh.write(f"# Which metric starts predicting the result first\n\n")
        fh.write(f"{ngames} games, {sum(1 for r in rows if r['won']=='1' and int(r['round'])==rounds[0])} wins. ")
        fh.write(f"Noise floor about {2/math.sqrt(max(1,ngames)):.2f}; a correlation inside that band is not evidence.\n\n")
        fh.write("Every metric is oriented so **higher is better for us**, so a positive correlation always means\n")
        fh.write("\"this being better goes with winning\". `~avg` is the running mean over all rounds so far rather than\n")
        fh.write("the snapshot at that round. Onset is the first round where the correlation reaches the threshold and holds.\n")
        fh.write("See `progress/METRICS.md` for how each quantity is computed.\n\n")
        fh.write("| onset | metric | peak corr | r50 | r100 | r200 | r300 | r400 | r600 |\n")
        fh.write("|---|---|---|---|---|---|---|---|---|\n")
        idx = {rr: i for i, rr in enumerate(rounds)}
        def at(curve, rr):
            i = idx.get(rr); 
            return "%+.2f" % curve[i] if i is not None and curve[i] is not None else "."
        for _, name, curve, onset in rank:
            pk = max((c for c in curve if c is not None), key=abs, default=None)
            fh.write(f"| {('r%d' % onset) if onset else '-'} | {name} | {('%+.2f' % pk) if pk is not None else '.'} | "
                     f"{at(curve,50)} | {at(curve,100)} | {at(curve,200)} | {at(curve,300)} | {at(curve,400)} | {at(curve,600)} |\n")
        fh.write("\n**Reading it.** Earliest onset is the first place to look: temporal precedence is the one causal hint a\n")
        fh.write("correlation can honestly give. Late-onset metrics are usually the scoreboard rather than the cause -- by then\n")
        fh.write("the winner leads on everything. A high correlation earns a diagnostic game, not a code change.\n")
    print('wrote', o.md)
print(f"\nOnset = first round where |corr| >= {o.threshold} and holds. Earliest riser is the first place to look:")
print("temporal precedence is the one causal hint a correlation can honestly give. Confirm with a")
print("diagnostic game that the metric can be moved before funding a test.")
