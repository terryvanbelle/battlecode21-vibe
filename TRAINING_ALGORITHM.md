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
  matters against ourselves (economy, bidding) is resolved by the
  head-to-head first. A doctrine or map-knowledge change is invisible to a
  twin that does not punish the deficiency (2026-09-17: relay, army and
  scouts all early-stopped at 6/14, the noise floor); for those, run the
  roster check first and the head-to-head only as a regression check on an
  accept.
- **Roster gauntlet for a candidate** uses the 4-map screen set both sides
  (72 games) against the baseline's record on the same cells; the full quick
  set (216 games) is played only for an accepted build, as the next baseline.
- **The VM holds 7 games at once.** A roster baseline runs at 4 jobs and leaves
  3 for one development run; nothing else starts until a slot frees.

### 4.6 Decide

- **Accept** when the head-to-head clears `AcceptMargin`, peers stay above
  `PeerFloor`, and the diff shape shows no unexplained one-directional
  regression. If the number holds but the pre-registered mechanism story fails,
  accept the number and log the attribution as OPEN; do not back-fill a story.
- **Near miss**: refine the same mechanism, up to 3 times, with a different dose
  or a narrower trigger. Not a new mechanism.
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
