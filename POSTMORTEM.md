# Battlecode 2021 post-mortem: a bot trained by measurement

*Team: one human (Terry Van Belle) directing one AI (Claude Code), 16-23 September 2026, on a
replay of the 2021 season ("Enlightenment Centers, politicians, slanderers, muckrakers").
This is the write-up a contestant would read after the finals: what we built, how we trained
it, what the engine turned out to do, what worked, what did not, and what we would do next.*

**Final standing.** Submission `g_iter13`. On a fixed field of eight released 2021 bots played
as scrimmages (random map, random side, six games per opponent per block): **298/384 (77.6%)**
over its eight blocks; **35% against awesomelemonade** (the top bot), **50% against rzhan11**,
69-100% against the other six. Elo ladder built from our own 1,200 scrimmages: rank 3 of 21
rated bots (1771), from a start of 29% overall and 7% against the top bot.

The interesting part is not the bot. It is the method that got a 45-minute-per-game season to
move at all, and the engine facts and strategic measurements it produced along the way. Those
are the parts worth reading if you are competing next year.

---

## 1. The season in one paragraph

Each team has Enlightenment Centers (ECs) that hold *influence*. Influence buys three unit
types: **slanderers** (earn influence for 50 rounds, then camouflage into politicians),
**politicians** (walk somewhere and *empower*: a one-shot speech that splits their conviction
over every unit in a radius, converting anything it exceeds -- including ECs), and
**muckrakers** (1-influence units that *expose* slanderers, killing them and buffing the
exposer's politicians). Neutral ECs sit on the map to be captured. Every round, each team's
highest bid buys one *vote*; 751 votes ends the game, and at round 1500 the votes decide it.
Comms are a 24-bit flag per robot that any robot can read if it knows the id.

## 2. What we built

The code is `src/bot` (13 files, ~1,800 lines). `DESIGN.md` has the full layout; the parts
that mattered:

- **Economy.** Slanderer income has *breakpoints* (21, 41, 63, 85, 107, 130, ..., 463): buying
  anything between two of them wastes the difference (`Econ.BREAK`). The first build is a
  slanderer, then scouts; standing caps on slanderers, guards and scouts; a "spare" branch so a
  centre is never idle with money in the bank.
- **Comms.** A 4-bit type and a 20-bit payload. Scouts report what they see (edges, centres,
  enemy units) and cycle their durable facts; a centre reads every child by id each round and
  rebroadcasts the most useful fact. **Every ownership claim carries a timestamp** (round/32)
  and the newer claim wins -- see section 5 for why that was necessary.
- **Capture.** The cheapest known neutral is bought at its influence + 14 when the bank allows;
  one live capturer per target; a capturer that lands adjacent announces its flip on its flag
  the turn before it speaks, so home stops buying capturers for that centre (section 5 again).
- **Guards.** Politicians that hold a ring outside the slanderers, chase enemy muckrakers, and
  speak when the speech is worth a fixed 12 points of value (a muckraker kill is worth 25). When
  a *swarm* is present -- three or more enemy muckrakers in a centre's sensor range within the
  last 50 rounds -- and no enemy politician is in range, guards cost 15 instead of 20-60. That
  one rule is the whole difference between `g_iter12` and `g_iter13`.
- **Bidding.** Adaptive: shrink the bid by a tenth after a won vote, grow it by a quarter after
  a lost one, capped at a fraction of the bank that rises through the game (1/30 before r600,
  1/5 to r1000, 1/3 after). A "safe" rule stops bidding once the opponent cannot catch up.

## 3. How we trained it (the part to steal)

Games this year take 15-45 minutes on a 4-vCPU box, so a training loop that "tries things and
sees" can run perhaps 200 games a day. Everything about the method follows from that.
`METHOD.md` is the portable version; `TRAINING_ALGORITHM.md` the exact loop.

1. **The accept gate is a sequential test.** A candidate plays the incumbent on random maps
   and sides in batches of 16; after each batch a log-likelihood ratio (`tools/sprt.py`) decides
   ACCEPT (+2.94), REJECT (-2.94) or continue, capped at 240 games. A strong change is accepted
   in ~100 games; a null one costs the cap. The incumbent is always the strongest snapshot
   (`src/g_iterN`), so the bar rises.
2. **No test before a diagnostic game proves the mechanism fires.** Three early candidates were
   implemented, compiled and *completely inert* -- a rule that never triggered, an order flag a
   newborn could never read. Only the `@tag` counters in a logged game showed it. One logged
   game costs five minutes; a wasted SPRT costs a night.
3. **Mine the games you already paid for.** After every ladder block, `tools/scrim-study.sh`
   dumps 25 metrics per side every 10 rounds from every replay (`tools/replaydump`), and
   `tools/onset.py` reports, for each metric, the first round at which its lead correlates
   with the result. The earliest riser is where to look (coverage at r100-150, slanderer lead
   at r200, centre count at r250). `tools/log-scan.sh` dumps our side's every log line for a
   whole block in one pass, so a question is a `grep`, not a dump per game.
4. **External bots only as scrimmages.** Random map, random side, rotating opponents, never a
   chosen map against a benchmark bot, never benchmark-vs-benchmark games. That is the only
   data a real contest gives you, and it keeps the ladder honest. A tier rule
   (`BENCHMARK.md`) said which opponents' replays could even be studied: not the ones we beat
   under 20% of the time, so the loop did not spend its nights on bots it could not yet touch.
5. **Sparring partners for what the mirror cannot see.** Self-play is free but a twin shares
   every blind spot. Archetypes (`src/arch_*`, one switch in `C.java`) reproduce one opponent
   behaviour each: muckraker rush, aggressive bidder, politician rush, expander, siege --
   and, from the last day, **`arch_hunt`** (muckrakers that patrol the ring where your
   slanderers live) and **`arch_lemon`** (the hunt plus attackers sized to your centres).
   Those two are the first partners that beat the current build, which is what makes a change
   against them readable. A partner has to be *run* before it is trusted: two of ours turned
   out not to reproduce the condition they were built for.
6. **When the mirror is the wrong opponent, add an arm; do not reinterpret the null.** Several
   repairs (to knowledge, bidding, stalls) gated at 53-55% against a twin that shares every
   other line, then moved the ladder from 38/48 to 40/48. The rule we settled on: a candidate
   that is *neutral* against the mirror and clearly better against the opponents that beat us
   can be accepted on the ladder's evidence -- pre-registered, with the counters named first.
7. **The dev runner's engine seed is fixed.** `tools/run-dev.sh` replays the same game until
   the code changes it, so a diagnostic rerun is like-for-like and a baseline run of the
   incumbent on the same seed is what an arm is read against. On one seed the incumbent itself
   ends 1 centre to 7; the arms only make sense relative to that.
8. **Unit tests for the bot's pure logic** (`test/bot`: comms round trips, slanderer
   economics, map knowledge, and *invariants between the tuning constants*), run after every
   change to the bot or to an analysis script. They caught a column misalignment that had been
   reporting cumulative moves as map coverage.

## 4. Timeline

| build | what changed | gate | ladder (48-game block) |
|---|---|---|---|
| v1 (16 Sep) | foundation: economy, scouting, symmetry, nav, guard/capture politicians, bidding, flags | -- | 29% |
| g_iter4-6 | opening deployment (slanderer first, not scouts): **71.9%**; economy stops only for a real threat: 68.8% | accepted | 40% |
| g_iter7-9 | caps that bound removed: slanderer caps 59.1%, the guard sink 84.4%, the capturer cap with two comms bugs 79.2% | accepted | 33/48 -> 35/48 |
| g_iter10 | (see `TRAINING_LOG.md`) | accepted | 35/48, rank 3 |
| g_iter11 | flat speech bar + the broadcast fix (a spawn order after every build starved every centre broadcast; captured centres were born deaf) + knowledge hand-off | 65.6% | 38/48, rank 2 |
| Iter 49-51 | flip intent, stamped ownership claims, abort reports, bid-while-saving, vote-estimate init, bytecode cuts, save mode off | 53-55% (inconclusive) | -- |
| **g_iter12** | the stack above, accepted on combined evidence | 55.4% | **40/48**, Elo 1798, rank 2 |
| Iter 52-57 | hold what we take (floor, sentinels, bank), target-aware attack, rich guards, interceptors | rejected / null | 38/48 |
| **g_iter13** | cheap guards under a swarm | 119-121 (neutral) | **78/96 then 298/384**, awesomelemonade 35%, rzhan11 50% |
| Iter 59-63 | rich interceptors; guards among the slanderers; attack on; scouts patrol the enemy ring; scout caps doubled | null / rejected | -- |

The two accepts that moved the ladder most were both found the same way: measure our
behaviour against what the opponent actually does, read one logged game closely enough to
find the mechanism, and instrument what the bot *hears*, not only what it does.

## 5. Engine facts we learned the hard way

All read from the engine source or measured in logged games; none guessed.

- **A robot over its bytecode budget loses whole rounds, silently.** The turn spills into the
  next round and `build`/`bid` simply do not run. One candidate's extra flag reads put the home
  centre at 20k and it lost 213-404 rounds a game while the incumbent lost 0-19; fixes worth
  +10% gated at 53% until profiled. Log `Clock.getBytecodeNum()` per stage and read
  `over=` for every centre before you trust a run.
- **A centre acts every other round** (action cooldown 2): at most ~750 builds a game. A
  1-influence muckraker costs a build slot, not influence; a swarm of 130 by r200 is a third of
  two centres' slots.
- **A centre can read any friendly robot's flag by id**, and robots act in id order, so a centre
  reads what a child set last round before that child acts this round. That is the whole basis
  of announcing a flip the turn before the speech that kills the messenger.
- **A robot built this round takes its first turn next round**; a one-round order flag is gone
  before the newborn can read it. Orders must persist through the following round.
- **A speech is split equally over every unit in its radius, EC included, friendly units too.**
  Bodies around a centre dilute an attacker; bodies around a target dilute a capturer.
- **The expose buff is `1 + 0.001 x` the influence of enemy slanderers exposed in the last 50
  rounds**, on every politician of the exposing team. It reached 6.3x in one loss and drained a
  3,500-influence centre in nineteen speeches.
- **Slanderer income per round is `I x (1/50 + 0.03 e^(-0.001 I))`** for 50 rounds: a 41
  returns 2.4x, a 463 returns 1.9x, a 1,000 returns 1.55x.
- **A converted centre's influence is the overshoot** of the flipping share, and **conversion
  restarts the player code**: every counter in a captured or retaken centre starts at zero.
- **The team's bid is its single highest centre bid**; the losing team pays half. One silent
  centre is harmless; all silent is the vote race lost.
- **Cooldown is charged on the tile you leave**, so a path costs the sum of `1/passability`
  over departed tiles. `senseNearbyRobots` returns row-major scan order. The only
  non-determinism is the final coin flip when votes, centres and influence all tie.

## 6. Strategic findings, measured

- **The opening deployment is worth more than any later reallocation.** Four 1-influence
  scouts in rounds 1-8 and a 107 slanderer at r9, against opponents who put their whole 150
  into a 130 slanderer at r1: fixing that was +22 points and the first change to carry to the
  ladder.
- **Find the constraint that binds before tuning anything.** Three separate accepts were the
  removal of a cap or a defect, not a new mechanism.
- **Knowledge must carry a timestamp.** A centre that changed hands ping-ponged between stale
  echoes for the rest of the game (520 politicians sent at a centre already ours). Refusing
  echoes instead threw away the sightings a sibling's scouts relayed (home knew no enemy
  centre for 1,200 rounds). Stamp every claim with the round of the sighting behind it.
- **A mechanism that paid when knowledge was poor can stall once knowledge is good.** "Save for
  a centre" bought an early capture when a centre rarely knew a neutral before r100; with the
  repaired channel it froze the economy at two slanderers for 20-187 rounds to buy a centre the
  ordinary chain bought nine rounds later from income.
- **A fresh capture is an empty shell.** In eight losses we gained 72 centres and lost 60, 34
  of them within 100 rounds: a centre priced at neutral + 14 is born with ~4 influence. Neither
  a 60-point floor nor four diagonal sentinels held them against attackers *sized to the target*
  (130-500 conviction, overshoots of 9-23). Every loss to the top bot was an annihilation.
- **The guard-for-muckraker trade is the defence, and its price is the lever.** Against the
  hunting archetype we built 108 politicians in 250 rounds to kill 83 influence of muckrakers.
  Not making the trade lost the slanderers (55 exposures by r250); making it at 15 instead of
  20-60 -- and only under a real swarm -- is the change that took rzhan11 from 42% to 53%.
- **Our slanderers were hunted and theirs were not.** Against rzhan11, twelve of sixteen and
  forty-seven slanderers died inside r250-500 against two and six of theirs, while they built
  five to eight times the slanderer influence; the 77k army was what that income bought.
- **A young centre that stops bidding loses the vote race for the team.** The "safe without
  bidding" estimate started at zero in every centre's fresh instance; seven young centres sat on
  136k of influence at 663-390 ahead and placed no bid for 300 rounds.
- **The mirror and the ladder disagree by design.** Repairs to knowledge and stalls gated at
  53-55% and moved the ladder; a board-strength change beat the fixed-seed baseline 5 centres
  to 1 and lost the mirror on votes to a hoarder. Read the two together, and pre-register
  which one decides.

### What predicts a win for the final bot

On 144 games of `g_iter13` against the field (noise floor ~0.19), the earliest and strongest
r200 correlates of the result, within opponent-and-map pairs, are the **coverage lead** (+0.49;
loss median -107 tiles) and the **muckraker-count lead** (+0.47; loss median -20), then moves,
slanderer lead and centre lead (+0.28 each). The final bot is beaten when the opponent has the
map and the muckrakers by r200, before the economy or the centres separate. Iteration 38
showed more scouts alone do not win (51.7%); what the swarm bots' muckrakers *do* -- hunt
slanderers, dilute speeches, block spawns -- is the difference, and `arch_hunt`/`arch_lemon`
reproduce it. `progress/ONSET.md` has the full table.

## 7. What did not work

- Holding fresh centres with influence floors, capture banks or dilution sentinels (Iteration
  52, rejected 15-33): the mirror's capturers arrive with 250-500 conviction and the
  overshoots simply grew.
- Target-aware attack (53): bought every cheap shell and made a tug-of-war of it (49 centres
  gained, 44 lost, 238 politicians aborting at contested tiles).
- Rich guards and interceptors (54, 57, 59): 300-conviction guards were spent on small targets
  or never existed because the collapse happens while poor (bank 157 at r250).
- Guards among the slanderers with a threat-only bar (60): won both sparring seeds outright
  and lost 2-14 in the first mirror batch. Fixed-seed single games are a filter, not a verdict.
- Reading a running batch: games that end by capture finish first, so a batch's early tally is
  its losses. A 0-5 read mid-batch that ended 11-5 cost a voided batch and a resumed gate.

## 8. Open questions for the next competitor

1. **How to answer a swarm without buying a hundred guards by r250.** Against `arch_lemon` the
   swarm forces ~100 guards (even at 15, the whole economy) and the sized attackers take the
   centres anyway. Conceding the swarm concedes the slanderers. Something structural --
   slanderer placement, a screen that does not die per kill, or an economy that out-produces
   the hunt -- is needed, and both partners exist to measure it against.
2. **Where our slanderers should live.** They are hunted; rzhan11's are not. Nobody has
   measured *where* theirs are relative to their centres and politicians.
3. **What to do with the bank.** In losses we hold 20-40k while the opponent holds six centres
   and an army; a flipped centre hands its hoard to the captor. Spending it on politicians sized
   to *their* centres' sighted influence, in waves, has been coded (`iter53`/`iter55`) and not
   made to work.
4. **The early vote race.** Against rzhan11 the first 250 votes cost us 300-650 influence and
   the games were still lost 674-750 with the economy gone. Cheap early votes are real; they
   need an economy to cash them.

## 9. Advice

- Build the measurement before the bot. The replay dumper, the block study, the onset table and
  the log scan are what turned 200 games a day into decisions.
- Log what the bot *hears* and *decides*, at the decision point, with a counter you can grep.
  Every accept this season came from a counter that said "this never fires" or "this fires 520
  times".
- Pre-register: the counters, the disqualifiers, the gate, and which instrument decides,
  before the first game. Then do not reinterpret a null.
- Keep the incumbent the strongest snapshot you can demonstrate, and keep `src/bot` byte-identical
  to it between candidates.
- Build a sparring partner for every opponent behaviour that beats you, and *run it* before
  you trust it: two of ours did not reproduce what they were built for, and the two that did
  became the most useful instruments we had.
- Read the engine source for anything you are about to rely on. Eleven of the facts in
  section 5 contradicted what we assumed.

## 10. Where everything is

- `src/bot` -- the bot; `src/g_iter13` the submission snapshot; `src/arch_*` the sparring partners.
- `tools/` -- `mirror.sh` + `sprt.py` (the gate), `scrim.sh` (ladder blocks), `gauntlet.sh`,
  `run-dev.sh` (fixed-seed diagnostics), `replay-dump.sh` + `replaydump/`, `scrim-study.sh`,
  `onset.py`, `correlate.py`, `log-scan.sh`, `econ-scan.sh`, `elo.py`, `bench-roster.py`,
  `snapshot.sh`, `unit-tests.sh`. `tools/README.md` describes each.
- `TRAINING_LOG.md` -- the append-only ledger: every iteration, its pre-registration, its
  counters and its verdict. `HANDOFF.md` -- the state of the loop and its gotchas.
- `METHOD.md` -- the method, written to be portable to another year. `LEARNINGS.md` -- engine
  and strategy facts. `DESIGN.md` -- the code. `BENCHMARK.md` -- the opponent roster and tiers.
  `progress/ELO.md`, `progress/ONSET.md` -- the ladder and the onset tables.
- `PROMPTS.md` -- every instruction the human gave, verbatim.
