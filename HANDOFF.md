# Handoff (written 2026-09-17 23:45 UTC, before a model switch)

Read `CLAUDE.md`, then this file, then the tail of `TRAINING_LOG.md`.

## Rules in force (all from the user; details in TRAINING_ALGORITHM.md 4.5.1-4.5.2)

1. Games run only on the VM `battlecode-dev` through `tools/vm-run.sh`; `claude-driver`
   (this box, 2 GB) only analyses. Background shells here get killed under memory
   pressure: keep watchers light (a `Monitor` with a 150 s ssh poll works).
2. **External bots are played only as scrimmages**: `tools/scrim.sh` (random map from the
   released corpus, random side, rotating opponents). `gauntlet.sh` refuses external
   opponents outside it. Never choose a map or side against a benchmark bot. Our own
   snapshots (`src/g_iter*`) and archetypes (`src/arch_*`) are unrestricted.
3. **No external-vs-external games** (waste of VM). The Elo ladder (`progress/ELO.md`,
   `elo.png`, `tools/elo.py`) is built from our scrimmages only; unmet bots are unrated.
   Challenges target the bots just above us: `elo.py --pool 6 --explore 2`.
4. The user pre-authorized any bot change; never pause to ask about bot design.
5. Push after every commit; record every user prompt verbatim in `PROMPTS.md`
   (format: `## <n>. <date>` then the text).

## The loop under the new rules

- Accept instrument: the **48-cell panel** (candidate vs `g_iter4` on the 12-map quick
  set both sides = 24, plus vs `arch_muck arch_bidder arch_polrush` on the screen set
  `maptestsmall Arena Maze Gridlock` both sides = 24), gate **+5 net** over the
  incumbent's record (mirror 12/24 by symmetry; archetypes 20/24 in
  `gauntlet/20260917-192947-panel-g_iter4`). Early stop when unreachable.
- Contest signal: a **48-scrimmage block** (`N=48 BOT=bot tools/scrim.sh`), recorded
  with `tools/scrim-record.py <run> --label <build>` then `tools/elo.py`; a veto on an
  accept, and the ladder standing. `g_iter4`: 14/48, Elo 1380.
- Post-accept: snapshot `src/g_iterN`, copy to `src/bot` stays, re-run the block.

## State right now

- `src/bot` = **Iteration 21 "capture and hold"** (opening saving mode with a 100 bank +
  a 3-guard garrison per captured neutral), commit 860afe5 onward. Panel **+3 on 48**
  (mirror 14/24, archetypes 21/24): NEAR MISS, one refinement allowed.
- Running on the VM (started ~23:10 UTC):
  - `gauntlet/scrim-hold1.log` -> run `gauntlet/20260917-211242-scrim-bot`, 48
    scrimmages of the candidate (0/7 when handed off). When `wrote` appears in the log:
    `tools/vm-collect.sh 20260917-211242-scrim-bot`,
    `tools/scrim-record.py gauntlet/20260917-211242-scrim-bot --label hold1`,
    `tools/elo.py`, commit `progress/`, log the block in TRAINING_LOG.md.
  - `gauntlet/devlog-hold-gridlock.log` + `gauntlet/devlogs/hold-vs-g_iter4-gridlock.log`:
    a logged game (bot as A vs g_iter4 on Gridlock, a mirror cell the candidate lost).
    Read `@opening capture`, `@garrison r=`, `@garrison-guard post=` and the home EC's
    `@econ` lines to see why the opening half did not win there. On Arena the opening
    was inert (cheap neutrals beyond `OPENING_MAX_D2` 500, near ones above
    `OPENING_MAX_TARGET` 320) and only the garrison fired (3 guards by r68).
- Next decision: dose 2 of Iteration 21 from the Gridlock reading (candidates: lift
  `OPENING_MAX_D2`, or target the nearest neutral instead of the cheapest, or garrison
  size/count), pre-register in TRAINING_LOG.md, panel again (48 cells), then a block.
  If dose 2 also misses, revert `src/bot` to `g_iter4` (`git checkout d82fc0c -- src/bot`)
  and pick the next candidate from the scrimmage loss census
  (`tools/loss-census.sh <run-dir>` over `losses/`).

## Gotchas learned the hard way

- `pkill -f`/`pgrep -f` with the pattern in your own command line kills your own shell:
  write patterns as `roster-hol[d]`.
- `gauntlet.sh` deletes `results.raw` at the end (only sorted `results.csv` remains) and
  saves replays of losses only.
- Raising a running gauntlet's parallelism: `kill -USR1 <xargs pid>` once per extra job;
  the pgrep pattern must match the original `-P N`.
- `vm-sync.sh` replaces `src tools test` on the VM at every `vm-run.sh`; never launch a
  second run while another is still compiling (first ~30 s).
- `pgrep -c "battlecode.server.Main"` **counts two processes per game**: gauntlet.sh
  wraps each game in `timeout 1200 java ...`, and the wrapper's command line contains
  the class name too. Divide by two, or match `[j]ava .*battlecode.server.Main`.
- Killing a `mirror.sh` run means killing the **script** (`pkill -f "[m]irror.sh"`),
  not just its `xargs`: the batch loop simply starts the next batch otherwise.
- The VM is shared with other projects (their games also show as
  `battlecode.server.Main`); our cap is 7 games at once.
