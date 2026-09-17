# Environment setup

Two GCP machines, project `tvanbelle-vibecode`, zone `us-west1-b`:

| machine | type | role |
|---|---|---|
| `claude-driver` | e2-small, 2 vCPU / 2 GB | hosts the Claude Code session, the repo checkout, replay analysis, plots. **Never runs games.** |
| `battlecode-dev` | e2-standard-8, 8 vCPU / 31 GB, 20 GB disk | runs every game: matches, gauntlets, scans, benchmark compiles. Shared with the 2022/2025/2026 projects. |

## Why the split matters

The engine plays a game as one JVM. Against a heavy bot that JVM grows to
1.1-1.4 GB (a 512 MB heap plus instrumented classes plus the replay built in
memory). On the driver, two concurrent games pushed 1.4 GB into swap and a
single game took 50 minutes; on the VM the same game takes 15 s to 3 min and
five to six run at once. Everything that spawns `battlecode.server.Main`
belongs on the VM.

## Layout (identical on both machines)

```
~/jdk/jdk8u504-b01                       JDK 8 (the 2021 engine needs Java 8)
~/projects/vibe/2021                     this repo; engine/ is built here and gitignored
~/projects/vibe/2021/engine              engine.jar, lib/, maps/, VERSION (tools/build-engine.sh)
~/projects/vibe/bc21-benchmarks          benchmark repos (driver only, 1.1 GB, never read)
~/projects/vibe/bc21-benchmarks/_classes compiled benchmark bots + ../manifest.tsv (synced to the VM)
```

Because the layout is mirrored, `tools/lib.sh` and `tools/gauntlet.sh` work on
either machine without configuration. The VM has no `rsync`; `tools/vm-sync.sh`
uses tar over ssh.

## Driver-side handles (`tools/vm*.sh`)

| command | does |
|---|---|
| `tools/vm-sync.sh` | push `src tools test` (always) and JDK, engine, benchmark classes (when missing, or `FULL=1`) |
| `tools/vm-run.sh <log> '<cmd>'` | sync, then start `<cmd>` detached on the VM in `~/projects/vibe/2021`, output to `gauntlet/<log>.log` |
| `tools/vm-tail.sh <log> [n]` | tail that log; shows games in flight and load |
| `tools/vm-collect.sh <run-id>` | pull `gauntlet/<run-id>/` (results.csv, summary.txt, losses/) back to the driver |
| `tools/vm-stop.sh` | stop the VM if no game (of any project) is running |

Budget rules for evaluation runs are in `TRAINING_ALGORITHM.md` section 4.5.1 (a 2021 game costs ~6 CPU-minutes: informative cells only, early stopping, screen set for candidates).

Typical runs:

```bash
tools/vm-run.sh scan1 'MAXJOBS=5 OPPONENTS="$(tools/bench-select.py)" MAPSET=screen TAG=scan1 tools/gauntlet.sh'
tools/vm-run.sh h2h 'CLASSES=$HOME/projects/vibe/2021/build/h2h MAXJOBS=2 OPPONENTS=g_iter1 MAPSET=quick TAG=h2h tools/gauntlet.sh'
tools/vm-tail.sh scan1
tools/vm-collect.sh 20260916-191801-scan1
tools/gauntlet-select.py gauntlet/*/results.csv --write tools/roster.txt   # on the driver
```

Rules of the road on the VM:

- `vm-sync.sh` replaces the VM's `src`, `tools` and `test` with the driver's on
  every `vm-run.sh`. Anything a run writes into those directories on the VM
  (notably `tools/roster.txt` from `scan.sh`) is lost at the next launch:
  fetch it to the driver and commit it first (2026-09-17: a roster gauntlet
  launched against `examplefuncsplayer` because its roster file had just been
  wiped, and `OPPONENTS=""` falls back to the default).

- One game per `MAXJOBS` slot; keep the VM at 7 games or fewer in total (a
  2021 game is ~5.7 CPU-minutes; at 10 JVMs on 8 cores everything slows). Use
  `CLASSES=<private dir>` for a second run so it cannot recompile the classes
  the first run loads.
- `GAME_TIMEOUT` (default 1800 s) caps a runaway game; it is recorded as `unknown`.
- Check `pgrep -fc battlecode.server.Main` before stopping the VM; other
  projects may be mid-run. Stop it when idle (billing).
- Disk: 20 GB. Old projects' replays fill it; with the user's permission
  they were deleted on 2026-09-16 (9 GB). Keep only losses locally, and fetch
  runs to the driver when done.

## Reaching the machines

```bash
gcloud compute ssh battlecode-dev --zone us-west1-b --project tvanbelle-vibecode
```

`tools/vm.sh` uses plain ssh with `~/.ssh/google_compute_engine` and caches
the VM's external IP in `/tmp/.bc21-vm-ip-battlecode-dev`, because
`gcloud compute ssh` re-pushes keys on every call and takes 30 s. `ensure_vm`
starts the instance when it is stopped.

## Building the engine (once per machine)

`tools/build-engine.sh` clones `battlecode/battlecode21`, patches and builds
it with JDK 8 and stages `engine/`. Official downloads are dead. The driver
built it; the VM received a copy through `vm-sync.sh`.
