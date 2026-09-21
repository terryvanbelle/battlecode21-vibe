package g_iter11;

import battlecode.common.*;

/**
 * What this robot knows about the map: bounds, symmetry hypotheses, ECs.
 * Bounds are discovered by probing rc.onTheMap at the sensing edge; symmetry
 * is inferred from where enemy ECs turn out to be relative to our own.
 */
public final class MapState {
    private MapState() {}

    public static int minX = -1, maxX = -1, minY = -1, maxY = -1;   // inclusive; -1 unknown
    public static MapLocation home;          // our EC
    public static int homeId = -1;
    public static MapLocation homeSpawnDir;  // unused placeholder

    // symmetry hypotheses: bit0 rotation, bit1 mirror-x (x -> minX+maxX-x), bit2 mirror-y
    public static int sym = 7;

    public static final int MAX_ECS = 12;
    public static final MapLocation[] enemyEC = new MapLocation[MAX_ECS];   public static int nEnemy = 0;
    public static final MapLocation[] neutralEC = new MapLocation[MAX_ECS]; public static int nNeutral = 0;
    public static final int[] neutralInf = new int[MAX_ECS];
    public static final MapLocation[] ownEC = new MapLocation[MAX_ECS];     public static int nOwn = 0;
    public static final int[] ownEcId = new int[MAX_ECS];                   public static int nOwnId = 0;   // sibling EC ids (not home)
    public static boolean addOwnEcId(int id) {
        if (id == homeId) return false;
        for (int i = nOwnId; --i >= 0;) if (ownEcId[i] == id) return false;
        if (nOwnId >= MAX_ECS) return false;
        ownEcId[nOwnId++] = id; return true;
    }

    public static boolean boundsKnown() { return minX >= 0 && maxX >= 0 && minY >= 0 && maxY >= 0; }
    public static int width()  { return maxX - minX + 1; }
    public static int height() { return maxY - minY + 1; }
    public static MapLocation center() { return new MapLocation((minX + maxX) / 2, (minY + maxY) / 2); }

    public static boolean known(MapLocation[] a, int n, MapLocation l) {
        for (int i = n; --i >= 0;) if (a[i].equals(l)) return true;
        return false;
    }
    public static boolean addEnemyEC(MapLocation l) {
        if (known(enemyEC, nEnemy, l) || nEnemy >= MAX_ECS) return false;
        enemyEC[nEnemy++] = l; removeNeutral(l); return true;
    }
    public static boolean addNeutralEC(MapLocation l, int inf) {
        // A centre we own was never neutral again: centres are neutral only at the start of the game.
        // Without this the captured-centre broadcast ping-pongs -- the centre drops it from the neutral
        // list, a scout whose own copy is still stale re-broadcasts it as neutral, the centre re-adds it
        // and buys another capturer for ground it already holds. Measured: 131 aborts in one game.
        for (int i = nOwn; --i >= 0;) if (ownEC[i].equals(l)) return false;
        for (int i = nNeutral; --i >= 0;) if (neutralEC[i].equals(l)) { neutralInf[i] = inf; return false; }
        if (nNeutral >= MAX_ECS) return false;
        neutralEC[nNeutral] = l; neutralInf[nNeutral] = inf; nNeutral++; return true;
    }
    public static void removeNeutral(MapLocation l) {
        for (int i = nNeutral; --i >= 0;) if (neutralEC[i].equals(l)) { nNeutral--; neutralEC[i] = neutralEC[nNeutral]; neutralInf[i] = neutralInf[nNeutral]; return; }
    }
    public static void removeEnemy(MapLocation l) {
        for (int i = nEnemy; --i >= 0;) if (enemyEC[i].equals(l)) { nEnemy--; enemyEC[i] = enemyEC[nEnemy]; return; }
    }
    public static boolean addOwnEC(MapLocation l) {
        if (known(ownEC, nOwn, l) || nOwn >= MAX_ECS) return false;
        ownEC[nOwn++] = l; removeNeutral(l); removeEnemy(l);
        if (boundsKnown()) for (int i = nEnemy; --i >= 0;) pruneWithEnemyEC(enemyEC[i]);
        return true;
    }

    /** Image of l under hypothesis bit (0 rot, 1 mirror-x, 2 mirror-y). Needs bounds. */
    public static MapLocation image(MapLocation l, int hyp) {
        switch (hyp) {
            case 0: return new MapLocation(minX + maxX - l.x, minY + maxY - l.y);
            case 1: return new MapLocation(minX + maxX - l.x, l.y);
            default: return new MapLocation(l.x, minY + maxY - l.y);
        }
    }

    /** Record that a tile is/is not on the map (from rc.onTheMap probing). */
    public static void edgeFound(int edge, int coord) {
        boolean before = boundsKnown();
        switch (edge) {
            case 0: if (minX < 0 || coord > minX) minX = coord; break;
            case 1: if (maxX < 0 || coord < maxX) maxX = coord; break;
            case 2: if (minY < 0 || coord > minY) minY = coord; break;
            default: if (maxY < 0 || coord < maxY) maxY = coord; break;
        }
        // bounds just became complete: evidence gathered earlier can now prune the hypotheses
        if (!before && boundsKnown()) for (int i = nEnemy; --i >= 0;) pruneWithEnemyEC(enemyEC[i]);
    }

    /**
     * Use a confirmed enemy EC at e to prune symmetry hypotheses: e must be the
     * image of one of our ECs under the surviving hypothesis. Only applied when
     * bounds are known.
     */
    public static void pruneWithEnemyEC(MapLocation e) {
        if (!boundsKnown() || nOwn == 0) return;
        int keep = 0;
        for (int h = 0; h < 3; h++) {
            if ((sym & (1 << h)) == 0) continue;
            for (int i = nOwn; --i >= 0;) if (image(ownEC[i], h).equals(e)) { keep |= 1 << h; break; }
        }
        if (keep != 0) sym = keep;   // if nothing matches (multi-EC map, partial own list), keep all
    }

    /** A sensed tile that holds NO EC cannot be the image of our home under h. */
    public static void pruneWithEmptyTile(MapLocation t) {
        if (!boundsKnown() || home == null) return;
        for (int h = 0; h < 3; h++) if ((sym & (1 << h)) != 0 && image(home, h).equals(t)) sym &= ~(1 << h);
        if (sym == 0) sym = 7;  // contradiction (multi-EC maps); reset rather than stay empty
    }

    public static int symCount() { return Integer.bitCount(sym); }

    /** The single surviving hypothesis, or -1. */
    public static int symKnown() { return sym == 1 ? 0 : sym == 2 ? 1 : sym == 4 ? 2 : -1; }
}
