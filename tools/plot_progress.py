#!/usr/bin/env python3
"""progress/cumulative_iterations.png -- accepted iterations over time.

Counts src/g_iterN/ snapshots by the date of the commit that added each one
(mtime for uncommitted ones, drawn hollow). Read the slope, not the height.
Milestones (process changes) go in progress/milestones.txt as `<commit>|<label>`.
"""
import re, subprocess, sys
from datetime import datetime, timezone
from pathlib import Path
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt, matplotlib.dates as mdates

REPO = Path(__file__).resolve().parent.parent

def commit_date(path):
    out = subprocess.run(["git", "log", "--diff-filter=A", "--format=%aI", "--", str(path)], cwd=REPO, capture_output=True, text=True).stdout.strip().splitlines()
    return (datetime.fromisoformat(out[-1]), True) if out else (datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc), False)

def main():
    snaps = sorted((int(m.group(1)), p) for p in (REPO / "src").glob("g_iter*") if (m := re.fullmatch(r"g_iter(\d+)", p.name)))
    if not snaps: print("no snapshots"); return
    rows = [(n, *commit_date(p / "RobotPlayer.java")) for n, p in snaps]
    rows.sort(key=lambda r: r[1])
    fig, ax = plt.subplots(figsize=(11, 6))
    xs = [r[1] for r in rows]; ys = list(range(1, len(rows) + 1))
    ax.step(xs, ys, where="post", color="#2563eb", lw=2); ax.scatter(xs, ys, color="#2563eb", s=24, zorder=3)
    prov = [(x, y) for (n, x, c), y in zip(rows, ys) if not c]
    if prov: ax.scatter([p[0] for p in prov], [p[1] for p in prov], s=110, facecolors="none", edgecolors="#dc2626", zorder=4, label="uncommitted")
    for (n, x, c), y in zip(rows, ys): ax.annotate(f"iter{n}", (x, y), textcoords="offset points", xytext=(5, -12), fontsize=8, color="gray")
    ms = REPO / "progress" / "milestones.txt"
    if ms.is_file():
        for i, line in enumerate(l for l in ms.read_text().splitlines() if "|" in l and not l.startswith("#")):
            commit, label = line.split("|", 1)
            d = subprocess.run(["git", "show", "-s", "--format=%aI", commit.strip()], cwd=REPO, capture_output=True, text=True).stdout.strip()
            if not d: continue
            ax.axvline(datetime.fromisoformat(d), color="#9333ea", ls="--", lw=1)
            ax.annotate(label.strip(), (datetime.fromisoformat(d), 0.5 + i * 0.7), rotation=90, fontsize=7.5, color="#9333ea", va="bottom")
    ax.set_title("Cumulative accepted iterations (Battlecode 2021)"); ax.set_ylabel("accepted iterations"); ax.set_xlabel("date (UTC)")
    ax.grid(alpha=.3); ax.set_ylim(0, len(rows) + 1)
    ax.xaxis.set_major_formatter(mdates.DateFormatter("%m-%d %H:%M")); fig.autofmt_xdate()
    if prov: ax.legend()
    out = REPO / "progress" / "cumulative_iterations.png"; out.parent.mkdir(exist_ok=True); fig.tight_layout(); fig.savefig(out, dpi=140); print("wrote", out)

if __name__ == "__main__": main()
