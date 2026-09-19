# What the metrics mean and how they are computed

Companion to `progress/onset-*.png` and the tables printed by
`tools/correlate.py` and `tools/onset.py`. Everything here is derived from
`.bc21` replay files by `tools/replaydump/ReplayDump.java`, which replays the
engine's own event stream (spawns, moves, deaths, actions, influence and
conviction changes, team changes, votes) and reconstructs every robot's state
round by round. Nothing is estimated or sampled from the game; it is all read
back from what the engine recorded.

## The pieces of a name

A metric name is `<quantity>` for our own value, `<quantity> (theirs)` for the
opponent's, and `<quantity> (us-them)` for the difference. A trailing
`[inverted]` means the raw quantity is better when *smaller*, so it has been
negated (see "Orientation" below).

"Us" is always the team our bot played, whichever side of the map that was;
the study script reads the side out of the replay filename and orients every
column accordingly, so A/B never leaks into the numbers.

## The quantities

| name | meaning | how it is computed |
|---|---|---|
| `ec` | enlightenment centres held | count of centres owned by the team at that round |
| `ecInf` | influence banked **in** centres | sum of the influence held by those centres — the war chest, not income |
| `sla` | slanderers alive | count of living slanderers |
| `muc` | muckrakers alive | count of living muckrakers |
| `pol` | politicians alive | count of living politicians. Note: a slanderer that survives 300 rounds *becomes* a politician, so this rises partly from ageing |
| `unitInf` | influence embodied in living units | sum of the influence of every living unit. The best single measure of "army plus economy on the board", because one 400-influence slanderer and ten 40-influence ones count the same |
| `exp` | exposures achieved | cumulative count of this team's successful muckraker exposures (each destroys an enemy slanderer) |
| `buff` | empower buff | the engine's team-wide multiplier on speech conviction, earned by exposing slanderers. Compounds: a large buff makes every later politician hit harder |
| `cov` | map coverage | share of passable tiles that *any* unit of the team has stood on at some point in the game. A proxy for how much of the map has been seen, hence which neutral centres are known |
| `moves` | total moves | every successful move by every unit, summed over the game |
| `meanMoves` | moves per unit | `moves` divided by units that lived long enough to act — how mobile a typical unit is, independent of army size |
| `aba` `[inverted]` | oscillation rate | share of moves that returned to the tile occupied two moves earlier (an A-B-A step). Wasted turns; lower is better |
| `swamp` `[inverted]` | low-passability steps | share of moves that landed on a tile with passability below the map's mean. Costly because cooldown is `actionCooldown / passability` of the tile you land on; lower is *usually* better |
| `firstEC` | first contact with an enemy centre | the round a unit of this team first stood within sensor range of an enemy centre. **Deliberately unoriented**: we reach one at r48 and strong opponents at r288, and we lose, so it is unclear which direction is good |

## Orientation: why every correlation reads the same way

Defined in `tools/polarity.py`. Each quantity is marked higher-is-better
(`+1`) or lower-is-better (`-1`), and the value is multiplied by that sign
before anything is correlated. Anything belonging to the *opponent* is negated
as well, since their advantage is our disadvantage. Differences are already
oriented by construction: further ahead is better.

The result is a single reading rule: **a positive correlation always means
"this being better goes with us winning"**, with no per-row sign-flipping.

## Verification

`tools/test_metrics.py` unit-tests this pipeline: the orientation convention,
the correlation (against hand-computed values, and its undefined cases), the
running mean, onset detection (including that a single spike does not count),
within-group stratification, and integrity checks on the live `study.tsv`
(coverage within range, cumulative counters never decreasing, every `us_`
column having a `th_` twin). The shared statistics live in `tools/statlib.py`
so both analysis scripts use one tested implementation rather than copies.

**It has already earned its keep.** The integrity check "coverage is a share
in [0,1000]" failed on its first run at 76,165: the per-round navigation
columns had been added to the CSV *header* after `empowers` but to the *values*
before it, shifting every later column. What the analysis was reporting as
coverage was cumulative moves. Run the tests after any change to the dump
format.

## Correlation, and what it can and cannot support

The number reported is the point-biserial correlation between a metric and
the game's result (1 for our win, 0 for our loss) — ordinary Pearson
correlation against a binary outcome. `tools/correlate.py` also reports a
**within-opponent** version, which subtracts each opponent's own average
before correlating, so a metric cannot score merely by identifying which
opponents are weak.

The noise floor is roughly `2 / sqrt(games)`: about 0.38 for 27 games, 0.29
for 48. Curves inside that band are not evidence of anything.

**The onset graph** (`tools/onset.py`) plots each oriented metric's
correlation at every 50-round sample, and marks the round where it first
crosses a threshold and stays — its *onset*. The point is temporal
precedence: if coverage starts predicting the result at r100 and centre count
only at r250, coverage is plausibly upstream. That is the one causal hint a
correlation can honestly give.

Two traps the graph is designed to expose rather than hide:

- **Outcome leakage.** Late in a game nearly every metric correlates with
  winning, because the winner is by then ahead on everything. Measured here:
  at r600 most metrics read +0.5 to +0.65, while at r200 the field spreads
  out and discriminates. Act on early onsets.
- **Symptom versus cause.** Correlation cannot tell them apart, and this
  project has a worked example: opponents' unit sizes and influence
  allocation correlate strongly with their winning, and a faithful
  reconstruction of that allocation (`src/arch_big`) lost 0-24 to our own
  bot, because the allocation was downstream of their advantage rather than
  its source. A high correlation earns a diagnostic game, not a code change.
