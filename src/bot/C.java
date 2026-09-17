package bot;

/** Tunable constants. One place, so a dose ladder is a one-line diff. */
public final class C {
    public static final boolean DEBUG = true;      // @tag log lines on/off
    /** Sparring archetype switch: 0 = the real bot; 1 = muckraker rush; 2 = aggressive bidder; 3 = politician rush. Set by tools/snapshot.sh <name> <archetype>. */
    public static final int ARCHETYPE = 0;
    public static final int BC_REPORT_EVERY = 50;  // rounds between @bc lines

    // Iteration 21 (capture and hold): two mechanisms in one candidate (user-authorized 2026-09-18).
    // (a) opening, saving mode: when a neutral of at most OPENING_MAX_TARGET within OPENING_MAX_D2 is known before
    //     OPENING_SAVE_UNTIL, keep the first two slanderers and save until a full-price capturer (+OPENING_BANK) can go.
    // (b) garrison: every neutral capture order is followed by GARRISON_GUARDS guards of GARRISON_SIZE ordered to the
    //     captured EC (they guard it as their home); the order lapses GARRISON_WINDOW rounds after the capture.
    public static final boolean OPENING_CAPTURE = true;
    public static final int OPENING_SAVE_UNTIL = 150;
    public static final int OPENING_MAX_TARGET = 320;
    public static final int OPENING_MAX_D2 = 500;
    public static final int OPENING_BANK = 100;         // the new EC starts with this (Iteration 16 used 30 and lost ECs at 5-61)
    public static final int GARRISON_GUARDS = 3;
    public static final int GARRISON_SIZE = 60;
    public static final int GARRISON_WINDOW = 200;
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
