# battlecode21-vibe: session rules

Read `SETUP.md` before running anything. The two rules that past sessions got wrong:

1. **Never run engine games on this machine (`claude-driver`).** It is an e2-small
   (2 vCPU, 2 GB) that exists only to host the Claude session. A single heavy
   game is a 1.1-1.4 GB process; two of them swap the box to a standstill
   (2026-09-16: 50-minute games, 40% I/O wait). Every game, gauntlet, scan and
   benchmark compile runs on the VM `battlecode-dev` through `tools/vm-run.sh`.
   `tools/run-match.sh`, `tools/run-dev.sh` and `tools/gauntlet.sh` are the
   *remote* runners; invoke them on the VM, not here. Replay dumps, plots and
   selectors are fine here.
2. **Push after every commit** (the user follows the repo from GitHub and the
   Claude app) and **record every user prompt verbatim in `PROMPTS.md`**.

3. **External bots are played only as scrimmages** (random map, random side,
   rotating opponents) through `tools/scrim.sh`; never choose a map or a side
   against a benchmark bot (user rule 2026-09-17, TRAINING_ALGORITHM 4.5.2).
   Our own snapshots and archetypes stay unrestricted.

Working rules that already live elsewhere: `TRAINING_ALGORITHM.md` (the loop),
`BENCHMARK.md` (never read benchmark source; the 20% rule), `tools/README.md`.
