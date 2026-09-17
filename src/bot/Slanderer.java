package bot;

import battlecode.common.*;

/**
 * Slanderer: earn, hide, survive. Flee any enemy in sight (all enemies are
 * lethal to us: muckrakers expose, politicians speak); otherwise hold a ring
 * around home on the side away from the known enemy, never on the spawn ring.
 * After 300 rounds the engine turns us into a politician and a NEW controller
 * is not created, so we keep running here: we then behave as a guard.
 */
public strictfp class Slanderer extends Robot {
    Slanderer(RobotController rc) { super(rc); }
    private Politician asPolitician = null;

    @Override protected void turn() throws GameActionException {
        if (rc.getType() == RobotType.POLITICIAN) {        // camouflage expired
            if (asPolitician == null) { asPolitician = new Politician(rc); asPolitician.adoptFrom(this); Debug.log("@camo id=" + id + " conv=" + rc.getConviction()); }
            asPolitician.turnAs(round); return;
        }
        sense();
        if (round % 5 == 0) readHome();
        if (!rc.isReady()) return;
        if (nearestEnemy != null) {
            // flee: away from the nearest enemy, biased toward home
            if (nav.fleeFrom(nearestEnemy.location)) return;
        }
        MapLocation home = MapState.home;
        if (home == null) return;
        int d2 = loc.distanceSquaredTo(home);
        if (d2 < C.SLANDERER_RING_MIN) { nav.fleeFrom(home); return; }      // clear the spawn ring
        if (d2 > C.SLANDERER_RING_MAX) { nav.setTarget(home); nav.step(); return; }
        // in the ring: relieve crowding first (a packed ring blocks everyone, including capturers leaving home)
        int cr = crowd();
        if ((cr > C.CROWD_MAX || (cr > 0 && nextInt(4) == 0)) && spreadOut(home, C.SLANDERER_RING_MIN, C.SLANDERER_RING_MAX)) return;
        MapLocation away = null;
        if (MapState.nEnemy > 0) away = MapState.enemyEC[0];
        if (away != null) {
            Direction d = away.directionTo(home);           // direction from enemy toward home = away from enemy
            MapLocation n = loc.add(d);
            if (n.distanceSquaredTo(home) <= C.SLANDERER_RING_MAX && n.distanceSquaredTo(home) >= C.SLANDERER_RING_MIN && rc.canMove(d) && rc.sensePassability(n) >= rc.sensePassability(loc) * 0.7) { rc.move(d); return; }
        }
        // otherwise stay put unless standing on swamp: try a better tile in the ring
        double p0 = rc.sensePassability(loc);
        if (p0 < 0.6) {
            Direction best = null; double bp = p0;
            for (int i = 8; --i >= 0;) { Direction d = DIRS[i]; if (!rc.canMove(d)) continue; MapLocation n = loc.add(d); int nd = n.distanceSquaredTo(home); if (nd < C.SLANDERER_RING_MIN || nd > C.SLANDERER_RING_MAX) continue; double p = rc.sensePassability(n); if (p > bp) { bp = p; best = d; } }
            if (best != null) rc.move(best);
        }
    }
}
