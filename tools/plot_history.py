#!/usr/bin/env python3
"""Two charts from progress/history.csv (rows: date, label, opponent, map, wins, total).

  progress/vs_roster.png  per accepted build: win% vs each frozen self-snapshot (g_iterN), all maps
  progress/ladder.png     per accepted build: win% vs external benchmark bots on the (opponent, map)
                          cells that EVERY build shown has played, one thin line per bot, the average,
                          and the ladder position = bots at or above 20% on those cells
"""
import csv, re
from collections import defaultdict
from pathlib import Path
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
REPO = Path(__file__).resolve().parent.parent

def iter_key(l): m = re.search(r"(\d+)$", l); return int(m.group(1)) if m else 99

def plot_ladder(rows, out):
    cells = defaultdict(lambda: [0, 0])                      # (label, opponent, map) -> wins, total
    for r in rows: k = (r["label"], r["opponent"], r["map"]); cells[k][0] += int(r["wins"]); cells[k][1] += int(r["total"])
    labels = sorted({k[0] for k in cells}, key=iter_key)
    played = {l: {(o, m) for (ll, o, m), (w, t) in cells.items() if ll == l and t > 0} for l in labels}
    ref = labels[-1]                                         # every earlier build is compared with the latest on the cells the two share
    common = {l: played[l] & played[ref] for l in labels}
    def rate(l, o):
        w = sum(cells[(l, o, m)][0] for (oo, m) in common[l] if oo == o); t = sum(cells[(l, o, m)][1] for (oo, m) in common[l] if oo == o)
        return 100 * w / t if t else None
    opps = sorted({o for l in labels for (o, _) in common[l]})
    fig, ax = plt.subplots(figsize=(11, 6.5))
    for o in opps:
        pts = [(i, rate(l, o)) for i, l in enumerate(labels) if rate(l, o) is not None]
        if pts: ax.plot([p[0] for p in pts], [p[1] for p in pts], "-o", lw=0.8, ms=3, alpha=0.4, color="tab:blue")
    avg, unlocked, ncells = [], [], []
    for l in labels:
        v = [rate(l, o) for o in opps if rate(l, o) is not None]
        avg.append(sum(v) / len(v) if v else float("nan")); unlocked.append(sum(1 for x in v if x >= 20) if v else float("nan")); ncells.append(len(common[l]))
    ax.plot(range(len(labels)), avg, "k-o", lw=2.4, ms=6, label="average win % on the cells shared with the latest build")
    for i, (a, n) in enumerate(zip(avg, ncells)): ax.annotate(f"{a:.0f}% ({n} cells)" if n else "no shared cells", (i, a if n else 50), xytext=(8, 6), textcoords="offset points", fontsize=9)
    for y, lab in [(20, "20% unlock: replays may be reviewed"), (50, "50% peer")]:
        ax.axhline(y, color="gray", ls=":", lw=1); ax.annotate(lab, (0.002, y), xycoords=("axes fraction", "data"), fontsize=7.5, color="gray", va="bottom")
    ax2 = ax.twinx(); ax2.plot(range(len(labels)), unlocked, "r-s", lw=2, ms=6, label="ladder position: shared-cell bots at or above 20%")
    ax2.set_ylabel("bots at or above 20%", color="r"); ax2.set_ylim(0, max(10, max([u for u in unlocked if u == u], default=0) + 2))
    ax.set_xticks(range(len(labels))); ax.set_xticklabels(labels); ax.set_xlim(-0.4, len(labels) - 0.6)
    ax.set_ylim(-3, 103); ax.set_ylabel("win % of the build vs each external bot"); ax.set_xlabel("accepted build")
    ax.set_title("Ladder: win % vs external Battlecode 2021 bots, per accepted build (scrimmage proxy)"); ax.grid(alpha=.3)
    ax.text(0.5, 0.01, "one thin line per external bot; a cell is (bot, map), both sides; each build is scored only on cells it shares with the latest build", transform=ax.transAxes, ha="center", fontsize=7.5, color="gray")
    h1, l1 = ax.get_legend_handles_labels(); h2, l2 = ax2.get_legend_handles_labels(); ax.legend(h1 + h2, l1 + l2, loc="upper left", fontsize=8)
    fig.tight_layout(); fig.savefig(out, dpi=140); print("wrote", out)

def plot_roster(rows, out):
    agg = defaultdict(lambda: [0, 0])
    for r in rows: agg[(r["label"], r["opponent"])][0] += int(r["wins"]); agg[(r["label"], r["opponent"])][1] += int(r["total"])
    labels = sorted({k[0] for k in agg}, key=iter_key); opps = sorted({k[1] for k in agg}, key=iter_key)
    fig, ax = plt.subplots(figsize=(9, 5.5)); cmap = plt.get_cmap("viridis_r")
    for i, o in enumerate(opps):
        pts = [(labels.index(l), 100 * agg[(l, o)][0] / agg[(l, o)][1]) for l in labels if (l, o) in agg and agg[(l, o)][1] > 0]
        if pts: ax.plot([p[0] for p in pts], [p[1] for p in pts], "-o", ms=5, lw=1.6, color=cmap(i / max(1, len(opps) - 1)), label=f"vs {o} (n={sum(agg[(l, o)][1] for l in labels if (l, o) in agg)})")
    ax.axhline(50, color="gray", ls=":", lw=1)
    ax.set_xticks(range(len(labels))); ax.set_xticklabels(labels); ax.set_xlim(-0.4, len(labels) - 0.6); ax.set_ylim(-3, 103)
    ax.set_ylabel("win % of the build"); ax.set_xlabel("accepted build"); ax.set_title("Win % vs frozen snapshots of our own lineage (regression yardstick)"); ax.grid(alpha=.3)
    ax.legend(fontsize=8, loc="lower right"); fig.tight_layout(); fig.savefig(out, dpi=140); print("wrote", out)

def main():
    hist = REPO / "progress" / "history.csv"
    if not hist.is_file(): print("no history yet"); return
    rows = list(csv.DictReader(open(hist)))
    roster = [r for r in rows if re.fullmatch(r"g_iter\d+", r["opponent"])]
    ladder = [r for r in rows if "." in r["opponent"]]
    if roster: plot_roster(roster, REPO / "progress" / "vs_roster.png")
    if ladder: plot_ladder(ladder, REPO / "progress" / "ladder.png")

if __name__ == "__main__": main()
