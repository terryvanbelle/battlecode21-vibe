package g_iter7;

/**
 * Slanderer arithmetic. income(x) = floor(x*(1/50 + 0.03f*e^(-0.001f*x))) per
 * round for 50 rounds. BREAK[k] is the smallest influence that yields k per
 * round (computed offline with the engine's float constants, see
 * TRAINING_LOG.md Phase 0); any influence between BREAK[k] and BREAK[k+1]-1
 * earns the same, so only BREAK values are ever spent.
 */
public final class Econ {
    private Econ() {}
    public static final int[] BREAK = {1, 21, 41, 63, 85, 107, 130, 154, 178, 203, 228, 255, 282, 310, 339, 368, 399, 431, 463, 497,
        532, 568, 605, 643, 683, 724, 766, 810, 855, 902, 949, 999, 1049, 1101, 1155, 1209, 1265, 1322, 1380, 1438, 1498, 1558, 1619, 1681,
        1743, 1805, 1868, 1930, 1993, 2056, 2118, 2181, 2243, 2306, 2368, 2429, 2491, 2552, 2613, 2674};

    /** Largest breakpoint <= budget (0 if budget < 21). */
    public static int bestSize(int budget) {
        int best = 0;
        for (int i = 1; i < BREAK.length; i++) { if (BREAK[i] > budget) break; best = BREAK[i]; }
        return best;
    }
    /** Income per round for a slanderer of size x (x a breakpoint). */
    public static int incomeOf(int x) {
        int k = 0; for (int i = 1; i < BREAK.length; i++) { if (BREAK[i] > x) break; k = i; }
        return k;
    }
    /** EC passive income at round t. */
    public static int passive(int round) { return (int) Math.ceil(0.2 * Math.sqrt(round)); }
}
