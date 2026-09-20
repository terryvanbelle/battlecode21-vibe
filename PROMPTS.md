# Prompt record

User task prompts for this project, in chronological order, recorded verbatim
(spelling, punctuation and whitespace preserved). Append-only for the prompt
text: never edit an entry's words. Each entry is `## <number>. <date>` followed
by the prompt as typed (the date is the day the prompt was given, UTC).

## 1. 2026-09-16

We are going to build a world-class champion Battlecode bot.  Battlecode is a contest where the contestants implement bots to play against other bots in an arena.  Each year’s rules are different from prior years, but they all share some common features.  We have built bots for several prior years already (Github repositories, in order of attempt:  battlecode22-vibe, battlecode26-vibe, and battlecode25-vibe.  Each attempt was built on previous attempts).  Read through the code and documentation for these projects thoroughly to learn what has already been attempted.  Pay particular attention to files called RESEARCH.md, LEARNINGS.md, DESIGN.md, TRAINING_LOG.md, and TRAINING_ALGORITHM.md.  Also review all code and documentation from the github repository anicolao/bcenv.  Feel free to steal any code that might be useful to you.

We’re not participating in an actual Battlecode tournament, we’re practicing.  In an actual tournament, you would have two sources of data:  local fights against old versions of yourself, and online scrimmages against a variety of opponents in the tournament standings.  We can’t perfectly replicate this latter source of data, but we should try to get as close as possible. 
Please do a thorough check of the web, especially github, for competitor bots from the relevant year that are publicly accessible.  Download all of them to serve as your benchmark.  You may not read their code.  To avoid over-indexing on bots out of your league, you may not review games against any bot until you can defeat it at least 20% of the time.

When you have thoroughly read all recommended repositories, formulate your own TRAINING_ALGORITHM.md file.  This file should be concise, complete, and formulated in year-agnostic terms.  Please do not simply copy a previous year’s TRAINING_ALGORITHM file.  You are forbidden from reading port-mortems from the current year.  Post-mortems from any other year are fair game.

You should start by building a strong, robust foundation in the basics:  good economy management; ensuring that your bots can move freely and efficiently to their destinations; board exploration; exploiting map symmetries; effective combat (e.g. kite and strike); and ensuring no bytecode overruns.  Also invest time at the beginning in building a good code architecture and good tools for understanding everything that happens in a game replay file.  Generate graphs that illustrate your progress, and keep them up to date.

Make sure that your attempts are a good combination of incremental tweaks and big swings.  If you get stuck for ideas, review principles that have worked in other years.  There will be times when no attempts are successful for a long period.  At those times, it’s important to keep trying new things, and to not give up.  If you believe that a complete rewrite will help, then you should do so.

Starting with this one, save all of my prompts in a document called PROMPTS.md.

This year we will compete in Battlecode 2021.  Store all results in a new Github repository called battlecode21-vibe.  Download the rules and begin.

## 2. 2026-09-16

Make sure everything is pushed to the repository

## 3. 2026-09-16

This session doesn't seem to be visible in the web or desktop app.  Can you make sure it's visible there?

## 4. 2026-09-16

Idiot copy/paste failed to work.  Can you please write instructions to make this session remote control into a file where I can access it via a real operating system?

## 5. 2026-09-16

Oh thank Christ.  OK, please resume

## 6. 2026-09-16

You can kill all jobs on the VM belonging to 2025 and release the resources for your use

## 7. 2026-09-16

Since we have an abundance of benchmark bots (good work there!), it makes sense to find a subset of them that best simulates the scrimmage.  Choose ones that are just slightly better than us to use as the gauntlet (20-50% of games won).  There are way too many of them to run every time

## 8. 2026-09-16

Wow, 10 more hours?  Is there any way to speed that up?

## 9. 2026-09-16

You're not running the games on claude-driver, are you?  We have a bigger VM called battlecode-dev that you should be using

claude-driver should only be used to run the Claude instance

## 10. 2026-09-16

You can free up disk space on battlecode-dev by deleting games from previous projects

## 11. 2026-09-16

Make sure to document the VM setup so that future work doesn't make the same mistake

## 12. 2026-09-16

task check
Consider doing a preliminary pass on the benchmark selection using a subset of boards to speed things up.  You should be able to eliminate some bots as either too weak or too strong that way

## 13. 2026-09-16

Maybe try more maps for scan stage 1

## 14. 2026-09-17

I think maybe get rid of the key in ladder.png.  It's distorting the plot and not providing much information

## 15. 2026-09-17

Ladder graph looks awesome

## 16. 2026-09-17

It still feels like roster runs slower than in previous years.  Can you confirm and/or explain?

## 17. 2026-09-17

OK, understood.  Do what you can to use our cycles efficiently, and I guess we just have to live with the higher engine cost

## 18. 2026-09-17

I just got up, can you summarize the last 8 hours?

## 19. 2026-09-17

Thanks for the summary, sounds like you made some good progress.  What's your estimate of the % of time you are blocked on waiting for runs to finish in the VM?

## 20. 2026-09-17

Understood about this being a limitation of the infrastructure.  Let's try reducing the cells per decision.  Can you work out how many cells are needed to obtain statistical significance?

## 21. 2026-09-17

I'm good with a 72-cell accept.  If it looks like we're flailing, we can always do a one-off 108 cell run

## 22. 2026-09-17

I don't know how much of this you're already doing, but we should be taking full advantage of the roster games, given how expensive they are in time.  Please review all games in a particular roster run to determine the best next improvement to try

## 23. 2026-09-17

Sorry, by roster I guess I meant ladder, i.e. the external bots that substitute as our scrimmage

## 24. 2026-09-17

You are at all times authorized to make whatever number and degree of changes to the bot that you think are appropriate, no need for my approval

## 25. 2026-09-17

We're going to make a structural change to better simulate actual contest conditions.  Up until now, you've been allowed to run as many games as you like on the external bots, on as many. boards as you like, using whichever side you like.  In reality, games against external opponents occur by challenging them to a scrimmage.  Players are assigned random sides on a random board, and the game is played out, with ELO rankings updated from the result.  In a real competition, you would only have this form of data on external opponents.  From now on, I'd like you to only run against opponents by choosing a random board and a random side, and not hit the same opponent too many times in a row.  You are still free to play against old versions of yourself using the old, unrestricted methodology.  Please adjust your strategy and training algorithm accordingly.

## 26. 2026-09-17

It would be great to maintain a constantly updated ELO-ranked bot list, so that we can see our submission climb over time.  We'd focus our scrimmage challenges on those slightly ahead of us in the rankings

## 27. 2026-09-17

See if you can do the ladder without having external bots play against each other.  That's a waste of VM resources.  I'm ok with having a less accurate ranking, but only play our bot vs external bots

## 28. 2026-09-17

OK, I'm going to switch us from Fable to Opus so that we don't blow our weekly budget.  Please do whatever you need in order to make the transition smoothly

## 29. 2026-09-17

OK, switched from Fable to Opus

## 30. 2026-09-17

That sounds to me like a reasonable change

## 31. 2026-09-18

Agreed about doing a diagnostic run first to avoid doing unnecessary work.  Make sure that's encoded in TRAINING_ALGORITHM.md so we don't lose it

## 32. 2026-09-18

I checked with Google about 2021 games running slowly, and it confirmed that that year was notorious for slow runs.  Here's their answer.  See if there's anything there that you can use:

Battlecode 2021 matches are notoriously slow because of poor engine and visualizer optimization, combined with specific game mechanics that generated massive unit counts. During the 2021 tournament ("Politicians and Conviction"), the engine suffered from heavy Client-side memory leaks, and the visualizer heavily consumed system RAM. Furthermore, strategies like "Muck Spam" caused thousands of active units to execute code simultaneously every single round, dragging the JVM simulation to a crawl.If you are trying to test or review Battlecode 2021 bots locally, use the following methods to dramatically speed up execution times:1. Run Headless Mode (Skip the Visualizer)The Battlecode visualizer is the single largest bottleneck. Avoid running games inside the client interface. Instead, generate the match purely via the terminal or command prompt using Gradle.Command: Run ./gradlew headless (or gradlew headless on Windows).This simulates the match without rendering any frames, saving the playback data to a .bc21 file inside your /matches folder, which you can open and watch later at your own speed.2. Force Java Garbage Collection (JVM Args)Because the 2021 engine leaks memory during extensive match sets, allocating more memory and enabling aggressive garbage collection prevents your local environment from stuttering.Open your project's gradle.properties or build.gradle file.Ensure your JVM arguments allocate adequate RAM and use a modern garbage collector. Add or tweak the configuration to include:textorg.gradle.jvmargs=-Xmx4g -XX:+UseG1GC
Use code with caution.3. Trim Down Match Constants (For Local Testing)The 2021 game was originally built to sustain a grueling 3,000 rounds per game. While "Teh Devs" later cut this down to 1,500 rounds for official competition to respect match limits, rendering long stalemates can take forever.If you are simply testing local logic adjustments, choose smaller maps (e.g., 32 x 32) over maximal 64 x 64 maps.Manually shorten the round limit inside your local engine config files to isolate performance behaviors quickly.4. Optimize Bot-Side Pathfinding & CachingIf it is your own bot causing the frame rate drop, it may be over-utilizing the engine's processing overhead. Even if your code stays under the strict bytecode limit, certain calculations stall execution.Pre-calculate and Cache Data: Implement look-up tables or grid maps to avoid running repetitive math loops on every unit's turn.Fix Matrix Inefficiencies: When iterating through maps, loop through rows first (grid[row][col]), rather than columns. Java processes multi-dimensional arrays sequentially; accessing them out of order breaks CPU cache lines and creates massive micro-stuttering.

## 33. 2026-09-18

task check

## 34. 2026-09-18

/loop 15m task check

## 35. 2026-09-18

I agree with your instinct to look at the behaviors of your opponents who beat you.  Given how expensive games are, any effort invested in generating the best possible hypothesis and solution will pay off.  I recommend you spend extra time here.  Are you using all the information provided by the replaydump tool?

## 36. 2026-09-18

Congratulations!

## 37. 2026-09-19

No gating against external bots, because it wouldn't be realistic in an actual competition.  You can't scrimmage without submitting a bot (though you could always withdraw a submission if you find yourself falling in the ratings)

## 38. 2026-09-19

You should, however, use the scrimmage games as much as possible to decide your next hypothesis

## 39. 2026-09-19

Maps have passability scores between 0 and 1, right?  Doesn't that suggest an unrolled BFS for navigation might work better than bug nav?

## 40. 2026-09-19

That kind of thing should show up in slower unit speeds vs. competitors.  Have you found that to be the case?

## 41. 2026-09-19

Please add map exploration to the list of metrics that you track when you do the ladder runs

## 42. 2026-09-19

What is the full list of metrics that you collect on ladder runs?

## 43. 2026-09-19

Would it be possible to calculate all metrics on wins as well, and perhaps use correlation of win rate vs. each metric to decide what to work on next?

## 44. 2026-09-19

That seems wise to keep in mind.  Let's explore whether the correlation method can serve as a general-purpose hypothesis generation mechanism.  At a minimum, it seems like a good source of data to inform hypotheses, even if it doesn't end up being the full picture

## 45. 2026-09-19

That seems like a good approach to me

## 46. 2026-09-19

Let's verify that it works in practice, and if so, let's make sure it's well documented so that future generations of competitors can take advantage of it.  But I'm getting ahead of myself

## 47. 2026-09-19

Would applying the methodology on more rounds help?  With a finer-grained correlation graph, we should be able to identify which metric came to prominence *first*, which would be strong evidence that it's the causal agent

## 48. 2026-09-19

In fact, I'd like to see a graph of that

## 49. 2026-09-19

But we're applying this methodology to ladder runs, right?  The whole point is to find out which advantages of our opponents are the ones that we need to focus on

## 50. 2026-09-19

Right, we're using mirror games as a substitute to work out the method, but then we'll apply it to ladder games when we're ready

## 51. 2026-09-19

Let's normalize the metrics so that a high degree of positive correlation with winning is always good.  Otherwise, we'll have some metrics where negative correlation is good, and some where positive correlation is good

## 52. 2026-09-19

Can I see the new graph?

## 53. 2026-09-19

When you save this graph in Github, please also include a doc that explains the abbreviations and explains how the metrics are calculated

## 54. 2026-09-19

This graph seems to be missing quite a few metrics.  I don't see moves, meanMoves, aba or swamp, for example

## 55. 2026-09-19

You could also create progressive versions of these metrics, where they're computed over all the rounds *so far*

## 56. 2026-09-19

Aside from the graph, also show me this information in tabular form, sorted from earliest onset to latest

## 57. 2026-09-19

Try out iteration 33, it'll provide independent verification.  I'd also like you to do a check for bugs on your metrics code, and write some unit tests.  But if all the tests pass, and especially if iteration 33 doesn't pan out, I say we trust the numbers

## 58. 2026-09-19

Good, glad to know the tests caught a real bug.  Make sure to run them every time you change the scripts

## 59. 2026-09-19

I'd also like you to write unit tests for the bot and run them after every change

## 60. 2026-09-19

Is everything checked in?  When I look at the onset-ladder.png graph you generated, I see a negative correlation for coverage and coverage average.  Only unitinf metrics are positively correlated throughout

## 61. 2026-09-19

Your interpretation seems right to me.  Let's proceed with the methodology, and see how it does

## 62. 2026-09-20

Wait, both sides have the same behavior in a mirror test?  I thought a mirror test posed the accepted bot against that bot with a change

## 63. 2026-09-20

Go for it

## 64. 2026-09-20

Go ahead

## 65. 2026-09-20

Nice!  Thinking good thoughts about the ladder test

## 66. 2026-09-20

/loop list, /loop delete f4764b51, then: /loop 15m task check.  If you're not working on anything and the VM is idle, start a new idea

## 67. 2026-09-20

I just got up and saw we've moved from 9th place to 4th, good work!  We might want to bring some new bots into the scrimmage soon

## 68. 2026-09-20

65 is fine for now

## 69. 2026-09-20

Can you update BENCHMARK.md with the new data?

## 70. 2026-09-20

Yeah, I'd rather not have stale data in the repository, so old documentation and graphs should either be updated or deleted

## 71. 2026-09-20

If you come out neutral against the mirror, but in a way that better aligns you with the behavior of the opponents that beat you, maybe that's worth something

## 72. 2026-09-20

Don't worry too much about awesomelemonade making it onto the roster.  The point of the locked bots was simply to keep us from spending too much time on bots that we aren't close to being able to beat.  Having some extra-hard bots on the roster isn't the end of the world, and we're going to have to tackle that bot eventually
