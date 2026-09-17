# Prompt record

User task prompts for this project, in chronological order, recorded verbatim
(spelling, punctuation and whitespace preserved). Append-only: never edit an
existing entry. Headings and code fences are record metadata, not prompt text.

## Prompt 1: Build a Battlecode 2021 champion bot

```text
We are going to build a world-class champion Battlecode bot.  Battlecode is a contest where the contestants implement bots to play against other bots in an arena.  Each year’s rules are different from prior years, but they all share some common features.  We have built bots for several prior years already (Github repositories, in order of attempt:  battlecode22-vibe, battlecode26-vibe, and battlecode25-vibe.  Each attempt was built on previous attempts).  Read through the code and documentation for these projects thoroughly to learn what has already been attempted.  Pay particular attention to files called RESEARCH.md, LEARNINGS.md, DESIGN.md, TRAINING_LOG.md, and TRAINING_ALGORITHM.md.  Also review all code and documentation from the github repository anicolao/bcenv.  Feel free to steal any code that might be useful to you.

We’re not participating in an actual Battlecode tournament, we’re practicing.  In an actual tournament, you would have two sources of data:  local fights against old versions of yourself, and online scrimmages against a variety of opponents in the tournament standings.  We can’t perfectly replicate this latter source of data, but we should try to get as close as possible. 
Please do a thorough check of the web, especially github, for competitor bots from the relevant year that are publicly accessible.  Download all of them to serve as your benchmark.  You may not read their code.  To avoid over-indexing on bots out of your league, you may not review games against any bot until you can defeat it at least 20% of the time.

When you have thoroughly read all recommended repositories, formulate your own TRAINING_ALGORITHM.md file.  This file should be concise, complete, and formulated in year-agnostic terms.  Please do not simply copy a previous year’s TRAINING_ALGORITHM file.  You are forbidden from reading port-mortems from the current year.  Post-mortems from any other year are fair game.

You should start by building a strong, robust foundation in the basics:  good economy management; ensuring that your bots can move freely and efficiently to their destinations; board exploration; exploiting map symmetries; effective combat (e.g. kite and strike); and ensuring no bytecode overruns.  Also invest time at the beginning in building a good code architecture and good tools for understanding everything that happens in a game replay file.  Generate graphs that illustrate your progress, and keep them up to date.

Make sure that your attempts are a good combination of incremental tweaks and big swings.  If you get stuck for ideas, review principles that have worked in other years.  There will be times when no attempts are successful for a long period.  At those times, it’s important to keep trying new things, and to not give up.  If you believe that a complete rewrite will help, then you should do so.

Starting with this one, save all of my prompts in a document called PROMPTS.md.

This year we will compete in Battlecode 2021.  Store all results in a new Github repository called battlecode21-vibe.  Download the rules and begin.
```

## Prompt 2: Push everything

```text
Make sure everything is pushed to the repository
```

## Prompt 3: Make the session visible in the apps

```text
This session doesn't seem to be visible in the web or desktop app.  Can you make sure it's visible there?
```

## Prompt 4: Write Remote Control instructions to a file

```text
Idiot copy/paste failed to work.  Can you please write instructions to make this session remote control into a file where I can access it via a real operating system?
```

## Prompt 5: Resume (after `/remote-control vibe-bc21`)

```text
Oh thank Christ.  OK, please resume
```

## Prompt 6: Free the VM

```text
You can kill all jobs on the VM belonging to 2025 and release the resources for your use
```

## Prompt 7: Pick a gauntlet subset

```text
Since we have an abundance of benchmark bots (good work there!), it makes sense to find a subset of them that best simulates the scrimmage.  Choose ones that are just slightly better than us to use as the gauntlet (20-50% of games won).  There are way too many of them to run every time
```

## Prompt 8: Speed

```text
Wow, 10 more hours?  Is there any way to speed that up?
```

## Prompt 9: Use battlecode-dev for games

```text
You're not running the games on claude-driver, are you?  We have a bigger VM called battlecode-dev that you should be using
```

```text
claude-driver should only be used to run the Claude instance
```

## Prompt 10: Disk on battlecode-dev

```text
You can free up disk space on battlecode-dev by deleting games from previous projects
```

## Prompt 11: Document the VM setup

```text
Make sure to document the VM setup so that future work doesn't make the same mistake
```

## Prompt 12: Preliminary benchmark pass on a subset of boards

```text
task check
Consider doing a preliminary pass on the benchmark selection using a subset of boards to speed things up.  You should be able to eliminate some bots as either too weak or too strong that way
```

## Prompt 13: More maps in scan stage 1

```text
Maybe try more maps for scan stage 1
```

## Prompt 14: Ladder chart legend

```text
I think maybe get rid of the key in ladder.png.  It's distorting the plot and not providing much information
```

## Prompt 15: Ladder graph

```text
Ladder graph looks awesome
```

## Prompt 16: Roster speed

```text
It still feels like roster runs slower than in previous years.  Can you confirm and/or explain?
```

## Prompt 17: Use cycles efficiently

```text
OK, understood.  Do what you can to use our cycles efficiently, and I guess we just have to live with the higher engine cost
```

## Prompt 18: Morning summary

```text
I just got up, can you summarize the last 8 hours?
```

## Prompt 19: Time blocked on the VM

```text
Thanks for the summary, sounds like you made some good progress.  What's your estimate of the % of time you are blocked on waiting for runs to finish in the VM?
```

## Prompt 20: Cells needed for significance

```text
Understood about this being a limitation of the infrastructure.  Let's try reducing the cells per decision.  Can you work out how many cells are needed to obtain statistical significance?
```

## Prompt 21: 72-cell accepts

```text
I'm good with a 72-cell accept.  If it looks like we're flailing, we can always do a one-off 108 cell run
```

## Prompt 22: Review all games in a roster run

```text
I don't know how much of this you're already doing, but we should be taking full advantage of the roster games, given how expensive they are in time.  Please review all games in a particular roster run to determine the best next improvement to try
```

## Prompt 23: Ladder, not roster

```text
Sorry, by roster I guess I meant ladder, i.e. the external bots that substitute as our scrimmage
```
