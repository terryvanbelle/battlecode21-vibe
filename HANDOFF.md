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

## State right now (2026-09-21 08:30 UTC)

- **Submission: `g_iter10`** = g_iter9 + Iteration 38 (scout cap) + Iteration 40
  (expired slanderers attack instead of guarding home). Gate 63.5% (61-35).
  Regression 38/40.
- **Ladder: Elo 1651, rank 3 of 21**, behind only awesomelemonade (1835) and
  rzhan11 (1742). **35/48 on the fixed field**, 6/6 against four of the eight.
- `src/bot` is byte-identical to `src/g_iter10`. Verify with the per-file diff
  loop in TRAINING_LOG before assuming otherwise.
- The challenge pool is **fixed**: the 8 most-played opponents
  (`tools/elo.py --established 8`). Exploration is off at the owner's
  instruction until our standing improves (`EXPLORE=n` re-enables it). This is
  what finally made consecutive blocks comparable.

## What is closed, with the evidence (do not re-tread)

- **Capture throughput** (Iterations 37, 40, 41, 42). The blocking is
  *conserved*: raise the cap and claims block, lapse the claims and the cap
  blocks, raise it again and claims block. Nearest-first targets halve captures
  against cheapest-first (33 flips to 14). Captures per game are maximised by
  the rule already in the bot.
- **The economy pause** (Iterations 39, 43). The defect is real and large -- one
  centre spent 1,192 of 1,500 rounds blocked, every centre ended with zero
  slanderers on up to 160,737 influence -- and unblocking it loses. Priced by
  four instruments: mirror 40%, `arch_polrush` cannot create the condition,
  `arch_siege` creates it but loses every game, ladder **-4 games in 48**.
  Building slanderers into sustained politician pressure costs more than the
  idle influence does.
- **Chip captures** (Iteration 44). Sending several cheap politicians at a
  centre we cannot buy outright: 61 chips delivered, flips fell 14 to 2, centres
  at r1500 2 against 6. Chips spend influence at a poor exchange rate and drain
  the bank below the clean purchase price.
- **Bidding is downstream, not a lever.** In all four rzhan11 losses we lead the
  vote race at r700 (366/312, 562/126, 492/198) and lose it by r1500, while they
  spend 6-50x more influence on bids (up to 602,149 against our 11,206). But at
  r700 in those games our centres hold 56 influence and 696 unit influence --
  there is nothing to redirect. The vote loss is the economy collapse arriving.

## The open question

Against rzhan11 the fork is at **r200 and it is centres, 2 against 4**, and
everything follows: by r700 their unit influence is 240x ours in losses, ours is
5x theirs in wins. At that fork we are not poor (2,379 banked), not blocked, and
not ignorant -- `--knowledge` shows we have sensed *more* neutrals in losses
than in wins. They simply convert earlier, and every mechanism we have tried for
converting faster has been measured and rejected.

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
- **`gauntlet.sh` re-execs itself as `.reexec-gauntlet.<pid>`**, so `pgrep -f gauntlet.sh`
  finds nothing while a gauntlet is running. A queued job that waited on that predicate
  started immediately and destroyed a 48-game block (2026-09-20). Wait on `scrim.sh` or
  `mirror.sh`, or on the run directory's `summary.txt` appearing, and *verify the
  predicate matches something* before relying on it.
- Two runs must not share a class tree: `mirror.sh` now defaults to `build/mirror-classes`
  and `gauntlet.sh` refuses to recompile a tree that running games are reading.
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
