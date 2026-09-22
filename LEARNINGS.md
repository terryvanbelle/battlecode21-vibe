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
- **A centre acts every other round** (`ENLIGHTENMENT_CENTER` action cooldown 2): at most ~750
  builds a game per centre, so a 1-influence muckraker costs a build slot, not influence, and a
  swarm of 130 by r200 is a third of two centres' slots. Politicians cooldown 1, muckrakers 1.5,
  slanderers 2.
- **A centre can read ANY friendly robot's flag by id** (`assertCanGetFlag` exempts ECs), and
  robots act in id order, so a centre reads what a child set last round *before* that child acts
  this round. That is the whole basis of the hand-off: a capturer announces its flip the turn it
  lands adjacent and home hears it before the speech that kills the messenger.
- **A robot over its bytecode budget loses whole rounds, silently.** The turn spills into the
  next round(s); `doBid` and `build` simply do not run. Iteration 49's extra reads put the home
  centre at 20k and it lost 213-404 rounds a game while the incumbent lost 0-19 -- fixes worth
  +10% gated at 53% until profiled (`@bcprof`) and cut. Read `@bc ... over=` for every centre in
  every logged game.
- **Slanderer income per round is `I x (1/50 + 0.03 e^(-0.001 I))`** for 50 rounds: a 41 returns
  2.4x, a 463 returns 1.9x, a 1000 returns 1.55x. **The expose buff is `1 + 0.001 x` the influence
  of enemy slanderers exposed in the last 50 rounds**, on every politician of the exposing team; it
  reached 6.3x in one loss and drained a 3,500-influence centre in nineteen speeches.
- **A speech is split equally over every unit in its radius, EC included** -- friendly units
  too -- so bodies around a centre dilute an attacker, and 1-influence muckrakers around a target
  dilute a capturer. **A converted centre's influence is the overshoot** of the flipping share,
  and **conversion restarts the player code** (a fresh instance for the new team), so every
  counter in a captured or retaken centre starts at zero.
- **The team's bid is its single highest centre bid** (`GameWorld`: max per team; the losing team
  pays half). One silent centre is harmless; all silent is the vote race lost.
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
- **Knowledge must carry a timestamp.** A centre that changed hands ping-ponged between stale
  echoes for the rest of the game (520 politicians sent at a centre already ours); refusing
  echoes instead threw away the sightings a sibling's scouts relayed (home knew no enemy centre
  for 1,200 rounds). Every ownership claim now carries the round of the sighting behind it and
  the newer claim wins whoever relays it (Iteration 49, `MapState.claimEnemy/claimOwn`).
- **A mechanism that paid when knowledge was poor can stall once knowledge is good.** Save mode
  (Iteration 22) bought an early centre when a centre rarely knew a neutral before r100; with
  the repaired channel a centre knew one by r50 and froze at two slanderers for 20-187 rounds to
  buy it, while the chain without it built 1,072 influence of slanderers and captured the same
  centre nine rounds later from income. Off since Iteration 51.
- **A fresh capture is an empty shell.** In eight losses we gained 72 centres and lost 60, 34 of
  them within 100 rounds: a centre priced at neutral+14 is born with ~4 and spends it on its first
  slanderer. Neither a 60-point floor nor four diagonal sentinels (3x dilution) held them against
  awesomelemonade, whose attackers are **sized to the target** (130-500 conviction, overshoots
  of 9-23). All twelve losses to that bot in three blocks are annihilations.
- **A young centre that stops bidding loses the vote race for the team.** `enemyVotesEst` (the
  "safe without bidding" bound) started at zero in every centre's fresh instance; seven young
  centres sat on 136k of influence at 663-390 ahead and placed no bid for 300 rounds. Initialised
  at birth to `round - teamVotes` since Iteration 50.
- **The mirror and the ladder disagree by design.** Repairs to knowledge, bidding and stalls gated
  at 53-55% against a twin that shares every other line, then took the ladder from 38/48 to 40/48
  and the Elo from 1752 to 1798; a board-strength candidate (sentinels) beat the fixed-seed
  baseline 5 centres to 1 and still lost that mirror on votes to a hoarder.
- **Coverage is the earliest honest predictor of the result** (onset r150,
  rising to +0.58), ahead of centres (r400) and centre influence (r350). But
  scouts sweeping more **did not** win games (51.7% over 240): coverage marks a
  winning position without being a lever on its own.

## How the loop itself should be run

That has its own document: `METHOD.md`, written to be portable to a future
Battlecode year. It covers the SPRT gate, why a diagnostic game must precede
every test, what self-play can and cannot see, the correlation-and-onset
hypothesis generator, and the instruments that turned out to be broken.
