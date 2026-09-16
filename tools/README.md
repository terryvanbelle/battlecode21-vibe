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
| `mirror.sh` | bot vs a byte-identical copy (the null) |
| `snapshot.sh name [archetype]` | freeze `src/bot` as `src/<name>` (archetype 1 = muck rush, 2 = aggressive bidder) |
| `compare.py base cand` | game-by-game diff of two runs: identical cells, flips, sweeps, by side/map |
| `replay-dump.sh replay [flags]` | replay -> text: aggregates, `--from/--to` events, `--robot`, `--map-at`, `--logs REGEX --logs-team A`, `--metrics` CSV, `--bytecode`, `--navstats` |
| `bench-compile.sh` | compile every benchmark repo without displaying source; writes `manifest.tsv` |
| `bench-select.py [--all|--table]` | name-only pick of each repo's final bot |
| `bench-roster.py` | regenerate the roster table in `BENCHMARK.md` |
| `gauntlet-select.py results.csv... [--write tools/roster.txt]` | tier every opponent from scan results; writes the standing roster (the 20-50% band) |
| `run-dev.sh A B map [replay]` | like `run-match.sh` but from a private compile (`build/dev-classes`): safe while a gauntlet owns `build/classes` |
| `scan.sh` | two-stage tiering scan: every opponent on one map both sides, then a second map only for split results; `SKIP=<results.csv>` reuses decided opponents; writes `tools/roster.txt` |
| `vm.sh` | sourced helpers: VM name/zone, `ensure_vm`, `gssh`, `gscp` |
| `vm-sync.sh` | push repo tree (+ JDK, engine, benchmark classes when missing) to the VM |
| `vm-run.sh <log> '<cmd>'` | sync, then run `<cmd>` detached on the VM, log to `gauntlet/<log>.log` |
| `vm-tail.sh <log>` / `vm-collect.sh <run-id>` / `vm-stop.sh` | follow a run, fetch its results, stop the idle VM |
| `ladder.sh` | roster run (default) or `SCAN=1` re-tiering scan + history + charts + roster table in one go |
| `track_history.py run [--label]` | append per-opponent win rates to `progress/history.csv` |
| `plot_history.py` | `progress/vs_roster.png` (frozen snapshots) and `progress/ladder.png` (external bots) |
| `plot_progress.py` | `progress/cumulative_iterations.png` |
| `unit-tests.sh` | compile and run `test/bot/*Test.java` (plain mains, no JUnit) |
| `mapinfo/MapInfo.java` | the map corpus table `tools/mapdata.csv` |

Rules of the road: never read benchmark source (`BENCHMARK.md`); never review a
game against a bot we beat under 20%; never `pkill -f` a pattern that appears in
your own command line; do not edit `gauntlet.sh` while a run is in flight (it
re-executes from a private copy, so edits are safe, but the collation of an
older run uses the copy it started with).
