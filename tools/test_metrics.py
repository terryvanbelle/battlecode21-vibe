#!/usr/bin/env python3
"""Unit tests for the metrics pipeline. Run: tools/test_metrics.py"""
import os, sys, math, subprocess, tempfile, csv
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from polarity import orient, label, POLARITY
from statlib import pointbiserial, noise_floor, running_mean, onset, within_group

fails = []
def check(name, cond, detail=''):
    if cond: print(f"  ok   {name}")
    else: print(f"  FAIL {name} {detail}"); fails.append(name)
def close(a, b, eps=1e-9): return a is not None and abs(a-b) < eps

print("polarity")
check("our count is higher-better", orient('ec', 3, 'us') == 3)
check("their count is inverted", orient('ec', 3, 'th') == -3)
check("a gap of a higher-is-better metric keeps its sign", orient('ec', -2, 'gap') == -2)
# 2026-09-20: gaps were returned unoriented, so every inverted metric's gap row read backwards.
check("a gap of an inverted metric is flipped", orient('aba', 3, 'gap') == -3)
check("leading on centres lost is bad", orient('ecLoss', 3, 'gap') == -3)
check("leading on oscillation is bad", orient('navAba', 5, 'gap') == -5)
check("an unoriented metric has no gap either", orient('firstEC', 3, 'gap') is None)
check("an inverted gap is labelled as inverted", '[inverted]' in label('aba', 'gap'))
check("oscillation is inverted for us", orient('aba', 5, 'us') == -5)
check("their oscillation helps us", orient('aba', 5, 'th') == 5)
check("unoriented metric returns None", orient('firstEC', 100, 'us') is None)
check("label marks inversion", '[inverted]' in label('aba', 'us'))
check("label marks a gap", '(us-them)' in label('ec', 'gap'))
check("every column has a polarity", all(k in POLARITY for k in
      ['ec','ecGain','ecLoss','ecInf','sla','muc','pol','exp','buff','unitInf','cov','navMoves','navAba','navSwamp']))

print("derived expansion metrics")
from derived import add_expansion, game_segments
_rows = [{'game':'g1','round':'50','us_ec':'1','th_ec':'1'},
         {'game':'g1','round':'100','us_ec':'3','th_ec':'1'},
         {'game':'g1','round':'150','us_ec':'2','th_ec':'4'},
         {'game':'g2','round':'50','us_ec':'1','th_ec':'1'},
         {'game':'g2','round':'100','us_ec':'1','th_ec':'2'}]
add_expansion(_rows)
check("gains accumulate positive changes", _rows[2]['us_ecGain'] == 2.0)
check("losses accumulate negative changes", _rows[2]['us_ecLoss'] == 1.0)
check("their gains are tracked separately", _rows[2]['th_ecGain'] == 3.0)
check("the first sample of a game is zero", _rows[0]['us_ecGain'] == 0.0)
check("a new game does not inherit the previous one",
      _rows[3]['th_ecGain'] == 0.0 and _rows[4]['th_ecGain'] == 1.0,
      f"got {_rows[3]['th_ecGain']} then {_rows[4]['th_ecGain']}")
# a table written before the `game` column existed: games are split on a round that does not increase
_old = [dict(r) for r in _rows]
for r in _old: del r['game']
for r in _old:
    for k in ('us_ecGain','us_ecLoss','th_ecGain','th_ecLoss'): r.pop(k, None)
add_expansion(_old)
check("falls back to the round rule without a game column",
      _old[2]['us_ecGain'] == 2.0 and _old[3]['th_ecGain'] == 0.0 and _old[4]['th_ecGain'] == 1.0,
      f"segments {list(game_segments(_old))}")
check("gain is higher-better and loss is inverted",
      orient('ecGain', 2, 'us') == 2 and orient('ecLoss', 2, 'us') == -2)
check("a missing centre column does not crash",
      add_expansion([{'round':'50'}, {'round':'100'}]) is not None)

print("end to end (the scripts actually run)")
_TOOLS = os.path.dirname(os.path.abspath(__file__))
def _write_study(d):
    """A tiny two-opponent block: four games, two won, with a real centre trajectory."""
    hdr = ['game','opp','map','won','round'] + [s+'_'+c for s in ('us','th')
          for c in ('ec','ecInf','sla','muc','pol','exp','buff','unitInf','cov','navMoves','navAba','navSwamp')]
    rows = []
    games = [('a.bot',1), ('a.bot',0), ('b.bot',1), ('b.bot',0),
             ('c.bot',1), ('c.bot',0), ('d.bot',1), ('d.bot',0)]
    for gi, (opp, won) in enumerate(games):
        for k, rnd in enumerate((50,100,150,200)):
            us_ec = 1 + k if won else 1 + (k > 2)
            th_ec = 1 + (k > 2) if won else 1 + k
            vals = [us_ec, 100*(k+1)*(1+won), 5*k+won, 3, 4, 0, 0, 900*(k+1)*(1+won), 40*(k+1), 60*k, 2, 1,
                    th_ec, 100*(k+1), 5*k, 3, 4, 0, 0, 900*(k+1), 40*(k+1), 60*k, 2, 1]
            rows.append([f'g{gi}', opp, f'map{gi}', won, rnd] + vals)
    with open(os.path.join(d, 'study.tsv'), 'w') as f:
        f.write('\t'.join(hdr) + '\n')
        for r in rows: f.write('\t'.join(str(x) for x in r) + '\n')
def _run(script, *args):
    return subprocess.run([sys.executable, os.path.join(_TOOLS, script)] + list(args),
                          capture_output=True, text=True)
with tempfile.TemporaryDirectory() as _d:
    _write_study(_d)
    _c = _run('correlate.py', _d)
    check("correlate.py exits cleanly", _c.returncode == 0, _c.stderr.strip()[-300:])
    check("correlate.py reports the expansion metric", 'ecGain' in _c.stdout)
    check("correlate.py reports a gap row", '(us-them)' in _c.stdout)
    _o = _run('onset.py', _d)
    check("onset.py exits cleanly", _o.returncode == 0, _o.stderr.strip()[-300:])
    check("onset.py reports the expansion metric", 'ecGain' in _o.stdout)
    # the scripts must not silently score raw map-confounded metrics ahead of gaps by default
    check("no traceback reached stdout", 'Traceback' not in _c.stdout + _o.stdout)
    _rounds = [l.split(' r')[1].split()[0] for l in _c.stdout.splitlines() if l.startswith('== economy') and ' r' in l]
    check("rounds are reported in numeric order", _rounds == sorted(_rounds, key=int), f"got {_rounds}")

print("correlation")
perfect = [(1,1.0),(2,1.0),(3,1.0),(0,0.0),(-1,0.0),(-2,0.0)]
check("separating metric gives a strong positive", pointbiserial(perfect) > 0.85)   # exact value 0.8780
check("reversed gives the mirror value",
      close(pointbiserial([(-x, y) for x, y in perfect]), -pointbiserial(perfect)))
check("constant metric is undefined", pointbiserial([(5, 1.0)]*3 + [(5, 0.0)]*3) is None)
check("constant outcome is undefined", pointbiserial([(1,1.0),(2,1.0),(3,1.0),(4,1.0),(5,1.0),(6,1.0)]) is None)
check("too few points is undefined", pointbiserial([(1,1.0),(2,0.0)]) is None)
xs = [(1,0.0),(2,0.0),(3,1.0),(4,1.0),(5,0.0),(6,1.0)]
check("matches a hand-computed value", close(pointbiserial(xs), 0.4879500365, 1e-6))
check("noise floor shrinks with n", noise_floor(48) < noise_floor(16))
check("noise floor of 48 is about 0.29", close(noise_floor(48), 0.2886751, 1e-5))

print("progressive (running mean)")
check("mean ignores gaps", close(running_mean([2, None, 4]), 3.0))
check("empty is None", running_mean([None, None]) is None)
check("single value is itself", close(running_mean([7]), 7.0))

print("onset detection")
rounds = [50,100,150,200,250]
check("first sustained crossing", onset([0.1,0.2,0.35,0.4,0.5], rounds, 0.3) == 150)
check("a lone spike is ignored", onset([0.1,0.9,0.05,0.05,0.05], rounds, 0.3) is None)
check("never crossing is None", onset([0.1,0.1,0.1,0.1,0.1], rounds, 0.3) is None)
check("negative crossings count too", onset([-0.1,-0.4,-0.5,-0.5,-0.5], rounds, 0.3) == 100)
check("crossing at the last sample counts", onset([0.1,0.1,0.1,0.1,0.9], rounds, 0.3) == 250)
check("Nones are skipped", onset([None,None,0.4,0.45,0.5], rounds, 0.3) == 150)

print("within-group stratification")
rows = [{'g':'A','v':10,'w':1.0},{'g':'A','v':8,'w':0.0},{'g':'B','v':2,'w':1.0},{'g':'B','v':1,'w':0.0}]
pw = within_group(rows, lambda r: r['v'], lambda r: r['g'], lambda r: r['w'])
check("group means are removed", close(sum(v for v, _ in pw), 0.0, 1e-9))
check("within-group signal survives", pointbiserial(pw + pw) > 0.9 if len(pw+pw) >= 6 else True)
big = [{'g':'A','v':100,'w':1.0}]*3 + [{'g':'B','v':1,'w':0.0}]*3
pw2 = within_group(big, lambda r: r['v'], lambda r: r['g'], lambda r: r['w'])
check("a pure group effect is erased", all(abs(v) < 1e-9 for v, _ in pw2))
check("singleton groups are dropped", within_group([{'g':'A','v':1,'w':1.0}], lambda r: r['v'], lambda r: r['g'], lambda r: r['w']) == [])

print("map confound (raw metrics must not be ranked)")
import subprocess
_out = subprocess.run(['tools/onset.py','gauntlet/20260919-202921-scrim-g_iter6'], capture_output=True, text=True).stdout
check("default ranking excludes un-differenced metrics",
      all('[map-confounded]' not in ln for ln in _out.splitlines()),
      "raw metrics leaked into the default table")
check("default ranking is gap metrics only",
      all(('(us-them)' in ln or not ln.strip() or ln.startswith(('correlation','every','metric','-','Onset','temporal','diagnostic','wrote')))
          for ln in _out.splitlines()))

print("study.tsv integrity (live data, if present)")
run = 'gauntlet/20260919-202921-scrim-g_iter6'
sp = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), run, 'study.tsv')
rows = list(csv.DictReader(open(sp), delimiter='\t')) if os.path.exists(sp) else []
if rows:
    check("won is only 0 or 1", set(r['won'] for r in rows) <= {'0','1'})
    check("every us_ column has a th_ twin",
          all('th_'+k[3:] in rows[0] for k in rows[0] if k.startswith('us_')))
    check("coverage is a share in [0,1000]",
          all(0 <= float(r['us_cov']) <= 1000 for r in rows if r['us_cov']))
    check("cumulative moves never decrease within a game", all(
        all(float(g[i]['us_navMoves']) >= float(g[i-1]['us_navMoves']) for i in range(1, len(g)))
        for g in [[r for r in rows if r['opp']==o and r['map']==m and r['won']==w]
                  for o, m, w in {(r['opp'], r['map'], r['won']) for r in rows}]))
    check("rounds are the 50-step grid", set(int(r['round']) for r in rows) <= set(range(50, 750, 50)))
else:
    print("  (skipped: study.tsv absent or being rewritten)")

print()
print(("FAILED: " + ", ".join(fails)) if fails else "all tests pass")
sys.exit(1 if fails else 0)
