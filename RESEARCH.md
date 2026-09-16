# RESEARCH.md -- what recurs across Battlecode years (2021 excluded)

Cross-year findings from published team post-mortems, kept here for the moment
the loop runs out of local ideas (`TRAINING_ALGORITHM.md` section 7). The
season being played is **2021**, so every 2021 post-mortem is out of bounds,
first- or second-hand. This file is built only from 2019, 2020, 2022, 2023,
2024 and 2025 sources, as synthesised in the predecessor projects'
`RESEARCH.md` files and in `bcenv/HISTORICAL_LEARNINGS.md`, with each entry
below traced to a non-2021 team.

**Disclosure.** The predecessor documents this project was required to read
(`battlecode22-vibe/RESEARCH.md`, `bcenv/HISTORICAL_LEARNINGS.md`) contain a
few paragraphs summarising 2021 post-mortems. Those paragraphs were read as
part of that mandatory review before this project's own rule could be applied;
nothing from them is reproduced here, no 2021 post-mortem will be opened, and
strategy for this season will be derived from the rules, the engine and our own
measurements.

## 1. Process (the part that transfers best)

- **Infrastructure first, strategy second.** Navigation, communication,
  resource gathering and the test harness rarely change when the strategy
  does. Every strong team says so (The High Ground 2020, SPAARK 2025, Stone
  Tao 2020).
- **A parallel local match runner is table stakes**, and teams copy each
  other's (SPAARK 2025 traces theirs back through 4 Musketeers 2023 to
  Producing Perfection 2022). A first-year team's top regret was not having
  one (no thoughts head empty 2023).
- **A handful of games is not a measurement** (don't @ me 2023). Full map sets,
  both sides, against old versions *and* outside opponents.
- **Outside opponents outrank frozen self-copies.** Self-play is blind to any
  weakness both sides share (SPAARK 2025; muskellunge 2024 found problems only
  external bots exposed). Nontransitivity is real: beating A which beats B says
  nothing about B (SPAARK 2025).
- **Prioritise by expected win-rate, not by replay to-do lists.** Do not fix
  everything a replay shows; pick one or two changes and do them very well (The
  High Ground 2020, both years' failure modes: too cautious in 2019, too many
  half-done changes in 2020).
- **Basics done robustly beat sophistication.** "Every time I'd implement a
  sophisticated strategy that requires a lot of coordination it would flop"
  (Java Best Waifu, 2020 winner). "Make it work, make it right, make it fast"
  (don't @ me 2023).
- **Rewrite when the strategy changes.** A from-scratch bot "is usually faster
  than expected and way better in the long run" (Java Best Waifu 2020).
- **Test the counter against the thing it counters**, in volume, before
  trusting it (confused 2020).
- **Do not submit an untested last-minute change** (SPAARK 2025, twice).
- **Overnight volume finds real gains** (SPAARK 2025).
- **Root-cause single losses from replays**; a null pointer in a corner-case
  spawn geometry cost 4 Musketeers (2023) a seeded match; Gone Fishin' (2023)
  traced a finals loss to one scouting pattern.

## 2. Perennial mechanics

- **Maps are symmetric** by rotation or reflection and this is "Battlecode 101":
  record what you see, eliminate symmetry hypotheses as terrain contradicts
  them, extrapolate the unseen half (The Kragle 2025, Gone Fishin' 2023 -- who
  assumed rotation and were burned; 4 Musketeers 2023 invalidated candidate
  mirror locations as they explored). Scouting toward the map centre
  disambiguates fastest.
- **Bytecode budgets forbid textbook search.** BFS on a 20x20 region eats a
  whole budget (The Kragle 2025). Winners use bug navigation on binary
  passability and greedy movement on graded passability (The Kragle 2025,
  SPAARK 2025 dropped BFS for Bug2), with an unrolled fixed-radius local search
  where affordable (4 Musketeers 2023), a turn stack to escape concave traps
  (Gone Fishin' 2023), soft treatment of friendly units as obstacles (75% wall
  / 25% empty worked best, Gone Fishin' 2023), and randomised tie-breaks as the
  last resort against loops.
- **Communication is a scarce, structured resource.** Design the schema before
  the logic: sectors instead of coordinates (4 Musketeers 2023), batched writes
  with dirty flags, a local cache refreshed lazily, bit-packed arrays of longs
  for explored tiles (SPAARK 2025). Define what happens when a writer dies
  (5 Musketeers 2022).
- **Micro beats macro.** "Slightly improved macro might add 5%; micro can add
  30-50%" (Gone Fishin' 2023). Always kite after attacking when the action
  cooldown allows; attack even blind if the cooldown resets anyway; retreat
  only near death and retreat as a group; prioritise targets by
  kills-per-turn; step onto favourable terrain after acting (Gone Fishin' 2023,
  4 Musketeers 2023, cout for clout 2024, Just Woke Up 2025).
- **Emergent coordination beats commanded coordination.** Spawn order as an
  implicit formation won two-thirds of self-play games with no messages (Gone
  Fishin' 2023). Repulsion between explorers spreads them without assignment.
- **Identify the real scarce resource** and denial tactics on it: partially
  completing a shared objective so the enemy cannot finish it (confused 2025),
  physically occupying spawn tiles, killing production before combat units.
- **Adapt rush vs. turtle per map from measured signals** (distance to enemy,
  passability, resource density), not at compile time (4 Musketeers 2023,
  confused 2025 kept both doctrines).
- **Balance patches invalidate razor-thin edges** (2023 HQ damage, 2025 tower
  HP). Keep mechanics decoupled from the strategy on top so a pivot is days,
  not a rewrite of everything.
- **State machines with remembered return points** let units resume interrupted
  tasks (Just Woke Up 2025); goal objects with start/run/stop conditions make
  behaviour experimentable (The Kragle 2025).
- **Economic discipline**: fewer wasted actions beats a better planner
  (confused 2025); reconstruct hidden information from income (Om Nom 2025).

## 3. Sources

All at `battlecode.org/assets/files/postmortem-<year>-<team>.pdf` unless noted.
2019: smite, Oak's Last Disciple, Big Red Battlecode, Double J (GitHub).
2020: Java Best Waifu, The High Ground, confused, Stone Tao (stonet2000.github.io).
2022: 5 Musketeers. 2023: Gone Fishin', 4 Musketeers, don't @ me, no thoughts
head empty. 2024: cout for clout, muskellunge (dteather.com). 2025: Just Woke
Up, confused, Om Nom, SPAARK, The Kragle. Plus Ivan Geffner's undated "A Guide
to Battlecode" (XSquare). **No 2021 source is used.**
