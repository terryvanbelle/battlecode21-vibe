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
a.add_argument('--metric', default=''); a.add_argument('--plot', default=''); o = a.parse_args()
def num(x):
    try: return float(x)
    except: return None
def corr(pairs):
    n = len(pairs)
    if n < 6: return None
    xs = [p[0] for p in pairs]; ys = [p[1] for p in pairs]
    mx, my = sum(xs)/n, sum(ys)/n
    sx = math.sqrt(sum((x-mx)**2 for x in xs)/n); sy = math.sqrt(sum((y-my)**2 for y in ys)/n)
    if sx == 0 or sy == 0: return None
    return sum((x-mx)*(y-my) for x, y in pairs)/(n*sx*sy)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from polarity import orient, label
run = o.run.rstrip('/')
rows = list(csv.DictReader(open(os.path.join(run, 'study.tsv')), delimiter='\t'))
if not rows: sys.exit("no study.tsv rows")
rounds = sorted({int(r['round']) for r in rows})
cols = ['ec','ecInf','sla','muc','pol','exp','buff','unitInf']
# every metric is oriented so that higher = better for us (tools/polarity.py),
# hence a positive correlation always means "this being better goes with winning"
names = []
for c in cols:
    def gap(r, c=c):
        a, b = num(r['us_'+c]), num(r['th_'+c])
        return None if a is None or b is None else orient(c, a - b, 'gap')
    def ours(r, c=c):
        v = num(r['us_'+c]); return None if v is None else orient(c, v, 'us')
    if orient(c, 0, 'gap') is not None: names.append((label(c, 'gap'), gap))
    if orient(c, 0, 'us') is not None: names.append((label(c, 'us'), ours))
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
    shown = [t for t in sorted(table) if t[3] is not None][:6]
    if not shown: shown = sorted(table, key=lambda t: -max((abs(c) for c in t[2] if c is not None), default=0))[:6]
    cmap = plt.get_cmap('tab10')
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
print(f"\nOnset = first round where |corr| >= {o.threshold} and holds. Earliest riser is the first place to look:")
print("temporal precedence is the one causal hint a correlation can honestly give. Confirm with a")
print("diagnostic game that the metric can be moved before funding a test.")
