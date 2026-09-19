"""Orientation for every tracked metric, so that a HIGHER oriented value is always
better for us and a POSITIVE correlation with the result is therefore always good.

Without this, a table mixes metrics where up is good (our ECs) with ones where up is
bad (our oscillation, their anything), and every row needs its own sign-flip in the
reader's head -- which is where misreadings come from.

  +1  higher is better for us        -1  lower is better for us
A metric of theirs is always -1: more for them is worse for us.
Gaps (us - them) are +1 by construction: being further ahead is better.
"""
POLARITY = {
    'ec': +1, 'ecInf': +1, 'sla': +1, 'muc': +1, 'pol': +1,
    'exp': +1,          # exposures WE achieve
    'buff': +1,         # our empower buff, which exposures earn
    'unitInf': +1,      # influence embodied in our living units
    'cov': +1,          # share of the map we have seen
    'moves': +1, 'meanMoves': +1,
    'navMoves': +1,     # cumulative moves by round
    'navAba': -1,       # cumulative oscillating moves
    'navSwamp': -1,     # cumulative steps onto low-passability tiles
    'aba': -1,          # oscillation: wasted turns
    'swamp': -1,        # steps onto low-passability tiles
    'firstEC': 0,       # round of first contact with an enemy EC: direction genuinely unclear
                        # (we reach one at r48 and they at r288, and we lose) -- reported, never oriented
}
def orient(metric, value, side):
    """side: 'us', 'th', or 'gap'. Returns the oriented value, or None if unoriented."""
    p = POLARITY.get(metric, 0)
    if p == 0: return None
    if side == 'gap': return value            # already us-minus-them
    if side == 'us': return p * value
    return -p * value                         # theirs: their advantage is our disadvantage
def label(metric, side):
    p = POLARITY.get(metric, 0)
    tag = '' if p == +1 or side == 'gap' else (' [inverted]' if p == -1 else ' [unoriented]')
    base = metric + (' (us-them)' if side == 'gap' else ('' if side == 'us' else ' (theirs)'))
    return base + tag
