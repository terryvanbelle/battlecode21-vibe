"""Metrics derived from a study table rather than read from the replay.

The centre count at a round says who is ahead; it does not say who has been *taking*
ground. Two games that both read "2 centres at r400" are different games if one team
took two and lost none while the other took four and lost two. `ecGain` and `ecLoss`
are the cumulative sums of the positive and negative changes in a team's centre count,
so they separate expansion from retention.

Rows must arrive in file order: a study table is written one game at a time with the
rounds increasing, so a round that does not increase starts a new game. When the table
carries a `game` column that is used instead, and the round rule is the fallback for
tables written before the column existed.
"""
def game_segments(rows):
    """Yield (start, end) index pairs, one per game, from rows in file order."""
    if not rows: return
    has_game = 'game' in rows[0]
    start = 0; prev_key = None; prev_round = None
    for i, r in enumerate(rows):
        if has_game:
            key = r['game']; new = prev_key is not None and key != prev_key
        else:
            key = None
            try: rnd = float(r['round'])
            except (KeyError, TypeError, ValueError): rnd = None
            new = prev_round is not None and rnd is not None and rnd <= prev_round
            prev_round = rnd
        if new:
            yield (start, i); start = i
        prev_key = key
    yield (start, len(rows))

def add_expansion(rows):
    """Add us_ecGain/us_ecLoss/th_ecGain/th_ecLoss to each row, in place. Returns rows."""
    for lo, hi in game_segments(rows):
        acc = {'us_ecGain': 0.0, 'us_ecLoss': 0.0, 'th_ecGain': 0.0, 'th_ecLoss': 0.0}
        prev = {}
        for i in range(lo, hi):
            r = rows[i]
            for side in ('us', 'th'):
                col = side + '_ec'
                try: v = float(r[col])
                except (KeyError, TypeError, ValueError): v = None
                if v is not None and side in prev:
                    d = v - prev[side]
                    if d > 0: acc[side + '_ecGain'] += d
                    elif d < 0: acc[side + '_ecLoss'] += -d
                if v is not None: prev[side] = v
            for k, v in acc.items(): r[k] = v
    return rows

EXPANSION_COLS = ['ecGain', 'ecLoss']
