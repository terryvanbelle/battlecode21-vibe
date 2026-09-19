"""Statistics shared by tools/correlate.py and tools/onset.py, kept in one place so it
can be unit-tested (tools/test_metrics.py) rather than duplicated in two scripts."""
import math

def pointbiserial(pairs):
    """Pearson correlation between a value and a 0/1 outcome. pairs = [(value, outcome)].
    Returns None when undefined: too few points, or either side constant."""
    n = len(pairs)
    if n < 6: return None
    xs = [p[0] for p in pairs]; ys = [p[1] for p in pairs]
    mx, my = sum(xs)/n, sum(ys)/n
    sx = math.sqrt(sum((x-mx)**2 for x in xs)/n); sy = math.sqrt(sum((y-my)**2 for y in ys)/n)
    if sx == 0 or sy == 0: return None
    return sum((x-mx)*(y-my) for x, y in pairs)/(n*sx*sy)

def noise_floor(n):
    """Rough 95% band for a correlation from n samples."""
    return 2/math.sqrt(n) if n > 0 else float('inf')

def running_mean(values):
    """values in round order, None allowed; returns the mean of the non-None values."""
    v = [x for x in values if x is not None]
    return sum(v)/len(v) if v else None

def onset(curve, rounds, threshold, hold=0.8):
    """First round where |corr| >= threshold and the NEXT sample still holds >= threshold*hold.
    Returns None if it never does. Requires the crossing to persist, so one lucky
    sample cannot declare an onset."""
    for i, c in enumerate(curve):
        if c is None or abs(c) < threshold: continue
        if i + 1 >= len(curve): return rounds[i]
        nxt = curve[i+1]
        if nxt is not None and abs(nxt) >= threshold * hold: return rounds[i]
    return None

def within_group(rows, value_of, group_of, outcome_of):
    """Deviations from each group's own mean, so a metric cannot score merely by
    identifying which groups (opponents) are weak."""
    from collections import defaultdict
    g = defaultdict(list)
    for r in rows:
        v = value_of(r)
        if v is not None: g[group_of(r)].append((v, outcome_of(r)))
    out = []
    for _, vs in g.items():
        if len(vs) < 2: continue
        mv = sum(v for v, _ in vs)/len(vs); my = sum(y for _, y in vs)/len(vs)
        out += [(v-mv, y-my) for v, y in vs]
    return out
