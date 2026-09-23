# tools/

**Games run on the `battlecode-dev` VM, never on the driver** (see `SETUP.md`; `vm-run.sh` below).
Everything runs with bare `java` (JDK 8 at `~/jdk/jdk8u504-b01`, see
`tools/lib.sh`). The engine is built from source once: `tools/build-engine.sh`.

| script | purpose |
|---|---|
| `build-engine.sh` | clone/patch/build the 2021 engine from `battlecode/battlecode21`, stage `engine/` (jar, deps, 76 maps, `bc21-maps.txt`) |
| `lib.sh` | shared: JDK/classpath, `run_game`, `parse_result`, `compile_src` |
| `run-match.sh A B map [replay]` | one headless game, prints `RESULT <winner> <round> <reason>` |
| `gauntlet.sh` | BOT vs OPPONENTS on MAPS/MAPSET (`full`, `quick` 12, `screen` 4), both sides, parallel; writes `gauntlet/<run>/results.csv`, `summary.txt`, `losses/`; opponents may be our packages or `owner.package` benchmark names; duds (opponent failed to instrument) are recorded, not counted as wins; `CLASSES=build/x` compiles privately so two gauntlets can run at once |
| `snapshot.sh name [archetype]` | freeze `src/bot` as `src/<name>` (archetype 1 = muck rush, 2 = aggressive bidder, 3 = politician rush, 4 = expander, 5 = siege, 6 = `arch_hunt` slanderer hunt, 7 = `arch_lemon` hunt + target-sized attacks) |
| `compare.py base cand` | game-by-game diff of two runs: identical cells, flips, sweeps, by side/map |
| `replay-dump.sh replay [flags]` | replay -> text: aggregates, `--from/--to` events (SPAWN/DIED/EMPOWER/EXPOSE/CONVERT/BID lines), `--robot`, `--map-at`, `--logs REGEX --logs-team A`, `--metrics` CSV, `--bytecode`, `--navstats`, `--threat`, `--knowledge`, `--hits` (enemy speeches reaching an EC: conviction, distance, n, wall, influence before -> after), `--speeches` (per team: speeches, conviction spent, share on enemies / enemy ECs / friendlies, empty) |
| `bench-compile.sh` | compile every benchmark repo without displaying source; writes `manifest.tsv` |
| `bench-select.py [--all|--table]` | name-only pick of each repo's final bot |
| `bench-roster.py` | regenerate the roster table in `BENCHMARK.md` |
| `gauntlet-select.py results.csv... [--write tools/roster.txt]` | tier every opponent from scan results; writes the standing roster (the 20-50% band) |
| `mirror.sh` (BOT, REF, N, BATCH, W0/L0) | the accept gate: candidate vs incumbent, random map and side per game, batches of 16, SPRT after each; own class tree (`build/mirror-classes`); `W0`/`L0` fold in a batch already played (resume after an interruption; void the partial batch by hand) |
| `sprt.py <wins> <losses>` | sequential probability ratio test, H0 p=0.50 vs H1 p=0.58: ACCEPT / REJECT / CONTINUE |
| `scrim.sh` (BOT, N, POOL, SEED) | the only way to play an external bot: random map and side per game, rotating opponents; results under `gauntlet/*-scrim-<bot>` |
| `scrim-record.py <run> [--label <build>]` | appends our block or a ladder tick to `progress/games.csv` |
| `elo.py [--pool N --explore K] [--build B]` | the Elo ladder from our scrimmages only: rewrites `progress/ELO.md` and `elo.png`; `--pool` = the N rated bots just above us, `--explore` = K least-met bots (together the challenge pool); `--build` = one build's record |
| `run-dev.sh A B map [replay]` | like `run-match.sh` but from a private compile (`build/dev-classes`): safe while a gauntlet owns `build/classes`. **The engine seed is fixed**, so the same pairing replays the same game until the code changes: diagnostics are like-for-like, and a baseline run of the incumbent on the same seed is what an arm is read against |
| `scrim-study.sh <run>` | the block study: `--metrics` and `--navstats` for every replay of a block (skips locked-tier opponents, reports why) -> `study.tsv`, `nav.tsv` |
| `onset.py <run> [--md --plot]` | per metric, the first round at which its lead correlates with the result (`progress/ONSET.md`, `onset-ladder.png`) |
| `correlate.py <run> --round N` | raw and within-pair correlation of each metric with the result at round N |
| `log-scan.sh <run>` | every `@tag` log line of our side for every game of a block, one VM pass -> `<run>/logs/*.log`, `logs.tar.gz` |
| `econ-scan.sh <run> [round]` | the home centre's `@econ` counters at one round for every game of a block -> TSV |
| `tier-check.sh` | enforces the replay-access tiers of `BENCHMARK.md` (used by `replay-dump.sh`; fails closed) |
| `scan.sh` | incremental two-stage tiering scan: every opponent on 3 maps both sides, then 4 more maps for the in-band ones; `DONE="<results.csv ...>"` cells are never replayed; writes `tools/roster.txt` |
| `scan-cells.py` | the cells (opponent map side) a stage still needs, given results files; `--band` keeps only in-band opponents |
| `vm.sh` | sourced helpers: VM name/zone, `ensure_vm`, `gssh`, `gscp` |
| `vm-sync.sh` | push repo tree (+ JDK, engine, benchmark classes when missing) to the VM |
| `vm-run.sh <log> '<cmd>'` | sync, then run `<cmd>` detached on the VM, log to `gauntlet/<log>.log` |
| `vm-tail.sh <log>` / `vm-collect.sh <run-id>` / `vm-stop.sh` | follow a run, fetch its results, stop the idle VM |
| `ladder.sh` | roster run (default) or `SCAN=1` re-tiering scan + history + charts + roster table in one go |
| `track_history.py run [--label]` | append per-opponent win rates to `progress/history.csv` |
| `plot_history.py` | historical per-opponent charts (the figures it wrote were retired as stale on 2026-09-21; `elo.py` and `onset.py` produce the current ones) |
| `plot_progress.py` | `progress/cumulative_iterations.png` |
| `unit-tests.sh` | compile and run `test/bot/*Test.java` (plain mains, no JUnit) |
| `mapinfo/MapInfo.java` | the map corpus table `tools/mapdata.csv` |

Rules of the road: never read benchmark source (`BENCHMARK.md`); never review a
game against a bot we beat under 20%; never `pkill -f` a pattern that appears in
your own command line; do not edit `gauntlet.sh` while a run is in flight (it
re-executes from a private copy, so edits are safe, but the collation of an
older run uses the copy it started with).
