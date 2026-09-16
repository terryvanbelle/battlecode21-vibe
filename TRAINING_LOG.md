# Training log

Append-only chronological record of every attempt. Format per entry: target,
trace, pre-registration, stage-0, evaluation, decision, learned. Functional-area
map and closed-directions ledger are kept at the bottom and updated in place.

Dates are UTC. Runs are `gauntlet/<run-id>/` (git-ignored); the numbers that
matter are copied here.

---

## Phase 0 -- foundation (2026-09-16)

Read in full before any code: battlecode22-vibe (RESEARCH, LEARNINGS,
TRAINING_ALGORITHM, ART_OF_WAR, tools), battlecode26-vibe (RESEARCH,
TRAINING_ALGORITHM, BENCHMARK, tools), battlecode25-vibe (TRAINING_ALGORITHM,
TRAINING_CASES, METHODS, MULTI_AGENT, OBJECTIVE, Darla's DESIGN, tools),
anicolao/bcenv (all docs). Distilled into `TRAINING_ALGORITHM.md` (own
formulation) and `RESEARCH.md` (2021 sources excluded; disclosure recorded
there about second-hand 2021 paragraphs present in the mandatory reading).

**Environment.** Local 2-vCPU / 2 GB box, JDK 8 at `~/jdk/jdk8u504-b01`. The
official 2021 engine artefacts are gone (access token URL dead; GitHub Packages
needs a scope we lack), so the engine is built from the public source with
`tools/build-engine.sh` (patched: jcenter -> mavenCentral, `jsi` RTree compiled
from `aled/jsi`). Engine commit `ed39c1a`, spec 2021.3.0.5, 76 built-in maps
(`Cow` excluded from evaluation as the organisers did).

**Engine facts verified from source** are in `RULES.md` with `[E]` tags. Two
that shape the design: cooldown is charged on the passability of the tile a
robot *leaves* (not the one it enters), and `senseNearbyRobots` returns robots
in row-major scan order (a play-symmetry hazard).

**Tooling built and tested.** `tools/lib.sh` (bare-java runner),
`tools/run-match.sh`, `tools/gauntlet.sh` (parallel, both sides, silences the
opponent's stdout), `tools/snapshot.sh`, `tools/compare.py` (game-by-game diff
with identity count and sweep counts), `tools/replay-dump.sh` +
`tools/replaydump/ReplayDump.java` (aggregates, event window, robot track,
ASCII board, `--logs`, `--metrics` CSV, engine-side bytecode overrun check,
map symmetry detection from passability), `tools/bench-compile.sh` (compiles
external bots without displaying any source), `tools/plot_progress.py`,
`tools/track_history.py`, `tools/plot_history.py`.

**Sandbox lesson (cost: one failed run).** A `Debug` class that called
`e.getClass()` / `e.getStackTrace()` was rejected by the instrumenter with the
unhelpful "Team is known to have errors", and because the first failure was
thrown inside `turn()` and caught by the loop's own `catch`, the real message
was never printed. Keep `Debug` trivial; if a bot dies at spawn with that
message, suspect reflection-flavoured calls first.

**Benchmark bots.** 96 GitHub repositories matched the 2021 season (searched by
"battlecode 2021", "battlecode21", "bc21", and the season's unit names);
cloned outside the repo under `~/projects/vibe/bc21-benchmarks/`. Their source
is never read; packages are identified by directory names and compiled by
`tools/bench-compile.sh`. Results in `BENCHMARK.md`.

## Iteration 0 -- minimal bot (2026-09-16)

`src/bot/`: EC builds one 1-influence muckraker on its most passable adjacent
tile and then idles; the muckraker wanders with a persistent heading, turning
90 degrees when blocked; no bids. Bytecode monitor (`@bc` lines every 50 turns:
used, max, near-misses, confirmed overruns) and `@exc`/`@spawn` logging wired
in. Snapshot `src/g_iter0/`.

- vs `examplefuncsplayer` on `maptestsmall` as A: loss at round 1500 on the
  unit-influence tiebreaker (expected: the example bot builds ~50 units, we
  build one). Bytecode: EC max 460 / 20000, muckraker max 127 / 15000, zero
  overruns.
- Determinism and speed check: see the next entry.
