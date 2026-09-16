#!/usr/bin/env python3
"""Compare two gauntlet runs game by game.

    tools/compare.py <baseline_run_dir> <candidate_run_dir> [--opponent NAME]

Joins on (opponent, map, bot_side). Reports: identical cells (same outcome AND
same round count -- the arm-to-arm identity check), outcome flips each way with
the maps they fall on, swept maps (both sides won) each way, and the per-side
split, so the SHAPE of a difference can be read: scattered mixed flips are
churn; one-directional flips or flips piling onto one map/side are a real effect.
"""
import csv, sys
from collections import Counter, defaultdict

def load(d):
    g = {}
    with open(f"{d}/results.csv") as f:
        for r in csv.DictReader(f):
            g[(r["opponent"], r["map"], r["bot_side"])] = (r["bot_result"], int(r["rounds"]) if r["rounds"].isdigit() else -1)
    return g

def sweeps(g, opp):
    by_map = defaultdict(list)
    for (o, m, s), (res, _) in g.items():
        if o == opp: by_map[m].append(res)
    won = sum(1 for v in by_map.values() if len(v) == 2 and all(x == "win" for x in v))
    lost = sum(1 for v in by_map.values() if len(v) == 2 and all(x == "loss" for x in v))
    return won, lost

def main():
    a = sys.argv[1:]
    opp_filter = None
    if "--opponent" in a:
        i = a.index("--opponent"); opp_filter = a[i + 1]; del a[i:i + 2]
    base, cand = load(a[0]), load(a[1])
    keys = sorted(k for k in base if k in cand and (opp_filter is None or k[0] == opp_filter))
    if not keys: print("no common games"); return
    ident = sum(1 for k in keys if base[k] == cand[k])
    wl = [k for k in keys if base[k][0] == "win" and cand[k][0] == "loss"]
    lw = [k for k in keys if base[k][0] == "loss" and cand[k][0] == "win"]
    bw = sum(1 for k in keys if base[k][0] == "win"); cw = sum(1 for k in keys if cand[k][0] == "win")
    print(f"{len(keys)} common games; byte-identical (same result and round): {ident}")
    print(f"wins: baseline {bw}  candidate {cw}  delta {cw - bw:+d}   flips loss->win {len(lw)}  win->loss {len(wl)}")
    for opp in sorted({k[0] for k in keys}):
        sb, sc = sweeps({k: v for k, v in base.items() if k[0] == opp}, opp), sweeps({k: v for k, v in cand.items() if k[0] == opp}, opp)
        ob = sum(1 for k in keys if k[0] == opp and base[k][0] == "win"); oc = sum(1 for k in keys if k[0] == opp and cand[k][0] == "win")
        n = sum(1 for k in keys if k[0] == opp)
        print(f"  vs {opp:40s} {ob:3d} -> {oc:3d} of {n}  swept-for {sb[0]}->{sc[0]}  swept-against {sb[1]}->{sc[1]}")
    if lw: print("loss->win: " + ", ".join(f"{m}/{s}({o})" for o, m, s in lw))
    if wl: print("win->loss: " + ", ".join(f"{m}/{s}({o})" for o, m, s in wl))
    side = Counter(); 
    for k in lw: side[("gain", k[2])] += 1
    for k in wl: side[("lose", k[2])] += 1
    if lw or wl: print("by side:", dict(side))
    maps = Counter(k[1] for k in lw + wl)
    hot = [f"{m}x{c}" for m, c in maps.most_common() if c > 1]
    if hot: print("maps with >1 flip:", " ".join(hot))

if __name__ == "__main__":
    main()
