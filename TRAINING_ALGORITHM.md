# Training Algorithm

How this project turns a season's rules into a strong bot, stated in terms that
do not depend on the season. Written fresh for this project after reading the
2022, 2026 and 2025 predecessors and `anicolao/bcenv`; it keeps what those runs
proved out (roughly 900 logged iterations between them) and drops what they
themselves found did not pay. `RULES.md` is the game; this is the method;
`TRAINING_LOG.md` is the record.

**Standing constraints for this project.** Post-mortems from the current
contest year are never read, first- or second-hand. External competitor bots
are downloaded as opponents but their **source is never read**, and **no game
against a bot may be reviewed (replay, trace, log, or indicator) until we beat
that bot at least 20% of the time**; until then only its score is visible.

---

## 1. Principles

1. **Instruments before strategy.** A change that cannot be measured cannot be
   accepted. Build the runner, the replay reader and the charts before the bot.
2. **The engine is the truth.** Read engine source or bytecode for every
   mechanic the bot relies on; tag each fact in `RULES.md` with where it was
   verified. Two digests agreeing is not verification.
3. **Trace, then theorise.** Every root cause in the predecessor projects was
   found in a replay, not by reasoning about what a good bot would do. A trace
   gives the symptom; the mechanism still needs its own test.
4. **Kill ideas as cheaply as possible.** Engine read < replay census < 1-map
   probe < screen < full evaluation. Most attempts fail; the loop's job is to
   make failure cheap and informative.
5. **Pre-register.** Before running the evaluation of a change, write down the
   mechanism metric that must move, the gate, and the observation that would
   make you drop the idea. Do not move a bar after seeing a number.
6. **Record the measurement, not the explanation.** Numbers survive their
   session; stories usually do not. Supersede in place; never delete.
7. **Absolute strength is the objective.** Head-to-head against your own
   predecessor is a partial derivative and can walk downhill; a frozen roster
   and external opponents are the only level readings.
8. **Never idle.** Waiting on a run is work; stopping because nothing comes to
   mind is not. The escalation ladder in section 7 always has a next step.

## 2. Phase 0: foundation (before any strategy work)

Do these in order and do not start the loop until each has a passing check.

1. **Rules digest.** `RULES.md` from the spec, cross-checked against engine
   source. List every `RobotController` method; the bot's unused methods are
   re-swept at iteration 5, then every 10 iterations, then whenever stuck.
2. **Match infrastructure.** A headless runner that invokes the engine with bare
   `java` (no Gradle daemon per game), runs games in parallel within the
   machine's memory, records `(opponent, map, side) -> winner, rounds, reason`
   and keeps the replays of losses. A snapshot tool that freezes the bot as a
   renamed package. A compare tool that diffs two runs game by game.
3. **Determinism check.** Same code twice, all maps, both sides: the results must
   be byte-identical. If they are not, find the source (usually `Math.random`
   in the bot or an engine coin flip) and either remove it or account for it.
4. **Replay reader.** A tool that turns a replay into text: per-round team
   aggregates (resources, unit counts by type, actions, deaths, the season's
   score terms), an event log with a round window, per-robot tracks, an ASCII
   board, and the bot's own indicator strings. This is the microscope; every
   hypothesis in the loop is checked with it.
5. **Bytecode monitor in the bot from day one.** Compare the round number before
   and after the robot's own logic (a change means the engine truncated the
   turn) and `Clock.getBytecodeNum()` against the type limit at the end of the
   turn (near-miss). Surface both in indicator strings and have the replay
   reader report them for every run. An overrun is a silent bug that breaks
   strategy without an exception.
6. **Mirror match harness.** The bot against a byte-identical copy of itself,
   all maps, both sides. This is the null: it calibrates what "no effect" looks
   like and exposes play-symmetry bugs (a map that splits lopsidedly between
   sides under identical code is a bug in the code, not the map).
7. **Play-symmetry audit as a habit.** Any fixed-order iteration over
   directions, any "first result that satisfies" from a sensing call whose
   scan order is fixed, any hard-coded compass default, anything correlated with
   team identity gives one side a compounding tempo edge. Audit new tie-break
   code when it is written; the mirror harness catches what the audit misses.
8. **Charts.** `progress/` holds the two standing charts (section 6) plus any
   per-season score chart, regenerated on every accept.

### The basics, each with its own diagnostic

Strategy is built on six capabilities that every season needs. Each gets a
**diagnostic scenario** (a chosen map or a hand-built sparring opponent) and a
**metric read from the replay**, checked before the capability is called done
and re-checked whenever its code changes:

| capability | diagnostic metric |
|---|---|
| economy | resource income per round vs. the engine's theoretical curve; idle resource held; production actions per 100 rounds |
| navigation | mean rounds from spawn to a fixed far target on a maze map; fraction of turns spent oscillating (A-B-A) or blocked |
| exploration | fraction of map tiles seen by round N; time to first sighting of the enemy base |
| symmetry inference | round at which the correct symmetry is identified; false-commit rate |
| combat micro | kills per unit lost against a scripted brawler; damage taken per damage dealt; kite success rate where the season permits kiting |
| bytecode safety | overruns and near-misses per game, zero tolerated |

Iteration 0 is the smallest legal bot that moves a unit and emits indicator
strings with the bytecode monitor. It is snapshotted and evaluated so that the
instruments are proven on something trivial before they judge anything real.

## 3. Opponents and instruments

Every evaluation plays `(opponent, map, side)` cells: all maps in the corpus,
both sides. The **full corpus** is the default; a random map subset is only for
cheap screens, and the same subset is then pinned for every arm of that screen.

### Opponent sources

- **Frozen lineage snapshots** `src/g_iterN/`: every accepted iteration. The
  regression suite and the fixed roster.
- **External benchmark bots**: every publicly available bot from the season,
  cloned outside the repo, compiled once, never read. They are the closest thing
  to a scrimmage ladder: many authors, many doctrines, a spread of strengths.
  `BENCHMARK.md` lists them with origin, commit and compile status.
- **Sparring archetypes** we write ourselves when no external bot exercises a
  behaviour we need to defend against (a pure rusher, a pure economy turtle).
  Resynced from the current bot's shared code on every accept, or they go stale
  and inflate scores.
- **Mirror**: the current bot, renamed. Regenerated every time it is used.

### Tiers, by our win rate against the opponent

| tier | win rate | what we may do with it |
|---|---|---|
| **locked** | < 20% | read its score only; no replays, traces or logs of its games |
| **target** | 20-50% | full replay access; the primary source of losing games to study |
| **peer** | 50-90% | regression check; gates acceptance |
| **solved** | > 90% in two consecutive evaluations | leaves the gate; stays in the fixed roster |

**The standing gauntlet is the target tier.** With 60+ external bots, playing
all of them per candidate is too slow to be the loop's instrument, and bots we
beat 0% or 100% of the time resolve nothing. `tools/gauntlet-select.py` reads
the last scan's results and writes `tools/roster.txt`: every opponent in the
20-50% band (min 2 decided games). A scan is two-stage and incremental
(`tools/scan.sh`): every opponent on three maps of different sizes, both
sides; then four more maps for whoever is neither 0% nor 100%; cells already
decided on disk are never replayed. One small map is not a stage: on
2026-09-16 nine opponents at 1-1 on `maptestsmall` went 1-34 on three larger
maps. That roster is what every candidate plays
(`tools/ladder.sh` uses it by default). The full external set is played only
in a **scan** (`SCAN=1 tools/ladder.sh`), after every accept, and any bot whose
rate crosses a band edge is re-tiered then. Peers stay in the fixed roster;
locked bots wait for the next scan.

Re-tier after every full evaluation. The **ladder position** -- the strongest
external bot in *target* or better -- is the headline progress number, and the
locked tier is the scoreboard we are climbing towards. Unlocking a bot is a
milestone; log it and archive the first replay reviewed against it.

### Instruments, and what each can see

| instrument | resolves | blind to |
|---|---|---|
| head-to-head vs. last accepted snapshot | marginal value of one change | any weakness both builds share; interactions with features both carry |
| peers + targets (external) | regressions, doctrines we never produce | nothing systematic, but low volume per opponent |
| mirror | play symmetry, the noise floor | anything both sides do identically |
| fixed roster (every 5th snapshot + all externals) | absolute level over time | nothing; it is the level |
| locked-tier scores | distance to the top | *why* (by rule) |

Rank instruments by how even the matchup is: a bot we beat 3% or 97% of the
time cannot resolve a few games. But evenness is not representativeness: an
even opponent that never rushes cannot price a defence against rushes. Before
trusting an ablation of a defensive feature, check that the evaluating
opponents actually pose the threat.

### The noise floor

The engine is deterministic, so re-running identical code adds no information.
But any code change perturbs the order of events, and half the maps may flip
under a policy-identical perturbation. Measure this once per lineage epoch: two
builds differing only in an inert constant, full corpus, both sides. The
standard deviation of their win-count difference is the floor; state gates in
**games, on the win count, over a named map set**, and never quote a margin
(`W - L`, twice the sd) as if it were a win count.

## 4. The iteration loop

Hyperparameters (starting values; change only with a recorded reason):

| name | value | meaning |
|---|---|---|
| `AcceptMargin` | 2 x noise-floor sd: +5 wins on a 24-game quick-set head-to-head (measured 2026-09-17: a policy-identical pair split 12/24; binomial sd 2.4); minimum +4 | candidate must beat the last accepted snapshot by this |
| `PeerFloor` | 55% | peer-tier win rate below which a candidate is rejected regardless |
| `NearMiss` | within 1 sd of `AcceptMargin`, no regression signature | licence for up to 3 refinements of the same mechanism |
| `MaxRejectsPerArea` | 3 | consecutive rejects in one functional area before the next attempt must leave it |
| `SwingEvery` | 4 | at most this many incremental attempts between structural attempts |
| `RosterEvery` | 1 accept | the fixed roster is played after every accept, before the next candidate |
| `ApiSweepAt` | iteration 5, then every 10 | unused-API sweep |

### 4.1 Select a target

Pick one of:

- a **losing game** against a target- or peer-tier opponent (never a locked one);
- an **absolute degeneracy** visible in our own replays without any opponent
  comparison: a resource pinned in a dead band, a unit count that collapses,
  actions per round falling to zero, a unit oscillating between two tiles;
- a **capability gap** named from the ladder: a doctrine the bots above us
  visibly use in the games we are allowed to watch, or a mechanic in the API
  sweep the bot never touches.

Keep a **functional-area map** in the log (economy, production mix, navigation,
exploration, combat micro, communication, bidding/scoring, map adaptation,
...). After `MaxRejectsPerArea` consecutive rejects in one area the next attempt
must come from another area or from the structural track. Do not sample only
losses: being behind on a scoring term in games you lost is a tautology.

### 4.2 Trace

Read the replay of the motivating game with the replay reader before forming a
hypothesis. Enumerate the mechanisms that could produce the symptom ("chooses
badly" and "never sees it" leave identical traces) and find the one the data
supports. Check whether the same symptom appears in at least one other losing
game against a different opponent or map; a shared symptom is not a shared
cause.

### 4.3 Hypothesis, pre-registered

Write into the log before touching code:

- the mechanism, and the **decision-point counter** that proves it fired (count
  the choice, not the downstream outcome);
- **reachability**: the branch is taken (gate values checked against observed
  ranges), the choice set has more than one option, and the property being
  optimised is visible at the scale of the decision;
- **trigger frequency** across other recent games, so a fix for one replay is
  not a promiscuous change everywhere;
- **price**: what the change spends or displaces, costed against the forgone
  use rather than against zero;
- **history**: grep the closed-directions ledger by name; a prior closure is
  re-opened only with a specific reason the recorded cause no longer applies;
- the **gate**, the **falsifier**, and for a numeric change the **dose ladder**
  including a zero arm that is byte-identical to the baseline.

### 4.4 Implement and verify the mechanism

One change per candidate; never bundle. Then:

1. **Stage 0, one map**: play the candidate and the baseline on a single
   ordinary map (not the corpus outlier) and diff counters. All-identical means
   the mechanism is dead; stop here.
2. **Re-run the motivating game.** Classify: *won*; *still lost but the
   mechanism demonstrably engaged and there is an evidenced reason this game
   could not flip*; or *no evidence of engagement* (discard).
3. If the change touches any shared or capped team resource, print that pool's
   level per round in the first run and read it beside the outcome.

### 4.5 Evaluate

1. **Head-to-head** vs. the last accepted snapshot, full corpus, both sides.
   Report win count, margin, swept maps each way, and the arm-to-arm identity
   count (how many cells are byte-identical; all-identical voids the run).
2. **Roster gauntlet** (`tools/roster.txt`, the target tier) on the same map set. Diff game by game against
   the baseline run and read the **shape**: scattered mixed-direction flips are
   churn; one-directional flips, or flips concentrated on one map or side across
   several opponents, are a real effect to trace before deciding.
3. **Fixed roster**, if the head-to-head margin is thin (within 1 sd of the
   gate) -- run it *before* accepting, not after.
4. Locked-tier scores are recorded every time but never decide anything alone.

### 4.5.1 Evaluation budget (2021: a game costs ~6 CPU-minutes)

- **Stage 0** plays only informative cells: drop any cell no build has ever
  won or lost across the last three candidates; 6 cells, not 8.
- **Head-to-head** on the quick set (24 games) stops early once the candidate
  can no longer reach the gate (`wins + remaining < 12 + AcceptMargin/2`), and
  the run is recorded as a reject at that point.
- **Order the two instruments by what the change touches.** A change that
  matters against ourselves (income, bidding) is resolved by the
  head-to-head first. **Spending-mix changes are not in that class**
  (amended 2026-09-17 after Iterations 17 and 19): the twin turns every
  spare influence into slanderers, so in the mirror any reallocation away
  from slanderers loses the vote count at r1500 whatever it buys (floor:
  eight of nine losses by votes; big guards: 4/12, every loss by votes at
  r1500). The mirror measures such a change's price and never its value;
  treat "what the income buys" as doctrine: roster check first, head-to-head
  as the regression check on an accept. A doctrine or map-knowledge change is invisible to a
  twin that does not punish the deficiency (2026-09-17: relay, army and
  scouts all early-stopped at 6/14, the noise floor); for those, run the
  roster check first and the head-to-head only as a regression check on an
  accept.
- **Roster gauntlet for a candidate** uses the 4-map screen set both sides
  (72 games) against the baseline's record on the same cells; the full quick
  set (216 games) is played only for an accepted build, as the next baseline.
- **How many cells a decision needs (measured 2026-09-17).** The engine is
  deterministic: two runs of the same build on the same cells flip 0 of 72.
  A candidate's flips are therefore perturbation plus effect; rejected
  candidates flip 11-17% of cells with margins of -2 to +2, accepted ones
  +6 on 72 (bid: 9 L->W, 3 W->L). The paired test is McNemar's on the
  flipped cells: with D flips the net margin must reach ~2*sqrt(D) (D=8:
  +6, D=12: +8, D=24: +10). At the measured flip rate the effect detectable
  with 80% power is ~4 cells on 24 (18-21% of cells), ~7 on 72 (10-12%),
  ~9-11 on 108 (8-10%), ~13-15 on 216 (6-7%). The effects real accepts
  produce are 8-10% of cells, so **72 cells is the floor for a roster
  decision, not a ceiling to cut**; 108 (screen set plus two maps) resolves
  them properly. Decision (user, 2026-09-17): accept on 72 cells; a one-off
  108-cell run only when a decision looks like flailing (a margin at the
  threshold, or two accepts in a row that the next baseline fails to confirm).
  Savings come from elsewhere:
  - the 24-game head-to-head resolves only 18-21% effects, so it is not a
    gate for doctrine or map-knowledge changes (it stays a regression check
    on an accept, and the first instrument for economy and bidding changes);
  - Stage 0 is a mechanism check with no statistical role: 4 informative
    cells, counters only;
  - **futility stop** on the roster check: after 36 of 72 shared cells, stop
    when the candidate is 3 or more cells behind the baseline (reaching +5
    from there needs 8 net flips in 36 cells, which no candidate has shown);
    a reject then costs ~40 games instead of ~100.
- **The VM holds 7 games at once.** A roster baseline runs at 4 jobs and leaves
  3 for one development run; nothing else starts until a slot frees.

### 4.5.2 Contest rules for external opponents (user, 2026-09-17, PROMPTS 25)

External bots are played **only as scrimmages**: for every game the map is
drawn at random from the released corpus (`tools/bc21-maps.txt`) and the
side at random; opponents rotate (never the same one twice in a row, each
at most ceil(N/pool) times per block). `tools/scrim.sh` is the only path
(`gauntlet.sh` refuses external opponents otherwise); the block is
recorded with `tools/scrim-record.py` into `progress/scrims.csv`, and
`tools/elo.py` gives the contest standing (one team rating across our
builds, K=32) plus per-build records with Wilson intervals. Our own
snapshots and archetypes (`src/g_iter*`, `src/arch_*`) stay unrestricted:
chosen maps, both sides, paired cells.

What this changes in the loop:

- **The roster check, the tiering scan and the paired ladder are gone.**
  No chosen cells against external bots, so no McNemar; a candidate's
  external evidence is an unpaired block of random scrimmages, and 48
  games resolve only a ~25-point difference (Wilson width ±14 points at
  40%). External play is the *standing*, not the accept instrument.
- **Accept instrument = the panel head-to-head**, paired and unrestricted:
  the incumbent on the quick set both sides (24 games) plus the three
  archetypes (muckraker rush, bidder, politician rush) on the screen set
  both sides (24 games), against the incumbent's own record on the same
  48 cells. Gate +5 net on the 48 (McNemar as before), early stop when
  the gate is unreachable. The archetypes exist to punish what the mirror
  cannot see (a rush, a bank race, a conversion race); add an archetype
  whenever a scrimmage loss census names a mechanism the panel lacks.
- **Blocks are for submissions only** (2026-09-17): a scrimmage block plays
  the incumbent or a build just accepted, never a candidate under trial. On
  the real ladder you submit what you believe in, and rating a build you
  will not keep pollutes the team rating. A candidate's external evidence,
  if wanted, is read from the panel plus the census, not from the ladder.
- **Scrimmage block as the regression check**: an accepted build plays a
  48-game block against the band (`tools/roster.txt`, chosen from the
  scrimmage standings: the bots between 20% and 50% of games won against
  us over the last 200 games, re-drawn after every accept). The block is
  a veto only: if the new build's Wilson upper bound falls below the
  incumbent's point estimate over its last 96 games, the accept is
  reverted and logged.
- **The Elo ladder (user, PROMPTS 26 and 27).** `progress/ELO.md` and
  `elo.png` are the constantly updated ranking of every benchmark bot
  (one per repo, `tools/ladder-bots.txt`) plus our team **us**, K=32 from
  1500, rebuilt by `tools/elo.py` from `progress/games.csv` after every
  recorded block. **Only our games feed it**: external bots never play
  each other (a waste of the VM; the user accepts the less accurate
  ranking). A bot we have not met is unrated and listed apart; each
  bot's rating moves only through its games against us. **Challenges
  target the bots just above us**: `scrim.sh` draws its pool from
  `elo.py --pool 6 --explore 2` (the six rated bots immediately above our
  rating, filled from just below when fewer exist, plus the two bots we
  know least), so the climb is measured against the next rungs while
  the list fills in. A candidate under trial plays as `us:<build>`, as a
  real submission would.
- **The loss census reads scrimmage replays** (losses are saved), so
  candidate selection is unchanged in kind and the census is the map of
  what the contest actually punishes.
- **Budget**: a candidate costs 48 paired games (~5 VM-hours at 7 jobs
  ≈ 45 min), an accept another 48 scrimmages; a block of 48 scrimmages
  for the incumbent every ~4 iterations keeps the band current.

### 4.5.3 The accept instrument is an SPRT mirror on random maps (2026-09-18)

Eight candidates in a row were rejected by the 48-cell panel, and the
pattern was the instrument, not the bot. 24 mirror cells resolve only an
edge above roughly 75%; a real 60% improvement needs about 190 games, and
the archetype half is capped at +4 because the incumbent already wins
20 of its 24. The panel could not have accepted anything we were able to
build.

**New gate**: `tools/mirror.sh` plays the candidate against the incumbent
on a **random map and random side per game** (our own builds, so the
contest rule does not apply; random maps because the ladder is played on
the whole corpus, not on twelve chosen maps), in batches of 16, and after
each batch runs `tools/sprt.py`: a sequential probability ratio test of
H0 "no better than the incumbent" (p=0.50) against H1 "worth a snapshot"
(p=0.58, about 56 Elo), alpha = beta = 0.05. It stops at ACCEPT or
REJECT, typically inside 80-200 games, and spends games only while they
are still deciding something. A candidate that is merely neutral is
rejected at a cost close to the old panel's; a real improvement is now
detectable, which it was not before.

**Provisional changes and the stack (2026-09-18).** Iteration 22 ended
111-97 (53.4%) over 208 games: inconclusive, and that is the common case,
because separating a true 55% from 50% needs roughly 800 games (~11
VM-hours) and the SPRT's two hypotheses straddle it. Discarding every
such result would mean never improving at this budget, and keeping each
one on faith would accumulate noise. The policy is therefore:

- **inconclusive and >= 53% over >= 200 games** -> keep it *provisionally*
  (`src/bot` grows; no snapshot, no submission, ladder untouched);
- **inconclusive and < 53%**, or REJECT -> revert it;
- each further candidate is built **on top of the provisional stack** and
  its SPRT is run against the **incumbent**, not against the stack. As the
  stack grows the edge grows: three true 55% changes are about 65%
  together, which the same test resolves in 60-100 games;
- the stack is snapshotted as `g_iterN` only when it reaches **ACCEPT**
  against the incumbent, and only then does it play a scrimmage block and
  move the team rating. A stack that reaches REJECT loses its most recent
  member, which is the change that broke it.

The archetypes stay as a **regression check on an accept** (they catch a
rush or bidding vulnerability the incumbent itself does not punish), and
the scrimmage block stays as the ladder standing for a new submission.

### 4.6 Decide

- **Accept** when the head-to-head clears `AcceptMargin`, peers stay above
  `PeerFloor`, and the diff shape shows no unexplained one-directional
  regression. If the number holds but the pre-registered mechanism story fails,
  accept the number and log the attribution as OPEN; do not back-fill a story.
- **Near miss**: refine the same mechanism, up to 3 times, with a different dose
  or a narrower trigger. Not a new mechanism.
- **One change per candidate is the default, not a limit** (user, 2026-09-17:
  "authorized to make whatever number and degree of changes to the bot").
  When the census points at a structural change that single mechanisms
  cannot test (Iterations 16-20: capturing neutrals and holding them each
  failed alone), pre-register the combined candidate with counters for
  each half, so a reject still says which half failed.
- **Reject** otherwise. Trace the flipped games; a specific understood failure
  mode earns one targeted refinement, else revert fully. A rejection that turns
  a belief into a measurement paid for its run: record what it closed.

### 4.7 Post-accept routine (one commit, every time)

1. Snapshot to `src/g_iterN/`; the head-to-head run becomes the new baseline.
2. Re-tier every opponent; note any unlock.
3. Play the fixed roster; append to `progress/vs_roster_history.csv`.
4. Regenerate every chart in `progress/`.
5. Archive one informative replay to `replays/iterNN_<opponent>_<map>_<side>`.
6. Update the functional-area map and the ledger in `TRAINING_LOG.md`.
7. Commit the explicit paths (never `git add -A`) and push.

## 5. Incremental tweaks and big swings

Two tracks run interleaved and are equally legitimate.

- **Incremental**: one parameter or one narrow mechanism, from a traced loss.
  Cheap, high rejection rate, and the source of most calibration knowledge.
- **Structural**: a new mechanic, a re-architected subsystem, a different
  opening, or a **from-scratch rewrite** on the same infrastructure. Named from
  a capability gap rather than one game. Verified with the same rigour.

Schedule the structural track deliberately: at least one structural attempt in
every `SwingEvery` attempts, immediately after `MaxRejectsPerArea` fires, and
whenever the fixed roster has been flat for five accepts. A rejected structural
attempt is an ordinary outcome. A rewrite is warranted when the strategy has
stopped moving and the code carries more closed directions than live ones; it
is cheaper than it looks because the infrastructure and the ledger survive it.

## 6. Records

- **`TRAINING_LOG.md`** (append-only): one entry per attempt with target, trace,
  pre-registration, stage-0 result, evaluation numbers, decision, and what was
  learned. A fresh session must be able to resume from the log alone, so an
  in-flight run is described by run id and gate, and unfinished pre-checks are
  named explicitly.
- **Closed-directions ledger** inside the log: each closed avenue with the
  measurement that closed it, its kind (refuted / priced below the gate /
  blocked on a prerequisite / engine-impossible) and a checkable re-open
  condition. Grep it by name before opening anything.
- **`LEARNINGS.md`**: durable lessons by theme, each naming the measurement
  behind it. Run a consistency pass over it every ten accepts, not only appends.
- **`BENCHMARK.md`**: the external bots, their tier history, and the unlock log.
- **`progress/`** charts, regenerated on every accept:
  - `cumulative_iterations.png` -- accepted iterations over time (read the slope);
  - `vs_roster.png` -- win % against every fixed-roster opponent over time, with
    the roster average; frozen opponents, so a rising line is real progress;
  - `ladder.png` -- win % against each external bot over time with the 20% and
    50% tier lines drawn, the scrimmage-standings proxy.
- **`replays/`**: one archived game per logged iteration.
- **`PROMPTS.md`**: every user prompt, verbatim, append-only.

## 7. When the loop stalls

In order, and do not skip to the last one:

1. **Ablate what you already carry.** Gate each accepted feature off in turn and
   measure it on the head-to-head. Features accepted on thin margins or without
   measurement are often worth nothing or less; failure-mode preventers are
   often worth the most. When the roster drops after a run of individually
   positive accepts, ablate pairwise, and let the lineage's ancestry nominate
   the pair.
2. **Sweep the API** for methods the bot never calls.
3. **Re-read the allowed games** on the ladder: every unlocked opponent's losses
   contain what you were not looking for last time.
4. **Re-read the cross-year research** (`RESEARCH.md`: prior-year post-mortems).
   The perennial levers: symmetry inference, emergent rather than commanded
   coordination, hybrid bug navigation, micro over macro, rush/turtle
   map-adaptivity, basics done well over elaborate plans.
5. **Structural attempt** or **rewrite** (section 5).

Watch for the regularities that fill ledgers: metrics that move without
converting to wins; survival bought with inactivity; and the winner's usual
profile of capability preserved at zero marginal cost -- standing defences,
spending idle resources, removing pure waste.
