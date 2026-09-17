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

**Iteration 3 REJECTED (21:50 UTC): the EC wall.** Target gauntlet,
`wall_r2` vs the 9 stage-2 opponents, screen set, both sides
(`gauntlet/20260916-202019-tgt-wall-r2`): 9/72 (12.5%) against the
baseline's 13/72 on the same cells. Cell diff: 58 loss->loss, 8 win->win, 5
win->loss (four of them on `maptestsmall` as B, one Arena), 1 loss->win
(iliao2345, Arena A). One-directional and concentrated on the small map,
where four units parked on the EC's orthogonal tiles cost the most: a real
regression, not churn. Per opponent: 0/8 vs Scott-Poole, VittalT,
aidan-mundy and anshgs; 4/8 vs iliao2345 (a muckraker rusher, the one
opponent the wall was not designed for and the one it helped). The +10
head-to-head vs `g_iter2` did not survive contact with politician-using
opponents: the head-to-head is a partial derivative (principle 7). Reverted
fully: `src/bot` is byte-identical to `g_iter2` again; `wall_r1`/`wall_r2`
removed. What the run bought: the per-hit dilution is real (0.2-0.35 of the
attacker's conviction instead of 1.0) but a 1000+ speech kills every
1-conviction wall unit in range and the rebuild (one EC build per 6 rounds)
loses the race to the next attacker, while the wall's build slots and
crowding cost more than the absorbed damage is worth. `--hits` stays as an
instrument. Defence area: 1 reject.

## Iteration 4 (in development) -- never idle (2026-09-16 22:05 UTC)

**Target: an absolute degeneracy in our own replays.** New `--navstats`
fields (units that lived >= 100 rounds, idle units, mean moves per unit,
moves onto swamp) on two target-tier losses: Maze vs Sihal3 -- units 230 vs
1612, mean moves/unit 56 vs 76, coverage 48% vs 99.5%, the opponent walks
onto swamp for 44% of its moves; Gridlock vs iyzg -- units 74 vs 1167, mean
moves 49 vs 118. Our units move about as much each; there are 7-15x fewer of
them. The capture survey over the 9 opponents' losses showed 12-40 capture
politicians per Maze game that never spoke: they were built late into a
corner pocket already jammed. The EC's production is the bottleneck: every
branch has a hard cap (12 slanderers, 10 guards, 3 capturers, scouts by
round) and once they bind the EC idles and hoards (34755 influence unspent at
r1500 in the Stage 0 probe; `@econ` shows `cap=3 g=10 sl=12` pinned for a
thousand rounds).

*Pre-registration, candidate "spend":* a last branch in `EC.build`: when
every capped branch declines and `inf - reserve() >= SPARE_MIN` (60), build
anyway -- a guard politician while guards trail slanderers, else a slanderer
up to a spare cap of 24, else a 1-influence hunter muckraker. Decision-point
counters in the `@econ` line: `idle=` (rounds the EC was ready with >= 21
influence and built nothing; baseline hundreds per game) and `spend=` (builds
through the new branch; baseline 0). Outcome counters: `unitsLived100` and
`meanMoves` from `--navstats`; EC influence at r1500. Reachability: the
branch is reached whenever the caps bind, i.e. every game past ~r300.
Trigger frequency: all games. Price: influence leaves the EC (its
conviction): the reserve is unchanged, and the spare branch never spends
below it; more bodies also means more congestion around home (ring/jitter
logic unchanged). History: no ledger entry. Dose ladder: SPARE_MIN = off
(byte-identical to g_iter2) / 60 / 20. Gate: Stage 0 = the two motivating
games on the VM (Gridlock vs iyzg, Maze vs Sihal3, both sides): `idle` must
drop by 5x and `unitsLived100` at least double; then head-to-head vs
`g_iter2` (quick set, +4) and the roster gauntlet (no regression on the
roster; peers above 55%). Falsifier: `spend` stays near 0 (some earlier
branch already fires and the diagnosis is wrong) or units do not increase.
Attempt count: 4th incremental in a row (census, wall, wall refinement,
spend); the next attempt must be structural (SwingEvery = 4).

**Stage 0, spend (`gauntlet/20260916-220154-spend-s0`, first three games):
mechanism engaged.** `spend=` 68 / 166 / 227 builds by r250-450 (baseline 0);
`idle=` 47 / 53 / 9 (baseline: hundreds); slanderers pinned at the spare cap
24, guards 24-32, hunters 40-50. `unitsLived100`: Gridlock as B 181 (baseline
74 on the same cell), Maze as A 400 (baseline 230 vs another opponent);
mean moves per unit unchanged (61-72). All three still lost (EC converted at
r406, r761, r908): the opponent still fields 3-7x the units. One gap in the
counters: on Maze at r400-450 the EC held 2575 -> 7021 influence with idle=9,
i.e. it was building every cooldown but 1-influence hunters, because guards
already exceeded slanderers + 2 and `danger` blocked slanderers. Completeness
tweak, same mechanism: a spare bank >= 300 buys a guard (cost spare/3)
regardless of the guard:slanderer ratio. Next: head-to-head vs `g_iter2`.

**Stage 0 complete (8 games): 1/8**, the one win a cell the baseline lost
(Sihal3, Gridlock, as B); no cell flipped the other way.

**Head-to-head vs `g_iter2`, quick set, both sides
(`gauntlet/20260916-220756-h2h-spend`): 20/24, +16, 11 as A and 9 as B.**
Nine maps swept for the candidate, two split (Arena, CrossStitch), one swept
against (Maze: the extra bodies jam the corner pocket the capture survey
found). 9 annihilations against 5 for the census build. One-directional:
the number stands. Target gauntlet launched on the wall's 72 cells
(`tgt-spend`), to be read against the baseline's 13/72 and the wall's 9/72.

## Iteration 4 -- ACCEPTED: never idle (spare-influence branch); snapshot `g_iter3` (2026-09-17 00:40 UTC)

**Target gauntlet, the 72 cells the wall was judged on
(`gauntlet/20260916-224601-tgt-spend`): 19/72 (26.4%) against the
baseline's 13/72 and the wall's 9/72.** Cell diff vs the baseline: 51
loss->loss, 12 win->win, 7 loss->win (Sihal3 Arena B and maptestsmall B,
Victoriano Maze B, iliao2345 maptestsmall B, jmerle Arena A and B and Maze B),
1 win->loss (jmerle maptestsmall B), 1 loss->unknown (anshgs Gridlock A).
One-directional and spread over four opponents and four maps. Per
opponent: Sihal3 4/8, iliao2345 4/8, Victoriano 3/8, jmerle 3/8; the four
that field 1000+ politician streams (VittalT, aidan-mundy, anshgs,
Scott-Poole) and iyzg stay at 1/8. With the head-to-head at 20/24 (+16) and
no regression signature, accepted. Attribution as pre-registered: the EC
builds every cooldown instead of idling once its caps bind (Stage 0
counters), and the extra bodies are what flipped the cells.

Post-accept: `src/g_iter3` snapshotted; archetypes `arch_muck`,
`arch_bidder`, `arch_polrush` resynced from the accepted code;
`progress/history.csv` has the head-to-head and the nine target rows;
`progress/ladder.png` and `vs_roster.png` redrawn. Re-tiering and the roster
gauntlet for this build wait for the three-map scan's roster (stage 2 at
~100/132). Attempt cadence: four incremental attempts in a row (census
accepted, wall rejected, wall refinement rejected, spend accepted): the
next attempt is structural.

## Iteration 5 (in development, structural) -- the neutral-EC race (2026-09-17 01:10 UTC)

**Target: a capability gap named from the ladder.** In every target-tier
loss on Arena, Gridlock and Maze (`gauntlet/20260916-224601-tgt-spend`), the
opponent holds 6-8 of the map's 8 ECs by r400 and we hold 0-2 (typical
sequence r200/400/600/900: `2v2 2v6 1v7 0v8`), and their unit count at r900
is 800-2000 against our 1-300. 70 of the 76 corpus maps have neutral ECs (4-6
worth 875-1600 influence in total; Gridlock's six average 200). Our EC knows
a neutral by r50 (Arena, Gridlock) or r150 (Maze) in all 40 surveyed losses,
yet flips none in 24 of them and never more than one before r250; the
opponents flip two by r200 and four to six by r400. Mechanism: the capture
branch waits until it can afford `neutralInf + 14` in one politician, keeps
at most 2 capturers and counts them forever (the capturer census pin seen at
Stage 0 of Iteration 2), so one capture per game is the ceiling.

*Pre-registration, candidate "race":* the capture branch (already second in
priority, after emergency guards) sends a chip politician toward the nearest
known neutral whenever `inf - reserve() >= 40`, sized `min(spare, remaining +
14)`, with up to `RACE_INFLIGHT` = 4 capturers younger than 200 rounds in
flight (older ones count as guards). Damage to an EC is permanent, so chips
accumulate. Decision-point counters: `race=` builds in the `@econ` line
(baseline: the old branch fired 0-2 times), `@speech role=capture ... FLIP`
count and first-flip round (baseline: <= 1, r96-419 or never), `A_ecs` at
r400 (baseline 0-2). Reachability: fires from the first neutral report; the
choice set is every known neutral. Trigger frequency: all games on the 70
maps with neutrals. Price: early influence goes to captures instead of the
first slanderers (income later) and the chips are bodies that die; the
reserve is unchanged. History: nothing in the ledger. Dose ladder:
RACE_INFLIGHT 2 (= old cap, with chips) / 4 / 6. Gate: Stage 0 on the VM,
Arena and Gridlock vs iyzg and Sihal3, both sides (8 games): >= 2 flips in at
least half the games and ECs at r400 >= 3 on average; then head-to-head vs
`g_iter3` (quick set, +4) and the roster gauntlet on the new roster.
Falsifier: `race=` stays near 0 (spare never reaches 40 early: the reserve
or bids eat it), or flips do not rise (chips never arrive: navigation), or
ECs at r400 unchanged. This is the structural attempt due after four
incremental ones.

**Stage 0, race (4 in flight, chips >= 40) vs iyzg and Sihal3 on Arena and
Gridlock (`gauntlet/20260917-010848-race-s0`): 0/8 (the baseline `g_iter3`
won 3 of these cells); the six read in detail: two at
r259-261 (Arena vs iyzg; the baseline lasted to r630+).** Counters: `race=`
7-12 builds by r150 then pinned (the EC had nothing left); chips spoken
6-12 per game; flips 0, 1, 0, 0, 1, 1; aborts 0-9 (the opponent converted
the target between our chips, so our damage was a gift); ECs at r400: 2v2,
1v4, 1v4, 1v6, 1v7, 0v8. The economy collapsed: EC influence 2-60 from r100
to r300 against 1000-3000 in the baseline, slanderers stuck at 12 tiny ones,
the spare branch never reached its threshold. Mechanism engaged, gate
failed, failure mode understood (undersized, dispersed chips against an
opponent who both out-earns and out-chips us). *Targeted refinement (one,
pre-registered):* a chip must carry at least half the remaining conviction
(so two chips flip a neutral and none is wasted), at most 2 in flight, and
none before 4 slanderers exist. Same counters and gate. If flips do not rise,
the candidate is rejected and reverted.

**Roster fixed (01:15 UTC) from the three-map scan** (`tools/roster.txt`,
tiers in `gauntlet/tiers-g_iter2.txt`, rates on `g_iter2`): iliao2345 21%,
123kevinlee 24%, max-titov 25%, Sihal3 36%, arya-k, astelmach20, nickel-dime,
nsortur, qawsedrftgzh 50%. Locked at 0-7%: the 13 bots that never lost to us
plus Scott-Poole, VittalT, aidan-mundy, anshgs, iyzg (1/14), Victoriano and
jmerle (2/14). Stage 2 overall 47/132 (36%). The roster gauntlet for
`g_iter3` (quick set, both sides, 216 games) is running as the baseline for
the next candidate.

**Refinement 1 Stage 0 (`gauntlet/20260917-*-race-r1`, six read): 1/6.**
Flips 0, 1, 1, 2, 1, 3 (baseline <= 1); the 3-flip game (Sihal3, Arena, A)
held 3 ECs at r400 and won by annihilation at r512. But `race=` fired only
0-4 times per game: the chip threshold (half a target, ~190 on Arena) is
rarely reached because the slanderer and guard branches, lower in priority
but always affordable, spend the income first; the EC never saves. ECs at
r400 averaged 1; Sihal3 Arena B, a baseline win, was lost. Gate missed.
*Refinement 2 (dose change on the same mechanism, pre-registered):* while a
neutral is known, 4 slanderers exist, fewer than 2 capturers are out and the
chip is unaffordable, the economy branches wait (`save=` counter) until r400;
emergency guards still fire. Expect `race=` >= 6 by r300 and flips >= 2 in
half the games; falsifier: the saved bank is converted with the EC (EC
influence is its conviction, so saving is also defence) or flips still < 2.

**Iteration 5 REJECTED (01:35 UTC): the neutral-EC race, three arms.**
Refinement 2 (`gauntlet/20260917-*-race-r2`): 0/8. Counters: `save=`
100-265 rounds (the EC held its bank as designed) but the bank never reached
a chip: EC influence 10-70 at r200-300 because, with the economy branches
paused, income stayed at the opening level; `race=` 0-4, flips 0-2, ECs at
r400 1-2 vs 2-7. Across the three arms: the mechanism engaged each time
(chips built, arrived, spoke) and each time the cost fell on the economy the
captures were supposed to feed. The opponents win the race because they
out-earn us 2-3x by r100 (EC influence 500-750 vs our 200-290 at r100 in the
target-tier traces) and can afford both. Reverted: `src/bot` is `g_iter3`
again. What the attempt bought: the capture instrument (flips, first-flip
round, ECs at r400) and a measured ceiling: with our opening income, a
capture doctrine cannot be funded before r200, and by r200 the neutrals are
gone. The next candidate is the opening economy itself (area: economy, 0
rejects); the race is re-opened when r100 EC influence is >= 500.

## Iteration 6 (in development) -- slanderer cap 24 (2026-09-17 01:45 UTC)

**Target: an absolute degeneracy in our own replays, economy area.** Opening
build orders from two roster opponents' replays (allowed: Sihal3 36%,
iliao2345 21%) against ours (`tgt-spend` losses, Gridlock and Arena): our
EC's first 90 rounds buy 4 scouts, one 107 slanderer, ~12 slanderers of
21-41 and ~14 guard politicians of 20-29; the slanderer count hits the cap
of 12 at r50 and stays there until the first ones age out at r350 (the spend
branch adds up to 24 only when 60 is spare, which the guard branch rarely
leaves). Sihal3 opens with cheap muckrakers and slanderers of 21-107, no
politicians until r68, and keeps adding slanderers: 7, 17, 29, 36 at r50,
r100, r150, r200 against our 12, 19, 24, 25 -- and its EC holds 623 at r200
against our 111. (The locked bots out-earn us 2-3x by r100; the roster bots
do not, they just keep growing.)

*Pre-registration, candidate "cap24":* `MAX_SLANDERERS` 12 -> 24 for the
normal slanderer branches (the spend branch's 24 is unchanged); one
constant. Decision-point counters: slanderers alive at r100 and r200 from
`--metrics` (baseline 12-20 / 20-25; expect 20+ / 30+ where danger allows),
EC influence at r200 (baseline 50-300), `unitsLived100`. Reachability: the
cap binds at r50 in every traced game. Price: more slanderers exposed to
muckrakers (Sihal3 fields 25-48 by r200; the guard cap `4 + sl/2` grows with
them), fewer early guards because the guard-ratio gate (`guards >= sl/3`)
now asks for 8. History: nothing in the ledger. Dose ladder 12 (= g_iter3) /
24 / 36. Gate: Stage 0 on the 8 cells (Arena, Gridlock vs iyzg, Sihal3, both
sides; g_iter3 scored 3/8 there): counters must move and the cell count must
not fall; then head-to-head vs `g_iter3` (quick, +4) and the roster gauntlet
against the g_iter3 roster baseline now running. Falsifier: slanderer count
unchanged (some other branch starves them) or EC influence at r200 lower.

**Iteration 6 REJECTED at Stage 0 (`gauntlet/20260917-013401-cap24-s0`): 0/8
against the baseline's 3/8 on these cells; both Sihal3
Arena cells, baseline wins, lost.** Counters: slanderers 24 at r100 in every
game (baseline 12-19: the cap moved), but EC influence at r200 unchanged
(64-230) and politicians 29-34 at r200: the extra income went straight into
guards through the spend branch (`guards < slanderers + 2`), whose price is
now visible: 20 guards of 20-30 by r100 in every game, ~400-600 influence
that neither earns nor defends much. iyzg meanwhile: 13 -> 34 -> 91
slanderers by r300 across the 5 ECs it holds by r200. Reverted to 12.
Economy area: 1 reject. Ledger: slanderer cap alone does nothing while the
guard sink absorbs the income.

## Iteration 7 (in development) -- guards scale with threat (2026-09-17 01:55 UTC)

*Pre-registration, candidate "guard ratio":* `GUARD_BASE` 4 -> 0 (no
standing guards before an enemy is seen; `danger` still builds up to
`MAX_GUARDS`) and the spend branch's guard rule `guards < slanderers + 2` ->
`guards < slanderers/2 + 2`. One mechanism: the influence the guard sink
took goes to slanderers (the normal branch, cap 12, then the spend branch to
24). Decision-point counters: politicians at r100 (baseline 9-20; expect
<= 10), slanderers at r100/r200 (expect up), EC influence at r200 (expect
up: baseline 64-300). Price: fewer bodies around home against muckraker
floods (Sihal3 exposed 0 of our slanderers in the cap24 games, iyzg 3-13:
watch `exposes`). Dose ladder: ratio 1 (= g_iter3) / 1/2 / 1/4. Gate:
Stage 0 on the same 8 cells (cell count must not fall below 3, counters
must move); then head-to-head vs `g_iter3` and the roster gauntlet.
Falsifier: politicians at r100 unchanged (another branch builds them) or
exposures explode.

**Iteration 7 Stage 0, first arm (`gauntlet/20260917-*-guard-s0`, 4 read,
0/4): falsifier hit.** Politicians at r100 were 12-16 (baseline 9-20): the
normal guard branch scales as `GUARD_BASE + slanderers/2`, so zeroing the
base left 12 guards at 12 slanderers and the halved ratio only reached the
spend branch. Fix for mechanism completeness: the same ratio in the normal
branch (`GUARD_BASE + slanderers * 1/2 / 2` = 6 at 12 slanderers). Also
seen: against iyzg's muckraker flood, exposures reached 34-41 by r300 (the
baseline's 9-14), the price the pre-registration named; the Sihal3 games
had 0. Relaunched as `guard-s0b` on the same 8 cells.

**Iteration 7 REJECTED at Stage 0 (`gauntlet/20260917-*-guard-s0b`, fixed
build, 6 read): 1/6 against the baseline's 3 on those cells; the one win
(Sihal3, Arena, B) an annihilation at r316.** Politicians at r100 still
11-16: with the spend branch filling slanderers to 24, half of 24 plus 2 is
14 guards, so the halved ratio never reduced the count -- the falsifier,
twice. Reverted to `g_iter3`. Economy area: 2 rejects.

**The ledger that explains all four economy attempts (from `--metrics`
cumulative `spawnInf` + `bidInf` + EC influence, i.e. total influence earned):**

| game | r200 us | r200 them | r300 us | r300 them |
|---|---|---|---|---|
| Sihal3, Arena, A | 4692 | 9239 | 5268 | 11717 |
| iyzg, Gridlock, B | 5066 | 11598 | 8887 | 30128 |
| Sihal3, Gridlock, A (baseline) | 4905 | 13297 | 7118 | 20325 |

We earn 2-3.4x less, and no reallocation of what we earn (walls, chips,
more slanderers, fewer guards) can close that. Sihal3 earns 2x with FEWER
slanderers than ours (15-20 vs 24): its slanderers are 41-107 early and
larger later; ours are mostly 21 because the EC buys a slanderer the moment
21 is affordable. Income per round by size: 21 -> 1, 41 -> 2, 63 -> 3,
85 -> 4, 107 -> 5, 154 -> 7, 463 -> 18; a slot is held 300 rounds either way.
Two more facts for the ledger: iyzg never bids (votes 0, wins by
annihilation), and against Sihal3 we sink a third of our income into bids
and still lose the vote (1608 of 4692 by r200; votes 60 vs 121).

## Iteration 8 (in development) -- minimum slanderer size (2026-09-17 02:05 UTC)

*Pre-registration, candidate "size":* after the first `OPENING_SLANDERERS`
(2), a slanderer is built only when `MIN_SLANDERER_SIZE` (63) is affordable
(all three slanderer branches); the EC otherwise falls through to its other
branches or waits. Decision-point counters: mean slanderer cost at r100 and
r200 from `@spawn t=2` (baseline ~25-40; expect >= 63), slanderer count
(expect lower early), and the outcome counter: total influence earned by
r200 and r300 (`spawnInf + bidInf + ecInf`; baseline 4700-5100 / 5300-8900;
gate: +30%). Price: slower opening (fewer slots filled at r50), and
influence idling in the bank while waiting (that bank is also EC conviction).
History: the ledger says count-only changes fail. Dose ladder 21 (=
g_iter3) / 63 / 107. Gate: Stage 0 on the 8 cells (cell count not below 3,
counters as above); then head-to-head vs `g_iter3` (+4) and the roster
gauntlet vs the g_iter3 roster baseline. Falsifier: mean size unchanged
(another branch buys small ones) or income not up.

**Stage 0, size (`gauntlet/20260917-*-size-s0`, 5 read, 0/5): mechanism
engaged, outcome counter cleared, cells not flipped.** Mean slanderer cost
at r100: 67, 72, 68, 87, 90 (baseline 25-40); at r200: 94-122. Total
influence earned by r200: 7132, 6336, 5435, 7594, 7517 against the
baseline's 4692-5066 on the same cells (+30-50%, the pre-registered gate);
by r300 +20-40%. The opponents still earn 12000-17500 by r200, and the two
baseline wins among the read cells (Sihal3 Arena A, iyzg Gridlock A) were
lost, so the extra cell rule fails on the two hardest opponents. Decision:
the counters are what Stage 0 exists to check and they moved as predicted;
the head-to-head and the roster gauntlet decide (4.5). Launched `h2h-size`
vs `g_iter3`, quick set, both sides.

**Why gauntlets are slower than in 2025 (02:20 UTC, user question).** Same
VM: the 2025 project's gauntlets ran 150-162 games in 10-18 min (0.07-0.12
min of wall per game at 3 jobs); our roster run does ~1 min of wall per game
at 4 jobs while 4-5 development games share the 8 cores. A single Gridlock
game, `bot` vs `g_iter3`, takes 415 s wall / 344 s CPU under that load: 1500
rounds with hundreds of units per side, each on a 15000-bytecode budget
through the instrumented engine, i.e. ~5.7 CPU-minutes per game -- 8-10x a
2025 game. Debug logging is not a factor: an identical game with `DEBUG =
false` on both sides took 416 s / 344 s. Policy from here: at most 7 games
on the VM at once (the 2025 semaphore's cap), and no development runs beside
a roster gauntlet unless they fit under it.

**Iteration 8 REJECTED (02:40 UTC): minimum slanderer size 63.** Head-to-head
vs `g_iter3` early-stopped at 12/23 (cannot reach 14); Stage 0 0/8 vs 3/8.
The counters moved as pre-registered (mean size 67-90 at r100, income +30-50%
by r200) and it changed nothing downstream: the extra influence went into
the same sinks (bids against bidders, guards) or arrived after the EC had
fallen. Economy area: 3 rejects in a row (cap 24, guard ratio, size) --
`MaxRejectsPerArea` reached: the next attempt must leave "production mix".
Reverted to `g_iter3`. Ledger: income-side changes without a change in what
the income buys do not move games at this level.

## Iteration 9 (in development) -- bid discipline (2026-09-17 02:45 UTC)

**Target: an absolute degeneracy in the ledger (area: bidding/scoring).**
Against Sihal3 (a bidder) we spent 1608 of the 4692 influence we had earned
by r200 on bids (34%) and 2593 of 7118 by r300, and lost the vote anyway
(60 vs 121 at r200; 112 vs 157 at r300); against iyzg, who never bids, we
spent 284-514 by r300 and won every vote at a bid of 1-2. Influence bid
before r600 is influence that cannot compound through slanderers, and most
games against target-tier opponents are decided by annihilation before the
vote count matters.

*Pre-registration, candidate "bid":* before r600 the bid cap is
`influence / BID_EARLY_DIV` (30) instead of /12 (r<200) and /8 (r200-600);
the adaptive bid, the majority stop and the late ramp are unchanged. One
constant. Decision-point counters: cumulative `bidInf` at r200 and r300
from `--metrics` (baseline 1608 / 2593 vs Sihal3; expect < 600 / < 1000),
EC influence and total earned at r300 (expect up), and votes at r1500 in
games that go the distance (the price: a bidder may take the vote in a game
we would otherwise have won on votes). Reachability: every bid round.
Trigger frequency: all games vs bidders. Dose ladder: 12-8 (= g_iter3) / 30
/ 60. Gate: Stage 0 = Sihal3 on Arena and Gridlock, both sides (4 informative
cells; iyzg does not bid, so its cells are uninformative for this
mechanism); then the roster gauntlet on the screen set (72 games) against
the g_iter3 baseline on the same cells. **The head-to-head vs `g_iter3` is
not a gate for this candidate**: two builds identical except for the bid
cap play a zero-sum vote war where the higher bidder wins on votes at
r1500, which is the instrument's known blind spot (section 3: "any
weakness both builds share"); it is still played and recorded. Falsifier:
`bidInf` unchanged (another path bids) or the roster record falls.

**Stage 0, bid (`gauntlet/20260917-*-bid-s0`, Arena cells read): 2/2 (both
baseline wins too), mechanism engaged.** Cumulative bids at r200: 862 and
858 (baseline 1608; the adaptive escalation still reaches the /30 cap), at
r300: 1776 and 1728 (baseline 2593). Total influence earned by r300: 9753
and 10372 against the baseline's 5268 on the same cell -- the unbid
influence compounded. Votes at r1500: 750 vs 581 and 750 vs 576, taken by
the unchanged late ramp with an EC bank of 87-95k. Gridlock cells pending;
then the roster gauntlet on the screen set.

## Iteration 9 -- ACCEPTED: early bid cap influence/30; snapshot `g_iter4` (2026-09-17 04:45 UTC)

**Roster gauntlet, screen set, both sides, 72 cells
(`gauntlet/20260917-024938-roster-bid`) against the `g_iter3` baseline's
record on the same cells (`gauntlet/20260917-011706-roster-g_iter3`, the
full quick-set run, 90/216 = 41.7%): 33 vs 27, +6 (gate +4).** Flips: 9
loss->win (123kevinlee Arena A and B, arya-k Arena B, astelmach20 Arena A,
iliao2345 Arena B, max-titov Maze B, nickel-dime Arena A, nsortur Arena A,
qawsedrftgzh maptestsmall A) and 3 win->loss (astelmach20, nickel-dime and
nsortur, all Arena B). By map: Arena 11-7 vs 7-11, Maze 4-14 vs 3-15,
maptestsmall 18-0 vs 17-1, Gridlock 0-18 vs 0-18. Stage 0 counters: early
bids halved (862 vs 1608 by r200 vs Sihal3), influence earned by r300 nearly
doubled. Attribution: as pre-registered (influence not bid before r600
compounds; the vote is still taken by the late ramp). OPEN: the three
reversals are one bot family (the piedPiper forks) on one map and side; not
traced. The head-to-head vs `g_iter3` was not a gate (zero-sum vote war
between builds identical except for bidding) and is played now for the
record.

Post-accept: `src/g_iter4` snapshotted; archetypes resynced; history and
charts updated; roster unchanged (the roster's rates on `g_iter3`: 25-50%
on every bot, 41.7% overall). The full quick-set roster baseline for
`g_iter4` is launched (216 games). Next target, named from the ladder: on
Gridlock (64x64, six 200-influence neutrals) both builds are 0-18 against
the roster while the other three maps are 33-21; a map-specific failure,
outside "production mix" (3 rejects there) -- scouting, navigation or
capture on a large map.

## Iteration 10 (in development, structural) -- threat-sized guards, else bank (2026-09-17 05:00 UTC)

**Target: the Gridlock 0-18, traced.** `nsortur.piedPiper` Gridlock as B
(`gauntlet/20260917-011706-roster-g_iter3/losses/`): we converted neutrals
at r126, r179, r193 and r276 (3 ECs to their 2 at r200) and lost every one:
r234 (a 1014 speech on an EC holding 5), r273 (436 on 23), r503 (526 on
10), r686 (1419 on 54). Each captured EC spent itself to nothing on
20-influence guards: 10-20 `t1c20` builds in a row from the moment it saw an
enemy (the `danger && guards < 2` branch, and the guard branch's
`min(max(20, inf/4), 60)`), because those guards die on contact and the
count drops below 2 again. Same shape in astelmach20 and arya-k Gridlock
games (ECs 2-3 -> 1 by r400-800). The opponents' captured ECs hold
thousands. Attempt cadence: four incremental attempts since Iteration 5
(cap 24, guard ratio, size, bid): this is the structural one.

*Pre-registration, candidate "threat":* the EC reads the largest enemy
politician conviction in its sensor range (ignoring < 40); a guard built
under threat costs at least `threat + 12` (a share above the attacker's
conviction at n = 1 after the tax), and if the EC cannot afford that it
builds nothing and banks (`bank=` counter) -- influence is conviction. Both
guard branches; the spend branch's big guards are unchanged. Decision-point
counters: guard costs from `@spawn t=1 role=2` (baseline: 20 in 80% of
builds under threat), `bank=` rounds, EC conversions of our ECs per game
(baseline 3-4 on Gridlock), captured-EC influence at conversion (baseline
5-54). Reachability: every EC turn with an enemy politician in range.
Price: fewer cheap guards against muckraker floods (they still get the
cheap guard when no politician is visible); banked influence idles.
History: the wall (rejected) diluted hits; this one either beats or banks.
Dose ladder: margin 12 / 40; THREAT_MIN 40 / 100. Gate: Stage 0 = Gridlock
vs astelmach20 and nsortur, both sides (4 cells; baseline 0/4): EC
conversions must drop and captured ECs must hold > 100 influence when hit;
then the roster on the screen set (72) vs `g_iter4`'s record on those cells
from the running quick-set baseline (+4). Falsifier: guard costs unchanged
(another branch buys 20s) or ECs still converted while holding < 100.

**Stage 0, threat, first arm, first game (astelmach20, Gridlock, A): lost
at r1371 (baseline r843).** Guard costs: 58 at 20, 209 at 21-60, 76 at
61-200 (baseline ~80% at 20); ECs held 2v6 through r1000 (baseline 1 by
r400). But `bank=` was 0, 0, 1, 31 across our four ECs and the eight
conversions of our ECs still landed on 8-133 influence: when the guard
branches declined, the spare-influence branch (and scouts) kept spending.
Completeness fix, not a new mechanism: the bank check now precedes every
branch. Relaunched as `threat-s0` (the half-engaged run stopped).

**Iteration 10 REJECTED at Stage 0 (05:15 UTC): threat-sized guards, else
bank.** The relaunched game (astelmach20, Gridlock, A) is identical round
for round to the half-arm game: `bank=` 0, 0, 3, 0 across our ECs, ten
conversions of our ECs holding 8-159 against attackers of 215-1866. The
decision point is unreachable: the EC's sensor (r^2 40, ~6 tiles) shows an
attacker 3-5 rounds before it speaks, and three rounds of banking are
nothing against 600+. Falsifier hit; reverted to `g_iter4`. Ledger: EC-level
defence cannot react in time; it has to be a standing posture.

**The mechanism the trace hands over:** a converted EC's influence and
conviction are set to the speech's surplus (RULES.md). We capture neutrals
with politicians costing exactly `neutralInf + 14`, so every EC we take
starts with ~0-40 influence (`CONVERT neutral:EC ... conv=-37`, `-82`,
`-43`, `-15`) and is converted back by the first politician that arrives;
the opponents take ours with 400-1900 speeches and their new ECs start with
hundreds. That is why they hold 6-7 ECs on Gridlock and we hold 1-2.

## Iteration 11 (in development) -- capture with a bank (2026-09-17 05:15 UTC)

*Pre-registration, candidate "capbank":* the capture politician's cost is
`min(spare, neutralInf + 14 + CAPTURE_BANK)` with CAPTURE_BANK 300 (one
constant; the old branch is CAPTURE_BANK 0). Decision-point counters: the
surplus at each of our conversions of a neutral (`CONVERT neutral:EC ...
conv=-N`, baseline 15-82; expect ~300), captured-EC influence when first hit
(baseline 5-54), conversions of our ECs per game (baseline 3-4 on
Gridlock), ECs held at r400 and r800 (baseline 1-2). Reachability: every
capture. Price: captures come ~300 influence later (about 30-60 rounds at
the opening income), and if the politician dies on the way the loss is
larger. Dose ladder 0 / 300 / 600. Gate: Stage 0 on Gridlock vs astelmach20
and nsortur, both sides (baseline 0/4; counters must move and ECs held at
r800 >= 2); then the roster on the screen set vs `g_iter4`'s record on those
cells from the running baseline (+4). Falsifier: surplus unchanged (captures
happen through another branch, e.g. the enemy-EC or all-in branch) or new
ECs still converted holding < 100.

## Standing tables (updated in place)

### Functional-area map

| area | last attempt | status |
|---|---|---|
| economy / production mix | Iteration 4: never idle (spare branch: guards / slanderers to 24 / hunters), 20/24 vs g_iter2, 19/72 vs 13/72 on targets | EC now builds every cooldown; opponents still field 3-7x the units (multi-EC, earlier captures) |
| bidding | Iteration 9: early bid cap influence/30 before r600 (accepted, +6 on 72 roster cells) | late ramp unchanged; bid war vs bidders still costs ~1700 by r300 |
| neutral-EC captures | Iteration 5 race (rejected, 3 arms) | opponents hold 6-8 ECs by r400, we hold 0-2; blocked on opening income |
| scouting / map knowledge | edge-seeking waypoints, fact cycling, sibling IDs | symmetry still often unresolved on multi-EC maps |
| navigation | greedy + bug + oscillation guard | aba 1-3% of moves; 0-18 on Gridlock vs the roster (both g_iter3 and g_iter4): next target |
| combat micro (politician speech) | radius/value evaluation, chip rule vs ECs | works vs example bot; unmeasured vs real opponents |
| EC defence vs politician streams | Iteration 3 wall (rejected) | target-tier losses are EC conversions r300-600 by 500-1750-conviction speeches; 1 reject |
| slanderer safety | flee any enemy, ring 8-45 | unmeasured vs hunters |
| muckraker hunting / blocking | expose nearest slanderer, sit at enemy EC | unmeasured |

### Closed-directions ledger

| direction | kind | measurement | re-open if |
|---|---|---|---|
| short-round smoke maps via map files | engine-impossible | map format has no round field; 400-round map played 1500 | never |
| one-round spawn ORDER flag | refuted | newborn acts next round; 0 captures -> 6 with two-round hold | never |
| threat-sized guards, else bank | rejected | the EC sees a 600+ attacker 3-5 rounds before the speech; bank fired 0-3 rounds; ECs converted holding 8-159 | standing posture only, never a reaction |
| minimum slanderer size 63 after the opening | rejected | mean size 67-90 at r100 and +30-50% income by r200, but 0/8 at Stage 0 and 12/23 head-to-head: the income fed the same sinks | what the income buys changes (bids, guards) |
| guards scale with threat (base 0, ratio 1/2) | rejected | politicians at r100 unchanged (spend branch fills 24 slanderers -> 14 guards); 1/6 vs 3 | never as a count rule; the income gap is 2-3x |
| slanderer cap 12 -> 24 alone | rejected | slanderers 24 at r100 but EC influence at r200 unchanged: the spend branch turned the income into guards (29-34 by r200); 0/6 vs 3/8 | guard sink removed (Iteration 7) |
| neutral-EC race by chip politicians (3 arms: 4 in flight / half-target chips / save for the chip) | rejected | chips built and spoke but flips <= 2 and the economy starved (EC influence 10-70 at r200-300); 0/8, 1/8, 0/8 on the motivating cells | opening income reaches >= 500 EC influence at r100 |
| EC wall of 1-influence muckrakers (4 or 7 adjacent tiles) | rejected | dilutes each hit to 0.2-0.35x but the units die to every big speech and the rebuild loses the race; roster 9/72 vs 13/72, five win->loss flips on maptestsmall B | wall units durable (conviction > share) or EC build cooldown much shorter |

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
