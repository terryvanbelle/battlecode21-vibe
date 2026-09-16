package arch_bidder;

import battlecode.common.*;

/** Base controller: the turn loop, bytecode monitor, per-robot RNG, shared sensing. */
public abstract strictfp class Robot {
    protected final RobotController rc;
    protected final Team us, them;
    protected final RobotType type;
    protected final int id;
    protected int round, birth;
    protected MapLocation loc;
    protected int rng;                     // per-robot LCG state, seeded from the id: deterministic, uncorrelated with team
    protected final Nav nav;

    // per-turn sensing cache
    protected RobotInfo[] nearby;          // everything in sensor range
    protected int nEnemy, nFriend, nNeutral;
    protected final RobotInfo[] enemies = new RobotInfo[64], friends = new RobotInfo[64], neutrals = new RobotInfo[8];
    protected RobotInfo nearestEnemy; protected int nearestEnemyD2;
    protected RobotInfo nearestEnemyMuck; protected int nearestEnemyMuckD2;

    // bytecode monitor
    private int bcMax = 0, bcOver = 0, bcNear = 0, turns = 0;
    private final int bcLimit;

    static final Direction[] DIRS = Direction.allDirections();  // 8 compass + CENTER last; never a tie-break order

    Robot(RobotController rc) {
        this.rc = rc; us = rc.getTeam(); them = us.opponent(); type = rc.getType(); id = rc.getID();
        rng = id * 1103515245 + 12345;
        bcLimit = type.bytecodeLimit;
        birth = rc.getRoundNum();
        loc = rc.getLocation();
        nav = new Nav(rc, this);
    }

    /** Uniform-ish int in [0, n). */
    protected int nextInt(int n) { rng = rng * 1103515245 + 12345; return ((rng >>> 8) & 0x7fffffff) % n; }

    public final void loop() {
        try { init(); } catch (Exception e) { Debug.exception(e); }
        while (true) {
            int r0 = rc.getRoundNum();
            round = r0; loc = rc.getLocation();
            try {
                turn();
            } catch (GameActionException e) { Debug.exception(e);
            } catch (Exception e) { Debug.exception(e); }
            int used = Clock.getBytecodeNum();
            turns++;
            boolean overran = rc.getRoundNum() != r0;
            if (overran) bcOver++;
            else { if (used > bcMax) bcMax = used; if (used > bcLimit - bcLimit / 10) bcNear++; }
            if (turns % C.BC_REPORT_EVERY == 0 || overran)
                Debug.log("@bc t=" + type.ordinal() + " used=" + used + " max=" + bcMax + " near=" + bcNear + " over=" + bcOver);
            Clock.yield();
        }
    }

    /** Once, before the first turn. */
    protected void init() throws GameActionException {
        // find the EC we were built by: the adjacent friendly EC
        RobotInfo[] adj = rc.senseNearbyRobots(2, us);
        for (int i = adj.length; --i >= 0;) if (adj[i].type == RobotType.ENLIGHTENMENT_CENTER) { MapState.home = adj[i].location; MapState.homeId = adj[i].ID; MapState.addOwnEC(adj[i].location); break; }
    }

    /** One turn of this robot's logic. */
    protected abstract void turn() throws GameActionException;

    // ---- shared helpers ----

    /** Sense everything once and bucket it. ~100 + 10/robot bytecodes. */
    protected void sense() {
        nearby = rc.senseNearbyRobots();
        nEnemy = nFriend = nNeutral = 0; nearestEnemy = null; nearestEnemyD2 = 1 << 30; nearestEnemyMuck = null; nearestEnemyMuckD2 = 1 << 30;
        for (int i = nearby.length; --i >= 0;) {
            RobotInfo r = nearby[i];
            if (r.team == us) { if (nFriend < 64) friends[nFriend++] = r; if (r.type == RobotType.ENLIGHTENMENT_CENTER) { MapState.addOwnEC(r.location); MapState.addOwnEcId(r.ID); } }
            else if (r.team == them) {
                if (nEnemy < 64) enemies[nEnemy++] = r;
                int d = loc.distanceSquaredTo(r.location);
                if (d < nearestEnemyD2) { nearestEnemyD2 = d; nearestEnemy = r; }
                if (r.type == RobotType.MUCKRAKER && d < nearestEnemyMuckD2) { nearestEnemyMuckD2 = d; nearestEnemyMuck = r; }
                if (r.type == RobotType.ENLIGHTENMENT_CENTER) { if (MapState.addEnemyEC(r.location)) MapState.pruneWithEnemyEC(r.location); }
            } else { if (nNeutral < 8) neutrals[nNeutral++] = r; if (r.type == RobotType.ENLIGHTENMENT_CENTER) MapState.addNeutralEC(r.location, r.influence); }
        }
    }

    protected boolean tryMove(Direction d) throws GameActionException {
        if (d == null || d == Direction.CENTER) return false;
        if (rc.canMove(d)) { rc.move(d); loc = rc.getLocation(); return true; }
        return false;
    }

    /** Read the home EC's flag and absorb what it broadcasts. Returns the flag or -1. */
    protected int readHome() throws GameActionException {
        if (MapState.homeId < 0 || !rc.canGetFlag(MapState.homeId)) return -1;
        int f = rc.getFlag(MapState.homeId);
        absorb(f, loc);
        return f;
    }

    /** Learn from any flag (from the home EC or a peer). */
    protected void absorb(int f, MapLocation ref) {
        int t = Comms.type(f);
        switch (t) {
            case Comms.ENEMY_EC: { MapLocation l = Comms.loc(f, ref); if (MapState.addEnemyEC(l)) MapState.pruneWithEnemyEC(l); break; }
            case Comms.NEUTRAL_EC: MapState.addNeutralEC(Comms.loc(f, ref), Comms.unbucket8(Comms.extra(f))); break;
            case Comms.MAP_EDGE: { MapLocation l = Comms.loc(f, ref); int e = Comms.extra(f); MapState.edgeFound(e, e < 2 ? l.x : l.y); break; }
            case Comms.OWN_EC: MapState.addOwnEC(Comms.loc(f, ref)); break;
            case Comms.OWN_EC_ID: MapState.addOwnEcId(Comms.payload(f)); break;
            default: break;
        }
    }

    /**
     * Probe for map edges: one onTheMap call per unknown edge per turn at the
     * sensing radius; when off the map, walk inward to find the exact edge.
     * Costs ~5 bytecodes per probe. Returns an edge id found this turn or -1.
     */
    protected int probeEdges() throws GameActionException {
        int r = (int) Math.sqrt(type.sensorRadiusSquared);
        int found = -1;
        if (MapState.minX < 0 && !rc.onTheMap(new MapLocation(loc.x - r, loc.y))) { int x = loc.x - r; while (!rc.onTheMap(new MapLocation(x, loc.y))) x++; MapState.edgeFound(0, x); found = 0; }
        if (MapState.maxX < 0 && !rc.onTheMap(new MapLocation(loc.x + r, loc.y))) { int x = loc.x + r; while (!rc.onTheMap(new MapLocation(x, loc.y))) x--; MapState.edgeFound(1, x); found = 1; }
        if (MapState.minY < 0 && !rc.onTheMap(new MapLocation(loc.x, loc.y - r))) { int y = loc.y - r; while (!rc.onTheMap(new MapLocation(loc.x, y))) y++; MapState.edgeFound(2, y); found = 2; }
        if (MapState.maxY < 0 && !rc.onTheMap(new MapLocation(loc.x, loc.y + r))) { int y = loc.y + r; while (!rc.onTheMap(new MapLocation(loc.x, y))) y--; MapState.edgeFound(3, y); found = 3; }
        return found;
    }

    /** Count friendly units adjacent (d^2 <= CROWD_D2). */
    protected int crowd() {
        int c = 0; for (int i = nFriend; --i >= 0;) if (friends[i].type != RobotType.ENLIGHTENMENT_CENTER && loc.distanceSquaredTo(friends[i].location) <= C.CROWD_D2) c++;
        return c;
    }
    /** Step to the free adjacent tile with the fewest friendly neighbours (and decent passability), staying within [minD2,maxD2] of anchor. */
    protected boolean spreadOut(MapLocation anchor, int minD2, int maxD2) throws GameActionException {
        Direction best = null; double bs = 1e9;
        for (int i = 8; --i >= 0;) {
            Direction d = DIRS[i]; if (!rc.canMove(d)) continue;
            MapLocation n = loc.add(d); int ad = n.distanceSquaredTo(anchor);
            if (ad < minD2 || ad > maxD2) continue;
            int c = 0; for (int k = nFriend; --k >= 0;) if (friends[k].type != RobotType.ENLIGHTENMENT_CENTER && n.distanceSquaredTo(friends[k].location) <= C.CROWD_D2) c++;
            double sc = c * 2 + 1.0 / rc.sensePassability(n) + n.distanceSquaredTo(anchor) * 0.01;
            if (sc < bs) { bs = sc; best = d; }
        }
        if (best == null) return false;
        rc.move(best); return true;
    }

    protected void setFlag(int f) throws GameActionException { if (rc.getFlag(id) != f && rc.canSetFlag(f)) rc.setFlag(f); }
}
