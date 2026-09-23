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

A mirror pits the candidate (with the change) against the incumbent (without
it), so the sides are **not** identical and an improvement is not cancelled by
the opponent sharing it. Do not reach for that excuse -- it is wrong, and it was
written here for a day before being caught.

The two real limitations are narrower:

- **Defensive changes have nothing to defend against.** If a change resists
  something the *external* bots do and our incumbent does not -- muckraker
  swarms, very fast expansion -- the mirror gives it no opportunity to pay, and
  it reads ~50%. Documented here: relay and scouts8 both stopped at exactly
  6/14 against a twin that never punished the deficiency they fixed.
- **Mirror gains need not transfer upward.** Beating your own previous version
  is not evidence of beating anyone stronger. Of three accepted changes worth
  +19, +9 and +22 points in self-play, the first two moved the ladder by
  **nothing** (14/48, 12/48, 27/96 across three submissions) and only the third
  carried (19/48). The one that carried was the one measured directly against
  the opponents' behaviour rather than against our own past.

An *economic* change -- spending differently, removing a constraint on your own
production -- has every chance to show itself in a mirror, and both accepts of
that kind transferred or at least held. When such a candidate reads 50%, that is
a real negative result about the change, not an artefact of the instrument.

**Expansion is the doubtful middle case.** A capture race does run in a mirror,
because the incumbent contests the same neutrals, so a mirror result there is
not obviously meaningless. But four expansion candidates in a row read level
(the neutral race, the opening capture bank, the capture reserve, the capturer
cap at 120-120 exactly) while the ladder said expansion was the single largest
gap: in losses the opponent went from one centre to six between r100 and r700
while we held two in *both* wins and losses. Two readings fit -- the changes
were genuinely worthless, or a twin that expands exactly as badly as we do
cannot show the difference. Do not assume the second; that is the excuse this
section exists to forbid. Instead remove the ambiguity by sparring against an
archetype that does the thing (`src/arch_expand`), which is a real opponent and
a real result either way.

Consequences:

- A change that only *matches* what opponents already do will look neutral
  in the mirror. That is a property of the instrument, not proof the change
  is worthless — but it is also not a licence to ship on faith.
- Distrust the proxy when a *series* of self-play accepts never moves the
  rating; never on a single block.
- Repairs of your own defects transfer better than reallocations. Five
  reallocations (guards, hunters, deposits, surplus, capture reserve) were
  all rejected; all three accepts removed a constraint or a bug.
- Keep one sparring archetype per thing the opponents do to you. When a whole
  functional area accumulates rejects, suspect the sparring partner before the
  hypotheses.

## 5b. When the mirror is the wrong opponent, add an arm -- do not reinterpret the null

A mirror measures the candidate against your own previous build. If the change
is built to exploit something the *external* opponents do and your incumbent
does not, the mirror gives it less to work with, and a null there is weaker
evidence than it looks. Coverage is the clear case: "see more of the map so you
can contest centres" has little to contest against a twin that also fails to
contest, while the bots that beat us take six or seven centres to our two.

This is a real limitation and it is also the most dangerous one, because it is
indistinguishable from an excuse. The rule that keeps it honest:

- **Never reinterpret a null.** "It came out level but it aligns us with what
  the winners do" is not evidence. Opponents win despite some of their
  behaviour as well as because of it. This project has a counter-example:
  Iteration 36 fixed a measured bug -- 41 of 54 capture politicians walked to
  ground the team already held -- and lost 65-79.
- **Instead, pre-register a second arm against an archetype that HAS the
  property**, decided before the gate runs, not after seeing the number. Keep
  one sparring archetype per thing the opponents do to you; `src/arch_expand`,
  `src/arch_hunt` (muckrakers that patrol the ring where slanderers live) and `src/arch_lemon`
  (the hunt plus attackers sized to your centres) are the ones that beat the current build;
  exists for exactly this and is verified to take 6 centres to our 2-3.
- **State the comparison as an A/B**: does the candidate beat that archetype
  more often than the incumbent does? A null in the mirror plus a win in that
  arm is a real finding about opponent classes. A null in both is a change that
  does not pay, whatever it resembles.
- A change that needs this treatment should say so **in its pre-registration**,
  with the archetype named. If you find yourself reaching for the argument after
  a disappointing gate, you have already failed the test.

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

## 6b-ii. A sparring partner is an instrument: run it before you trust it

The diagnostic-first rule applies to the *opponents you build*, not only to the
bot. An archetype that does not do the thing it is named for is worse than
having none, because a candidate will read level against it and that null will
be recorded as a real negative. In one session an expander archetype was written,
committed, and only then run. It failed twice, in ways no code review caught:

- It sent every capturer to the cheapest neutral, took it once, filled its own
  cap permanently and then idled with its influence frozen for 1,300 rounds.
- Fixed, it took 8 centres to the incumbent's 0 and 77,491 unit influence to 3
  -- and lost on votes, because it spent every influence on captures and never
  bid. A candidate's win against *that* would have been a verdict on bidding.

Both are invisible in the win/loss column, which is exactly why the check has to
be a logged game read for the mechanism: does it capture, how many distinct
targets, how many bodies in flight, what is its influence doing. The same
session found the correlation script dead on its first metric (a parameter
shadowing an imported function), and the onset tool carrying a private copy of a
rule that the shared module was supposed to own. Every one of these was found by
*running the thing*, never by reading it.

**Run it on a case whose answer you already know.** Every instrument built in one
session was wrong on first use -- a replay mode referencing classes that did not
exist, two archetypes that failed for opposite reasons, a rule guard that refused
legitimate files, and a knowledge tool whose denominator flattered us. All were
caught downstream, by a number looking odd, never by inspection. A tool that
reports "4 of 4, a perfect score" is telling you to check its denominator against
a source you trust.

Rules worth keeping:

- Re-run an instrument after every change to it, including changes you are sure
  are cosmetic.
- An end-to-end test that merely executes each script on a small synthetic input
  catches a whole class of failure that unit tests on library functions cannot.
- Before you queue one job behind another, **verify the wait predicate matches a
  live process.** One that matched nothing started a run early, which rebuilt a
  class tree underneath 48 games in flight and voided the block.

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
- **Never read a running batch as a result.** Games that end by capture finish first
  and games that go the distance finish last, so a batch's early tally is its losses
  (or, against opponents who annihilate, its wins). A 0-5 read mid-batch that ended
  11-5 cost a voided batch and a resumed gate (`mirror.sh` now takes `W0`/`L0`).
- **The dev runner's engine seed is fixed**, so `run-dev.sh` replays the same game
  until the code changes it: every diagnostic rerun is like-for-like, and a baseline
  run of the incumbent on the same seed is what an arm is read against -- on one
  seed the incumbent itself ends 1 centre to 7, and the arms only make sense relative
  to that.
- **Check `@bc ... over=` for every centre before trusting a candidate.** A centre
  over its bytecode budget loses rounds silently and gates at 53% no matter what
  the change was worth.
- **The VM disk fills**: a gate keeps every loss at 4-8 MB; prune run directories
  once their study is fetched, and check `df` before launching.
- **Measure before optimising.** We wrote a cost-aware pathfinder because
  passability makes movement expensive, then measured: our units averaged
  86 moves each against the opponent's 84, and we already stepped onto bad
  tiles half as often as the bot beating us. Reverted unrun; the same table
  showed the real gap was map coverage, 46% against 93%.
