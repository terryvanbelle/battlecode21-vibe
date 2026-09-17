package bot;

/** Tunable constants. One place, so a dose ladder is a one-line diff. */
public final class C {
    public static final boolean DEBUG = true;      // @tag log lines on/off
    /** Sparring archetype switch: 0 = the real bot; 1 = muckraker rush; 2 = aggressive bidder; 3 = politician rush. Set by tools/snapshot.sh <name> <archetype>. */
    public static final int ARCHETYPE = 0;
    public static final int BC_REPORT_EVERY = 50;  // rounds between @bc lines

    // Iteration 21 dose 2 (garrison, the half that fires): guards built for a newly captured neutral EC and posted AT it.
    // Dose 1's opening half never triggered (0 `@opening capture` on Arena and Gridlock: cheap neutrals lie beyond the
    // distance cap and near ones above the influence cap), so it is removed; dose 1's +3 on the panel was the garrison alone.
    // Dose 1's guards were posted but then held the normal ring (d^2 20-80 from the post), diluting nothing: now they hug it.
    public static final int GARRISON_GUARDS = 3;      // dose 1's value (dose 2 used 4 and hugged: it sealed the new EC's spawn tiles)
    public static final int GARRISON_SIZE = 60;
    public static final int GARRISON_WINDOW = 200;
    public static final int GARRISON_LEASH_D2 = 20;   // dose 3: a posted guard patrols within ~4 tiles of its EC (normal guards: 80)
    public static final int GARRISON_RING_MIN = 2;    // ... and spreads no closer than this, so it never seals the spawn ring
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
