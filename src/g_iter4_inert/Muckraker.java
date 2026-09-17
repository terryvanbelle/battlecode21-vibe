package g_iter4_inert;

import battlecode.common.*;

/**
 * Muckraker. SCOUT: walk a heading away from home, report edges and ECs on the
 * flag, bounce at edges; expose any slanderer in range; once the enemy EC is
 * known, head there and sit adjacent (blocking a spawn tile) while exposing.
 */
public strictfp class Muckraker extends Robot {
    private Direction heading;
    private int report = 0; private int reportRound = -10; private int lastSiblingReported = -1;
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
        else if (oEC != null && oEC.ID != lastSiblingReported) { report = Comms.encodeRaw(Comms.OWN_EC_ID, oEC.ID); reportRound = round; lastSiblingReported = oEC.ID; }
        else if (nearestEnemy != null && round - reportRound > 6) { report = Comms.encode(Comms.ENEMY_UNIT, nearestEnemy.type.ordinal(), nearestEnemy.location); reportRound = round; }
        else if (edge >= 0) { report = Comms.encode(Comms.MAP_EDGE, edge, edge == 0 ? new MapLocation(MapState.minX, loc.y) : edge == 1 ? new MapLocation(MapState.maxX, loc.y) : edge == 2 ? new MapLocation(loc.x, MapState.minY) : new MapLocation(loc.x, MapState.maxY)); reportRound = round; }
        if (round - reportRound <= 6 && (round & 1) == 0) setFlag(report);
        else setFlag(cycleFacts());

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

    /** Slowly cycle every durable fact we hold (edges, ECs) so the EC eventually hears all of them. */
    private int factI = 0;
    private int cycleFacts() {
        for (int k = 0; k < 8; k++) {
            int i = factI++ % 8;
            switch (i) {
                case 0: if (MapState.minX >= 0) return Comms.encode(Comms.MAP_EDGE, 0, new MapLocation(MapState.minX, loc.y)); break;
                case 1: if (MapState.maxX >= 0) return Comms.encode(Comms.MAP_EDGE, 1, new MapLocation(MapState.maxX, loc.y)); break;
                case 2: if (MapState.minY >= 0) return Comms.encode(Comms.MAP_EDGE, 2, new MapLocation(loc.x, MapState.minY)); break;
                case 3: if (MapState.maxY >= 0) return Comms.encode(Comms.MAP_EDGE, 3, new MapLocation(loc.x, MapState.maxY)); break;
                case 4: case 5: if (MapState.nEnemy > 0) return Comms.encode(Comms.ENEMY_EC, 0, MapState.enemyEC[(i + round) % MapState.nEnemy]); break;
                case 6: if (MapState.nNeutral > 0) { int j = (round / 2) % MapState.nNeutral; return Comms.encode(Comms.NEUTRAL_EC, Comms.bucket8(MapState.neutralInf[j]), MapState.neutralEC[j]); } break;
                default: if (MapState.nOwnId > 0) return Comms.encodeRaw(Comms.OWN_EC_ID, MapState.ownEcId[(round / 2) % MapState.nOwnId]);
                         if (MapState.nOwn > 1) return Comms.encode(Comms.OWN_EC, 0, MapState.ownEC[(round / 2) % MapState.nOwn]); break;
            }
        }
        return 0;
    }

    private MapLocation nearestEnemyEC() {
        MapLocation b = MapState.enemyEC[0]; int bd = loc.distanceSquaredTo(b);
        for (int i = MapState.nEnemy; --i > 0;) { int d = loc.distanceSquaredTo(MapState.enemyEC[i]); if (d < bd) { bd = d; b = MapState.enemyEC[i]; } }
        return b;
    }

    private int lastMoveRound = 0; private MapLocation lastLoc;
    private void wander() throws GameActionException {
        if (lastLoc == null || !lastLoc.equals(loc)) { lastLoc = loc; lastMoveRound = round; }
        boolean reached = explore != null && (Nav.cheb(loc, explore) <= 2
            || (rc.canSenseLocation(explore) && (MapState.boundsKnown() ? isEC(explore) == false && MapState.nEnemy == 0 && explore.equals(candidateFor(explore)) : !rc.onTheMap(explore))));
        if (explore == null || reached || round - lastMoveRound > 12) {
            if (explore != null && MapState.boundsKnown() && rc.canSenseLocation(explore) && !isEC(explore)) MapState.pruneWithEmptyTile(explore);
            explore = pickExplore(); lastMoveRound = round;
            Debug.log("@scout goal=" + (explore.x - MapState.minX) + "," + (explore.y - MapState.minY) + " sym=" + MapState.sym + " bounds=" + MapState.boundsKnown());
        }
        nav.setTarget(explore); nav.step();
    }
    private MapLocation candidateFor(MapLocation e) { return e; }

    /** Next exploration waypoint. */
    private MapLocation pickExplore() {
        if (MapState.boundsKnown() && MapState.home != null) {
            // candidate enemy-EC positions under surviving hypotheses, split among scouts by id; else a far random point
            int n = MapState.symCount();
            if (n > 0 && MapState.nEnemy == 0) {
                int k = (id + visits) % n; visits++;
                for (int h = 0; h < 3; h++) if ((MapState.sym & (1 << h)) != 0) { if (k-- == 0) return MapState.image(MapState.home, h); }
            }
            int x = MapState.minX + nextInt(MapState.width()), y = MapState.minY + nextInt(MapState.height());
            return new MapLocation(x, y);
        }
        // bounds unknown: head for an edge nobody has found yet (scouts split by id), far along that axis
        int unknown = (MapState.minX < 0 ? 1 : 0) | (MapState.maxX < 0 ? 2 : 0) | (MapState.minY < 0 ? 4 : 0) | (MapState.maxY < 0 ? 8 : 0);
        int dx = 0, dy = 0;
        if (unknown != 0) {
            int n = Integer.bitCount(unknown), k = (id + visits++) % n;
            for (int e = 0; e < 4; e++) if ((unknown & (1 << e)) != 0 && k-- == 0) { dx = e == 0 ? -1 : e == 1 ? 1 : 0; dy = e == 2 ? -1 : e == 3 ? 1 : 0; }
            // add a sideways component so two scouts on the same edge sweep different columns
            if (dx == 0) dx = (nextInt(3) - 1); else dy = (nextInt(3) - 1);
        } else { dx = heading.getDeltaX(); dy = heading.getDeltaY(); }
        if (dx == 0 && dy == 0) dx = 1;
        heading = new MapLocation(0, 0).directionTo(new MapLocation(dx, dy));
        MapLocation t = new MapLocation(loc.x + dx * 30, loc.y + dy * 30);
        if (MapState.minX >= 0 && t.x < MapState.minX) t = new MapLocation(MapState.minX, t.y);
        if (MapState.maxX >= 0 && t.x > MapState.maxX) t = new MapLocation(MapState.maxX, t.y);
        if (MapState.minY >= 0 && t.y < MapState.minY) t = new MapLocation(t.x, MapState.minY);
        if (MapState.maxY >= 0 && t.y > MapState.maxY) t = new MapLocation(t.x, MapState.maxY);
        return t;
    }
    private int visits = 0;

    private boolean isEC(MapLocation l) throws GameActionException { RobotInfo r = rc.senseRobotAtLocation(l); return r != null && r.type == RobotType.ENLIGHTENMENT_CENTER; }
}
