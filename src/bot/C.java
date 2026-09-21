package bot;

/** Tunable constants. One place, so a dose ladder is a one-line diff. */
public final class C {
    public static final boolean DEBUG = true;      // @tag log lines on/off
    /** Sparring archetype switch: 0 = the real bot; 1 = muckraker rush; 2 = aggressive bidder; 3 = politician rush; 4 = neutral-EC expander. Set by tools/snapshot.sh <name> <archetype>. */
    public static final int ARCHETYPE = 0;
    public static final int BC_REPORT_EVERY = 50;  // rounds between @bc lines

    // Iteration 22 (saving mode, uncapped): the mechanism Iterations 16 and 21 pre-registered but never actually ran --
    // both capped the target at 320 influence and Iteration 21 also at d^2 500, and the logs show 0 firings on the maps
    // that mattered (cheap neutrals lie far, near ones are 500). The census says the gap opens before r200: ECs 1.6 v 2.8.
    // Once any neutral is known and round <= SAVE_UNTIL, the EC keeps SAVE_SLANDERERS slanderers for income and saves
    // everything else until it can send one full-price capturer (+SAVE_BANK, so the new EC is not converted while empty).
    public static final boolean SAVE_MODE = true;      // false = g_iter4
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
    private C() {}
}
