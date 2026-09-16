# DESIGN.md -- bot architecture

The bot lives in `src/bot/` (Java 8, package `bot`). Frozen accepted iterations
are copied to `src/g_iterN/` by `tools/snapshot.sh`. This file is the standing
description of how the code is organised and why; strategy findings go to
`TRAINING_LOG.md` / `LEARNINGS.md`, rules to `RULES.md`.

## Constraints that shape the code

- **Bytecode budgets** (EC 20k, politician 15k, muckraker 15k, slanderer 7.5k)
  are hard; `senseNearbyRobots` costs 100, `setFlag` 100, exceptions 500.
  `java.util` collections are counted as our own code, so hot paths use arrays
  and unrolled loops, never `ArrayList`/`HashMap`.
- **Static state is per robot.** Each robot gets its own copy of every class, so
  static fields are free per-robot memory. No shared array exists; the only
  channel is the 24-bit flag, readable by ECs from anywhere and readable on
  every EC by everyone.
- **Coordinates are offset randomly** (10000-30000). Nothing absolute may be
  assumed; the map bounds are discovered, not known.
- **Determinism.** No `Math.random`; a per-robot LCG seeded from `rc.getID()`
  supplies any needed randomness, so mirror matches are reproducible.
- **Play symmetry.** Every tie-break among directions or targets is relative to
  the robot's own geometry (toward a target, toward map centre, by score) never
  a fixed compass order or "first sensed". `senseNearbyRobots` order is
  row-major; consumers must pick the best, not the first.

## Layout

| file | role |
|---|---|
| `RobotPlayer.java` | entry point: builds the right `Robot` subclass and runs its loop |
| `Robot.java` | base class: the turn loop, bytecode monitor, shared sensing cache, RNG, debug log |
| `EC.java` | Enlightenment Center: production, bidding, comms hub |
| `Politician.java` | combat/conversion unit |
| `Slanderer.java` | economy unit: hide, flee, return |
| `Muckraker.java` | scout, slanderer hunter, spawn blocker |
| `Nav.java` | movement: passability-aware greedy step with bug fallback, oscillation guard |
| `Comms.java` | flag encode/decode: message types, `mod 128` coordinates |
| `MapState.java` | discovered bounds, symmetry hypotheses, known ECs (own, enemy, neutral) |
| `Econ.java` | slanderer size table and build-order arithmetic |
| `Debug.java` | `@tag k=v` log lines for the replay dumper, gated by a compile-time flag |
| `C.java` | tunable constants, one place |

## The turn loop (`Robot.run`)

```
while (true) {
  round0 = rc.getRoundNum();
  try { turn(); } catch (Exception e) { Debug.exception(e); }
  bytecode monitor: if rc.getRoundNum() != round0 -> overrun counter; else record Clock.getBytecodeNum()
  Clock.yield();
}
```

Overrun and near-miss counters are printed once per robot every 50 rounds as
`@bc type=... used=... max=... over=...` so `tools/replay-dump.sh --logs '@bc'`
and the engine's own `bytecodesUsed` field can be cross-checked.

## Communication protocol (`Comms`)

24 bits per flag. Layout: `[type:4][payload:20]`. A location is
`(x & 127) << 7 | (y & 127)` (14 bits) and is decoded to the candidate nearest
the reader's own position, which is unambiguous on maps at most 64 wide.
Message types (initial set; extend in `Comms`):

| type | payload | writer -> reader |
|---|---|---|
| 0 IDLE | -- | anyone |
| 1 ENEMY_EC | loc, influence bucket (6 bits) | scout -> EC -> everyone |
| 2 NEUTRAL_EC | loc, influence bucket | scout -> EC -> everyone |
| 3 MAP_EDGE | which edge (2 bits), coordinate (7 bits) | scout -> EC |
| 4 ENEMY_UNIT | loc, type (2 bits) | any unit -> EC -> defenders |
| 5 ORDER | target loc, role (3 bits) | EC -> its units |
| 6 STATUS | EC's own influence bucket, symmetry known (2 bits) | EC -> its units |

An EC reads the flags of the units it built (it knows their IDs) each round,
within a bytecode budget, and re-broadcasts the most useful fact on its own
flag, which every unit can read for 5 bytecodes.

**Timing rule.** A robot built in round N takes its first turn in round N+1
(the engine iterates a snapshot of the spawn order), so an ORDER for a
newborn is held on the EC flag for rounds N and N+1. The EC cannot build twice
in that window (its cooldown is at least 2), so orders never collide. Scouts
cycle their durable facts (known edges, ECs) on odd rounds and their fresh
sighting on even rounds, so a fact found once is still delivered.

## Map knowledge (`MapState`)

Each robot keeps: known bounds (min/max x/y, `-1` when unknown), its home EC
location, the list of known enemy and neutral ECs, and a 3-bit symmetry
hypothesis set {rotation, mirror-x, mirror-y}. A hypothesis is eliminated when
a sensed tile's passability contradicts the remembered passability of its
image, or when a known EC's image is observed to be empty. Once one hypothesis
remains, the enemy EC positions are the images of our own.

## Navigation (`Nav`)

Cooldown is charged by the passability of the tile the robot **stands on when it
acts** (engine: `addCooldownTurns` runs before the location changes), so a path's
cost is the sum of `1/passability` over the tiles it departs from. The first
navigation is greedy: among the up-to-8 legal steps, pick the one minimising
`distance_after_step + k / passability(destination)`; when the greedy step is
blocked or worse than the current position for several turns, fall back to
bug-style wall following with a fixed turning direction chosen by a per-robot
RNG. An oscillation guard remembers the last few positions and forbids
returning to them. A local unrolled search over the sensed area is the planned
upgrade once the greedy step's failure modes are measured.

## Roles

- **EC**: every round decides between bidding, building a slanderer (size from
  `Econ`), building a politician (defence or capture), or a muckraker (scout or
  hunt), then reads unit flags and updates its broadcast.
- **Slanderer**: stays within a safe radius of home on the side away from known
  enemies, flees any detected unit that is not a friendly, returns when safe.
- **Politician**: guards slanderers (kills muckrakers within action range),
  picks the empower radius that maximises converted value per conviction
  spent, and, when large enough, captures neutral or enemy ECs.
- **Muckraker**: spreads out (repulsion from other muckrakers), reports ECs and
  edges, exposes slanderers, and blocks enemy EC spawn tiles when nothing better
  is available.

## Instrumentation conventions

- `Debug.log(tag, ...)` prints `@tag k=v ...` once per call; tags in use are
  listed at the top of `Debug.java`. The gauntlet silences the opponent's
  stdout, so our lines are the only logs in a replay.
- Every decision the loop may later need to count is logged at the decision
  point (what was chosen and why), not only its outcome.
