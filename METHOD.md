# Measuring a Battlecode bot: what this project learned the expensive way

A portable account of the *method*, not the bot. Written for whoever runs the
next year's project. Every claim here is backed by a measurement in
`TRAINING_LOG.md`; where a rule cost us hours or days to learn, that is said.

## 1. Your instrument decides what you can discover

We spent a long stretch rejecting eight candidates in a row with a 48-cell
panel (a fixed set of opponent-map-side cells, compared against the
incumbent's record on the same cells). The panel could only resolve an
effect above roughly **75%** win rate; its archetype half was capped at +4
because the incumbent already won 20 of those 24 cells. A real 60%
improvement needs about 190 games to show. **The instrument could not have
accepted anything we were able to build**, and eight "no effect" verdicts
said more about the measurement than the bot.

Replacing it with a **sequential probability ratio test (SPRT)** changed the
project immediately: the very next candidate was accepted at 68.8%, and the
defect it fixed had been sitting in the code the whole time.

- `tools/mirror.sh` plays candidate vs incumbent, **random map and random
  side per game**, in batches of 16.
- `tools/sprt.py` after each batch: H0 "no better" (p=0.50) against H1
  "worth a snapshot" (p=0.58), alpha = beta = 0.05.
- It stops as soon as the evidence is decisive, usually inside 80-200 games,
  and spends games only while they still decide something.

Use random maps, not a chosen set: a fixed map set answers a different
question than the ladder asks.

## 2. Run a diagnostic game before you fund a test

**No test starts until one logged game shows the mechanism firing.**

```
LOG_OUT=out.log tools/run-dev.sh bot <incumbent> <Map> out.bc21 \
    -Dbc.server.robot-player-to-system-out=true
```

then grep your pre-registered `@tag` counters. This rule was earned three
times over:

| candidate | what a test would have measured | what the log showed |
|---|---|---|
| opening capture | a 48-cell panel, twice | fired **0** times: its caps excluded every neutral on those maps |
| garrison guards | +3 cells, credited to the garrison | guards were posted, then a ring rule walked them 4-9 tiles away |
| collapse screen | a 240-game SPRT | 397 holds, **0** moves: its free-tile rule was unreachable |
| capture reserve | looked decisive on one map | the map had four visible cheap neutrals; elsewhere the mechanism cannot fire at all |

A mechanism that compiles, runs, and logs nothing useful is the *normal*
failure, not the exception. "It is implemented" is not evidence that it
happens. Two further points: run the diagnostic on a cheap map (a 32x32
board costs half a 64x64 one) **but only if the mechanism can fire there**,
and sanity-check on a map where it cannot.

## 3. Mine the games you have already paid for

Scrimmages cost rating, so squeeze them. After every block:
`tools/scrim-study.sh <run>` writes both sides' figures at r200/400/600
(`study.tsv`) and their exploration (`nav.tsv`), for **wins as well as
losses**. Hand-done versions of this produced the two best findings of the
project inside an hour, both invisible to every aggregate we had been
keeping:

- one enemy 1-influence muckraker within six tiles was switching our whole
  economy off, because `danger` meant *any* enemy and gated every
  economy branch (accepted, +19 points);
- our slanderer cap, not our influence, was capping our economy (accepted,
  +9 points).

Use the whole replay tool, not one mode of it: aggregates (`--every`), a
spawn window for unit *sizes* (`--from/--to`), one unit's whole life
(`--robot`), what converts your centers (`--hits`), where conviction lands
(`--speeches`), movement and coverage (`--navstats`), and your own bytecode
headroom (`--bytecode`).

## 4. Correlate to generate hypotheses; diagnose to filter them

`tools/correlate.py <run>` reports, for every metric and for the us-minus-them
gap, the point-biserial correlation with the result, a **within-opponent**
correlation, and the medians in wins vs losses.

Three traps, two with fixes:

1. **Outcome leakage.** At r600 nearly every metric correlates +0.53 to +0.61
   with winning, because the winner is by then ahead on everything. At r200
   the signal is selective (centers +0.47, the rest +0.19 to +0.32). *Act on
   early metrics only.*
2. **Opponent strength.** Any metric that merely identifies weak opponents
   correlates with winning. The within-opponent column removes each
   pairing's own average.
3. **Symptom versus cause — no statistical fix.** The opponents' unit sizes
   and influence allocation correlated strongly with their winning. We
   rebuilt that allocation faithfully as a sparring bot; **it lost 0-24 to
   our own bot**, because the allocation was downstream of their advantage,
   not its source. Only a diagnostic game distinguishes a lever from a
   symptom.

See `progress/METRICS.md` for what every metric means, how it is computed
from the replay stream, and the orientation convention.

## 5. Self-play is free but it cannot see everything

Against a twin, any *symmetric* improvement partly cancels: both sides scout
better, or expand harder, so the win rate barely moves. Our two accepted
changes were worth +19 and +9 points in self-play and moved the ladder by
**nothing** (14/48, 12/48, 14/48 across three submissions).

Consequences:

- A change that only *matches* what opponents already do will look neutral
  in the mirror. That is a property of the instrument, not proof the change
  is worthless — but it is also not a licence to ship on faith.
- Distrust the proxy when a *series* of self-play accepts never moves the
  rating; never on a single block.
- Repairs of your own defects transfer better than reallocations. Five
  reallocations (guards, hunters, deposits, surplus, capture reserve) were
  all rejected; both accepts removed a constraint or a bug.

## 6. Re-test old rejections after you change the instrument or fix a defect

A verdict is only as good as the instrument and the bot that produced it.
Re-test when **both**: the mechanism was verified to fire at the time, and
the margin sat inside the old instrument's resolution. The slanderer cap had
been rejected once; re-tested after the economy defect was fixed, the same
idea was worth +9 points. But be honest about the base rate: of the old
rejects re-tested here, most were *worse* than they had looked
(collapse -5, relay -7.5), not better.

## 6b. Test the measuring apparatus, not just the bot

The analysis code is as capable of being wrong as the bot, and its errors are
worse because they are silent and they misdirect the next candidate.
`tools/test_metrics.py` covers orientation, the correlation and its undefined
cases, the running mean, onset detection, stratification, and **integrity
checks on the live data**. It found a real bug on its first run: new per-round
columns had been added to the CSV header in one position and to the values in
another, so "coverage" was actually cumulative moves, and a finding built on it
was wrong. The integrity check that caught it was the simplest one -- coverage
must lie in [0, 1000]. Write those first, and run the suite on every script
change, not only on bot changes.

## 6c. Unit-test the bot's pure logic, especially the tuning constants

Most of a Battlecode bot needs a game to exercise it, but the parts that decide
*sizes and targets* are pure functions and belong under test: the slanderer
breakpoint table and `bestSize` (which sizes every economic unit), the comms
flag encoding, the map-knowledge registry, and Chebyshev distance.

Add invariant tests over the tuning constants themselves. A dose ladder means
constants change constantly, and the failure mode is silent: set the
spare-branch slanderer cap below the normal cap and surplus influence can never
become economy, with nothing to indicate it but a worse win rate weeks later.
Tests that assert the *relationships* (`SPEND_SLANDERER_CAP >=
MAX_SLANDERERS`, ring inside leash, size cap is a real breakpoint) catch that in
a second. Verify the tests bite by breaking an invariant on purpose and
checking they fail.

## 7. Housekeeping that cost us real time

- **`pgrep -c battlecode.server.Main` double-counts**: each game runs under a
  `timeout` wrapper carrying the same class name. Divide by two.
- **Killing a batch runner's `xargs` does not stop the run**; its loop starts
  the next batch. Kill the script.
- **`git checkout` will not revert a change you already committed.** Restore
  from the snapshot directory instead, and diff against it to confirm.
- **Measure before optimising.** We wrote a cost-aware pathfinder because
  passability makes movement expensive, then measured: our units averaged
  86 moves each against the opponent's 84, and we already stepped onto bad
  tiles half as often as the bot beating us. Reverted unrun; the same table
  showed the real gap was map coverage, 46% against 93%.
