#!/usr/bin/env python3
"""Two charts from progress/history.csv.

  progress/vs_roster.png  win% vs frozen self-snapshots (g_iterN) over time + average
  progress/ladder.png     win% vs every external benchmark bot over time, with the
                          20% (unlock) and 50% lines -- the scrimmage-standings proxy
"""
import csv, re
from collections import defaultdict
from datetime import datetime
from pathlib import Path
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt, matplotlib.dates as mdates
REPO = Path(__file__).resolve().parent.parent

def plot(series, title, out, lines, legend=True):
    fig, ax = plt.subplots(figsize=(12, 7))
    names = sorted(series, key=lambda n: (0, int(m.group(1))) if (m := re.search(r"g_iter(\d+)$", n)) else (1, n))
    cmap = plt.get_cmap("viridis_r"); 
    for i, n in enumerate(names):
        pts = sorted(series[n]); c = cmap(i / max(1, len(names) - 1))
        ax.plot([p[0] for p in pts], [p[1] for p in pts], "-o", ms=4, lw=1.4, color=c, label=f"{n} (n={pts[-1][2]})")
    by_date = defaultdict(list)
    for n in names:
        for d, pct, tot in series[n]: by_date[d].append(pct)
    full = sorted(d for d, v in by_date.items() if len(v) == len(names))
    if len(full) >= 2:
        avg = [sum(by_date[d]) / len(by_date[d]) for d in full]
        ax.plot(full, avg, "k--", lw=2.4, label="average")
        if not legend: ax.annotate(f"average {avg[-1]:.0f}%", (full[-1], avg[-1]), xytext=(6, 0), textcoords="offset points", fontsize=8, va="center")
    for y, lab in lines: ax.axhline(y, color="gray", ls=":", lw=1); ax.annotate(lab, (0.002, y), xycoords=("axes fraction", "data"), fontsize=7.5, color="gray", va="bottom")
    ax.set_ylim(-3, 103); ax.set_ylabel("win % of bot"); ax.set_xlabel("date (UTC)"); ax.set_title(title); ax.grid(alpha=.3)
    ax.xaxis.set_major_formatter(mdates.DateFormatter("%m-%d %H:%M")); fig.autofmt_xdate()
    if legend: ax.legend(fontsize=7, loc="center left", bbox_to_anchor=(1.01, 0.5))
    else: ax.text(0.998, 0.02, f"{len(names)} external bots, one line each; dashed = average over bots played on every date", transform=ax.transAxes, ha="right", fontsize=7.5, color="gray")
    fig.tight_layout(); fig.savefig(out, dpi=140); print("wrote", out)

def main():
    hist = REPO / "progress" / "history.csv"
    if not hist.is_file(): print("no history yet"); return
    roster, ladder = defaultdict(list), defaultdict(list)
    for r in csv.DictReader(open(hist)):
        pt = (datetime.fromisoformat(r["date"]), float(r["win_pct"]), int(r["total"]))
        if re.fullmatch(r"g_iter\d+", r["opponent"]): roster[r["opponent"]].append(pt)
        elif "." in r["opponent"]: ladder[r["opponent"]].append(pt)
    if roster: plot(roster, "Win % vs frozen snapshots of our own lineage (absolute yardstick)", REPO / "progress" / "vs_roster.png", [(50, "even")])
    if ladder: plot(ladder, "Ladder: win % vs external Battlecode 2021 bots (scrimmage proxy)", REPO / "progress" / "ladder.png", [(20, "20% unlock: replays may be reviewed"), (50, "50% peer")], legend=False)

if __name__ == "__main__": main()
