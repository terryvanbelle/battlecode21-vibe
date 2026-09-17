#!/usr/bin/env python3
"""Append a gauntlet run's results to progress/history.csv, one row per (opponent, map).

    tools/track_history.py gauntlet/<run-id> [--label g_iter7]
    tools/track_history.py --rebuild "<run-id>=<label> <run-id>=<label> ..."   # regenerate from scratch

Rows: date, label (the build that played), opponent, map, wins, total. Per-map rows let
the charts compare builds on the cells (opponent, map) they have both played, because
different runs use different map sets. history.csv is committed; gauntlet/ is not.
"""
import csv, sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
REPO = Path(__file__).resolve().parent.parent
FIELDS = ["date", "label", "opponent", "map", "wins", "total"]
HIST = REPO / "progress" / "history.csv"

def rows_for(run, label):
    res = run / "results.csv"
    date = datetime.strptime(run.name[:15], "%Y%m%d-%H%M%S").replace(tzinfo=timezone.utc).isoformat()
    tally = defaultdict(lambda: [0, 0])
    with open(res) as f:
        for r in csv.DictReader(f):
            if r["bot_result"] not in ("win", "loss"): continue
            k = (r["opponent"], r["map"]); tally[k][1] += 1
            if r["bot_result"] == "win": tally[k][0] += 1
    return [dict(date=date, label=label, opponent=o, map=m, wins=w, total=t) for (o, m), (w, t) in sorted(tally.items())]

def write(rows):
    rows.sort(key=lambda r: (r["date"], r["opponent"], r["map"]))
    HIST.parent.mkdir(exist_ok=True)
    with open(HIST, "w", newline="") as f:
        wr = csv.DictWriter(f, fieldnames=FIELDS); wr.writeheader(); wr.writerows(rows)

def main():
    a = sys.argv[1:]
    if a and a[0] == "--rebuild":
        rows = []
        for spec in a[1].split():
            run, label = spec.split("="); rr = rows_for(REPO / "gauntlet" / run, label); rows += rr
            print(f"  {run}: {len(rr)} cells as {label}")
        write(rows); print(f"rebuilt {HIST} with {len(rows)} rows"); return
    label = "bot"
    if "--label" in a: i = a.index("--label"); label = a[i + 1]; del a[i:i + 2]
    run = Path(a[0])
    new = rows_for(run, label)
    rows = [r for r in (csv.DictReader(open(HIST)) if HIST.is_file() else []) if r["date"] != new[0]["date"]] if new else []
    write(rows + new); print(f"recorded {len(new)} (opponent, map) cells from {run.name} as {label} -> {HIST}")

if __name__ == "__main__": main()
