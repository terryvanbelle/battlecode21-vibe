"""Shared Elo bookkeeping over progress/games.csv (one row per scrimmage, in play order).
Columns: run,seq,teamA,teamB,map,winner(A|B),rounds,reason. Our team appears as 'us:<build>' and is
rated as the single player 'us' (a new version inherits the rating, as on the real ladder)."""
import csv, os, collections
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GAMES = os.path.join(REPO, 'progress', 'games.csv')
HDR = ['run', 'seq', 'teamA', 'teamB', 'map', 'winner', 'rounds', 'reason']
def pid(name): return 'us' if name.startswith('us:') else name
def load():
    return list(csv.DictReader(open(GAMES))) if os.path.exists(GAMES) else []
def expected(ra, rb): return 1 / (1 + 10 ** ((rb - ra) / 400))
def ratings(rows, k=32):
    """-> (R, games, wins, hist) ; hist = list of (game index over all rows, our rating) after each of our games."""
    R = collections.defaultdict(lambda: 1500.0); games = collections.Counter(); wins = collections.Counter(); hist = []
    for i, r in enumerate(rows):
        a, b = pid(r['teamA']), pid(r['teamB']); sa = 1.0 if r['winner'] == 'A' else 0.0
        ea = expected(R[a], R[b])
        R[a] += k * (sa - ea); R[b] += k * ((1 - sa) - (1 - ea))
        games[a] += 1; games[b] += 1; wins[a] += sa; wins[b] += 1 - sa
        if 'us' in (a, b): hist.append((i, R['us']))
    return R, games, wins, hist
def ladder_bots():
    p = os.path.join(REPO, 'tools', 'ladder-bots.txt')
    return [l.strip() for l in open(p) if l.strip() and not l.startswith('#')]
def append(rows):
    new = not os.path.exists(GAMES)
    with open(GAMES, 'a', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=HDR)
        if new: w.writeheader()
        for r in rows: w.writerow(r)
