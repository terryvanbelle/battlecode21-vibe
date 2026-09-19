#!/usr/bin/env python3
"""Summarise a study.tsv from tools/scrim-study.sh: medians per opponent and overall, us vs them."""
import csv, sys, statistics as st, collections
rows = list(csv.DictReader(open(sys.argv[1]), delimiter='\t'))
def num(x):
    try: return float(x)
    except: return None
def med(rs, k):
    v = [num(r[k]) for r in rs]; v = [x for x in v if x is not None]
    return st.median(v) if v else float('nan')
for rnd in ('200', '400', '600'):
    rs = [r for r in rows if r['round'] == rnd]
    if not rs: continue
    print(f"\n== r{rnd}  ({len(rs)} losses)")
    print(f"{'':22s} {'ECs':>11s} {'ecInf':>13s} {'slander':>11s} {'muck':>11s} {'polit':>11s} {'exposes':>11s} {'buff':>11s} {'unitInf':>13s}")
    print(f"{'us (median)':22s} {med(rs,'us_ec'):11.1f} {med(rs,'us_ecInf'):13.0f} {med(rs,'us_sla'):11.1f} {med(rs,'us_muc'):11.1f} {med(rs,'us_pol'):11.1f} {med(rs,'us_exp'):11.1f} {med(rs,'us_buff'):11.0f} {med(rs,'us_unitInf'):13.0f}")
    print(f"{'them (median)':22s} {med(rs,'th_ec'):11.1f} {med(rs,'th_ecInf'):13.0f} {med(rs,'th_sla'):11.1f} {med(rs,'th_muc'):11.1f} {med(rs,'th_pol'):11.1f} {med(rs,'th_exp'):11.1f} {med(rs,'th_buff'):11.0f} {med(rs,'th_unitInf'):13.0f}")
rs6 = [r for r in rows if r['round'] == '600']
by = collections.defaultdict(list)
for r in rs6: by[r['opp']].append(r)
if by:
    print("\n== per opponent at r600 (median): our unitInf / theirs, our exposes / theirs")
    for o, v in sorted(by.items(), key=lambda kv: med(kv[1], 'th_unitInf') / max(1, med(kv[1], 'us_unitInf')), reverse=True):
        print(f"  {o[:28]:28s} {med(v,'us_unitInf'):8.0f} / {med(v,'th_unitInf'):8.0f}   exposes {med(v,'us_exp'):4.0f} / {med(v,'th_exp'):4.0f}   ECs {med(v,'us_ec'):.1f} / {med(v,'th_ec'):.1f}")
