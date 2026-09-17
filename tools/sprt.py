#!/usr/bin/env python3
"""Sequential probability ratio test for a two-player match (no draws in BC21).
   tools/sprt.py <wins> <losses> [--p0 0.50] [--p1 0.58] [--alpha 0.05] [--beta 0.05]
H0: the candidate's win rate is p0 (no better than the incumbent); H1: it is p1.
Prints the log-likelihood ratio and one of ACCEPT / REJECT / CONTINUE.
p1 = 0.58 is the smallest edge worth a snapshot here: ~56 Elo, and it is resolved in
80-200 games, where a 24-cell panel resolves nothing below ~75%."""
import argparse, math
a = argparse.ArgumentParser(); a.add_argument('wins', type=int); a.add_argument('losses', type=int)
a.add_argument('--p0', type=float, default=0.50); a.add_argument('--p1', type=float, default=0.58)
a.add_argument('--alpha', type=float, default=0.05); a.add_argument('--beta', type=float, default=0.05)
o = a.parse_args(); w, l = o.wins, o.losses; n = w + l
llr = w * math.log(o.p1 / o.p0) + l * math.log((1 - o.p1) / (1 - o.p0)) if n else 0.0
lo = math.log(o.beta / (1 - o.alpha)); hi = math.log((1 - o.beta) / o.alpha)
state = 'ACCEPT' if llr >= hi else 'REJECT' if llr <= lo else 'CONTINUE'
pct = w / n if n else 0
print(f"{w}-{l} ({pct:.1%})  LLR={llr:+.2f}  bounds [{lo:.2f}, {hi:.2f}]  -> {state}")
