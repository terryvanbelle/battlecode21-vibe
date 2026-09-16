package bot;

import battlecode.common.*;

/**
 * Politician. Roles: GUARD (default) stays near home and speaks when the
 * speech is worth it -- kills muckrakers/slanderers, converts weak enemy
 * politicians -- otherwise shadows enemy muckrakers; CAPTURE walks to a
 * neutral/enemy EC and speaks when adjacent with enough conviction to flip it.
 */
public strictfp class Politician extends Robot {
    private int role = Roles.GUARD;
    private MapLocation target;          // capture target
    private int targetInf = 0;
    private boolean orderRead = false;

    Politician(RobotController rc) { super(rc); }

    /** Called when a slanderer becomes a politician mid-life. */
    void adoptFrom(Robot r) { orderRead = true; role = Roles.GUARD; }
    void turnAs(int round) throws GameActionException { this.round = round; this.loc = rc.getLocation(); turn(); }

    @Override protected void turn() throws GameActionException {
        sense();
        if (!orderRead) { int f = readHome(); orderRead = true; if (f >= 0 && Comms.type(f) == Comms.ORDER) { role = Comms.extra(f); target = Comms.loc(f, loc); } }
        else if (round % 4 == 0) readHome();
        if (role == Roles.CAPTURE && target != null) { capture(); return; }
        guard();
    }

    // ---------------------------------------------------------------- speech value
    static final int[] RADII = {1, 2, 4, 5, 8, 9};

    /**
     * Value of speaking at radius r2: sum over targets of (kill value) for
     * enemies pushed below zero, minus wasted share on friendlies. Returns the
     * best radius or -1 if nothing worth it. `minValue` is in influence units.
     */
    private final int[] d2s = new int[64];
    private int bestSpeech(int minValue) {
        int conv = rc.getConviction() - 10;
        if (conv <= 0) return -1;
        // only robots within 9 matter; collect them once with their distance
        int m = 0; int any = 0;
        for (int i = nearby.length; --i >= 0 && m < 64;) { int d = nearby[i].location.distanceSquaredTo(loc); if (d <= 9) { d2s[m] = d; cand[m++] = nearby[i]; any++; } }
        if (any == 0) return -1;
        double buff = rc.getEmpowerFactor(us, 0);
        int bestR = -1; double bestV = minValue - 1e-9;
        for (int ri = 0; ri < RADII.length; ri++) {
            int r2 = RADII[ri];
            int n = 0;
            for (int i = m; --i >= 0;) if (d2s[i] <= r2) n++;
            if (n == 0) continue;
            int share = (int) ((double) conv / n * buff);   // engine: (int)(convPerBot * buff), convPerBot double
            double v = 0;
            for (int i = m; --i >= 0;) {
                if (d2s[i] > r2) continue;
                RobotInfo r = cand[i];
                if (r.team == us) { if (r.type != RobotType.ENLIGHTENMENT_CENTER) v -= share * 0.5; continue; }   // friendly units: mostly wasted (capped)
                if (r.type == RobotType.ENLIGHTENMENT_CENTER) { if (share > r.conviction) v += r.influence + 60; else v += share * 0.2; continue; }
                if (share > r.conviction) v += (r.type == RobotType.MUCKRAKER ? 25 : r.type == RobotType.SLANDERER ? 40 : 15) + r.influence * 0.5;
                else v += share * 0.1;
            }
            if (v > bestV) { bestV = v; bestR = r2; }
        }
        return bestR;
    }
    private final RobotInfo[] cand = new RobotInfo[64];

    // ---------------------------------------------------------------- roles
    private void guard() throws GameActionException {
        int conv = rc.getConviction();
        if (rc.isReady()) {
            int r2 = bestSpeech(Math.max(12, conv / 3));
            if (r2 > 0 && rc.canEmpower(r2)) { Debug.log("@speech role=guard r2=" + r2 + " conv=" + conv + " n=" + nearby.length); rc.empower(r2); return; }
        }
        // move: toward the nearest enemy muckraker (they kill our slanderers) if within leash, else hold a ring around home
        MapLocation home = MapState.home;
        if (nearestEnemyMuck != null && (home == null || nearestEnemyMuck.location.distanceSquaredTo(home) <= C.GUARD_LEASH_D2 * 2)) { nav.setTarget(nearestEnemyMuck.location); nav.step(); return; }
        if (nearestEnemy != null && nearestEnemy.type != RobotType.ENLIGHTENMENT_CENTER && (home == null || nearestEnemy.location.distanceSquaredTo(home) <= C.GUARD_LEASH_D2)) { nav.setTarget(nearestEnemy.location); nav.step(); return; }
        if (home == null) return;
        int d2 = loc.distanceSquaredTo(home);
        if (d2 > C.GUARD_LEASH_D2) { nav.setTarget(home); nav.step(); return; }
        if (d2 < C.GUARD_RING_MIN) {                                       // get out of the slanderer ring, toward the enemy side if known
            MapLocation out = MapState.nEnemy > 0 ? MapState.enemyEC[0] : (MapState.boundsKnown() ? MapState.center() : loc.add(home.directionTo(loc)).add(home.directionTo(loc)));
            if (out.equals(loc)) { nav.fleeFrom(home); return; }
            nav.setTarget(out); if (nav.step()) return; nav.fleeFrom(home); return;
        }
        if (crowd() > C.CROWD_MAX) { spreadOut(home, C.GUARD_RING_MIN, C.GUARD_LEASH_D2); return; }
    }

    private void capture() throws GameActionException {
        if (round % 8 == 0 && MapState.nNeutral == 0 && MapState.nEnemy == 0) { /* no targets known any more */ }
        // is the target still capturable?
        RobotInfo t = null;
        if (rc.canSenseLocation(target)) { t = rc.senseRobotAtLocation(target); if (t == null || t.type != RobotType.ENLIGHTENMENT_CENTER || t.team == us) { MapState.removeNeutral(target); MapState.removeEnemy(target); if (t != null && t.team == us) MapState.addOwnEC(target); role = Roles.GUARD; Debug.log("@capture abort target gone/ours"); return; } targetInf = t.influence; }
        if (rc.isReady() && t != null && loc.isAdjacentTo(target)) {
            // speak if we flip it at the tightest radius that includes it
            int r2 = loc.distanceSquaredTo(target);
            int n = 0; for (int i = nearby.length; --i >= 0;) if (nearby[i].location.distanceSquaredTo(loc) <= r2) n++;
            int share = (int) ((rc.getConviction() - 10) / (double) n * (t.team == them ? rc.getEmpowerFactor(us, 0) : 1.0));
            if (share > t.conviction && rc.canEmpower(r2)) { Debug.log("@speech role=capture r2=" + r2 + " conv=" + rc.getConviction() + " n=" + n + " tgt=" + t.conviction); rc.empower(r2); return; }
            // not enough: wait next to it for a second speaker unless enemies threaten, else fall back to guard
            if (share <= t.conviction && n > 1) { nav.fleeFrom(target); return; }   // step aside to reduce n? (someone else adjacent)
            if (rc.getConviction() - 10 <= t.conviction) { role = Roles.GUARD; Debug.log("@capture abort too weak conv=" + rc.getConviction() + " need>" + t.conviction); return; }
        }
        // opportunistic kill on the way
        if (rc.isReady()) { int r2 = bestSpeech(Math.max(40, rc.getConviction())); if (r2 > 0 && rc.canEmpower(r2)) { rc.empower(r2); return; } }
        nav.setTarget(target); nav.step();
    }
}
