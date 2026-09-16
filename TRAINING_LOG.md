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

**Determinism and speed (Iteration 0 vs its own snapshot, `maptestsmall`).**
Two identical runs both reached round 1500 with nothing to separate the sides
(no votes, one EC each, equal influence) and the engine's coin flip
(`Math.random`) picked A once and B once. So: the *play* is deterministic and
the only randomness in the engine is the final tiebreak, which any bot that
bids even once will never reach. Consequence for the mirror harness: a mirror
that ends in coin flips is not measuring anything; the mirror needs the real
bot. A trivial 1500-round game with two units takes ~17 s wall-clock on this
box (engine overhead per round dominates), the example bot with ~100 units
~2 min with the compile job competing for the two cores. Full-corpus runs (150
games per opponent) are therefore hours, not minutes: use the 12-map quick set
for screens and reserve the full corpus for accept decisions.

## Iteration 1 (in development) -- the foundation bot (2026-09-16)

Written after Iteration 0 proved the pipeline. Not yet evaluated on the
corpus; this entry records the design and the smoke tests.

**Design** (`DESIGN.md`): EC produces 4 cheap scouts early, then breakpoint-
sized slanderers (`Econ.BREAK`) when no enemy is in sight, guard politicians
in proportion, capture politicians for known neutral ECs (cost = influence +
14, rounded up from the flag bucket) and, when rich, for the enemy EC; adaptive
bid (grow a third on a lost vote, shrink slowly on a won one, cap influence/6,
frozen once 752 votes are held). Slanderers hold a ring 2-4 tiles from home on
the far side from the enemy and flee anything hostile; when camouflage turns
them into politicians the same controller hands over to the guard logic.
Politicians pick the empower radius maximising kill/convert value per speech;
guards chase muckrakers near home. Muckrakers scout, report ECs/edges/enemy
units on their flag, expose slanderers, and sit next to the enemy EC. Flags:
`[type:4][extra:6][x%128:7][y%128:7]`, decoded relative to the reader.

**Smoke tests vs `examplefuncsplayer` on `maptestsmall`** (wins by votes,
752-0, every time; that opponent never bids):

- v1a: 24 slanderers by r250 (cap not enforced in one branch), 9000 influence
  hoarded by r500, enemy EC never found, symmetry never resolved, politician
  bytecode peak 14233/15000.
- v1b (cap enforced, distance cache in the speech evaluator, 4 scouts, more
  guards): bounds known r300, symmetry resolved to mirror-x (correct) and enemy
  EC located r~520; still no capture (enemy EC influence outgrew our reserve).
  Scout trace: heading-based wandering spent 10 rounds on a 0.1 tile and
  needed 150 rounds to cross the map -> replaced by passability-aware
  waypoints (`Muckraker.pickExplore`), under test.

**Instrument**: `tools/replay-dump.sh <replay> --logs '@econ|@scout' --logs-team A`
gives the EC's 50-round economy line and every scout goal; `--robot ID` tracks
one unit; `--bytecode` prints per-type peaks from the engine's own counters.

**Smoke tests, congestion and captures (2026-09-16, later).** Two further
findings from traced replays, both about bodies rather than logic:

- A 269-influence capture politician built at r105 on `Gridlock` never moved
  for 1400 rounds: boxed in on the spawn ring by 12 idle slanderers and 30
  idle guards (ring d^2 4-18 around a single EC holds ~40 tiles). Same for a
  freshly built scout on `maptestsmall`. Fixes: slanderer ring widened to
  d^2 8-45, guards hold outside it (d^2 20-80), any unit with more than two
  adjacent friends (or occasionally with one) steps to the emptiest
  neighbouring tile, capturers spawn on the target's side, guard cap cut
  from 30 to 4 + slanderers/2 (max 10). Fewer bodies is the real fix.
- With 30 guards the EC never held more than ~100 influence on `Gridlock`, so
  none of the six known neutral ECs (150-250 influence, all located by r250)
  was ever affordable to capture. Production priority must leave room for
  captures; the guard cap does that, and a rich EC with a known enemy EC now
  spends everything above reserve on capture politicians.
- Edge discovery: scouts now head for edges nobody has found (split by id);
  `Gridlock` bounds were complete by r250 (v1f) versus never in v1c. Enemy EC
  contact on `maptestsmall` moved from r537 (heading-walk scouts) to r60
  (waypoint scouts). Symmetry on `maptestsmall` still unresolved in v1e because
  the top edge was never probed; being tested with edge-seeking scouts.
- `--navstats`: our A-B-A oscillation rate 0.1-3.4% of moves versus the
  example bot's 5.1-5.5%; coverage 25-33% of tiles visited (the example bot
  49-72%, but it wanders with 3-10x more moves).
- Bytecode after the distance cache: POL peak 8.5k, EC 7.4k, SLA 4.5k, MUC
  4.1k; zero overruns in every game so far.

**Order-timing bug found and fixed (v1i, `Gridlock`).** Tracing a 221-influence
capture politician showed it idling by home for 400 rounds: it had run as a
guard because the EC's one-round ORDER flag was set in the spawn round, and a
robot built in round N first acts in round N+1 (engine iterates a snapshot of
the spawn order). Every capturer in every earlier smoke test had this defect.
With the order held for two rounds, the same map went from 0 captured ECs to
**all six neutral ECs captured by r700** (seven ECs owned). Attacks on the
enemy EC then failed only because capturers aborted as "too weak" against a
2200-influence EC; since EC damage is permanent, capturers now speak whenever
most of the speech lands on hostile targets, and the EC sizes them from a
recent estimate rather than requiring the whole amount.

## Iteration 1 -- ACCEPTED by construction; snapshot `g_iter1` (2026-09-16 15:50 UTC)

The foundation bot (economy, scouting, symmetry inference, navigation, guard
and capture politicians, adaptive bidding, flag comms, anti-congestion) is
frozen as `src/g_iter1/`. There is no meaningful head-to-head against
`g_iter0` (a one-unit bot); Iteration 1 is the first real baseline, and every
later candidate is measured against it. It beats `examplefuncsplayer` 752-0 on
votes on every map tried and captured all six neutral ECs on `Gridlock`.

**First external ladder scan launched**: `g_iter1` vs the 65 name-selected
primary benchmark bots (`tools/bench-select.py`), maps `maptestsmall` and
`Gridlock`, both sides (260 games, ~9 h at two parallel games on this box).
Purpose: a coarse first tiering (locked / target / peer) and the first
`progress/ladder.png` point. Run id `gauntlet/*-ladder-scan1/`, log
`gauntlet/ladder-scan1.log`. Per the 20% rule no replay of a game against a
locked-tier bot will be opened; the scan writes losses to `losses/` and the
reader checks the tier table first.

**Functional-area map** (attempts so far, all in Iteration 1's development):
economy/production (guard cap, slanderer cap, capture sizing), scouting
(waypoints, unknown-edge seeking, fact cycling), comms timing (order fix),
congestion (rings, jitter), combat micro (speech radius evaluation) -- none of
these has yet been measured against a real opponent. Bidding is untested
against a bidder.

**Scan restarted (16:15 UTC).** The first result of the scan was `unknown`
with no engine output. Cause: the engine is invoked with `-c=-` (configuration
from stdin) and the games were spawned by `xargs`, whose children inherit the
job pipe as stdin. `tools/gauntlet.sh` now gives every game `</dev/null`,
keeps the engine log for any unknown result, records a `dud` when the
opponent's package failed to instrument (an opponent that never ran is not a
win), and re-executes from a private copy so the script can be edited while a
run is in flight. Killing the old run also killed the shell issuing `pkill -f`
twice more (exit 144): the fix is to kill by PID after inspecting
`/proc/<pid>/cmdline`, never by pattern. The scan was relaunched at 16:15 UTC
as `gauntlet/*-ladder-scan1`.

**Maze (2 ECs per side, rotation) vs example bot, sibling-sharing build:** won
on votes; captured 4 neutral ECs by r500 (two FLIP speeches, several chips).
But no EC ever completed its bounds (one knew minY, another maxY) and sibling
IDs did not propagate in time, so nobody resolved the symmetry. Sibling-ID
reports are now sent as soon as a scout sees a sibling EC. A single Maze game
took 18 minutes with the scan running beside it: development tests must be
rationed while the scan is in flight.

---

**Session resumed 18:25 UTC (Remote Control on).** Ladder scan at 22/128 after
2 h 28 m: 6.7 min per game throughput at 2 jobs, so ~12 h remain, worse while a
job from the 2025 project shares the two CPUs. So far: 4-0 vs Aryan34, 4-0 vs
Aryo-Patel; 0-4 vs 123kevinlee, BSreenivas0713, IvanGeffner (all annihilations
by r300-r660), 0-2 vs JasonYe4273. Losses to 0% opponents are locked: no replay
opened. The visible shape of the losses is early annihilation, i.e. our EC is
converted within 300-700 rounds.

**Gauntlet policy change (user, 18:40 UTC):** the standing gauntlet is the
20-50% band only (`tools/gauntlet-select.py` -> `tools/roster.txt`); the full
external set is a scan, played after accepts. The running scan is the first
tiering pass. With 4 games per opponent the band is coarse (1-2 wins of 4);
band-edge opponents get more games in the next scan.

**Probe (pre-registered, 18:40 UTC): can the EC survive our own rushes?** The
losses so far are annihilations by r300-660 and their replays are locked, so
the allowed measurement is an own-archetype probe on `maptestsmall`:
(a) `arch_muck` (archetype 1: 4 slanderers then 1-influence muckrakers at us),
(b) `arch_polrush` (archetype 3, new: 2 scouts, 2 small slanderers, then every
100+ influence becomes a capture politician at our EC). Counters read from the
replay: EC influence per round, slanderer count, `@speech role=guard` count
and what the guards spoke at, round and cause of any EC conversion. Gates: a
loss to either archetype, or EC influence pinned under 100 while enemy
politicians of >100 conviction are within 5 tiles, names the defence as the
target. Not a candidate: no code in `bot` changes for this probe. Runner:
`tools/run-dev.sh` (private compile; `gauntlet.sh` wipes `build/classes`, so
compiling there mid-scan could dud a scan game).

**Probe results (19:05 UTC).** Both archetypes lost to `bot` on votes at
r1500 (`matches/probe_muck.bc21`, `probe_polrush.bc21`), so neither reproduces
the ladder annihilations. `arch_polrush` is a dud rusher: its EC never reached
100 influence (our muckrakers sat on its spawn ring, `danger` blocked its
slanderers, passive income went to bids) -- to be rebuilt as a fixed-opening
rusher before it is used again. `arch_muck`'s 15 hunters never found our EC
and loitered on the top edge (their scouting is our scouting: the same
edge-clumping exists in `bot`).

**Absolute degeneracy found in our own replays (the target).** In every game
that lasts past ~r500 the EC stops building: `probe_muck` r700-1500 spawned 4
units (all 1-influence scouts) while EC influence rose 305 -> 11822 and the true
slanderer count fell 14 -> 0; `bid1` shows the same (slanderers 18 -> 0 by r700,
14648 influence hoarded). Trace: the EC's census (`countAlive`) recounts
children by `canGetFlag` but keeps the role recorded at spawn, so a slanderer
that the engine converted to a politician at roundsAlive == 300 is still
counted as ECON. The `@econ` line shows the belief `sl=12` pinned from r200 to
r1500 against engine truth 0; the slanderer cap (12) therefore closes forever
once the first 12 slanderers have aged, and the guard cap (`4 + sl/2 = 10`) and
`capturers < 3` close the other branches, so only the scout branch ever fires.

*Pre-registration, candidate "census":* reclassify ECON children as GUARD once
`round - birth >= 300`. Decision-point counter: `@econ sl=` (belief) vs the
replay's `A_sla` (truth) must track within one slanderer lifetime; spawns of
slanderers after r600 (baseline 0 in both probes). Reachability: fires in every
game > r500; the branch is the only path that re-opens slanderer production.
Trigger frequency: all long games; irrelevant to games lost before r500.
Price: influence leaves the EC (its conviction) for slanderers; the existing
reserve and caps are unchanged. History: nothing in the ledger. Gate: Stage 0
on `maptestsmall` vs `arch_muck` (the motivating game): candidate `A_sla > 0`
at r800+ and `sl=` belief tracking truth; then head-to-head vs `g_iter1`, quick
12-map set both sides (24 games; the full corpus waits for the scan to finish),
AcceptMargin = +4 games (the noise floor is not yet measured; recorded as such).
Falsifier: `A_sla` still 0 after r800 with the fix in (some other branch
starves slanderers), or a head-to-head loss.

**Stage 0, census candidate vs `arch_muck`, `maptestsmall`
(`matches/census_s0_muck.bc21`): won on votes, mechanism engaged.** Slanderer
spawns per 100 rounds: baseline `0 9 5 1 20 4 0 0 0 0 ...` (nothing after
r500); candidate `9 5 1 20 4 - 8 4 - 8 4 - 8 4` (a 12-slanderer cycle every
300 rounds for the rest of the game). Belief `sl=12` now matches per-EC truth;
the team total of 24 in r400-600 is two ECs (the archetype's EC was captured at
r~300 and runs our code). Unit influence at r1500: 29110 vs 4781 baseline.
Shared pool read beside the outcome: EC influence still hoards (34755 at r1500
vs 11822) because the slanderer cap and the guard cap hold while income is
now ~5x -- idle influence is the next economy target, not part of this
candidate. Proceeding to the head-to-head vs `g_iter1`, quick set, both sides.

**Why the scan is slow (19:20 UTC): memory, not CPU.** A game against a heavy
bot is a 1.1-1.4 GB process (`-Xmx512m` heap plus instrumented classes and the
in-memory replay); the two scan games held 878 MB and 528 MB in swap, paging
at 2-4 MB/s with 40% of the CPU waiting on I/O, and a JasonYe4273 game on
Gridlock passed 55 minutes. This box (2 vCPU, 2 GB) fits ONE game. Changes:
the running scan was dropped to one job in place (SIGUSR2 to xargs, no game
lost); `gauntlet.sh` gained `GAME_TIMEOUT` (default 1800 s, capped games are
`unknown`) and `CLASSES=` (private compile dir, so a second run cannot wipe
the classes a running one loads); `tools/scan.sh` is the two-stage tiering
scan (one map both sides for everyone, the second map only for 1-1 splits),
which roughly halves a scan. A larger VM is the only real speed-up: 8 GB RAM
would allow 4-5 concurrent games. The census head-to-head is queued behind
the scan; two runs at once thrash.

**Games moved to `battlecode-dev` (19:40 UTC, user instruction: the driver
runs only Claude).** The VM (e2-standard-8, 8 vCPU / 31 GB) mirrors the
driver's layout (`~/jdk/jdk8u504-b01`, `~/projects/vibe/2021`,
`~/projects/vibe/bc21-benchmarks/{_classes,manifest.tsv}`) so `tools/lib.sh`
and `gauntlet.sh` run unchanged there; `tools/vm.sh`, `vm-sync.sh`, `vm-run.sh`
(detached run), `vm-tail.sh`, `vm-collect.sh`, `vm-stop.sh` are the driver-side
handles. Smoke test on the VM: `bot` vs `examplefuncsplayer`, `maptestsmall`,
1500 rounds in 15 s (the same game took minutes on the swapping driver). The
driver scan was killed at 22/128 (results kept in
`gauntlet/scan1-partial-results.csv`). Relaunched on the VM as `scan1`: all 32
name-selected opponents on the 4-map screen set, both sides (256 games, 5
jobs), and the census head-to-head `h2h-census` (quick set, 24 games, 2 jobs)
beside it. The VM's disk was 87% full of 2025 replays; the user authorised
deleting old projects' games.

**Scan restructured as a two-stage screen (19:28 UTC, user suggestion).** The
520-game screen-set scan ran at 2.4 games/min on the VM (3.6 h); it was
stopped at 22 games. `tools/scan.sh` now runs on the VM: stage 1 = every
remaining opponent on `maptestsmall`, both sides (2-0 = peer for now, 0-2 =
locked for now); stage 2 = the 1-1 splits on `Arena Maze Gridlock`, both
sides. Opponents already decided by the two partial scans
(`gauntlet/scan1-driver-partial-results.csv`, `scan1-vm-partial-results.csv`)
are skipped and their results are included in the final selection. Expected:
a coarse roster in ~1 h, the refined one in <2 h.

## Iteration 2 -- ACCEPTED: EC census counts aged slanderers as guards; snapshot `g_iter2` (2026-09-16 19:40 UTC)

**Head-to-head vs `g_iter1`, quick 12-map set, both sides
(`gauntlet/20260916-192408-h2h-census`, on the VM): 24/24, 12 as A and 12 as
B; 19 on votes, 5 by annihilation.** Gate was +4 games; the margin is +24, so
the noise floor (still unmeasured) cannot be the explanation. Shape: no
losses at all, so no flip analysis; the five annihilations are new (Iteration
1 never annihilated its own predecessor) and come from the influence the
re-opened slanderer cycle produces. Attribution: as pre-registered (the
mechanism engaged at Stage 0: slanderer spawns continue in a 12-per-300-round
cycle; belief tracks per-EC truth). Arm-to-arm identity count: 0 of 24 cells
identical (every game differs from round ~300 on).

Post-accept: `src/g_iter2` snapshotted; `progress/history.csv` has the row;
`progress/vs_roster.png` redrawn. Re-tiering waits for the running scan; the
roster gauntlet for this build runs as soon as `tools/roster.txt` exists.
Open from the Stage 0 read: EC influence still hoards (34755 at r1500 in the
probe) -- the slanderer cap (12) and guard cap (10) are now the binding
constraints; that is the next economy candidate. The `arch_polrush` archetype
needs rebuilding before it is used as a rush opponent.

## Iteration 3 (in development) -- EC wall (2026-09-16 19:50 UTC)

**Target: a losing game vs a target-tier opponent.** Stage 1 of the scan put
9 opponents at 1-1 on `maptestsmall` (provisional roster); 8 of their 9 wins
over us end the same way: our EC is converted between r300 and r600 by single
enemy politicians of 500-1750 conviction speaking at radius 1 from a tile
adjacent to the EC, with the enemy's expose buff at 1.2-1.9x. Trace, iyzg.sbot17
as A vs us as B (`gauntlet/20260916-192830-scan-s1/losses/iyzg...botB.bc21`):
our EC held 4125 at r241 when a 1614 speech landed from (26,4); two of our
politicians happened to stand in its radius, so the hit split three ways and
the EC lost 608. At r293 a 1725 speech from (27,5) had nothing else in range:
the EC lost 3190 in one round (3595 -> 405). At r353 a 1749 speech converted
it. Same shape in VittalT (669, 751 then a buffed finisher), anshgs, jmerle,
Scott-Poole, iyzg, aidan-mundy, Victoriano. The EC cannot bank faster than
1700-conviction politicians arrive; what decides the damage is `n`, the
number of robots inside the speech radius.

*Pre-registration, candidate "wall":* the EC builds `WALL_SIZE` 1-influence
muckrakers (role WALL) from r12 that hold the tiles adjacent to home (leaving
one spawn tile free). An attacker can then not stand orthogonally adjacent,
and any speech that reaches the EC also covers several wall units, so the
EC's share is conv/n instead of conv. Decision-point counters: `@wall placed`
count (expect 7 by r~40 and after every loss); for every enemy speech that
reaches our EC, `EC influence lost / attacker conviction` from the event
stream (baseline 1.0 or 0.33 in the trace; expect <= 0.25) and the attacker's
distance to the EC (baseline 1; expect >= 4). Reachability: 8 of 9 target-tier
losses have adjacent-tile speeches. Trigger frequency: every game against a
politician-using opponent. Price: 7 influence, 7 of 96 child slots, one spawn
tile instead of eight (the EC's build cooldown, 2-10 rounds, is longer than a
newborn's exit), and units routing around the wall. History: nothing in the
ledger. Dose ladder: WALL_SIZE 0 (byte-identical to g_iter2 apart from the
constant) / 4 / 7. Gate: Stage 0 re-runs the motivating game on the VM
(`iyzg.sbot17` vs bot, `maptestsmall`, both sides); then head-to-head vs
`g_iter2` on the quick set (+4 games) and the roster gauntlet (PeerFloor 55%
on peers, and the roster win rate must not fall). Falsifier: fewer than 6
walls in place when the first adjacent speech lands, or a per-hit loss ratio
unchanged, or the EC still converted by adjacent speeches.

**Stage 0, wall (7 from r12) vs `iyzg.sbot17`, `maptestsmall`, both sides
(`gauntlet/20260916-194443-wall-s0`): lost both (r602 as A, r730 as B);
mechanism engaged, price too high.** New instrument `replay-dump.sh --hits`
prints every enemy speech that reaches an EC with the attacker's conviction,
distance, `n` (robots sharing it), `wall` (our units on the EC's 8 adjacent
tiles) and the EC's influence before -> after. Walls: 7 in place by r62 (one
build every 6 rounds). Hits as B: 964, 1101, 1075, 1017, 1070, 847, 662
conviction at d2 = 4, 1, 4, 2, 4, 1, 5 with n = 3-4; per-hit loss ratio
0.25-0.45 (baseline trace 0.38, 1.86, 0.44) -- the dilution is real, and the
attackers at d2=1 stood on the free spawn tile. But the EC still lost 2376
over seven hits against a bank that peaked at 2141. As A the EC fell at r174
with 262 influence to hits of 463 and 325: the seven wall builds occupied the
EC's build slots from r12 to r62 (the EC's cooldown, ~6 rounds per build on
this tile, is the scarce resource, not the 7 influence), so at r50 we had 0
slanderers against the baseline's 4, and that game, a win in the scan, flipped
to a loss. Classification: still lost, mechanism engaged, evidenced reason
(opening delay + a 7-politician stream) -- a near miss.

*Refinement 1 (pre-registered, two arms, same mechanism):* walls are built
only after `WALL_AFTER_SLANDERERS` = 4 slanderers exist (or r150), so income
comes first; arm `wall_r1` keeps WALL_SIZE 7, arm `wall_r2` uses 4 (orthogonal
tiles only: an attacker on a diagonal still shares with 2+ walls). Counters as
before, plus slanderers at r50 (must equal the baseline's 4) and the round the
wall completes (expect < r160, the earliest first hit seen). Gate unchanged.

**Refinement 1 Stage 0 (`gauntlet/20260916-194808-wall-r1`, `-194838-wall-r2`),
vs `iyzg.sbot17`, `maptestsmall`, both sides.** Opening restored in both
arms: 4 slanderers at r50 (= baseline). Wall complete at r93 (7) / r74 (4),
before the first hit (r199-237). `wall_r1` (7): lost both (r687, r800); as B
the EC took eight hits of 800-1750 at d2=4 with n=4-5 (loss ratios
0.18-0.36, one 0.74) and fell at r524 against r353 in the baseline -- the
dilution holds until the wall is depleted (a 1600 speech kills every
1-conviction wall unit in range; the rebuild takes 6 rounds per unit and the
next attacker arrives first). `wall_r2` (4): won both (r963, r1495), but not
through the wall: its home EC fell at r237 / r441 and the wins came from EC
swaps afterwards; with only 4 units the attackers stood orthogonally adjacent
(d2=1, n=2, ratios 0.72-0.77) because a wall unit holds whichever tile it
spawned on, diagonals included. Fix applied to both arms (mechanism
completeness, not a new mechanism): wall units move from a diagonal to a free
orthogonal tile, and the EC spawns them on orthogonal tiles first.

*Decision:* a single opponent that fields 1600-conviction politicians every
50 rounds cannot decide the candidate; it measures the mechanism, which is
now as pre-registered (walls up before r100, attackers pushed to d2=4, per-hit
ratio ~0.2-0.35). Both arms go to the head-to-head vs `g_iter2` (quick set,
both sides, 24 games each); the arm that clears +4 and shows no
one-directional regression goes to the roster gauntlet.

**Head-to-head vs `g_iter2`, quick set, both sides.** `wall_r1` (7 walls
after 4 slanderers): 12/24, 7 as A, 5 as B; four maps swept each way, four
split; churn, below the +4 gate -> rejected. `wall_r2` (4 walls, orthogonal
tiles, after 4 slanderers): **17/24, +10**, 8 as A and 9 as B; against arm 1
six cells flipped to wins (Arena x2, Saturn x2, Circles B, Gridlock B) and one
to a loss (Blotches A): one-directional, spread over maps and sides. The
only map swept against it is `maptestsmall` (32x32), where four units
parked on the EC's orthogonal tiles crowd the spawn ring most. Attribution:
the opponent fields no large politicians, so the wall's dilution is not what
wins these; the likely mechanism is the 4 cheap units themselves (blocking
enemy muckrakers from the EC's neighbourhood and exposing early). Recorded as
OPEN, not back-filled. Next per 4.5: the target gauntlet, `wall_r2` vs the 9
stage-2 opponents on the screen set, diffed cell by cell against the scan's
record for `bot` (9-9 on maptestsmall, 1-34 on the larger maps).

**Engine fact (21:10 UTC): heavy bots exhaust the JIT code cache.** Two
`unknown` cells in the three-map scan (JasonYe4273 and StoneT2000 on
Gridlock) were games where the JVM's 240 MB code cache filled with
instrumented methods (`compilation: disabled (not enough contiguous free
space left)`), after which the game crawled into the 1200 s cap. Both runners
now pass `-XX:ReservedCodeCacheSize=512m`. Unknown cells are not decided, so
the incremental scan replays them on its next pass.

## Standing tables (updated in place)

### Functional-area map

| area | last attempt | status |
|---|---|---|
| economy / production mix | Iteration 2: census fix (aged slanderers -> guards), 24/24 vs g_iter1 | caps (12 slanderers, 10 guards) now bind: influence hoards late |
| bidding | bidding v2 + overflow/reserve fix | smoke-tested vs `arch_bidder` (win 750 votes); unmeasured vs real bidders |
| scouting / map knowledge | edge-seeking waypoints, fact cycling, sibling IDs | symmetry still often unresolved on multi-EC maps |
| navigation | greedy + bug + oscillation guard | aba 1-3% of moves; boxed-in-by-friends failure seen and mitigated by jitter |
| combat micro (politician speech) | radius/value evaluation, chip rule vs ECs | works vs example bot; unmeasured vs real opponents |
| slanderer safety | flee any enemy, ring 8-45 | unmeasured vs hunters |
| muckraker hunting / blocking | expose nearest slanderer, sit at enemy EC | unmeasured |

### Closed-directions ledger

| direction | kind | measurement | re-open if |
|---|---|---|---|
| short-round smoke maps via map files | engine-impossible | map format has no round field; 400-round map played 1500 | never |
| one-round spawn ORDER flag | refuted | newborn acts next round; 0 captures -> 6 with two-round hold | never |

**Bidding v2 vs `arch_bidder` (our code with bid x2+1 on every lost vote, cap
influence/2), `maptestsmall`, bot as A:** win on votes 750 to ~70. The
archetype bankrupted itself early (eVotes~70 by r150 and nothing after); our
bid stayed at 2-8. Two defects surfaced: after the safe-stop condition fired
at 750 votes the bid kept adapting on rounds we did not bid, overflowed `int`,
and through `reserve()` (2x bid) blocked all production for 600 rounds (EC
hoarded 4390 influence, unit counts frozen). Fixed: adapt only after a round
in which we bid, clamp the bid to influence, cap the reserve at half the
influence. A result against our own archetype is a smoke test, not evidence
of bidding strength.
