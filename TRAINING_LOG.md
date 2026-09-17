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

**Iteration 5 REJECTED (18:16 UTC): the neutral-EC race, three arms.**
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

**Why gauntlets are slower than in 2025 (18:28 UTC, user question).** Same
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

**Stage 0, capbank, first arm, first game (astelmach20, Gridlock, A): lost
at r1050; surplus unchanged (28-104).** The capture branch still fired at
the old threshold (`neutralInf + 14` affordable) and `min(spare, +300)` gave
270-294, so the bank never formed; our ECs were converted holding 8-72 as
before. Completeness fix: the branch waits until `neutralInf + 14 + 300` is
spare. Relaunched.

**Stage 0, capbank, second arm, first game (astelmach20, Gridlock, A): lost
at r848; the bank never formed.** The home EC's influence peaked at 300
(r150) and the spare branch spent it on guards (33 by r200); 514 was never
spare, so the only capture politician was a 92 sent at the enemy EC. The
neutral conversions I had read as ours in this trace were the opponent's
(the `CONVERT neutral:EC` event does not say who spoke; our captures show up
as conversions of `piedPiper:EC`). Completeness fix: while a neutral is
known, a capture is pending and the bank is not yet affordable, the spare
branch buys slanderers (income) but no guards or hunters (`save=` counter).
Relaunched as the third arm.

**Stage 0, capbank, third arm, first game (astelmach20, Gridlock, A): lost
at r653; mechanism engaged.** Bank formed (home EC 384 at r100, `save=5`),
two 521-cost capture politicians at r103 and r115, one conversion at r136
with surplus 311 (pre-registered target ~300; baseline 15-82); the new EC
held 586 at r200, captured on its own account (92, 56, 56, 106), and fell
at r485 to a 455 speech while holding 252 (baseline: 5-54). The other
capturer's target was taken by the opponent first (their neutral captures
at r121 and r126). Still lost: the home EC spent itself on capturers (203,
171, 312 at r253-378) and was converted at r359 by a 2123-conviction
politician while holding 22; units at r600 30 vs 878. ECs at r400 2v6, at
r600 1v7 (gate asked >= 2 at r800: missed). Classification: mechanism
engaged, outcome counter (captured-EC survival) moved 5x, cell not flipped,
evidenced reason (a 2123 attacker and a 30x army gap on this map). The
three remaining cells decide whether it goes to the 72-cell roster check
(70 of 76 maps have neutrals, so the bank matters everywhere, not only on
Gridlock).

**Record head-to-head, `g_iter4` vs `g_iter3`, quick set
(`gauntlet/20260917-044628-h2h-bid`): 16/24, +8.** The expected zero-sum
vote war did not happen: the lower early bidder wins more often, because the
influence it keeps compounds into units and the late ramp still takes the
vote. The pre-registration's exemption was unnecessary; noted so the next
bidding candidate uses the head-to-head normally.

**capbank Stage 0 complete: 0/4 (baseline 0/4)**, the two piedPiper forks
producing identical games (r653 as A, r793 as B). The candidate's roster
check on the screen set (`roster-capbank`, 72 cells) is running against
`g_iter4`'s record on those cells from the quick-set baseline.

**Noise floor measured (07:55 UTC): 12/24.** `g_iter4` vs `g_iter4_inert`
(identical code except the per-robot RNG seed offset 12345 -> 12346), quick
set, both sides (`gauntlet/20260917-070618-noise`): 12/24, after standing at
5/17 and 7/19 -- a perturbation that changes no policy moves single cells
freely. With 24 cells the win count's binomial sd is ~2.4 games; the
algorithm's rule (2 sd) makes `AcceptMargin` +5 on the quick set, not +4.
Re-reading the accepts: Iteration 2 (+24) and Iteration 4 (+16) are far
above the floor; Iteration 9 was accepted on +6 over 72 paired roster cells
(sd ~4.2 under the worst-case independence assumption, so ~1.4 sd) with the
+8 record head-to-head in support -- accepted at the floor's edge, and the
next candidate's baseline will re-measure it for free. Ledger: a single
24-game head-to-head resolves margins of +5 and up; a +2 or +3 is noise.

**Iteration 11 REJECTED (08:05 UTC): capture with a bank.** Roster check
on the screen set (`gauntlet/20260917-052724-roster-capbank`): 31/72
(43.1%) against `g_iter4`'s 33 on the same cells in its own screen run, and
31 vs 33 on the 68 cells its quick-set baseline had reached, with 4 cells
left that cannot bring the margin to +5 (or +4). Per opponent the candidate
sat at 3/8 against six of the nine. The mechanism was real (surplus 311,
captured EC held 252 when hit, 5x the baseline) and did not move games:
the bank delays the capture by ~300 influence, the opponent takes the
neutral first in about half the cases, and the games are still decided by
the army gap. Reverted to `g_iter4`. Neutral-EC captures area: 2 rejects
(race, capbank). Ledger: holding an EC needs an army, not a bank.

## Iteration 12 (in development) -- relayed threats for slanderers (2026-09-17 08:15 UTC)

**Target: an absolute degeneracy, area slanderer safety (0 rejects).**
Survey of the 126 `g_iter3` roster-baseline losses: our slanderers exposed
per game by r300 / r600 -- iliao2345 11.8 / 20.8, max-titov 4.3 / 22.4,
arya-k 4.0 / 12.8, the piedPipers 2.8 / 5.5, Sihal3 1.2 / 2.5, qawsedrftgzh
0.8 / 5.5, 123kevinlee 0.6 / 3.6. Each exposure removes a slanderer's income
and feeds the opponent's speech buff. Mechanism: a slanderer flees only what
its own sensor shows (r^2 20, ~4.5 tiles); a muckraker exposes at r^2 12 and
moves on a 1.5 cooldown against the slanderer's 2.0, so once seen it is
usually caught. The EC's sensor (r^2 40) sees the hunter first and says
nothing.

*Pre-registration, candidate "relay":* when the EC is in danger it
broadcasts the nearest enemy's position (ENEMY_UNIT flag, odd rounds, above
the enemy-EC rotation); slanderers read home every 2 rounds (was 5) and flee
a relayed position within 8 tiles (`RELAY_FLEE_D2` 64) reported in the last
12 rounds, before it enters their own sensor. Decision-point counters:
`@relayflee` count (baseline 0), exposures against us by r300 and r600
(baseline above; expect halved vs iliao2345 and max-titov), slanderer count
and income by r300. Price: slanderers moving on relayed threats earn while
moving (slanderers earn regardless of movement) but crowd toward home; the
EC's flag slot is taken on odd rounds under danger (edges and neutrals are
still broadcast on even rounds). History: nothing in the ledger. Dose:
RELAY_FLEE_D2 64 / 100. Gate: Stage 0 on maptestsmall and Arena vs iliao2345
and max-titov, both sides (8 cells; `g_iter4` won 5 of them in the bid roster
run): exposures must fall; then the roster on the screen set vs `g_iter4`'s
baseline (+5). Falsifier: exposures unchanged (the relay arrives too late or
the flee runs into the hunter) or slanderer income falls.

**relay Stage 0, first arm (4 games read): the relay never fired**
(`@relayflee` 0 in every game; exposures vs iliao2345 13 by r300 as in the
baseline). Cause: the EC sets the relay on odd rounds and rewrites its flag
on even rounds; it acts before its slanderers within a round, so a slanderer
reading on even rounds never sees the odd-round flag. Completeness fix:
slanderers read the home flag every round (one flag read). Relaunched.

**`g_iter4` roster baseline complete (08:20 UTC,
`gauntlet/20260917-044622-roster-g_iter4`): 107/216 (49.5%)** against
`g_iter3`'s 90/216 on the same 216 cells: +17 (sd ~7.3 under independence,
2.3 sd), which puts Iteration 9 well above the floor after all. Per
opponent on `g_iter4`: iliao2345, max-titov and arya-k 37.5%, 123kevinlee
50%, Sihal3 and the three piedPipers 54%, qawsedrftgzh 67% (above the band;
re-tiered at the next scan). Capbank's final comparison on all 72 shared
cells: 31 vs 33 (rejected, as logged). This baseline is the comparison for
every candidate until the next accept.

**relay Stage 0, second arm (5 of 8 read): mechanism engaged, exposures
halved or better.** `@relayflee` (one log line per 10 flees): 45, 37, 111,
3, 15 per game. Exposures against us by r300 / r600: iliao2345 Arena B 1 / 1
(baseline 11.8 / 20.8 per game), iliao2345 maptestsmall A 7 by r300, B 5 / 9;
max-titov maptestsmall 0 / 0 and 3 / 10 (baseline 4.3 / 22.4). Cells 4/5 so
far (one loss on votes vs max-titov at r1500, where the slanderer count fell
to 0 by r600: relayed flight does not stop a sustained hunt, it delays it).
The Arena B game ended by annihilation at r657 with 122 slanderers alive
across our ECs. Head-to-head vs `g_iter4` launched; the roster check on the
screen set follows when the Stage 0 slots free.

**relay Stage 0 complete: 4/8** against `g_iter4`'s 5 on the same cells
(within a cell of noise; the three Arena losses vs max-titov and iliao2345
went to r1500). Counters moved as pre-registered; the head-to-head (+5
gate, early stop) and the 72-cell roster check decide.

**relay head-to-head vs `g_iter4`: early-stopped at 6/14 (cannot reach
17 of 24, the +5 gate).** By the rule this rejects Iteration 12. The
head-to-head's opponent fields hunters only through the spare branch, so it
prices the defence weakly; the 72-cell roster check is allowed to finish
(now at 6 jobs) for the ledger's sake: if it comes in at +5 or better on the
shared cells the mechanism is kept as a re-open condition, otherwise
slanderer safety by relayed flight is closed.

## Session summary, 2026-09-16 18:25 -> 09-17 08:45 UTC

| iteration | candidate | result |
|---|---|---|
| 2 | EC census counts aged slanderers as guards | accepted, 24/24 vs g_iter1 (`g_iter2`) |
| 3 | EC wall of muckrakers (2 arms) | rejected, 9/72 vs 13 on targets |
| 4 | never idle: spare-influence branch | accepted, 20/24 and 19/72 vs 13 (`g_iter3`) |
| 5 | neutral-EC race by chips (3 arms) | rejected, economy starved |
| 6 | slanderer cap 24 | rejected at Stage 0 |
| 7 | guards scale with threat | rejected at Stage 0 (falsifier) |
| 8 | minimum slanderer size 63 | rejected, 12/23 head-to-head |
| 9 | early bid cap influence/30 | accepted, 33 vs 27 on 72 roster cells, +8 head-to-head, +17 on the 216-cell baseline (`g_iter4`) |
| 10 | threat-sized guards, else bank | rejected, decision point unreachable |
| 11 | capture politicians carry a 300 bank (3 arms) | rejected, 31 vs 33 on 72 roster cells |
| 12 | EC relays threats, slanderers flee early | rejected by the head-to-head gate (6/14); roster check finishing |

Ladder: on the 36 cells every build shares, 34% (g_iter2) -> 38% (g_iter3)
-> 46% (g_iter4); roster baseline 41.7% (g_iter3) -> 49.5% (g_iter4) on
216 cells; 9 roster bots at or above 20%. Infrastructure: games moved to
`battlecode-dev`; roster from a three-map incremental scan; per-cell
history and a per-build ladder chart; `--hits`, extended `--navstats`, the
income ledger; noise floor 12/24 (gate +5). What the rejects bought: the
losses are decided by an army gap (units alive 3-10x, ECs held 6-8 vs 1-2)
that no reallocation of our income has moved; EC-level defence cannot react
in time; captured ECs need an army to hold, not a bank. The next structural
attempt should be the army itself: how politicians and muckrakers are
grouped, aimed and timed.

**Iteration 12 closed (09:30 UTC): relay roster check 35/72 vs `g_iter4`'s
33 on the same cells (+2, inside the noise; the gate is +5).** Flips: 5
loss->win (arya-k Arena A and Gridlock A -- the first roster win on Gridlock
for any build -- and the three piedPipers on Arena B), 3 win->loss
(123kevinlee Arena B, arya-k Arena B, max-titov maptestsmall A). By map:
Arena 13 vs 11, Gridlock 1 vs 0, Maze 4 vs 4, maptestsmall 17 vs 18. With
the head-to-head early-stopped at 6/14, rejected; reverted (already). Ledger:
halving exposures is real and worth about two roster cells on its own; it
is kept as a component for an army doctrine (guards that hunt the hunters
would turn relayed sightings into kills instead of flight), not as a
standalone candidate. Slanderer safety area: 1 reject.

## Iteration 13 (in development, structural) -- the army: attack politicians instead of guards (2026-09-17 10:10 UTC)

**Target: a capability gap named from the ladder.** Politician spawns per
game in 28 sampled `g_iter4` roster losses (conviction bucket, rounds
<= 300 / 301-600): them <100: 58 / 126, 100-299: 8.7 / 12.3, 300-599: 7.1 /
21.2, >= 600: 3.0 / 4.7; us <100: 68 / 56, 100-299: 13.4 / 35, 300-599: 1.0
/ 5.7, >= 600: 0.06 / 1.2. By r300 the opponents have sent ten politicians
of 300+ (the EC-takers) and we have sent one; the same budget goes into ~80
politicians under 300 (danger guards at 20-30, spare-branch guards at a
third of the spare) that take nothing. Attempt cadence: this is the
structural attempt (the last one was Iteration 10; since then size, bid,
capbank, relay).

*Pre-registration, candidate "army":* the spare branch no longer buys
guards or hunters. It buys slanderers for income while it can; otherwise,
when `spare >= ATTACK_SIZE` (300) and a hostile EC is known, it builds an
attack politician of `max(300, targetInf + 114)` (capped by the spare) aimed
at the nearest hostile EC, neutral first by distance, enemy otherwise
(role CAPTURE, the politician's existing capture logic); otherwise it saves
(`save=` counter). The danger-guard branch and the normal guard branch (cap
`4 + slanderers/2`) are unchanged. Decision-point counters: `attack=`
builds and politicians >= 300 spawned by r300 / r600 (baseline 1.0 / 5.7;
expect >= 5 / >= 15), politicians < 100 spawned by r600 (baseline 124;
expect halved), conversions of hostile ECs by us, ECs held at r400 and
r800. Price: fewer bodies around home against muckraker floods (the
relay's lesson: exposures may rise); attack politicians that die en route
lose 300+ each. History: race (chips) and capbank were capture-only and
starved or delayed; this one keeps slanderers first and sends 300+ units at
whatever is nearest. Dose: ATTACK_SIZE 300 / 500. Gate: Stage 0 on Arena and
Gridlock vs Sihal3 and arya-k, both sides (8 cells; `g_iter4` scored 4 of
them in its baseline): the counters must move and the cell count must not
fall; then head-to-head vs `g_iter4` (+5, early stop) and the 72-cell
roster check vs the baseline (+5). Falsifier: `attack=` near 0 (spare never
reaches 300 because slanderers take it: then the mechanism needs a save
rule) or exposures explode.

**army Stage 0, first four (Arena): 2/4, mechanism engaged.** Politicians
>= 300 spawned by r300 / r301-600: 18 / 27 vs Sihal3 (baseline 1.0 / 5.7)
and 7 / 10 vs arya-k; politicians < 100 unchanged (108-138 by r300: the
danger and normal guard branches, not the spare branch). Sihal3 Arena A: 9
hostile ECs converted by us, 3 of ours, 5v1 ECs at r400, annihilation at
r461. arya-k Arena A: 21 conversions by us against 19 of ours, 2v6 ECs at
r400, lost at r739 -- arya-k sent 12 politicians >= 300 by r300 and 43 in
r301-600 with 292 under 100: an army bot. Exposures 0 and 11.

**army Stage 0 complete: 2/8** (`gauntlet/20260917-100233-army-s0`) against
`g_iter4`'s 3 on the same cells (arya-k Arena B lost, all four Gridlock cells
lost by both). Within a cell; counters far past their targets. Head-to-head
(+5, early stop) and the 72-cell roster check are running.

**army head-to-head vs `g_iter4`: early-stopped at 6/14.** Twelve of the
fourteen games went to the vote at r1500 (the two annihilations, CrossStitch,
split 1-1): a build that spends its spare on 300+ politicians loses the late
vote war to a twin that banks and bids. By the rule this rejects Iteration
13; the head-to-head cannot price an army doctrine against a build without
one (section 3's blind spot, as with bidding), so the 72-cell roster check
finishes at 7 jobs and decides the ledger wording: +5 or better keeps the
army as the re-open path (with the head-to-head gate re-examined for
doctrine changes), anything less closes it.

**Iteration 13 REJECTED (11:05 UTC): the army.** Roster check on the
screen set: 31 vs `g_iter4`'s 33 on the first 70 shared cells with two left
(final numbers appended when the run closes); head-to-head early-stopped at
6/14, twelve of fourteen decided on votes. The mechanism was the strongest
of the session -- 300+ politicians 18/27 per phase against 1/6, nine
hostile ECs converted in one game -- and it moves the roster by nothing:
what it takes in ECs it gives back in the late economy and the vote, and
against army bots (arya-k) it trades even. Reverted to `g_iter4`. Ledger:
attack politicians from the spare branch alone are a wash; the army needs
timing (waves, not a stream) and a bank behind it, i.e. the doctrine is a
schedule, not a branch.

## Iteration 14 (in development) -- eight early scouts (2026-09-17 11:20 UTC)

**Target: an absolute degeneracy, area scouting / map knowledge (0
rejects).** In 8 sampled `g_iter4` losses on Gridlock and Maze our EC knew
its first neutral at r50-150 and never knew all of them in 7 of 8 games
(`nEC` in `@econ` never reached the map's count), while the first neutral
was taken by anyone at r89-145 (Gridlock) and r284-413 (Maze). Our
`--navstats` coverage on these maps is 25-44% at r900 against the
opponents' 99%; four 1-influence scouts explore a 4096-tile map.

*Pre-registration, candidate "scouts8":* `EARLY_SCOUTS` 4 -> 8 (one
constant; dose 4 / 8 / 12). Decision-point counters: the round the EC knows
all neutrals (baseline: never, in 7 of 8), coverage at r300 from
`--navstats`, first-neutral report round; price counters: slanderers and EC
influence at r100 (four more 1-cost builds take 4 build slots early).
Outcome: neutral captures by us by r300. Gate: Stage 0 on Gridlock and Maze
vs 123kevinlee and Sihal3, both sides (8 cells; `g_iter4` won 2: Sihal3
Maze A and B... to be read from the baseline); all-known round must appear
and the opening must not lose more than one slanderer at r100; then
head-to-head (+5, early stop) and the 72-cell roster check. Falsifier:
all-known still never (scouts die or wander the same ground) or slanderers
at r100 down by two or more.

**scouts8 Stage 0, first game (123kevinlee, Gridlock, A): lost at r1029
(baseline lost too); mechanism engaged.** All six neutrals known at r200
(baseline: never in 7 of 8), first at r50; coverage 49.9% (baseline 25-44%
at r900); opening at r100: 17 slanderers and 48 influence (baseline 12-19),
so the four extra scouts cost nothing visible.

**scouts8 Stage 0 (7 of 8 read, 0 wins, baseline 0; one timeout):
mechanism engaged.** All neutrals known at r200 in 4 of 6 games (baseline:
never in 7 of 8; the two Sihal3 Gridlock games still never), first neutral
at r50 (Gridlock) / r150 (Maze, unchanged: Maze's four are 20 tiles from
either corner behind swamp), coverage 50-68% (baseline 25-44%), opening at
r100 unchanged (16-17 slanderers, 48-79 influence). The cells are the
hardest on the roster and did not move; the head-to-head (+5, early stop)
and the 72-cell roster check decide.

**scouts8 head-to-head vs `g_iter4`: early-stopped at 6/14**, the third
candidate in a row to stop at exactly 6/14 (relay, army, scouts). Against a
twin that does not punish the deficiency each candidate fixes, the pair plays
at the noise floor (the inert pair went 12/24), and the +5 gate stops it
after fourteen games. By the rule this rejects Iteration 14; the 72-cell
roster check, the instrument that can see scouting, finishes and decides the
ledger wording. Note for the algorithm: the head-to-head resolves only
changes that matter against ourselves (economy, bidding); doctrine and
map-knowledge changes need the roster, and the head-to-head should be run
second for those, or skipped when the roster check is already +5.

**Iteration 14 REJECTED (13:05 UTC): eight early scouts.** Roster check
stopped at 40 of 72 shared cells: 18 vs `g_iter4`'s 23, a regression
signature that cannot reach +5 in the remaining 32 (stopped to save the
games); head-to-head early-stopped at 6/14. The counters were as
pre-registered (all neutrals known by r200, coverage 50-68%, opening
intact), so knowing where the neutrals are does not help a build that
cannot take or hold them; the four extra scouts are four extra bodies the
hunters kill and four build slots. Reverted to `g_iter4`. Scouting area: 1
reject. Ledger: map knowledge is not the binding constraint at this level.

## Iteration 15 (in development) -- deposit: expired slanderers cash in at the EC (2026-09-17 13:20 UTC)

**The loop stalled (five rejects in a row), so section 7 applies.** The
accepts are well measured (ablation adds little); the cheapest untried lever
was micro. New instrument `replay-dump.sh --speeches` (per team: speeches,
conviction spent, share landing on enemies, enemy ECs, friendlies, empty).
Six `g_iter4` roster losses: ours lands 40-82% on enemies and 9-20% on
friendlies; the opponents' lands 5-63% on enemies and **36-88% on
friendlies** (123kevinlee 36%, piedPiper 49%, Sihal3 57%, iliao2345 88%),
at a similar total conviction (e.g. 106k vs 97k against arya-k). That is
not bad aim: RULES.md, empower -- friendly units gain conviction up to
their initial cap, friendly ECs gain influence and conviction uncapped. A
slanderer that has earned for 300 rounds becomes a politician holding its
purchase price as conviction; the opponents walk it home and speak, and the
EC banks it. Ours (47 idle politicians holding thousands in the Iteration 2
probe) guard forever. This is where their EC banks of thousands come from,
and it removes the count cap's cost: a slanderer's price comes back.

*Pre-registration, candidate "deposit":* a politician created by
camouflage expiry with conviction >= 100 walks home and, on an orthogonal
tile next to its EC with at most one other robot in radius 1, empowers at
radius 1; with enemies within r^2 20 it guards instead that turn; a diagonal
tile steps to an orthogonal one first. Decision-point counters: `@deposit`
count and conviction per game (baseline 0), `--speeches` toFriend share
(baseline 9-20%; expect > 40%), EC influence at r400 / r800 (baseline
100-500). Price: those politicians no longer guard the slanderer ring (the
EC-built 20-60 guards remain), and each deposit is a body gone. History:
no ledger entry; the wall and threat candidates were about EC conviction
from the outside, this is from our own units. Dose: DEPOSIT_MIN 100 / 200.
Gate (doctrine change, per 4.5.1): the 72-cell roster check first (+5 over
`g_iter4` on the same cells), Stage 0 on Arena and Gridlock vs Sihal3 and
arya-k for the counters (baseline 3/8), the head-to-head as a regression
check on an accept. Falsifier: `@deposit` near 0 (politicians never reach
an uncrowded orthogonal tile) or toFriend unchanged.

**deposit Stage 0, first two (Sihal3, Arena, A and B): 2/2, both by
annihilation at r547 and r689 (the baseline won them on votes at r1500).**
Deposits 1 (107) and 3 (368): the first slanderers expire at r310-400 and
these games ended soon after, so the mechanism barely had time to fire;
toFriend 13% and 27% (baseline 9-20%). EC influence at r400: 371 and 1400
against the baseline's 100-500; 5v1 ECs at r400 in both. The Gridlock cells,
which run long, will show the counter properly; the roster check waits for
them.

**deposit Stage 0, the first long game (Sihal3, Gridlock, A, lost at r1500
as in the baseline): the mechanism fires.** 52 deposits worth 7827
conviction (13 with the EC alone in range); toFriend 28% (baseline 9-20%);
EC influence 1076 at r400 and 1736 at r600 against the baseline's 100-500,
and ECs held 3v2, 4v4, 5v3 at r400/600/800 on a map where every earlier
build was 1v7 by r400. Sihal3 still out-banked us late (69k to 27k at
r1200) and took the vote. 377 camouflage expiries produced 52 deposits, so
most expired slanderers never reach an uncrowded tile: room for a
completeness pass after the roster reads. Roster check launched (72 cells,
first per 4.5.1).

**deposit Stage 0 complete: 3/8**, equal to `g_iter4` on the same cells,
with the two Sihal3 Arena wins now annihilations instead of votes and
arya-k Arena B won; counters as logged (52 deposits in the long game). The
72-cell roster check decides, with the futility rule at 36.

**Re-tier on `g_iter4` (14:00 UTC): 20 formerly locked bots on the three
stage-1 maps (`gauntlet/20260917-110555-retier-g_iter4`, 17/120, 6
timeouts among the finalists), combined with the 216-cell roster baseline
(`gauntlet/tiers-g_iter4.txt`).** Unlocked: awesomelemonade 2/6,
Scott-Poole 2/5, jmerle 3/6, rzhan11 1/5; ten bots sit at exactly 1/6
(Victoriano, VittalT, aidan-mundy, anshgs, edlwang, iyzg, mvpatel2000,
ryanbai1412, winkelmantanner) and seven remain at 0 (IvanGeffner, JasonYe,
BSreenivas, StoneT2000, mhahn, pranayagra, rqi3). Ladder position: 13 bots
at or above 20% (was 9). New band (8): rzhan11 20%, awesomelemonade 33%,
arya-k, iliao2345 and max-titov 38%, Scott-Poole 40%, 123kevinlee and
jmerle 50%; Sihal3, the three piedPipers (54%) and qawsedrftgzh (67%) move
to peers. The roster file switches to the new band after the deposit
decision, so its 72-cell comparison stays on one roster.

**Iteration 15: NEAR MISS (14:50 UTC).** Roster check on the screen set
(`gauntlet/20260917-*-roster-deposit`): 35 vs `g_iter4`'s 33 on the same 72
cells (+2), only 4 flips (3 loss->win: the three piedPipers on Arena B; 1
win->loss: max-titov Maze B), McNemar p = 0.31; Arena 14 vs 11, the other
maps level. Under the +5 gate a reject; under the near-miss rule (within
1 sd, no regression signature) it earns refinement of the same mechanism.
The counters say why the effect is small: 377 camouflage expiries produced
52 deposits in the long game, and 68 of 72 cells were unchanged, so the
mechanism only acts in games that run past r400 and only for the minority of
expired slanderers that reach an uncrowded orthogonal tile with no enemy
within r^2 20. Refinement candidates: allow n <= 3, ignore muckraker-only
threats, accept radius 2 from a diagonal tile. `src/bot` reverted to
`g_iter4` (the deposit code is in commit 7ea14ec); the refinement waits for
the ladder census (user instruction) to rank it against the other causes.

**Roster switched to the g_iter4 band (8 bots)**; the four newcomers get
their baseline cells on the screen set now (`roster-topup`).

## Ladder census, `g_iter4` roster baseline (2026-09-17 15:00 UTC, user instruction)

`tools/loss-census.sh` over the 109 loss replays of
`gauntlet/20260917-044622-roster-g_iter4` (97 read at the time of writing;
the 14 losses to the four newly unlocked bots follow as a second pass):

- **How lost:** 51 annihilations (median r839) and 46 vote losses at r1500
  (median deficit 222 votes).
- **The EC:** our first EC is converted in 87 of 97 losses, at r214 / r278 /
  r460 (quartiles), by an attacker of median conviction 395, while holding a
  median of 19 influence.
- **The snowball:** ECs at r400 1.6 vs 4.7, at r800 1.7 vs 5.5; influence
  earned by r300 median 6741 vs 40436 (5.2x); units at r600 200 vs 455;
  slanderers exposed by r600 median 9, ten or more in 36 games.
- **Where:** Andromeda 16, Gridlock 16, Corridor 14, Snowflake 11, Maze 10,
  CrossStitch 7, Arena 7, Blotches 6, Circles 5, Saturn 4, Radial 1,
  maptestsmall 0. The two maps without neutral ECs produce one loss between
  them; the multi-neutral maps produce 67 of 97.
- **Who:** arya-k, iliao2345 and max-titov 15 each; 123kevinlee 12; Sihal3,
  astelmach20 and nickel-dime 11; nsortur 7.

Reading: the losses are one mechanism seen from different angles. Neutral
ECs are taken early by the opponent (first landings at r89-145 on Gridlock
in earlier traces), the extra ECs multiply production and income five-fold
by r300, and a 400-conviction politician converts our single EC, which holds
nothing because the never-idle branch spends everything, at r278. Every
capture candidate so far (race, capbank) waited for slanderer income
before capturing and was starved or late; the deposit candidate banks only
after the first slanderers expire at r310, after the median EC loss. The
untried lever is the opening: the starting 150 plus the first fifty rounds
of passive income is a full-price capturer for any neutral under ~300, and
the scouts report the first neutral by r50 on the large maps.

### Census, final numbers (2026-09-17 15:40 UTC)

All 107 parsed roster losses: ECs 1.8 v 2.7 at r200, 1.6 v 4.6 at r400,
1.8 v 5.3 at r800; influence earned by r300 median 6,859 v 40,436 (ratio
5.2x); units alive at r600 median 221 v 454; first EC lost at median r288
holding 19; 55 vote losses, 52 annihilations. By map at r400 (us v them):
Andromeda 1.3 v 4.5 (18 losses), Gridlock 2.2 v 5.3 (18), Corridor 1.0 v 5.3
(15), Maze 2.0 v 4.8 (13), Snowflake 1.1 v 2.9 (12).

The 14 losses to the four newly unlocked bots (speech shares now filled;
`loss-census.sh` had empty columns under mawk): same mechanism on the
multi-neutral maps (ECs 0-3 v 3-8 at r400, first EC lost r122-447 holding
5-27; rzhan11 takes every neutral by r400 and spends 41-80% of its politician
conviction on its own ECs, i.e. the deposit mechanism at scale). The four
maptestsmall losses are a second, smaller mechanism: single-EC map, income
level or better than theirs at r300 (8,036 v 13,645; 6,193 v 6,077; 9,925 v
3,807; 8,589 v 6,104), yet the EC falls at r587-630 holding 15-110 to
annihilation. Our politicians spend 78-79% of conviction on enemy units there
and only 8-11% on friendlies, so the bank is never built; theirs is. Ranked
second, behind the opening (which the multi-neutral maps decide).

**Roster baseline top-up complete (15:50 UTC,
`gauntlet/20260917-145018-roster-topup-g_iter4`): `g_iter4` 10/32 against
the four newly unlocked bots on the screen set** (Scott-Poole 3/8, rzhan11
1/8, jmerle 4/8, awesomelemonade 2/8; 2 timeouts). With the retained bots'
screen cells from the 216-cell baseline (arya-k 4/8, iliao2345 5/8,
max-titov 3/8, 123kevinlee 4/8 = 16/32), the `g_iter4` baseline on the new
8-bot roster's 64 screen cells is **26/64** (26/62 read). Roster decisions
from here: 64 cells, +5 gate, futility at 32 cells if 3 or more behind.

## Iteration 16 (in development, structural) -- the opening capture (2026-09-17 15:10 UTC)

**Target: the census's single mechanism.** Neutral ECs are taken early by
the opponent and multiply everything after; our first EC falls at r278
holding 19; the multi-neutral maps hold 67 of 97 losses.

*Pre-registration, candidate "opening":* from round 1 the EC builds its
four scouts and one slanderer (income), then banks; the moment a neutral of
at most 320 influence is known and `inf - 5 >= target + 14 + 30`, it builds
a full-price capturer (+30 so the new EC starts with a bank) and the opening
ends; if no such neutral is known by r120 the opening ends and the normal
build resumes. A captured EC runs the same code, so it banks its passive
income and takes the next neutral in turn (the snowball). Emergency guards
(`danger && guards < 2`) keep priority. Decision-point counters:
`@opening capture` (round, target; baseline none), round of our first
neutral conversion (baseline r158-489 in ~40% of games, never in the rest),
ECs at r200 and r400 (baseline 1.6 at r400), influence earned by r300 (the
price: baseline median 6741), first-EC-loss round and influence held
(baseline r278, 19). Reachability: scouts report the first neutral by r50 on
Gridlock and Andromeda; the bank reaches 250-350 by r40-70. Price: the
slanderer economy starts 40-60 rounds later; a capturer that dies en route
loses 250-350 and the opening. History: race and capbank (rejected) captured
from slanderer income, never from the opening bank. Dose: OPENING_CAPTURE
off (= g_iter4) / on; OPENING_MAX_TARGET 320 / 500. Gate (doctrine, 4.5.1):
Stage 0 on Gridlock and Andromeda vs arya-k and max-titov, both sides (8
cells; counters), then the 72-cell roster check on the new 8-bot band
against the g_iter4 baseline plus its top-up (+5), head-to-head as a
regression check on an accept. Falsifier: no `@opening capture` by r120
(neutrals not known in time or too expensive), or ECs at r400 unchanged.

**opening Stage 0, 5 of 8 read (16:05 UTC): 2 wins (Gridlock vs arya-k A,
Gridlock vs max-titov A) against `g_iter4`'s 0/8 on these cells.** The
mechanism fires: on Gridlock we hold 2 ECs at r100 (baseline 1); on
Andromeda the logged dev game (`LOG_OUT`, run-dev.sh now resolves benchmark
names through the manifest) shows the first neutral report only at r100
(nEC=0 at r50, 2 at r100), `@opening capture r=109 target=151 cost=195`,
the capturer arriving around r215 (2 ECs at r220), and the new EC lost by
r260 while arya-k went 1-3-5-6 ECs at r100-400. So the reachability premise
("scouts report the first neutral by r50") holds on Gridlock, not on
Andromeda, where the scouts hunt edges and symmetry candidates and the
capturer walks 19 tiles in 100 rounds. Roster check launched at once (64
cells, `gauntlet/*-roster-opening`, +5 over 26/64, futility at 32 if 3 or
more behind); the last three Stage 0 games finish beside it.

**opening Stage 0 complete: 3/8** (`gauntlet/20260917-151617-stage0-opening`;
arya-k 1/4, max-titov 2/4) against `g_iter4`'s 0/8 on the same cells. The
roster check runs at 7 in parallel; first 7 cells 0 wins (rzhan11 4 cells,
awesomelemonade maptestsmall both sides lost at r273-276: the 120-round bank
on a map with no neutral is the price, paid early).

**opening dose 1: FUTILITY STOP at 13 cells (16:45 UTC).** 0/13 against
`g_iter4`'s 4/13 on the same cells, four flips all against (arya-k
maptestsmall B r476, rzhan11 maptestsmall A r421, awesomelemonade Arena A
r450 and B r470), none for. The pre-registered futility rule (3 behind at
32) was passed at 13, so the remaining 51 games were not played. The flips
are all early annihilations on maps where no neutral is reported by r120:
the 120-round bank stalls the slanderer economy and builds no guard (the
opening branch sat *before* the emergency-guard branch, against the
pre-registration), so a rush kills the EC at r420-480 where `g_iter4` lived.
Gridlock and Andromeda, the Stage 0 maps, hid this price.

*Dose 2 (pre-registered):* the opening branch moves after the emergency
guard; OPENING_UNTIL 80 (bank at most 80 rounds); the opening ends as soon
as the map bounds are known and no neutral has been reported (maptestsmall:
by r30). Stage 0 = the four flipped cells plus the three dose-1 wins and
arya-k Andromeda A (`tools/cells-opening2.txt`, 8 cells): the bar is
recovering all four flips while keeping at least two of the three wins;
below that the opening is rejected and the next candidate targets the
maptestsmall attrition mechanism (deposit refinement).

**opening dose 2: REJECTED at Stage 0, 0/4 on the four flipped cells
(17:20 UTC).** All four stay lost (arya-k maptestsmall B r458, rzhan11
maptestsmall A r1016, awesomelemonade Arena A r373, B r453); the remaining
four cells were not played. The logged dev game of arya-k maptestsmall B
shows why the exit signal never fired: the scouts had found only two of the
four edges by r50 and three by r100, so "bounds known and no neutral" never
held; the EC banked 161 by r50 with one slanderer, spent it on three
30-guards at r57-80 under threat, and at r86 the normal enemy-EC branch
(`inf - reserve() >= 200`) threw the remaining 202 at the enemy EC; the
economy stood at 1 slanderer until r150 (3) and 11 at r200, and the EC died
at r458. Bank-first is structurally priced on every map without an early
neutral, and the exit signals are unreliable.

*Dose 3 (pre-registered), "saving mode":* no bank-first. The normal
`g_iter4` build runs from round 1; only when a neutral of at most 320 is
known before r150 does the EC switch to saving (keeps its first two
slanderers, emergency guards keep priority, everything else saved) until it
can send the full-price capturer (+30 bank); after r150 the normal capture
branch applies. Maps with no early neutral pay nothing, which is the
falsifier for the four flipped cells: they must all be won again (they are
`g_iter4`'s game there). The three dose-1 wins (Gridlock A vs arya-k and
max-titov, Andromeda B vs max-titov) measure what saving mode keeps of
bank-first; bar: 4/4 flips recovered and at least 1 of the 3 wins kept.
Stage 0 on the same 8 cells (`gauntlet/*-stage0-opening3`).

**Iteration 16: REJECTED (17:55 UTC).** Dose 3 Stage 0 2/8
(`gauntlet/20260917-155449-stage0-opening3`): the two maptestsmall flips
recovered (no neutral, so saving mode never fires and the build is
`g_iter4`'s), both Arena flips still lost, and all three dose-1 wins lost
(Gridlock A vs arya-k and max-titov, Andromeda B vs max-titov). Across the
three doses the family is 3/8, 0/4+, 2/8 against `g_iter4`'s 4/8 on the
dose-2/3 cells and 0/8 on the dose-1 cells. What the Arena replays showed
is the lesson: dose 3 wins the opening outright (Arena B: ECs 5 v 2 at
r450, 152 politicians and 92 slanderers against 14 and 41) and still loses
at r899, because every one of our ECs is converted while holding 5-61
influence (`--hits`: 13, 15, 10, 5, 22, 61 at the moment of conversion, by
107-500-conviction politicians) while awesomelemonade's two ECs hold
5,054. The never-idle doctrine (Iteration 4) keeps each EC at
`reserve()` = max(2*bid, 10), so more ECs are more empty targets, and the
ECs ping-pong (we re-convert with 16-44, they take them back). `src/bot`
is back to `g_iter4`. An opening is worth revisiting only once ECs keep a
bank.

## Iteration 17 (in development, economy) -- the EC floor (2026-09-17 18:00 UTC)

**Target: the bank that is never built** (census mechanism 2, and the
reason Iteration 16's early leads evaporated). Non-capture spending
(slanderers, guards, spare, the rich-and-idle capture) must leave
`floored()` = max(reserve(), min(FLOOR_MAX, round * FLOOR_PER_ROUND / 100))
in the EC; emergency guards (`danger && guards < 2`) and neutral captures
keep the old reserve. Dose: FLOOR_PER_ROUND 50 / FLOOR_MAX 300 (100 at
r200, 300 from r600); 0 = `g_iter4`; second arm 100/600 if the first is a
near miss. Counters: EC influence at r400/r600 (baseline median 282 at r400
across losses, 5-61 at conversion), first-EC-loss round and influence held
(baseline r288, 19), converted-EC count per game (`--hits`), slanderers at
r300 (the price: baseline ~24). Reachability: the floor is reached by
passive income alone (0.2*sqrt(r) per round, ~60 by r200 cumulative
excluding slanderer income) plus slanderer income, so it binds only when the
EC would otherwise spend to zero. Price: up to 300 influence of units per
EC not built; guard count falls at the margin. History: Iteration 4
(never-idle) was accepted on the opposite argument (spending beats
banking) and the bid cap (Iteration 9) let the EC keep more early; this
tests the middle. Gate (economy, 4.5.1): head-to-head vs `g_iter4` first
(24-game quick set, +5, early stop), then the 64-cell roster check (+5
over 26/64, futility at 32). Falsifier: converted-EC influence unchanged
(the floor is spent before the hit lands) or first-EC-loss unchanged.

**Iteration 17: REJECTED at the head-to-head, 3/12 (18:40 UTC,
`gauntlet/*-h2h-floor`, stopped one game before the formal early-stop
line: 15/24 needed twelve straight wins).** Eight of the nine losses are
vote losses at r1500 (maptestsmall both sides, Arena A, Andromeda B,
Corridor A, Blotches B), one annihilation each on Blotches A, CrossStitch
A and Circles A; wins on Andromeda A, Arena B, CrossStitch B. In the
mirror the floored EC holds up to 300 that the never-idle twin turns into
slanderers, and the compounding difference decides the votes. What it
closed: a static floor is the wrong shape of bank against ourselves, so
it cannot pass the economy gate; the bank has to come from influence
that would otherwise be lost (the deposit mechanism, Iteration 15, +2 on
72) rather than from influence that would otherwise be spent. `src/bot`
back to `g_iter4`.

## Iteration 18 (in development, doctrine) -- deposit, refinement 1 of the Iteration 15 near miss (2026-09-17 18:50 UTC)

**Target: the bank that is never built, from influence that would otherwise
be lost.** Iteration 15 (+2 on 72, 4 flips) showed the mechanism fires
(52 deposits, EC bank 1076 at r400 in the long game) but only ~1 in 7
expired slanderers deposits: the rest stall adjacent to a crowded EC or
turn back for any enemy in range. Refinement (pre-registered, same
mechanism, dose 1 of up to 3): the depositor turns to guarding only for an
enemy *politician* within r²20 (muckrakers cannot hurt it); it speaks
with up to two friendlies in range (`DEPOSIT_MAX_N` 3; the EC still gets a
third of the share and the friendlies keep theirs); and after 8 crowded
rounds adjacent it speaks regardless. Counters (logged dev game
awesomelemonade Arena B, the game where dose 3 of Iteration 16 held 5
ECs at 5-61 influence each): deposits per camouflage expiry (baseline
~1/7), `waited=` distribution, EC influence at r400/r600 (baseline 444 at
r400 in that game), `--hits` converted-EC influence (baseline 5-61),
first-EC-loss round. Price: depositors that speak among friendlies give
two thirds away as guard conviction (not lost), and a depositor waiting
8 rounds is 8 rounds not guarding. Gate (doctrine, 4.5.1): 64-cell roster
check first (`gauntlet/*-roster-deposit2`, +5 over 26/64, futility at 32
if 3 or more behind), head-to-head as a regression check on an accept.
Falsifier: deposits per expiry unchanged, or EC influence at conversion
unchanged.

**deposit2 counters (logged dev game, awesomelemonade Arena B, lost r1281 by
annihilation; 19:35 UTC):** 78 deposits from 215 camouflage expiries
(36%, Iteration 15: ~14%), 12,008 conviction deposited; 52 of 78 spoke at
once, 26 after 1-8 crowded rounds; 48 shared with two friendlies (n=3), 19
with one, 8 alone. The refinement does what it says. Our total EC
influence: 253 (r200), 245, 374, **2,308 (r500)**, 982, 468 (r700), 2,075,
1,005, 334 (r1000) against awesomelemonade's 184, 1,427, 2,166, 3,754,
3,077, 2,311, 1,735, 9,831, 6,049. The deposits arrive and the never-idle
branches spend them within 100-200 rounds (146 politicians at r500 against
13); the ECs are still converted in sequence from r264. Source fixed, sink
unchanged. The 64-cell roster check decides.

**Iteration 18: REJECTED, level (17:17 UTC,
`gauntlet/20260917-161455-roster-deposit2`, 63 of 64 cells read):** 26 v 26
on the 61 cells with a baseline result (two baseline cells were timeouts),
flips +4/-4 (for: arya-k Arena A and Gridlock A, Scott-Poole Maze A,
jmerle Arena A; against: awesomelemonade Arena B, max-titov Maze B,
jmerle maptestsmall A and Maze B). The last cell cannot change the
verdict. With the source of the bank fixed (36% of expiries deposit) and
no roster movement, the deposit mechanism is closed at two doses: what
the EC receives it spends, so the bank never forms whatever feeds it.
`src/bot` back to `g_iter4`.

## Iteration 17, refinement 1 (in development, economy) -- the floor that is bid away (2026-09-17 17:17 UTC)

Understood failure mode of dose 1: in the mirror the held 300 is never
used, and the twin's extra slanderers win the votes at r1500 (8 of 9
losses by votes). Refinement (pre-registered): the same floor (half an
influence per round, capped at 300, non-capture spending only) falls
linearly to 0 between r900 and r1200, so from r900 the bank flows into
the bid cap (inf/5 to r1000, inf/3 after) and units. Counters: EC
influence at r400/r600/r900/r1200, votes at r1500 in the mirror (dose 1
lost them by 94 median in the census's vote losses), converted-EC
influence (`--hits`). Gate unchanged: head-to-head first (24 games, +5,
early stop at `wins + remaining < 15`), then the 64-cell roster check.
Falsifier: the mirror still lost on votes, or lost by annihilation before
r900 (then the floor's price is paid before it can be released).

**Implementation bug found and fixed before the refinement was read (17:22
UTC).** The first six cells of `h2h-floor2` repeated dose 1's results
game for game (same rounds), and the maptestsmall A replay shows why the
floor lost votes: the floored EC held 146-235 from r300 (the floor) with
**0 slanderers from r450** while the twin kept 11-15. The regular guard
branch (`inf >= 20 && guards < GUARD_BASE + slanderers/2`) tests raw
influence, not `floored()`, so with the floor binding it ate every surplus
20 as a guard and the slanderer branches (`inf - floored() >= 21`) never
fired: the floor starved the economy instead of banking. Dose 1 and the
refinement both ran this bug, so the mechanism has not been tested yet.
Fix: the guard branch is floored too (cost from the surplus); emergency
guards, scouts (1 influence) and neutral captures stay unfloored. The
head-to-head restarts as `h2h-floor3` under the same pre-registration
(floor released r900-1200). Deposit2 final: 26/64, the baseline's number.

**floor3 (guard branch floored): 0/4, all vote losses, and the same
starvation (17:25 UTC).** maptestsmall A: 12 slanderers to r300, 0 from
r450 to the end, while the twin keeps 12-18. The reason is the design, not
a branch: the first slanderers expire at 300 rounds alive (r300-450), the
EC then sits *below* the floor (187 v 225 at r450) because the bids
(inf/30, then inf/5) drain the passive income every round, and a
slanderer needs `inf - floor >= 21`, which never comes. A floor on the
source (slanderers) kills the income it was meant to bank. This is the
last attempt on this mechanism, logged as **dose 2, the sink-only floor**:
the two slanderer branches use the plain `reserve()` again; guards
(regular and rich), the spare branch, the rich-and-idle capture and the
enemy-EC capture keep `floored()` (still released r900-1200); emergency
guards, scouts and neutral captures unfloored. Same counters and gate
(`h2h-floor4`). If this loses the mirror too, the floor family is closed:
against ourselves, whatever is banked is out-produced.

**floor4 (sink-only) head-to-head: 13/24 final, 11/22 when read (17:39
UTC): level with the twin, the +5 gate out of reach.** Wins on Arena both
sides, CrossStitch both sides, maptestsmall both sides, Blotches B,
Circles A, Corridor B, Maze A, Radial A; losses on Andromeda both sides,
Gridlock both sides, Saturn A, Snowflake B, and the other sides of
Blotches, Circles, Corridor, Maze, Radial. The starvation is gone (the
maptestsmall cells flipped from loss to win against dose 1 and floor3).
By the letter of 4.5.1 a level mirror is a reject for an economy change.
**Deviation, logged:** the roster check (64 cells, `roster-floor4`,
futility at 32) was launched as the deciding instrument, because the
mirror cannot show what this candidate is for: the twin never converts an
EC with a 107-500 politician (its guards are 20-60), so a bank that
survives conversion has no value against ourselves and the mirror only
measures its price, which is now zero. The pre-registered roster gate
applies unchanged (+5 over 26/64); a level roster is a reject and closes
the floor family.

**Iteration 17 (all doses): REJECTED, roster futility at 22 cells (2026-09-17 18:05 UTC, `gauntlet/*-roster-floor4`): 6 v 9, flips +1/-4.** Against:
awesomelemonade Arena A r519 and B r521 (annihilation; baseline wins),
rzhan11 maptestsmall A r1398 (annihilation), arya-k Maze A (votes); for:
arya-k Arena A (votes). The sink-only floor withholds exactly the guards
that stop a mid-game rush: the two Arena games are lost 250 rounds
earlier than any baseline loss on that map. Closed: the floor family
(hard reserve: starves slanderers; sink-only: level in the mirror, worse
against rushers). What the day's three mechanisms established together:
the opponents' bank is a by-product of income we do not have (the neutral
snowball), not a policy we can copy by withholding; withholding at our
income level costs the units that keep the EC alive. `src/bot` back to
`g_iter4`.

## Iteration 19 (in development, economy) -- big standing guards (2026-09-17 18:06 UTC)

**Target: the guard sink, kept but reshaped.** The ledger's re-open
condition for guard sizing ("standing posture only, never a reaction")
and the day's replays agree: the EC's income goes into 20-60-conviction
guards (152 politicians against 14 in the Arena B game) that neither stop
a rush nor threaten a 107-500 converter; the floor showed those guards
cannot simply be withheld (rush losses at r519-521). Candidate: the same
guard spending in fewer, larger bodies. Dose 1: the regular guard's cap
60 -> 200 (`GUARD_MAX_SIZE`, cost stays max(20, inf/4)), the spare
branch's guard spare/3 -> spare/2; counts (GUARD_BASE 4, MAX_GUARDS 10)
and triggers unchanged. Dose 2 if near miss: cap 400, spare/1. Counters:
mean guard conviction at spawn (`@spawn t=1 role=2 inf=`; baseline
20-60), guards alive at r300/r600, `@speech role=guard conv= n=` (value
per speech), converted-EC count and influence (`--hits`), slanderers at
r300 (the price: a 200 guard is three slanderers not built).
Reachability: `inf/4 >= 60` needs 240 in the EC, which the deposit game
shows at r200-500; the spare branch fires at every surplus. Price: fewer
bodies to absorb muckrakers reaching the slanderer ring. History: guard
ratio (count) rejected, threat-sized guards (reaction) rejected, army
(300+ attackers, not guards) rejected. Gate (economy, 4.5.1): head-to-head
first (24 games, +5, early stop), then the 64-cell roster check.
Falsifier: guards still 20-60 at spawn (the cap never binds) or rush
losses unchanged.

**big guards head-to-head: 4/12 when stopped (18:16 UTC), every loss a
vote loss at r1500** (maptestsmall both sides, Arena A, Andromeda B,
Blotches both sides, Corridor A, CrossStitch A r259 the one annihilation);
wins Andromeda A, Arena B, Circles A, CrossStitch B. The same shape as the
floor: a reallocation away from slanderers loses the mirror's vote count
regardless of what it buys. **Rule amended (TRAINING_ALGORITHM 4.5.1):
spending-mix changes are doctrine for the gate order; the mirror measures
only their price.** The roster check (64 cells, `roster-bigguard`,
futility at 32, +5 over 26/64) runs as the deciding instrument under the
pre-registration; the mirror becomes the regression check on an accept.

**Iteration 19: REJECTED, roster futility at 13 cells (18:28 UTC,
`gauntlet/*-roster-bigguard`): 2 v 5, flips +0/-3**, the same three cells
the floor lost (awesomelemonade Arena A r857 and B r637, rzhan11
maptestsmall A r817, all annihilations where `g_iter4` wins). Big guards do
not stop those games either. The Arena A replay: we snowball to 5 ECs at
r400 (them 3) with 104 politicians (them 31), 519 influence across our
five ECs against their 1,643 at r400, then 4,059 at r500, 11,829 at r700,
14,239 at r800 with 3-7 ECs; our ECs are converted holding 10-57 by
91-500-conviction politicians from r400. Their income at 2-4 ECs is 3-5x
ours at 5: the difference is not the number of ECs or slanderers (34 v 61
at r400) but what each slanderer earns, i.e. slanderer size. `src/bot`
back to `g_iter4`.

## Iteration 20 (in development, spending mix) -- invest the surplus (2026-09-17 18:30 UTC)

**Target: the income gap itself, seen at last in the replay.** In the
Arena A big-guard game awesomelemonade spawns a 949-influence slanderer at
r400 from a 2,360 bank (30 influence per round for 50 rounds); our
slanderers are 21-130 (1-6 per round), and our surplus above 300 becomes
a 100+ guard (Iteration 4's spare branch). Their 34 slanderers out-earn
our 61 by 3-5x because the influence invested in slanderers is 5x. The
ledger's re-open condition for the slanderer cap ("guard sink removed")
is met by redirecting the sink: in the spare branch, a spare bank of 300
or more buys one slanderer of `bestSize(spare)` (any breakpoint;
`MAX_SLANDERER_SIZE` 463 -> 2674) instead of a big guard, when not in
danger and under the spare-branch count cap (24); everything else
unchanged (the ratio guard, the small-spare slanderer, the hunter).
Counters (logged dev game, awesomelemonade Arena A): `@invest size=`
(baseline none), slanderer sizes at spawn (`@spawn t=2 inf=`; baseline
21-130), influence earned by r300/r600 (baseline 6,859 median in
losses; this game's EC totals 289 at r300, 519 at r400), EC influence at
r400/r600, exposes suffered by r600 (the price: one 949 slanderer exposed
is 949 lost; baseline 8 median exposes). Reachability: spare reaches 300
whenever the deposit or capture income lands (the Arena games show
250-500 at r200-400). Price: fewer guards when rich (the rush cells
awesomelemonade Arena A/B, rzhan11 maptestsmall A are the watch list:
three candidates lost them). History: cap24 and size63 rejected as count
and minimum-size rules with the ≥300 guard sink intact. Gate (spending
mix, amended 4.5.1): 64-cell roster check first (`roster-invest`, +5
over 26/64, futility at 32 if 3 or more behind), head-to-head as the
regression check on an accept. Falsifier: no `@invest` before r400 (the
surplus never reaches 300 without the sink) or income at r600 unchanged.

**Iteration 20: REJECTED, level, stopped at 33 cells (2026-09-17 19:00
UTC, `gauntlet/*-roster-invest`): 12 v 14, flips +0/-2** (rzhan11
maptestsmall A r1124, arya-k Maze A votes; one baseline-unknown cell
lost). Stopped inside the futility rule's letter (it asks for 3 behind at
32) because no cell flipped for in 33: +5 would need 7 net flips in the
remaining 31, which the rule's own measurement says no candidate shows.
The mechanism fires exactly as pre-registered (logged Arena A game: 301
investments from r373, slanderers of 368-605, home EC 5,314 at r800, won
on votes as the baseline does), and moves nothing on the roster: the
income arrives after r400, when the neutral snowball has already
decided the multi-neutral maps, and on the rush cells the big slanderers
are 300-600 lost per expose. Closed with the ledger's note: income after
r400 is not the constraint; income before r300 is, and that is the
neutral snowball. `src/bot` back to `g_iter4`.

## Session summary, 2026-09-17 15:00 to 19:00 UTC

Five candidates from the ladder census, none accepted; `g_iter4` stands
at 26/64 on the new 8-bot roster's screen cells (standings 49 of 65 at
20% or more; ladder average 39% on the 82 common cells).

| iteration | mechanism | result | what it closed |
|---|---|---|---|
| 16 | opening capture (3 doses) | 0/13, 0/4, 2/8 | early neutrals are taken and lost at 5-61 influence; bank-first stalls the economy on maps without an early neutral |
| 17 | EC floor (hard, sink-only, released) | starved; 13/24 mirror; 6 v 9 roster | a reserve starves slanderers or loses the rush cells |
| 18 | deposit refinement | 26 v 26 on 64 | 36% of expiries deposit, the EC spends it within 200 rounds |
| 19 | big standing guards | 4/12 mirror; 2 v 5 roster | fewer bigger guards lose the same rush cells as the floor |
| 20 | invest the surplus | 12 v 14 at 33 | income after r400 arrives too late to matter |

Three findings that outlast the rejects: (1) the mirror only prices a
spending-mix change (rule amended, 4.5.1); (2) the three rush cells
(awesomelemonade Arena A/B, rzhan11 maptestsmall A) are `g_iter4`'s
many-small-guards wins and every reallocation loses them; (3) the
opponents' income is the neutral snowball plus 500-1000-influence
slanderers bought from that bank by r400, and our copies of either half
without the other do not move the roster. Cost: ~28 VM-hours of games
(five roster checks stopped at 13-33 cells, four mirrors, four Stage 0s).

**Recommendation for the next session (the user's call):** the census
points at one structural change that no single-iteration candidate can
test: hold captured neutrals (an opening that captures *and* keeps
guards at each new EC, or capture only the neutrals within a few tiles
of home). That is two mechanisms in one candidate, outside the loop's
one-change rule, so it needs the user's go-ahead as a one-off; the
alternative is the untested areas of the functional map (navigation on
Gridlock, muckraker hunting), each cheaper but with weaker evidence. The
VM is stopped.

## Iteration 21 (in development, structural) -- capture and hold (2026-09-17 19:19 UTC)

User authorization (PROMPTS 24, 2026-09-17): any number and degree of bot changes
without approval. This candidate combines the two halves the census
asked for and the day's rejects tested separately.

*(a) Opening, saving mode* (Iteration 16 dose 3, which won the opening:
Arena ECs 5 v 2 at r450): when a neutral of at most 320 influence within
d² 500 of home is known before r150, the EC keeps its first two
slanderers and saves until it can send a full-price capturer; the
capturer now carries a **100** bank (was 30), so the new EC starts able
to build two guards and survive a 107-conviction hit.

*(b) Garrison:* every neutral capture order arms three 60-conviction
guards, built right after the emergency-guard priority over the next
200 rounds and ordered to the captured EC's location; a garrison guard
treats that EC as its home (leash, ring, muckraker chase) and reverts to
home if the post is held by the enemy or is not an EC. Captured ECs run
the same code, so they garrison their own captures.

Counters (logged dev game, awesomelemonade Arena A, the day's reference
game): `@opening capture` round and target, `@garrison` count and
`@garrison-guard post=` arrivals, ECs at r200/r400/r600 (dose 3: 3/4/2),
converted-EC influence at the hit (`--hits`; dose 3: 5-61), first-EC-loss
round (dose 3: r264), income by r300/r600. Price: three 60-guards per
capture is 180 not spent at home; the rush cells (awesomelemonade Arena
A/B, rzhan11 maptestsmall A) are the watch list. Gate (structural,
4.5.1): 64-cell roster check first (`roster-hold`, +5 over 26/64,
futility at 32 if 3 or more behind), head-to-head as the regression check
on an accept. Falsifier per half: (a) no `@opening capture` by r150 on
Arena; (b) captured ECs still converted at under 100 influence with no
garrison guard within d² 20 at the hit.

## Contest rules from 2026-09-17 19:30 UTC (PROMPTS 25)

The user restricted external play to scrimmages: random map, random side,
rotating opponents, Elo standings; our own versions stay unrestricted.
The roster check of Iteration 21 (8 of 64 cells played, 1 v 2) and its
logged dev game were stopped; neither counts. New tooling: `scrim.sh`,
`scrim-record.py`, `elo.py`, and `gauntlet.sh` now refuses external
opponents outside a scrimmage. Method (TRAINING_ALGORITHM 4.5.2): the
accept instrument is a 48-cell paired panel (incumbent on the quick set +
three archetypes on the screen set); a 48-game scrimmage block is the
standing and a veto. First runs: `g_iter4`'s 48-scrimmage block (the
standing under the new rules) and `g_iter4` vs the archetypes (the panel
baseline), then Iteration 21's panel.

## The Elo ladder (2026-09-17 20:10 UTC, PROMPTS 26)

The user asked for a constantly updated Elo-ranked bot list with our
submission on it, and challenges aimed at the bots just above us.
Built: `progress/games.csv` (every scrimmage, ours and external-vs-
external), `tools/elolib.py`, `elo.py` (ranking -> `progress/ELO.md`,
`elo.png`; `--pool`), `ladder-pair.py` (Swiss pairings), `ladder-play.sh`
(plays a tick on the VM), `scrim-record.py` (records both kinds);
`scrim.sh` now draws its pool from the ladder once it holds 100 ladder
games. The 65-bot list is `tools/ladder-bots.txt` (one package per
repo). Seed tick: 130 pairings (each bot ~4 games) queued behind the
baseline runs; then ~14 games per tick beside every development run.

**Panel baseline (2026-09-17 20:50 UTC, `gauntlet/20260917-192947-panel-g_iter4`):
`g_iter4` 20/24 against the archetypes on the screen set** (arch_muck 6/8,
arch_bidder 8/8, arch_polrush 6/8). The archetypes are far below the
incumbent, so the panel's 24 archetype cells can move at most 4 cells for
a candidate and 8 against: they are a regression detector for rush and
conversion vulnerability, not a source of the +5. The +5 has to come
mostly from the 24 mirror cells plus at most 4 archetype flips, which
makes the panel gate stricter than the old roster gate; if the first
panels show it cannot resolve real gains, the archetypes get strengthened
(a muckraker rush with 8 hunters, a politician rush that saves to 300)
rather than the gate loosened. Ladder seed tick (122 games) launched at 3
jobs beside the scrimmage block.

**`g_iter4` first scrimmage block (2026-09-17 22:00 UTC,
`gauntlet/20260917-192922-scrim-g_iter4`): 14/48 = 29% [18%, 43%]** on
random maps and sides against the old band (max-titov 5/6, 123kevinlee,
Scott-Poole, arya-k and jmerle 2/6, awesomelemonade 1/6, iliao2345 and
rzhan11 0/6). Team Elo 1380 after 48 games, rank 66 of 66 while the
external bots still sit at 1500 (the seed tick is at 20 of 122). The old
screen-set number was 41% (26/64) on four maps of our choosing; 29% on
the released corpus is the honest standing. Iteration 21's panel is
running (mirror on the quick set, archetypes on the screen set, 2 jobs
each beside the seed).

**Ladder without external-vs-external games (2026-09-17 22:20 UTC,
PROMPTS 27).** The seed tick was stopped at 20 of 122 and its games
discarded (none recorded); `ladder-pair.py` and `ladder-play.sh` removed.
The ladder now rates only from our scrimmages: 9 rated bots after the
first block (we rank 9 of 9 at 1380, max-titov the only one below 1500),
57 not yet met. The challenge pool is the six rated bots just above us
plus the two least-met bots (`elo.py --pool 6 --explore 2`), so the
ranking fills in one block at a time while the challenges stay aimed at
the next rungs. First pool: awesomelemonade, 123kevinlee, arya-k,
Scott-Poole, jmerle, max-titov, + edlwang and Techno-coder to explore.

**Iteration 21 panel: +3 on 48, NEAR MISS (2026-09-17 23:10 UTC).** Mirror
14/24 against `g_iter4` (`gauntlet/*-panel-hold-mirror`; the incumbent's
own mirror is 12/24 by symmetry, so +2: losses on Andromeda B, Arena A,
Blotches B, CrossStitch A, Gridlock both sides, Radial B, Saturn A,
Snowflake A, maptestsmall A). Archetypes 21/24 against the baseline's
20/24 (`*-panel-hold-arch`: arch_polrush Arena both sides loss->win,
arch_muck Arena B win->loss; bidder 8/8 as before). Below the +5 gate;
the mechanism family is worth one refinement. A 48-scrimmage block
plays now as `us:hold1` (the contest signal, and the ladder grows by two
unmet bots) beside a logged game against `g_iter4` on Arena for the
`@opening` and `@garrison` counters.

## Standing tables (updated in place)

### Functional-area map

| area | last attempt | status |
|---|---|---|
| economy / production mix | Iteration 4: never idle (spare branch: guards / slanderers to 24 / hunters), 20/24 vs g_iter2, 19/72 vs 13/72 on targets | EC now builds every cooldown; opponents still field 3-7x the units (multi-EC, earlier captures) |
| bidding | Iteration 9: early bid cap influence/30 before r600 (accepted, +6 on 72 roster cells) | late ramp unchanged; bid war vs bidders still costs ~1700 by r300 |
| neutral-EC captures | Iterations 5 (race) and 11 (capture bank), both rejected | captured ECs now hold 250+ when hit but still fall; 2 rejects |
| scouting / map knowledge | Iteration 14 eight scouts (rejected: knowledge without captures) | all neutrals known by r200 with 8 scouts; 1 reject |
| navigation | greedy + bug + oscillation guard | aba 1-3% of moves; 0-18 on Gridlock vs the roster (both g_iter3 and g_iter4): next target |
| army / attack doctrine | Iteration 13 attack politicians (rejected, roster 31 vs 33) | converts ECs in bunches, loses the late economy; 1 reject |
| combat micro (politician speech) | radius/value evaluation, chip rule vs ECs | works vs example bot; unmeasured vs real opponents |
| EC defence vs politician streams | Iteration 3 wall (rejected) | target-tier losses are EC conversions r300-600 by 500-1750-conviction speeches; 1 reject |
| slanderer safety | Iteration 12 relayed flight (rejected, +2 roster, exposures halved) | hunters expose 12-22 per game vs iliao2345 and max-titov; 1 reject |
| muckraker hunting / blocking | expose nearest slanderer, sit at enemy EC | unmeasured |

### Closed-directions ledger

| direction | kind | measurement | re-open if |
|---|---|---|---|
| short-round smoke maps via map files | engine-impossible | map format has no round field; 400-round map played 1500 | never |
| one-round spawn ORDER flag | refuted | newborn acts next round; 0 captures -> 6 with two-round hold | never |
| eight early scouts (was four) | rejected | all neutrals known by r200 (baseline never), coverage 50-68%, roster 18 vs 23 on 40 cells | when captures can use the knowledge |
| attack politicians (300+) from the spare branch at the nearest hostile EC, no guards/hunters | rejected | 300+ politicians 18-27 per phase (vs 1-6), 9 ECs taken in one game, roster 31 vs 33 on 70 cells, head-to-head 6/14 on votes | as a scheduled wave with a bank behind it |
| EC relays the nearest enemy; slanderers flee relayed threats within 8 tiles | rejected | exposures halved (iliao2345 1-7 by r300 vs 12), roster +2 on 72 cells (noise), head-to-head 6/14 | as a component of an army doctrine that kills hunters |
| capture politicians carry a 300 bank (three arms) | rejected | surplus 311 and captured ECs hold 5x longer, but 31 vs 33 on 68 roster cells: captures come later and the army gap decides | an army that can hold ground |
| threat-sized guards, else bank | rejected | the EC sees a 600+ attacker 3-5 rounds before the speech; bank fired 0-3 rounds; ECs converted holding 8-159 | standing posture only, never a reaction |
| minimum slanderer size 63 after the opening | rejected | mean size 67-90 at r100 and +30-50% income by r200, but 0/8 at Stage 0 and 12/23 head-to-head: the income fed the same sinks | what the income buys changes (bids, guards) |
| guards scale with threat (base 0, ratio 1/2) | rejected | politicians at r100 unchanged (spend branch fills 24 slanderers -> 14 guards); 1/6 vs 3 | never as a count rule; the income gap is 2-3x |
| slanderer cap 12 -> 24 alone | rejected | slanderers 24 at r100 but EC influence at r200 unchanged: the spend branch turned the income into guards (29-34 by r200); 0/6 vs 3/8 | guard sink removed (Iteration 7) |
| neutral-EC race by chip politicians (3 arms: 4 in flight / half-target chips / save for the chip) | rejected | chips built and spoke but flips <= 2 and the economy starved (EC influence 10-70 at r200-300); 0/8, 1/8, 0/8 on the motivating cells | opening income reaches >= 500 EC influence at r100 |
| opening capture: bank from r1 / bank to r80 / saving mode on a known neutral (three doses) | rejected | takes the neutrals (Arena: ECs 5 v 2 at r450) and loses them at 5-61 influence each; 0/13 roster, 0/4, 2/8 Stage 0 | ECs keep a bank |
| EC influence floor (hard reserve; sink-only, released r900-1200) | rejected | hard: starves slanderers below the floor (0 from r450); sink-only: level in the mirror (13/24), roster 6 v 9 at 22 cells with rush losses at r519-521 | never as a reserve; a bank must come from income |
| deposit: expired slanderers speak at the EC (dose 2: politician-only threat, share with two, speak after 8 rounds) | rejected | 36% of expiries deposit (was 14%), 12k conviction in a game, EC spends it within 200 rounds; roster 26 v 26 on 64 | the guard sink is gone |
| big standing guards (cap 60 -> 200, spare/2) | rejected | mirror 4/12 (votes), roster 2 v 5 at 13 cells: the same three rush cells as the floor | never as a size rule |
| invest the surplus (spare >= 300 buys a slanderer of that size) | rejected | fires as designed (368-605 slanderers, EC 5,314 at r800) but roster 12 v 14 at 33 cells, no flip for: income after r400 is too late | with an opening that holds its neutrals |
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
