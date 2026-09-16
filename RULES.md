# Battlecode 2021 "Campaign" -- rules digest

Working digest of the official spec (`specs/specs.md.html`, version 2021.3.0.5,
the final release) cross-checked against the engine source in the public
`battlecode/battlecode21` repository. Facts marked **[E]** were read directly
from engine source (file named); everything else is from the spec. When the two
disagree, the engine wins.

## Map and game

- Grid of 32x32 to 64x64. Origin offset is random in [10000, 30000] on each axis
  (so never hardcode coordinates; work relative to observed positions).
- Every tile has a **passability** in [0.1, 1.0]. An action's cooldown is
  `base cooldown / passability(tile the robot stands on)` **[E]** `InternalRobot.addCooldownTurns`.
  Cooldown decreases by 1 each round; a robot may act when cooldown < 1.
- Maps are symmetric by a rotation or a reflection (not exposed via the API;
  must be inferred).
- **1500 rounds** (`GameConstants.GAME_MAX_NUMBER_OF_ROUNDS`; the map file can
  carry its own round count, `LiveMap.rounds`). Robots act in **spawn order**
  each round **[E]** `ObjectInfo.eachDynamicBodyByExecOrder` -- initial ECs
  first, then units in creation order.
- Each team starts with 1-3 Enlightenment Centers (ECs) holding 150 influence.
  Up to 6 **neutral ECs** with 50-500 influence may exist.
- 76 built-in maps live inside the engine jar (`battlecode/world/resources/*.map21`).
  `Cow` was excluded from official scrimmages for size.

## Units

| | EC | Politician | Slanderer | Muckraker |
|---|---|---|---|---|
| conviction ratio | 1 | 1.0 | 1.0 | 0.7 (ceil) |
| initial cooldown | 0 | 10 | 0 | 10 |
| base action cooldown | 2.0 | 1.0 | 2.0 | 1.5 |
| action radius^2 | 2 | 9 | 0 | 12 |
| sensor radius^2 | 40 | 25 | 20 | 30 |
| detection radius^2 | 40 | 25 | 20 | 40 |
| true sense | yes | no | no | yes |
| bytecode limit | 20000 | 15000 | 7500 | 15000 |

- Units are built by an EC on an adjacent free tile for any influence `C >= 1`
  the EC holds; the EC pays `C` and takes its own 2.0/passability cooldown
  **[E]** `RobotControllerImpl.buildRobot`. Conviction = ceil(ratio * C).
- Movement: to an adjacent unoccupied on-map tile when ready; costs one action
  cooldown. Only units move.
- **Politicians and slanderers see slanderers as politicians** (no true sense).
  ECs and muckrakers see the truth. Muckrakers *detect* (location only) out to
  r^2 40 but *sense* details only to r^2 30.
- Influence cap 10^8 per robot.

### Politician: Empower (self-destruct speech)

**[E]** `InternalRobot.empower`:
- `empower(r2)` for any `r2 <= 9`. All robots (own, enemy, neutral) within r2 are
  affected, excluding self. Let `n` = their count; if `n == 0` the speech is
  wasted (the politician still dies).
- Conviction to give = `conviction - 10` (EMPOWER_TAX). If `<= 0` nothing
  happens (politician still dies).
- Each target receives `floor(conv/n * buff)` where `buff = 1 + 0.001 * (sum of
  influence of slanderers our muckrakers exposed in the last 50 rounds)`.
  Exceptions: friendly ECs get no buff; enemy ECs get buff only on the part that
  brings them to 0, the surplus beyond conversion is unbuffed.
- Friendly units gain conviction up to their **initial** conviction cap; friendly
  ECs gain influence and conviction (uncapped). Enemy/neutral robots lose
  conviction. When it goes negative: politicians and ECs **convert** to the
  speaker's team with conviction = |new value| (units capped at their initial
  conviction; a converted EC's influence and conviction are set to the
  overflow); slanderers and muckrakers **die**.
- A converted robot gets a new ID and a fresh copy of your code; its flag resets.
- The politician is destroyed after speaking, whatever happened.

### Slanderer: Embezzle and Camouflage

**[E]** `RobotType.getPassiveInfluence`, `InternalRobot.processEndOfRound`:
- For its first 50 rounds alive (`roundsAlive <= 50`), a slanderer with
  influence `x` pays its **parent EC** `floor(x * (1/50 + 0.03 * e^(-0.001 x)))`
  per round, as long as the slanderer is alive and the parent EC still exists
  and is on the slanderer's team. Total over 50 rounds is roughly
  `x * (1 + 1.5 e^(-0.001x))` -- i.e. about 2.5x cost at small x, 2.0x at x=400,
  ~1.55x at x=1000. Small slanderers give the best ratio; large ones give the
  most absolute influence per EC action.
- At `roundsAlive == 300` the slanderer becomes a politician of equal conviction.
- Slanderers have no active ability; action radius 0. They move on a 2.0 cooldown.

### Muckraker: Expose

**[E]** `InternalRobot.expose`, `TeamInfo`:
- `expose(loc|id)` on an enemy slanderer within r^2 12 destroys it and adds its
  influence to the team buff pool for the next 50 rounds (buff factor
  `1 + 0.001 * pool`). `rc.getEmpowerFactor(team, roundsInFuture)` reads it.
- Muckraker conviction is only 0.7 of cost, so it dies to smaller speeches; but
  its detection radius (40) is the largest of any unit.

### Enlightenment Center

- Cannot be built. Passive income `ceil(0.2 * sqrt(round))` per round
  (1 at round 1, 3 at round 100, 8 at round 1500) **[E]**.
- **Bid** (does not use the action cooldown): `bid(x)` sets this round's bid;
  the influence is held back immediately (`setBid` deducts, `resetBid` refunds
  before settlement) **[E]** `InternalRobot.setBid`. Each team's highest bid
  (ties within a team broken by youngest robot, then lowest ID) competes:
  - strictly highest bid > 0 wins one vote and pays its full bid;
  - the losing team's highest bidder pays `ceil(bid/2)`;
  - equal highest bids: nobody wins, both pay half.
  A bid of 0 is free.
- Neutral ECs bid 0 and generate nothing until captured. They can only be
  captured by empowering them below 0.

## Victory

**[E]** `GameWorld.processEndOfRound`:
1. A team with **zero robots** loses immediately (ANNIHILATED). Note that a
   captured EC counts as a robot, and ECs cannot die -- only convert -- so
   annihilation requires taking every EC and killing every unit.
2. Otherwise at the round limit: more **votes**; then more **ECs**; then higher
   **total unit influence** (sum over all owned robots including ECs); then a
   coin flip (`Math.random()`, the one non-deterministic thing in the engine).

## Communication

- Every robot has a 24-bit **flag** (`setFlag`, 100 bytecodes; `getFlag`, 5).
- Visibility **[E]** `assertCanGetFlag`: an EC can read *any* robot's flag on
  the map; any robot can read *any* EC's flag; otherwise a robot must be within
  its own sensor radius of the target. Enemies can read your flags too.
- No shared array. Static fields are per-robot. Each robot gets its own copy of
  the code.

## Bytecode and sandbox

- Limits as in the unit table. Overrun pauses the robot mid-instruction and it
  resumes next round -- no exception. `Clock.getBytecodeNum()` /
  `Clock.getBytecodesLeft()` are free.
- Costs (from `MethodCosts.txt`): `senseNearbyRobots` 100, `detectNearbyRobots`
  100, `setFlag` 100, `senseRobot*` 25, `isLocationOccupied` 20,
  `getRobotCount` 20, `can*` 5-10, `getFlag` 5, `sensePassability` 1,
  `MapLocation` methods 1-2, `move/empower/expose/buildRobot` 0 (they end your
  useful turn anyway via cooldown). Exceptions cost 500. Array allocation costs
  its length.
- `senseNearbyRobots` returns robots in the engine's **row-major scan order over
  the bounding box (x outer loop, y inner)** **[E]**
  `GameWorld.getAllLocationsWithinRadiusSquared` -- an absolute order that is a
  play-symmetry hazard if a bot takes "the first match".
- `java.util` is bytecode-counted as your own code. `Math.random` likewise.
  Heap limit 8 MB.

## Strategic corollaries (derived, not rules)

- Votes are the primary win condition; ECs and influence are only tiebreakers.
  But votes cost influence, influence comes from ECs and slanderers, and units
  cost influence -- so the game is an economy race with a bidding sub-game.
- Slanderers are the only income multiplier and they are fragile (any enemy
  politician speech or muckraker expose kills them), so protecting slanderers
  while hunting the enemy's is the core tactical loop. Muckrakers can also
  simply stand adjacent to an enemy EC tile to block its spawns ("bury").
- Politician conviction is spent, not regenerated: value per speech falls with
  the number of targets, so isolating one target maximises conversion.
- Neutral ECs are large influence swings and extra spawn/bid points.
