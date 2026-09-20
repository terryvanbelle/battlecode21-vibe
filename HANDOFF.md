# Handoff -- the state of the loop (updated 2026-09-20 07:15 UTC)

Read `CLAUDE.md`, then `METHOD.md` (how this project measures things, portable
across years), then this file, then the tail of `TRAINING_LOG.md`.

## Rules in force (all from the user; details in TRAINING_ALGORITHM.md 4.5)

1. Games run only on the VM `battlecode-dev` through `tools/vm-run.sh`.
   `claude-driver` (this box, 2 GB) only analyses: replay dumps, plots, selectors.
   Background shells here get killed under memory pressure, so keep watchers light.
2. **External bots are played only as scrimmages**: `tools/scrim.sh`, random map from
   the released corpus, random side, rotating opponents. `gauntlet.sh` refuses external
   opponents outside it. Never choose a map or side against a benchmark bot. Our own
   snapshots (`src/g_iter*`) and archetypes (`src/arch_*`) are unrestricted.
3. **No external-vs-external games.** The Elo ladder (`progress/ELO.md`, `elo.png`,
   `tools/elo.py`) is built from our scrimmages only; unmet bots are unrated.
   Challenges target the bots just above us: `elo.py --pool 6 --explore 2`.
   No ladder gating: you cannot scrimmage without a submission, so a block never
   blocks an accept, it only reports.
4. The user pre-authorized any bot change; never pause to ask about bot design.
5. Push after every commit; record every user prompt verbatim in `PROMPTS.md`
   (format `## <n>. <date>`, then the text).
6. **No test starts before a diagnostic game proves the mechanism fires.** Three
   candidates were implemented, compiled and completely inert; only the `@tag`
   counters in a logged game showed it.
7. **`tools/unit-tests.sh` after every change**, to the bot or to an analysis script.

## The instruments

- **Accept gate: SPRT** (`tools/mirror.sh` drives games, `tools/sprt.py` decides).
  Candidate against the incumbent, random map and random side per game, batches of
  16. H0 p=0.50 against H1 p=0.58, alpha = beta = 0.05, log-likelihood bounds
  +/-2.94, cap 240 games. This replaced the old 48-cell panel, which could only
  resolve effects of about 75% and accepted nothing real.
- **Stacking policy.** ACCEPT: snapshot `src/g_iterN`. Inconclusive at the cap but
  >= 53% over >= 200 games: keep provisionally, do not snapshot. Below 53%, or
  REJECT: revert. `git checkout` cannot undo an already-committed change -- restore
  from the snapshot directory with
  `for f in src/g_iter7/*.java; do sed 's/^package g_iter7;/package bot;/' "$f" > src/bot/$(basename $f); done`.
- **Contest signal: a 48-scrimmage block**, `BOT=bot N=48 tools/scrim.sh`, recorded
  with `tools/scrim-record.py <run-dir> --label <build>` then `tools/elo.py`.
- **Hypothesis generation: the correlation/onset method** (TRAINING_ALGORITHM 4.5.3b).
  `tools/polarity.py` orients every metric so higher is better for us,
  `tools/correlate.py` gives point-biserial correlation with the result per 50-round
  sample, `tools/onset.py` finds the first round where a correlation appears and
  holds. Output: `progress/ONSET.md`, `progress/onset-ladder.png`,
  `progress/METRICS.md` (what each metric means and how it is computed).
  Earliest onset first: temporal precedence is the only causal hint a correlation
  honestly gives. Raw, un-differenced metrics are map-confounded and sit behind
  `--raw`; a unit test fails if they leak into the default ranking.
- **The microscope: `tools/replaydump/ReplayDump.java`.** `--metrics --every 50` for
  the per-round table, `--threat A|B` for enemy pressure on a team's centres,
  `--robot`, `--logs`, `--navstats`, `--hits`, `--speeches`.
- **Sparring archetypes** (`tools/snapshot.sh <name> <archetype-int>`):
  1 `arch_muck` muckraker rush, 2 `arch_bidder`, 3 `arch_polrush`, 4 `arch_expand`
  neutral-centre expander, plus `arch_big` (rebuilt allocation).

## State right now

- **Submission: `g_iter7`** = g_iter6 + Iteration 34 (the opening deployment).
  Block: **19/48, 39.6%** (95% 27.0-53.7), the first submission to beat its
  predecessors (g_iter4 29.2%, g_iter5 25.0%, g_iter6 28.1%). Elo **1426**,
  rank 7 of 9 rated bots, 240 scrimmages. Regression 32/32 on all four archetypes.
  Record against awesomelemonade: **0/18**.
- **`src/bot` = g_iter7 + Iteration 35** (the guard sink): the spare branch now
  fills the economy to `SPEND_SLANDERER_CAP` before building a guard, and caps
  standing guards at `SPEND_GUARD_CAP` = 12. Pre-registered in TRAINING_LOG.md.
  Not yet gated.
- **In flight on the VM:**
  - `gauntlet/scrim-iter7b.log` -- a second 48-scrimmage block on `g_iter7`
    (compiled before Iteration 35 was written, so the block is clean). Collect with
    `tools/vm-collect.sh <run-id>`, then `scrim-record.py`, then `elo.py`.
  - `gauntlet/diag-i35.log` -- the Iteration 35 diagnostic, `bot` vs `g_iter7` on
    NotAPuzzle. `run-dev.sh` writes `LOG_OUT` only when the game ends, so the
    `.out` file appearing is the completion signal. Check in the `@econ` lines that
    guards at r200 fall from 38 toward 12-15, slanderers reach the cap sooner,
    centre influence at r200-300 rises above 336, and `@speech role=capture` fires
    more than four times.
- **Next decision.** If the diagnostic shows the mechanism firing, run the SPRT
  (`tools/mirror.sh bot g_iter7`) once the block frees the machine; the cap is 7
  games at once and the block already uses 6.

## The open question the ladder is asking

Our centre count is flat at two in wins and in losses alike; what separates them is
the opponent's, which reaches six by r700 in losses. Ten capture politicians in one
diagnostic game produced one flip, three chips, and **six aborts because the target
was already taken**. We lose the expansion race on rate. Four expansion candidates
have been rejected, all gated by a mirror against a twin that expands exactly as
badly as we do; `arch_expand` now exists so the next one gets a real opponent.

## Gotchas learned the hard way

- `pkill -f`/`pgrep -f` with the pattern in your own command line kills your own
  shell: write patterns as `roster-hol[d]`.
- `pgrep -c battlecode.server.Main` **counts two processes per game** (the `timeout`
  wrapper carries the class name). Match `[j]ava .*battlecode.server.Main`.
- Killing a `mirror.sh` run means killing the **script** (`pkill -f "[m]irror.sh"`),
  not its `xargs`: the batch loop just starts the next batch otherwise.
- `gauntlet.sh` compiles `src` into `build/classes` **once at the start**, so editing
  `src` during a run does not contaminate it -- but `vm-sync.sh` does replace
  `src tools test` on the VM at every `vm-run.sh`, so never launch a second run while
  another is still compiling (the first ~30 s).
- `gauntlet.sh` deletes `results.raw` at the end and saves losses only unless
  `KEEP_ALL=1` (`scrim.sh` sets it, so wins are studied too).
- `tools/run-dev.sh` buffers the whole engine output and writes `LOG_OUT` at the end.
- The VM is shared with other projects; our cap is **7 games at once**.
- Check every substitution: `str.replace` that silently matches nothing left three
  slanderer branches unchanged for a whole iteration. Use `assert old in s`.
