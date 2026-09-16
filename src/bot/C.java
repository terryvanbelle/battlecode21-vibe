package bot;

/** Tunable constants. One place, so a dose ladder is a one-line diff. */
public final class C {
    public static final boolean DEBUG = true;      // @tag log lines on/off
    public static final int BC_REPORT_EVERY = 50;  // rounds between @bc lines

    // economy
    public static final int MAX_SLANDERERS = 12;        // alive at once (they become politicians after 300 rounds)
    public static final int MAX_GUARDS = 30;
    public static final int EARLY_SCOUTS = 4;
    public static final int MAX_SLANDERER_SIZE = 463;   // largest breakpoint we buy
    public static final int BID_CAP_DIV = 6;            // bid at most influence / this

    // slanderer positioning
    public static final int SLANDERER_RING_MIN = 4;     // d^2 from home: stay outside the spawn ring
    public static final int SLANDERER_RING_MAX = 18;

    // politician
    public static final int GUARD_LEASH_D2 = 36;        // guards wander this far from home
    private C() {}
}
