# LEARNINGS.md

Durable lessons by theme, each naming the measurement or observation behind
it. `TRAINING_LOG.md` is the chronological record; this is what to tell a fresh
session. A lesson without a measurement is a belief -- mark it as such.

## Engine and sandbox (Battlecode 2021)

- **The instrumenter rejects reflection-flavoured calls and reports only "Team
  is known to have errors".** `Object.getClass()` and `Throwable.getStackTrace()`
  in a `Debug` class killed every robot at spawn, and because the first failure
  happened inside `turn()` and was caught by the loop's own `catch`, the real
  message never printed. Keep `Debug` trivial. (Phase 0, one failed run.)
- **Cooldown is charged on the tile you leave, not the one you enter**
  (`InternalRobot.addCooldownTurns` runs before `setLocation` in `move`). A
  path's cost is the sum of `1/passability` over departed tiles; standing on a
  0.1 tile makes every subsequent action cost 10x. Read from engine source.
- **`senseNearbyRobots` returns row-major scan order** (x outer, y inner, over
  the bounding box). Taking the first match encodes an absolute-position bias.
  Read from `GameWorld.getAllLocationsWithinRadiusSquared`.
- **Robots act in spawn order**; initial ECs act first every round. Flags set
  this turn are visible to everyone who acts later in the same round.
- **A robot built this round takes its first turn NEXT round**: the engine
  iterates a snapshot of the execution order taken at the start of the round
  (`ObjectInfo.eachDynamicBodyByExecOrder`). A one-round "order" flag set by
  the EC at build time is therefore gone before the newborn can read it: every
  v1 capture politician silently ran as a guard for two smoke tests (traced:
  a 221-influence capturer idling 400 rounds by home). Orders must persist
  through the following round.
- **The only non-determinism is the final coin flip** (`Math.random` in
  `GameWorld.setWinnerArbitrary`) when votes, EC count and influence all tie.
  Two identical Iteration-0 mirror games differed only there. A bot that bids
  once never reaches it.
- **Slanderer breakpoints**: with the engine's float constants the smallest
  influences yielding k/round are 21, 41, 63, 85, 107, 130, 154, 178, 203, 228,
  255, 282, 310, 339, 368, 399, 431, 463, 497, 532 ... (`Econ.BREAK`). Buying
  anything between two breakpoints wastes the difference.
- **A slanderer that camouflages into a politician keeps its controller**: the
  engine changes `type` in place and no new `RobotPlayer.run` is started, so
  the slanderer code must notice `rc.getType() == POLITICIAN` and hand over.
  (Observed: `@camo` lines from our own slanderer controller.)
- **Bytecode headroom is real but not free**: v1's politician speech
  evaluation (6 radii x all sensed robots) hit 14.2k of 15k on a crowded map;
  caching distances once brought it down. Measure with `--bytecode` on every run.

## Infrastructure

- **Game speed is dominated by the engine's per-robot thread hand-off**
  (`SandboxedRobotPlayer` runs each robot on its own thread with
  wait/notify), so a 1500-round game with ~100 robots takes 2-5 minutes on
  two loaded cores; a two-unit game still takes ~17 s. Plan evaluations in
  hours, and keep short screens to the 12-map quick set.
- **A benchmark compiler must not read source, so it must be fully
  automatic**: source roots are the parents of directories holding
  `RobotPlayer.java`; a root must never escape the repo (one bot with
  `RobotPlayer.java` at the repo top level made the root the whole benchmarks
  directory and the compile ran for 15 minutes at the heap limit).
- **Map files carry no round limit**: `GameMapIO` deserialises with `GAME_MAX_NUMBER_OF_ROUNDS`, so a "short" map written with `rounds=400` still plays 1500 rounds (measured). Smoke tests cannot be shortened that way.
- **Never `pkill -f` a pattern that appears in your own command line.** It
  killed the shell issuing it (exit 144) once.

## Strategy (this season) -- measured

Each line names what measured it. Rates in brackets are SPRT mirror results
against the previous accepted build unless stated otherwise.

- **The opening deployment is worth more than any later reallocation.** We spent
  rounds 1-8 on four 1-influence scouts and deployed a 107-influence slanderer at
  r9; the opponents put their whole 150 into a 130-influence slanderer at r1.
  Fixing that was **+22 points (71.9%)** and it is the first change that carried
  to the ladder (29% -> 40%). Iteration 34.
- **Never let a rule stop the economy for something that cannot hurt it.**
  `danger` was any enemy inside the centre's sensor radius, so one 1-influence
  muckraker six tiles away halted slanderer production, and opponents build those
  in bulk. Distinguishing a real threat was **68.8%**. Iteration 27.
- **Find the constraint that actually binds before tuning anything else.** Three
  separate accepts were removals of a cap or a defect, not new mechanisms: the
  slanderer caps (**59.1%**), the spare branch's guard sink (**84.4%**, the
  largest yet), and the capturer cap with its two comms bugs (**79.2%**).
- **A surplus that becomes standing bodies is a surplus wasted.** The spare
  branch tested `guards < slanderers + 2` before the economy, so a rich centre
  bought a 242-influence guard instead of the 264-influence centre it could
  afford: 46 guards at r250 and four known neutral centres never taken.
  Iteration 35.
- **"It is obviously a bug" is not evidence that fixing it helps.** Broadcasting
  a captured centre so the team stops treating it as neutral fixed a measured
  defect -- 41 of 54 capture builds walked to ground we already held -- and lost
  **45.1%** on its own, because an aborted capturer becomes a guard and keeps its
  influence. It was accepted only as part of a change that used the freed
  capacity. Iterations 36 and 37.
- **Bidding can carry a game the board has lost.** In one diagnostic our bot was
  reduced to zero centres and three influence of units and still won on votes at
  r1500. The loser pays half of its bid, so a large losing bid is close to burned
  influence: an archetype spent 411,465 influence on bids for 643 votes against
  our 214,572 for 750.
- **Slanderers pay back ~2.3x in 50 rounds and become politicians at 300**, so a
  steady stream is both the economy and a free army. The army half is now
  measured: it is why removing wasteful capturers *hurt*.
- **Coverage is the earliest honest predictor of the result** (onset r150,
  rising to +0.58), ahead of centres (r400) and centre influence (r350). But
  scouts sweeping more **did not** win games (51.7% over 240): coverage marks a
  winning position without being a lever on its own.

## How the loop itself should be run

That has its own document: `METHOD.md`, written to be portable to a future
Battlecode year. It covers the SPRT gate, why a diagnostic game must precede
every test, what self-play can and cannot see, the correlation-and-onset
hypothesis generator, and the instruments that turned out to be broken.
