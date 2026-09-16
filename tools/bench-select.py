#!/usr/bin/env python3
"""Pick, by NAME ONLY, the package most likely to be each repo's final bot.

    tools/bench-select.py            # one name per repo, space-separated (for OPPONENTS=...)
    tools/bench-select.py --all      # every non-junk package
    tools/bench-select.py --table    # repo -> chosen, candidates

Heuristic score: 'final' > 'postqual' > 'qual(s)' > 'seeding' > highest version
number > 'sprint2' > 'sprint' ; names that look like tests/templates/do-nothing
bots are excluded. Directory names are scaffold metadata, not source.
"""
import csv, re, sys
from collections import defaultdict
from pathlib import Path
MANIFEST = Path.home() / "projects/vibe/bc21-benchmarks/manifest.tsv"
JUNK = re.compile(r"(test|template|donothing|do_nothing|nothing|example|empty|idle|testbed|tester|profiler|placeholder|null|failure|scratch|old|bad|naive|aux|debug|donot)", re.I)

def score(pkg):
    n = pkg.lower(); s = 0
    if "final" in n: s += 1000
    if "postqual" in n: s += 900
    elif re.search(r"qual", n): s += 800
    if "seeding" in n: s += 700
    if "sprint2" in n or "sprint_2" in n: s += 300
    elif "sprint" in n: s += 200
    m = re.findall(r"(\d+)", n)
    if m: s += min(150, int(m[-1]))       # later versions score higher, capped
    if n in ("bot", "player", "myplayer", "my_player", "ourplayer", "robot"): s += 100
    s -= n.count(".") * 50                  # nested packages are usually subprojects
    return s

def main():
    rows = list(csv.DictReader(open(MANIFEST), delimiter="\t"))
    by_repo = defaultdict(list)
    for r in rows:
        if JUNK.search(r["package"]) and "final" not in r["package"].lower(): continue
        by_repo[r["repo"]].append(r["name"])
    mode = sys.argv[1] if len(sys.argv) > 1 else ""
    if mode == "--all":
        print(" ".join(n for names in by_repo.values() for n in sorted(names))); return
    chosen = {repo: max(names, key=score) for repo, names in by_repo.items()}
    if mode == "--table":
        for repo in sorted(chosen): print(f"{repo:48s} -> {chosen[repo]:40s} ({len(by_repo[repo])} candidates)")
        return
    print(" ".join(sorted(chosen.values())))

if __name__ == "__main__": main()
