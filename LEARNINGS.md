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

## Strategy (this season) -- early observations, not yet measured against strong opponents

- Against a non-bidding opponent a bid of 1 wins every vote; the game is then
  decided by round 752. Against bidders the bid must adapt; the v1 rule
  (grow by a third on a loss, shrink slowly on a win, cap at influence/6) is a
  placeholder awaiting measurement.
- Slanderers pay back ~2.3x in 50 rounds at small sizes and turn into
  politicians at 300 rounds, so a steady slanderer stream is both the economy
  and a free army. (Arithmetic from the spec; the army half is not yet
  measured in combat.)
- Scouting with raw compass headings through swamp took 150 rounds to cross a
  32-tile map (traced scout: 10 rounds stuck on a 0.1 tile, 2.75 rounds per
  step average). Waypoints reached via passability-aware navigation are the
  fix being measured.
