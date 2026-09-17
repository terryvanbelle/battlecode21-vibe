#!/usr/bin/env python3
"""Pairings for a ladder tick: external bots play each other so the public ranking exists, as on the
real ladder where other teams scrimmage among themselves. Swiss-style: a bot with few games is picked
first, its opponent is drawn from the nearest ratings; map and side random; no pair repeats within a tick.
   tools/ladder-pair.py --k 14 [--rounds R] [--seed S] > tools/ladder-cells.txt      # lines: teamA teamB map"""
import argparse, random, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); import elolib
ap = argparse.ArgumentParser(); ap.add_argument('--k', type=int, default=14); ap.add_argument('--seed', type=int, default=None); ap.add_argument('--window', type=int, default=6); ap.add_argument('--rounds', type=int, default=1, help='each bot at most once per round')
a = ap.parse_args(); random.seed(a.seed)
rows = elolib.load(); R, games, wins, _ = elolib.ratings(rows); bots = elolib.ladder_bots()
maps = [l.strip() for l in open(os.path.join(elolib.REPO, 'tools', 'bc21-maps.txt')) if l.strip() and l.strip() != 'Cow']
order = sorted(bots, key=lambda b: R[b]); idx = {b: i for i, b in enumerate(order)}
out = []; seen_pairs = set()
for _ in range(a.rounds):
    used = set()
    for b in sorted(bots, key=lambda b: (games[b], random.random())):
        if len(out) >= a.k: break
        if b in used: continue
        i = idx[b]; cands = [c for c in order[max(0, i - a.window): i + a.window + 1] if c != b and c not in used and frozenset((b, c)) not in seen_pairs]
        if not cands: continue
        c = random.choice(cands); used.update([b, c]); seen_pairs.add(frozenset((b, c)))
        pair = [b, c]; random.shuffle(pair); out.append(f"{pair[0]} {pair[1]} {random.choice(maps)}")
print('\n'.join(out))
