# Handoff -- the state of the loop (updated 2026-09-22 10:30 UTC)

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

## State right now (2026-09-21 22:45 UTC)

- **In flight: Iteration 52 ladder block** (`gauntlet/ladder-i52.log`; `src/bot` = g_iter12 +
  sentinels/floor/capture bank, TRAINING_LOG 2026-09-22 10:30). Read it against 40/48; SPRT
  vs g_iter12 if it earns it, else revert `src/bot` from `src/g_iter12`.
- **Submission: `g_iter12`** = g_iter11 + Iteration 49 (flip intent, presume, abort report,
  stamped ownership claims) + Iteration 50 (bid while saving, vote-estimate init at birth,
  bytecode cuts) + Iteration 51 (save mode off). Snapshotted 2026-09-22 on combined evidence:
  mirror inconclusive 133-107 (55.4%, LLR +1.08) and **ladder 40/48, Elo 1798, rank 2**
  (awesomelemonade 1841). `src/bot` is byte-identical to `src/g_iter12`. **The next mirror gate
  plays `REF=g_iter12`.** Regression **40/40**; block study done (`gauntlet/20260922-060056-scrim-bot/study.tsv`,
  logs via `tools/log-scan.sh`). The seven losses (awesomelemonade 4, rzhan11 2) show their
  muckraker swarm (median 84 vs our 23 at r200) beating a richer, more-slandered us.
- **Read `@bc ... over=` for every centre in any logged game before trusting a candidate**: a
  centre over its 20k bytecode budget silently loses rounds (Iteration 49 lost 213-404 a game).
  `@bcprof` logs the stage bytecodes when a turn passes 15k. `BROADCAST_OWN` exists and is OFF.
- On a verdict: ACCEPT -> `tools/snapshot.sh g_iter13`, regression, ladder block
  (`scrim.sh`, then `scrim-record.py`, `elo.py`, `bench-roster.py`), block study;
  inconclusive >= 53% over >= 200 -> keep provisionally; else revert `src/bot` from
  `src/g_iter12` (the whole stack -- or drop doses one at a time if a counter says which).
- **Next candidate, already diagnosed** (fixed-seed Hexes run 6): the rich-and-idle
  branch (`inf - reserve() >= 300 -> CAPTURE at enemyEC[0]`) pours the bank into
  politicians at a *contested* centre that flips every few rounds (363 aborts "by=ours" in
  one game). Choose the enemy target by stamp and size, and cap politicians in flight per
  tile as `claimed()` does for neutrals.
- `@bcprof` (EC.java) logs the stage bytecodes of a centre's turn when it passes 15k.
- The dev runner's engine seed is fixed: `tools/run-dev.sh bot g_iter11 <Map>` replays the
  same game until the code changes it, which makes diagnostic reruns like-for-like.

## The finding behind Iteration 48 (read this before any comms work)

After every build the centre put a spawn ORDER on its flag for two rounds. Only
politicians read orders, and their default role already matched, so the order
carried information only for a CAPTURE. Readers of a centre's flag skip orders.
**So a centre that builds most rounds broadcast its map to nobody**, including
its own scouts, which read home every three rounds and got an order every time.
Captured centres were born deaf; couriers arrived carrying nothing. Orders now
go on the flag only for captures. Everything else in 48 either exploits that
(nearby-flag absorption, couriers, home's id in the rotation, ownership as a
tile) or contains what it woke up (only one scout in four camps the enemy
centre -- the mechanism rejected as Iteration 33 while the channel was blocked).

Four earlier hypotheses about the r200 fork each measured something real one
hop upstream of this. It was found by six counters on the centre's intake.

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

## The open questions

- **awesomelemonade** (tier `target`, first legitimate look 2026-09-21, four losses read):
  their weapon is the expose buff -- 6.3x on their politicians in HexesAndOhms, 10x late in
  BattleCode, never above 0.4x in the games we won -- and it drained a 3,500-influence centre
  of ours in nineteen speeches. Ladder-wide, exposure count alone does not separate wins
  from losses (we won BattleCode vs rzhan11 with 81 exposed), so the question is where our
  slanderers are when the swarm arrives, and what our guards trade for it: a 20-1000
  influence guard dies to kill one 1-influence muckraker.
- **rzhan11**: the fork is at r200 and it is centres, 2 against 4; we are not poor, not
  blocked, not ignorant at that fork. Iteration 49 removes one measured reason (capturers
  re-bought for a centre already ours, 1,485 influence in one game); the rate question stays.

## Gotchas learned the hard way

- **The VM disk fills**: every kept replay is 4-8 MB and a gate keeps every loss; at 97% full
  (2026-09-22) the next batch would have failed. After a block's study.tsv/logs are fetched to
  the driver, `rm -rf` its run directory on the VM; check `df -h ~` before launching a gate.
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
