#!/usr/bin/env python3
"""Append a gauntlet run's per-opponent win rates to progress/history.csv.

    tools/track_history.py gauntlet/<run-id> [--label g_iter7]

Rows: date, label (the build that played), opponent, wins, total, win_pct,
maps. history.csv is committed; gauntlet/ is not. Every opponent in the run is
recorded -- snapshots, benchmarks and archetypes alike -- so one file feeds both
the fixed-roster chart and the external ladder chart.
"""
import csv, sys, re
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
REPO = Path(__file__).resolve().parent.parent
FIELDS = ["date", "label", "opponent", "wins", "total", "win_pct", "maps"]

def main():
    a = sys.argv[1:]; label = "bot"
    if "--label" in a: i = a.index("--label"); label = a[i + 1]; del a[i:i + 2]
    run = Path(a[0]); res = run / "results.csv"
    date = datetime.strptime(run.name[:15], "%Y%m%d-%H%M%S").replace(tzinfo=timezone.utc).isoformat()
    tally = defaultdict(lambda: [0, 0])
    with open(res) as f:
        for r in csv.DictReader(f):
            tally[r["opponent"]][1] += 1
            if r["bot_result"] == "win": tally[r["opponent"]][0] += 1
    nmaps = len((run / "maps.txt").read_text().split())
    hist = REPO / "progress" / "history.csv"; hist.parent.mkdir(exist_ok=True)
    rows = list(csv.DictReader(open(hist))) if hist.is_file() else []
    rows = [r for r in rows if not (r["date"] == date)]
    for opp, (w, t) in sorted(tally.items()):
        rows.append(dict(date=date, label=label, opponent=opp, wins=w, total=t, win_pct=f"{100*w/t:.1f}", maps=nmaps))
    rows.sort(key=lambda r: (r["date"], r["opponent"]))
    with open(hist, "w", newline="") as f:
        wr = csv.DictWriter(f, fieldnames=FIELDS); wr.writeheader(); wr.writerows(rows)
    print(f"recorded {len(tally)} opponents from {run.name} as {label} -> {hist}")

if __name__ == "__main__": main()
