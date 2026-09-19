package bot;

/** Tunable constants. One place, so a dose ladder is a one-line diff. */
public final class C {
    public static final boolean DEBUG = true;      // @tag log lines on/off
    /** Sparring archetype switch: 0 = the real bot; 1 = muckraker rush; 2 = aggressive bidder; 3 = politician rush. Set by tools/snapshot.sh <name> <archetype>. */
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
    // Iteration 28 (hunt the ring, not the tile): measured across three scrimmage losses, our muckrakers exposed
    // NOTHING all game (0 exposes, 0 buff, 168 muckrakers built in one game) because once the enemy EC is known they
    // walk to it and sit adjacent. Enemy slanderers hold a ring at d^2 8-45 from their own EC, so a muckraker on the
    // EC tile is inside that ring, mostly outside the r^2 12 expose radius, and in the guards' kill zone.
    public static final int HUNT_RING_MIN = 10;   // patrol the enemy EC at this d^2 ...
    public static final int HUNT_RING_MAX = 40;   // ... out to this, which is where their slanderers live
    // economy
    public static final int MAX_SLANDERERS = 12;        // alive at once (they become politicians after 300 rounds)
    public static final int MAX_GUARDS = 10;              // absolute cap; the live cap is GUARD_BASE + slanderers/2 (fewer bodies = less congestion)
    public static final int GUARD_BASE = 4;
    public static final int EARLY_SCOUTS = 4;
    public static final int MAX_SLANDERER_SIZE = 463;   // largest breakpoint we buy
    public static final int BID_CAP_DIV = 6;            // (unused) historical cap
    public static final int BID_EARLY_DIV = 30;         // Iteration 9: before r600 bid at most influence / this (baseline: /12 before r200, /8 to r600); dose 12-8 / 30 / 60
    // never idle: when every capped branch declines and this much is spare above the reserve, build anyway
    public static final int SPARE_MIN = 60;             // dose ladder: 1<<30 (off, = g_iter2) / 60 / 20
    public static final int SPEND_SLANDERER_CAP = 24;   // slanderer cap for the spare branch (the normal cap stays MAX_SLANDERERS)

    // slanderer positioning
    public static final int SLANDERER_RING_MIN = 8;     // d^2 from home: stay well outside the spawn ring
    public static final int SLANDERER_RING_MAX = 45;
    public static final int CROWD_D2 = 2;                // neighbours within this d^2 count as crowding
    public static final int CROWD_MAX = 2;               // more than this many adjacent friends -> spread out

    // politician
    public static final int GUARD_LEASH_D2 = 80;        // guards wander this far from home
    public static final int GUARD_RING_MIN = 20;        // guards hold outside the slanderer ring
    private C() {}
}
