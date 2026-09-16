package bot;

import battlecode.common.*;

/**
 * Muckraker. SCOUT: walk a heading away from home, report edges and ECs on the
 * flag, bounce at edges; expose any slanderer in range; once the enemy EC is
 * known, head there and sit adjacent (blocking a spawn tile) while exposing.
 */
public strictfp class Muckraker extends Robot {
    private Direction heading;
    private int report = 0; private int reportRound = -10;
    private MapLocation explore;   // current exploration waypoint

    Muckraker(RobotController rc) { super(rc); }

    @Override protected void init() throws GameActionException {
        super.init();
        // heading: away from home through our spawn tile
        heading = MapState.home != null ? MapState.home.directionTo(loc) : DIRS[nextInt(8)];
        if (heading == Direction.CENTER) heading = DIRS[nextInt(8)];
    }

    @Override protected void turn() throws GameActionException {
        sense();
        int edge = probeEdges();
        if (round % 3 == 0) readHome();
        // report: enemy EC seen this turn > neutral EC > edge found > idle
        RobotInfo eEC = null, nEC = null, oEC = null;
        for (int i = nearby.length; --i >= 0;) { RobotInfo r = nearby[i]; if (r.type == RobotType.ENLIGHTENMENT_CENTER) { if (r.team == them) eEC = r; else if (r.team == Team.NEUTRAL) nEC = r; else if (!r.location.equals(MapState.home)) oEC = r; } }
        if (eEC != null) { report = Comms.encode(Comms.ENEMY_EC, Comms.bucket(eEC.influence), eEC.location); reportRound = round; }
        else if (nEC != null) { report = Comms.encode(Comms.NEUTRAL_EC, Comms.bucket8(nEC.influence), nEC.location); reportRound = round; }
        else if (oEC != null && round - reportRound > 6) { report = Comms.encode(Comms.OWN_EC, 0, oEC.location); reportRound = round; }
        else if (nearestEnemy != null && round - reportRound > 6) { report = Comms.encode(Comms.ENEMY_UNIT, nearestEnemy.type.ordinal(), nearestEnemy.location); reportRound = round; }
        else if (edge >= 0) { report = Comms.encode(Comms.MAP_EDGE, edge, edge == 0 ? new MapLocation(MapState.minX, loc.y) : edge == 1 ? new MapLocation(MapState.maxX, loc.y) : edge == 2 ? new MapLocation(loc.x, MapState.minY) : new MapLocation(loc.x, MapState.maxY)); reportRound = round; }
        if (round - reportRound <= 6) setFlag(report); else setFlag(0);

        if (!rc.isReady()) return;
        // expose the most valuable slanderer in range
        RobotInfo best = null;
        for (int i = nEnemy; --i >= 0;) { RobotInfo r = enemies[i]; if (r.type == RobotType.SLANDERER && loc.distanceSquaredTo(r.location) <= 12 && (best == null || r.influence > best.influence)) best = r; }
        if (best != null && rc.canExpose(best.ID)) { Debug.log("@expose inf=" + best.influence); rc.expose(best.ID); return; }
        // chase a visible slanderer
        for (int i = nEnemy; --i >= 0;) { RobotInfo r = enemies[i]; if (r.type == RobotType.SLANDERER && (best == null || loc.distanceSquaredTo(r.location) < loc.distanceSquaredTo(best.location))) best = r; }
        if (best != null) { nav.setTarget(best.location); nav.step(); return; }
        // known enemy EC: go sit next to it
        if (MapState.nEnemy > 0) {
            MapLocation e = nearestEnemyEC();
            if (loc.isAdjacentTo(e)) return;
            nav.setTarget(e); if (nav.step()) return;
        }
        // explore: keep heading; bounce off edges and obstacles
        wander();
    }

    private MapLocation nearestEnemyEC() {
        MapLocation b = MapState.enemyEC[0]; int bd = loc.distanceSquaredTo(b);
        for (int i = MapState.nEnemy; --i > 0;) { int d = loc.distanceSquaredTo(MapState.enemyEC[i]); if (d < bd) { bd = d; b = MapState.enemyEC[i]; } }
        return b;
    }

    private void wander() throws GameActionException {
        // bounds known: aim at the candidate enemy EC positions (one per surviving hypothesis), scouts split by id
        if (MapState.boundsKnown() && MapState.home != null && MapState.nEnemy == 0) {
            if (explore == null || loc.distanceSquaredTo(explore) <= 8 || (rc.canSenseLocation(explore) && !isEC(explore))) {
                if (explore != null && rc.canSenseLocation(explore)) MapState.pruneWithEmptyTile(explore);
                explore = null;
                for (int h = 0; h < 3; h++) if ((MapState.sym & (1 << h)) != 0) { MapLocation c = MapState.image(MapState.home, h); if (explore == null || (id + h) % 3 == 0) explore = c; }
                if (explore == null) explore = MapState.center();
            }
            nav.setTarget(explore); if (nav.step()) return;
        }
        // bounds unknown: walk the heading, but bias toward the far side of the map from home once any edge is known
        if (!MapState.boundsKnown() && MapState.home != null && (round - birth) % 25 == 0) {
            // re-aim: away from home, preferring axes whose far edge is still unknown
            int dx = heading.getDeltaX(), dy = heading.getDeltaY();
            if (MapState.maxX >= 0 && dx > 0 && MapState.maxX - loc.x < 6) dx = -1;
            if (MapState.minX >= 0 && dx < 0 && loc.x - MapState.minX < 6) dx = 1;
            if (MapState.maxY >= 0 && dy > 0 && MapState.maxY - loc.y < 6) dy = -1;
            if (MapState.minY >= 0 && dy < 0 && loc.y - MapState.minY < 6) dy = 1;
            if (dx == 0 && dy == 0) dx = 1;
            heading = new MapLocation(0, 0).directionTo(new MapLocation(dx, dy));
        }
        MapLocation n = loc.add(heading);
        boolean off = MapState.boundsKnown() ? (n.x < MapState.minX || n.x > MapState.maxX || n.y < MapState.minY || n.y > MapState.maxY) : !rc.onTheMap(n);
        if (off) { heading = nextInt(2) == 0 ? heading.rotateLeft().rotateLeft().rotateLeft() : heading.rotateRight().rotateRight().rotateRight(); n = loc.add(heading); }
        if (rc.canMove(heading)) { rc.move(heading); return; }
        // blocked by a robot or swamp: sidestep
        Direction l = heading.rotateLeft(), r = heading.rotateRight();
        if (rc.canMove(l) && rc.canMove(r)) { if (rc.sensePassability(loc.add(l)) >= rc.sensePassability(loc.add(r))) rc.move(l); else rc.move(r); }
        else if (rc.canMove(l)) rc.move(l); else if (rc.canMove(r)) rc.move(r);
        else heading = nextInt(2) == 0 ? heading.rotateLeft().rotateLeft() : heading.rotateRight().rotateRight();
    }

    private boolean isEC(MapLocation l) throws GameActionException { RobotInfo r = rc.senseRobotAtLocation(l); return r != null && r.type == RobotType.ENLIGHTENMENT_CENTER; }
}
