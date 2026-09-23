package bot;

/** Tunable constants. One place, so a dose ladder is a one-line diff. */
public final class C {
    public static final boolean DEBUG = true;      // @tag log lines on/off
    /** Sparring archetype switch: 0 = the real bot; 1 = muckraker rush; 2 = aggressive bidder; 3 = politician rush; 4 = neutral-EC expander; 6 = slanderer hunt (rzhan11's r250-500: muckrakers patrol the ring around the enemy centre where slanderers live); 7 = the hunt plus target-aware attacks on enemy centres (awesomelemonade-like). Set by tools/snapshot.sh <name> <archetype>. */
    public static final int ARCHETYPE = 0;
    public static final int BC_REPORT_EVERY = 50;  // rounds between @bc lines

    // Iteration 22 (saving mode, uncapped): the mechanism Iterations 16 and 21 pre-registered but never actually ran --
    // both capped the target at 320 influence and Iteration 21 also at d^2 500, and the logs show 0 firings on the maps
    // that mattered (cheap neutrals lie far, near ones are 500). The census says the gap opens before r200: ECs 1.6 v 2.8.
    // Once any neutral is known and round <= SAVE_UNTIL, the EC keeps SAVE_SLANDERERS slanderers for income and saves
    // everything else until it can send one full-price capturer (+SAVE_BANK, so the new EC is not converted while empty).
    // Iteration 51: OFF. Save mode was accepted (Iteration 22) when a centre rarely knew a neutral before r100. With
    // Iteration 49/50's channel a centre knows one by r50 and freezes at two slanderers for 20-80 rounds to buy it.
    // JerryIsEvil, both homes identical to r40: g_iter11's home (no neutral known yet) built 1,072 influence of
    // slanderers and still captured the same 503-neutral at r91 from income; the candidate's home saved from r54,
    // captured at r82 with a fifth of the economy, and its second centre froze 80 rounds and abandoned. Five of six
    // gate losses had fewer slanderers AND fewer politicians than g_iter11 at r300. The normal chain captures when
    // the bank is there (captureAffordable) and compounds meanwhile.
    public static final boolean SAVE_MODE = false;     // true = Iterations 22-50; false = g_iter4's chain
    public static final int SAVE_UNTIL = 200;          // dose 1 (dose 2 extended this to 300 and repeated: rejected, 46.5%)
    public static final int SAVE_MAX_TARGET = 600;     // covers the 500-influence neutrals that sit near home
    public static final int SAVE_SLANDERERS = 2;       // income kept while saving
    public static final int SAVE_BANK = 60;            // the new EC starts with this
    // Iteration 27 (danger means a real threat): `danger` is any enemy inside the EC's r^2 40 sensor, and it gates every
    // slanderer branch -- so one 1-influence enemy muckraker within ~6 tiles stops our economy dead. The opponents build
    // exactly those in bulk (rzhan11 on Stonks: 24 muckrakers by r100, 114 by r600), and our slanderers fall from 71 at
    // r300 to 3 at r600 while theirs rise to 117. Production now stops only for something that can actually hurt:
    // an enemy politician in sensor range, or a muckraker close enough to expose a newborn slanderer at once.
    public static final int ECON_DANGER_POL_CONV = 20;   // an enemy politician of at least this conviction blocks the economy
    public static final int ECON_DANGER_MUCK_D2 = 9;     // ... as does a muckraker this close (it would expose the newborn)
    // Iteration 34 (the opening deployment): the unit-influence LEAD is the only metric that predicts the result from
    // r50 (+0.37, rising to +0.68), and in losses we are already 226 behind at r50 from an identical 150 start.
    // Measured opening, 123kevinlee vs us: they spend their whole start on a 130-influence slanderer at r1; we spend
    // r1-r7 on four 1-influence scouts, deploy a 107 slanderer at r9, then fragment the rest into 21s earning 1/round.
    public static final int OPENING_SLANDERER_FIRST = 1;  // build the first slanderer before the scouts (0 = g_iter6)
    public static final int MIN_SLANDERER_SIZE = 41;      // below this, spend the action on a 1-influence scout instead
    // economy
    public static final int MAX_SLANDERERS = 20;        // Iteration 30 (was 12): the cap, not influence, was capping our economy
    // Iteration 35 (the guard sink): the spare branch built a guard whenever `guards < slanderers + 2`, so every
    // surplus influence became a standing body. Diagnostic vs awesomelemonade on NotAPuzzle: 26 guards at r150,
    // 38 at r200, 46 at r250 against 25-40 slanderers, EC influence never above 336 -- while four neutral centres
    // were known from r150 and cost 100-1272, so not one was affordable. Four capture speeches in 610 rounds.
    // The spare branch now fills the economy first and caps standing guards.
    // Dose 1 capped the spare branch's guards at 12 but kept its `|| spare >= 300` escape, and the diagnostic
    // showed the cap never bound: guards still reached 31 at r200 and 46 at r250, because a rich centre took the
    // escape on every build. The escape is exactly backwards -- a big bank is what buys a centre. Dose 2 removes it.
    public static final int SPEND_GUARD_CAP = 12;       // dose 1 (was: unbounded, tracked slanderers + 2)
    // Iteration 47: how readily a guard speaks. The threshold was max(12, conviction/3), so a
    // politician grows fussier as it grows bigger -- a 300-conviction guard demands 100 of value before
    // it will fire. Measured across six rzhan11 games: our kills per speech match or beat theirs in all
    // six (1.15-1.73 v 0.91-1.27) while they speak up to 3.5x as often, so we win the exchange rate and
    // lose the attrition war on volume. Dose 1 makes the bar flat instead of proportional.
    public static final int SPEECH_MIN_VALUE = 12;      // flat floor
    public static final int SPEECH_CONV_DIV = 0;        // 0 = flat; the old rule was conviction/3
    public static final int MAX_GUARDS = 10;              // absolute cap; the live cap is GUARD_BASE + slanderers/2 (fewer bodies = less congestion)
    public static final int GUARD_BASE = 4;
    public static final int EARLY_SCOUTS = 4;
    // Iteration 38: the standing scout cap. It was `3 + round/300 + 3 if rich`, i.e. six for most of a
    // game, while the strong opponents field 22 muckrakers at r100 and 48 at r200. In losses we see
    // less than half the map they do at r200 (145 tiles to 338) and hold one centre to their four.
    // Muckrakers cost 1 influence, so the price of this is the build action, not the economy.
    public static final int SCOUT_BASE = 8;             // dose 1 (was 3)
    public static final int SCOUT_PER_ROUND = 40;       // plus round/this (was round/300)
    public static final int SCOUT_MAX = 40;             // absolute ceiling
    // Iteration 37: the cap on capture politicians in flight. Pinned at 2 from r150 to r300 while five
    // real centres were known and 2,905-14,145 influence sat banked -- ten capturers' worth. The 2 -> 4
    // test was rejected 120-120 before, but that ran with the guard sink (no money) and the stale neutral
    // list (extra capturers dispatched to centres already ours), so it could only buy waste.
    // Iteration 48: knowledge hand-off to captured centres. A captured centre is born knowing nothing,
    // has no children to read and no sibling ids to read, so it is DEAF until a scout it builds happens
    // to pass a sibling. Measured over four logged games: home centres know 3 neutrals, captured centres
    // know 0 in nine of fourteen cases. Capture timing vs rzhan11 shows the cost -- our cadence is one
    // centre per 110-150 rounds to their 40-80, because only home ever expands and home is broke after
    // its first wave (SlowMusic: two captures by r140, then none in 1,360 rounds with five on the map).
    // Three pieces: (1) a centre absorbs flags of ALL nearby friendlies, not only its children;
    // (2) scouts route through own centres they have not visited, delivering their fact rotation;
    // (3) scouts broadcast HOME's id, which the sibling list excludes, so a newborn can readSiblings(home).
    public static final int HANDOFF = 1;                // 0 = the old behaviour
    public static final int HANDOFF_READS = 24;         // nearby friendly flags a centre reads per turn
    // Dose 5: with the channel open, every scout learns the enemy centre by ~r150 and the scout code sends any
    // scout that knows it to go and sit beside it -- so all of them abandon exploration at once, march across
    // the map and die on the way. Waypoint picks to r400 fell 188 -> 42, coverage 537 -> 274. Only one scout in
    // this many camps; the rest keep sweeping. (Iteration 33 tried this at 51.7%, when the channel was blocked
    // and scouts rarely learned the enemy centre at all, so it could barely act.)
    public static final int CAMP_ONE_IN = 4;
    // Iteration 62: the scouts that used to camp beside the enemy centre (one in CAMP_ONE_IN) patrol the ring 3-8 tiles
    // around it instead -- arch_hunt's behaviour, which exposed 39-55 of our slanderers in 250 rounds when we did not
    // answer it. Our exposures of theirs also buff every politician of ours by 0.001 x the influence exposed.
    public static final int HUNT_PATROL = 1;            // 0 = camp (g_iter13)
    public static final int MAX_CAPTURERS = 4;          // dose 1 (was 2)
    public static final int MAX_SLANDERER_SIZE = 463;   // largest breakpoint we buy
    public static final int BID_CAP_DIV = 6;            // (unused) historical cap
    public static final int BID_EARLY_DIV = 30;         // Iteration 9: before r600 bid at most influence / this (baseline: /12 before r200, /8 to r600); dose 12-8 / 30 / 60
    // never idle: when every capped branch declines and this much is spare above the reserve, build anyway
    public static final int SPARE_MIN = 60;             // dose ladder: 1<<30 (off, = g_iter2) / 60 / 20
    public static final int SPEND_SLANDERER_CAP = 40;   // Iteration 30 (was 24): surplus keeps compounding instead of becoming guards

    // slanderer positioning
    public static final int SLANDERER_RING_MIN = 8;     // d^2 from home: stay well outside the spawn ring
    public static final int SLANDERER_RING_MAX = 45;
    public static final int CROWD_D2 = 2;                // neighbours within this d^2 count as crowding
    public static final int CROWD_MAX = 2;               // more than this many adjacent friends -> spread out

    // politician
    public static final int GUARD_LEASH_D2 = 80;        // guards wander this far from home
    public static final int GUARD_RING_MIN = 20;        // guards hold outside the slanderer ring
    // Iteration 40: an expired slanderer became a GUARD and stayed near home, so the standing
    // population around a centre grows without bound -- 63 "guards" around one centre at r1500,
    // which walled it in for 458 rounds of the game. They are full-conviction politicians; send
    // them at the enemy instead. 0 = the old behaviour.
    public static final int CAMO_ATTACKS = 1;
    // Iteration 49 (bank and knowledge hygiene): three doses, each with its own counter in @econ.
    // (a) The save branch waited for a centre it could never afford. BlobWithLegs vs awesomelemonade: 187 save
    //     rounds with 2 slanderers while doBid spent 1/30 of the bank every round -- 794 on bids by r200, the
    //     bank fell 324 -> 119 against a price of 374, and nothing else was built until r200. Three of the
    //     block's ten losses had 80-187 save rounds by r200 (one of 38 wins). While waiting: no bid, and a bound.
    //     Iteration 50: the gate's losses showed the cost -- 0-12 votes at r100 against 14-56 -- and by r300 the
    //     bank difference had not decided anything. Bid normally; the 80-round bound alone ends the stall.
    public static final int SAVE_NO_BID = 0;            // 1 = Iteration 49's no-bid; 0 = bid while saving (g_iter11)
    // Iteration 50: `enemyVotesEst` (rounds our team gained no vote, the "safe without bidding" bound) started at
    // zero whenever a centre's player code started -- a captured neutral, or a centre lost and retaken, because the
    // engine runs a fresh instance for the new team. BadSnowflake mirror loss: seven young centres, 663-390 ahead
    // with 135,846 banked at r1200, and not one bid in the last 300 rounds; the opponent bought a vote a round at
    // 6-7 influence and won 689-663. The engine takes only the team's highest bid, so all-young means silence.
    public static final int EST_INIT_AT_BIRTH = 1;      // 0 = start at zero (g_iter11)
    // Iteration 50: the centre's turn was over its 20k bytecode budget on build turns (home lost 121-404 rounds a
    // game after Iteration 49's reads; g_iter11 0-19). Profiled and cut: claimed() over live capturers only, the
    // child list compacted only on a death, each flag value absorbed once a turn, and sibling/neighbour reads
    // moved to the off-turn (the centre builds every other round).
    public static final int OFFTURN_READS = 1;          // 0 = read every turn (Iteration 49)
    // Iteration 56 (do not trade a guard for a wandering muckraker): against arch_hunt on Arena (and rzhan11 in the
    // ladder) we built 108 politicians in the first 250 rounds and their 1-influence muckrakers died by the
    // hundred -- each death one of our guards spent in a speech. The flat speech bar (Iteration 47) values any
    // muckraker kill at 25, so a lone wanderer six tiles from anything is worth a 20-60 guard. A muckraker is
    // worth killing when it threatens something: a slanderer of ours within THREAT_SLA_D2 or a centre within
    // THREAT_EC_D2 of it. Otherwise its kill is worth MUCK_IDLE_VALUE, below the bar on its own.
    //     Arm 1 (idle value 5): annihilated at r479 with 55 exposures by r250 against 0 -- the trade IS the defence.
    //     Arm 2 changes its price instead: while no enemy politician is in a centre's sensor range, guards cost
    //     GUARD_MUCK_COST (5 conviction after tax kills a 1-influence muckraker at radius 1-2) instead of 20-60.
    public static final int MUCK_IDLE_VALUE = 25;       // 25 = g_iter12 (every muckraker worth a speech); arm 1 was 5
    public static final int GUARD_MUCK_COST = 15;       // guard cost while the only enemies in sensor range are muckrakers (0 = g_iter12)
    // Iteration 58: 56 unconditionally (cheap guards whenever no enemy politician was in range) took awesomelemonade
    // to 3/6 -- the best ever -- and lost the mirror 18-30, whose 300-1,000 attackers convert 15-guards wholesale.
    // The cheap guard is a swarm answer, so it is bought only while a swarm is present: SWARM_MUCKS enemy
    // muckrakers in the centre's sensor range at once, within the last SWARM_MEMORY rounds.
    //     Dose 1 (3 at once, 50 rounds) under-fired against arch_hunt: 156 guards at 20 to 86 at 15, and the r726
    //     win became a loss on votes. The patrol spreads its muckrakers around the ring, so few are in one
    //     centre's sensor at once. Dose 2: two at once, remembered for 100 rounds.
    //     Dose 2 on the fixed seeds: annihilated by arch_hunt at r746 (dose 1 lost on votes at r1500, baseline was
    //     annihilated at r816) and still only 104 guards at 15 -- single games, and they say the trigger is not
    //     the lever it looked. Dose 1 goes to the ladder and the SPRT: it is the hedge that fires only under a
    //     real swarm (on the Superposition seed it held 6 centres to 2 where the baseline held 1 to 7).
    public static final int SWARM_MUCKS = 3;            // 0 = 56's unconditional cheap guards
    public static final int SWARM_MEMORY = 50;
    public static final int THREAT_SLA_D2 = 20;         // a muckraker this close to one of our slanderers is about to expose it (expose range 12)
    public static final int THREAT_EC_D2 = 9;           // ... or this close to a centre (it blocks a spawn tile)
    // Iteration 53 (target-aware attack): awesomelemonade retakes our fresh centres with politicians sized to the
    // job -- 130-500 conviction, overshoots of 9-23 on the first flips (NotAPuzzle) -- and holds a 6-8 centre lead
    // by r400 while our only moves against enemy centres were two branches aimed at enemyEC[0]: one priced from a
    // single remembered bucket, one that threw the whole bank (38 politicians aborting at a contested tile in one
    // game). Enemy centres now carry their last SIGHTED influence; the cheapest known one is bought like a neutral,
    // priced at influence x margin + tax, claimed per target, subject to the capturer cap and the flip presumption.
    public static final int ATTACK = 0;                 // 0 = g_iter13 (the branch also runs for ARCHETYPE 7, the awesomelemonade-like sparring partner)
    public static final int ATTACK_MARGIN_NUM = 3, ATTACK_MARGIN_DEN = 2;   // conviction bought per point of the centre's influence (their bodies dilute the speech)
    public static final int ATTACK_INF_AGE = 200;       // a sighting older than this is not a price
    public static final int ATTACK_CAP = 1000;          // the old "rich and idle" branch no longer empties the bank into one politician
    public static final int SAVE_MAX_WAIT = 80;         // give up after this many waiting rounds (0 = never)
    // (b) Home never learns that a centre it targeted became ours: the capturer dies in the flip and nobody
    //     else was there. HexesAndOhms: heardOwn=0 for 100 rounds after the r97 flip, nine 165-influence
    //     capturers bought for the centre we already held (cheapest-first picks exactly the one just taken);
    //     wins show hundreds of politicians sent at an enemy centre that was already ours. A capturer that
    //     lands adjacent with a flipping share puts FLIP_INTENT(target) on its flag; home reads every child
    //     by id before the child acts, marks the tile presumed-own and buys nothing for it for PRESUME_ROUNDS.
    public static final int FLIP_INTENT = 1;            // 0 = silent flips (g_iter11)
    public static final int PRESUME_ROUNDS = 100;
    // (c) A politician that arrives to find the centre ours keeps OWN_EC(target) on its flag, so home learns for good.
    public static final int ABORT_REPORT = 1;
    // Dose 2 (from the Hexes diagnostic): the intent was on the flag for one round and home reads 24 of ~40
    // children a turn, so it missed the flip that mattered; capture-role children are now read every turn.
    // The abort reports WERE heard (heardOwn=328) but eEC never dropped: scouts echo ENEMY_EC for a centre we
    // took, and addEnemyEC had no own-tile refusal. Hearsay is refused for own tiles (sightings override), and
    // home broadcasts its own tiles so the scouts' copies are corrected and the echo dies.
    // Diagnostic 4-5: a centre we took at r227 was retaken and the sightings of it as enemy came from a sibling's
    // scouts, which reach home only as relayed knowledge -- a plain "refuse echoes" guard left home knowing no
    // enemy centre for 1,200 rounds. Every ownership claim now carries the stamp of the sighting behind it
    // (MapState.claimEnemy / claimOwn) and the newer claim wins whoever relays it.
    public static final int STAMPED_CLAIMS = 1;     // 0 = every claim accepted, latest heard wins (g_iter11)
    // Diagnostic 2 on Hexes: with own tiles broadcast, every newborn scout learned all siblings and the Iteration 48
    // courier rule sent it to tour them before exploring -- home never found the enemy centre in 1500 rounds
    // (eEC=0, heardOwn 328 -> 1327). The hearsay guard alone stops the echo at the centres, so this stays off.
    public static final int BROADCAST_OWN = 0;
    private C() {}
}
