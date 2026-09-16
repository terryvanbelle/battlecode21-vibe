package g_iter1;

import battlecode.common.*;

/**
 * Enlightenment Center: production, bidding, and the comms hub.
 *
 * Production order (v1): a couple of 1-influence scouts, then slanderers at
 * breakpoint sizes whenever no enemy is near, interleaved with guard
 * politicians; when a neutral EC is known and affordable, a capture politician.
 * Bidding (v1): a small adaptive bid that grows while we lose votes and shrinks
 * while we win them, capped at a fraction of influence.
 */
public strictfp class EC extends Robot {
    private static final int MAX_CHILDREN = 96;
    private final int[] childId = new int[MAX_CHILDREN]; private final int[] childType = new int[MAX_CHILDREN]; private int nChild = 0;
    private int scouts = 0, slanderers = 0, guards = 0, capturers = 0;
    private int bid = 2, lastVotes = 0, votesWon = 0, votesLost = 0;
    private int lastBuildRound = -100;
    private int broadcast = 0, broadcastAge = 0;
    private int captureTargetIdx = -1;

    EC(RobotController rc) { super(rc); }

    @Override protected void init() throws GameActionException {
        MapState.home = loc; MapState.homeId = id; MapState.addOwnEC(loc);
    }

    @Override protected void turn() throws GameActionException {
        sense();
        probeEdges();
        readChildren();
        int inf = rc.getInfluence();
        boolean danger = nearestEnemyD2 < 1 << 30;   // any enemy in our 40 r2 sensor range
        if (rc.isReady()) build(inf, danger);
        doBid();
        updateBroadcast(danger);
        if (round % 50 == 0) Debug.log("@econ inf=" + rc.getInfluence() + " votes=" + rc.getTeamVotes() + " sl=" + slanderers + " g=" + guards + " sc=" + scouts + " cap=" + capturers + " bid=" + bid + " sym=" + MapState.sym + " bounds=" + MapState.minX + "," + MapState.maxX + "," + MapState.minY + "," + MapState.maxY + " eEC=" + MapState.nEnemy + " nEC=" + MapState.nNeutral);
    }

    // ---------------------------------------------------------------- production
    private void build(int inf, boolean danger) throws GameActionException {
        RobotType t; int cost; int role = 0;
        int alive = countAlive();
        if (C.ARCHETYPE == 1) {   // muckraker rush: a few slanderers for income, everything else 1-influence muckrakers sent at the enemy
            if (slanderers < 4 && Econ.bestSize(inf - 5) >= 21 && !danger) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - 5, 130)); role = Roles.ECON; }
            else if (inf >= 2) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.HUNT; }
            else return;
            Direction d0 = spawnDir(t, cost, role); if (d0 == null) return;
            rc.buildRobot(t, d0, cost); RobotInfo nb0 = rc.senseRobotAtLocation(loc.add(d0));
            if (nb0 != null && nChild < MAX_CHILDREN) { childId[nChild] = nb0.ID; childType[nChild] = role; nChild++; }
            if (role == Roles.ECON) slanderers++; else scouts++;
            pendingOrder = Comms.encode(Comms.ORDER, role, loc); pendingOrderRound = round + 1; return;
        }
        if (scouts < C.EARLY_SCOUTS && round < 60) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
        else if (danger && guards < 2 && inf >= 20) { t = RobotType.POLITICIAN; cost = Math.min(inf - 5, 30); role = Roles.GUARD; }
        else if (captureAffordable(inf) >= 0) { captureTargetIdx = captureAffordable(inf); t = RobotType.POLITICIAN; cost = MapState.neutralInf[captureTargetIdx] + 14; role = Roles.CAPTURE; }
        else if (MapState.nEnemy > 0 && enemyEcInf > 0 && inf - reserve() >= Math.max(200, enemyEcInf / 2) && capturers < 3) { captureTargetIdx = -1; t = RobotType.POLITICIAN; cost = Math.min(inf - reserve(), enemyEcInf + 40); role = Roles.CAPTURE; }
        else if (!danger && slanderers < C.MAX_SLANDERERS && Econ.bestSize(inf - reserve()) >= 21 && (guards >= slanderers / 3)) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - reserve(), C.MAX_SLANDERER_SIZE)); role = Roles.ECON; }
        else if (inf >= 20 && (guards < C.GUARD_BASE + slanderers / 2 || (danger && guards < C.MAX_GUARDS))) { t = RobotType.POLITICIAN; cost = Math.min(Math.max(20, inf / 4), 60); role = Roles.GUARD; }
        else if (inf >= 30 && scouts < 3 + round / 300 + (inf > 400 ? 3 : 0) + (MapState.nEnemy == 0 && round > 150 ? 2 : 0)) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
        else if (!danger && slanderers < C.MAX_SLANDERERS && Econ.bestSize(inf - reserve()) >= 21) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - reserve(), C.MAX_SLANDERER_SIZE)); role = Roles.ECON; }
        else if (inf - reserve() >= 100 && guards < C.MAX_GUARDS) { t = RobotType.POLITICIAN; cost = Math.min(inf - reserve(), Math.max(50, inf / 3)); role = Roles.GUARD; }
        else if (MapState.nEnemy > 0 && inf - reserve() >= 300 && capturers < 3) { captureTargetIdx = -1; t = RobotType.POLITICIAN; cost = inf - reserve(); role = Roles.CAPTURE; }   // rich and idle: throw everything at the enemy EC
        else return;
        if (cost <= 0 || cost > inf) return;
        Direction d = spawnDir(t, cost, role);
        if (d == null) return;
        rc.buildRobot(t, d, cost);
        RobotInfo nb = rc.senseRobotAtLocation(loc.add(d));
        if (nb != null && nChild < MAX_CHILDREN) { childId[nChild] = nb.ID; childType[nChild] = role; nChild++; }
        switch (role) { case Roles.SCOUT: scouts++; break; case Roles.GUARD: guards++; break; case Roles.ECON: slanderers++; break; case Roles.CAPTURE: capturers++; break; default: break; }
        lastBuildRound = round;
        // tell the newborn its role via our flag for one round: ORDER with target
        MapLocation tgt = role == Roles.CAPTURE ? (captureTargetIdx >= 0 ? MapState.neutralEC[captureTargetIdx] : MapState.enemyEC[0]) : loc;
        // a robot spawned this round takes its FIRST turn next round (the engine iterates a snapshot of the
        // spawn order), so the order must still be on the flag next round; the EC cannot build again before that
        pendingOrder = Comms.encode(Comms.ORDER, role, tgt); pendingOrderRound = round + 1;
        Debug.log("@spawn t=" + t.ordinal() + " inf=" + cost + " role=" + role + " dir=" + d + " ecInf=" + rc.getInfluence());
    }
    private int pendingOrder = 0, pendingOrderRound = -10;
    private int enemyEcInf = 0;   // last reported influence of the enemy EC we target (bucketed)

    private int reserve() { return Math.max(bid * 2, 10); }

    private int captureAffordable(int inf) {
        int best = -1, bestCost = 1 << 30;
        for (int i = MapState.nNeutral; --i >= 0;) {
            int c = MapState.neutralInf[i] + 14;
            if (c <= inf - reserve() && c < bestCost && capturers < 2) { best = i; bestCost = c; }
        }
        return best;
    }

    /** Spawn tile: for a scout, spread around; else the most passable free tile away from the nearest enemy. */
    private Direction spawnDir(RobotType t, int cost, int role) throws GameActionException {
        Direction best = null; double bs = -1e9;
        for (int i = 8; --i >= 0;) {
            Direction d = DIRS[i];
            if (!rc.canBuildRobot(t, d, cost)) continue;
            MapLocation n = loc.add(d);
            double s = rc.sensePassability(n) * 2;
            if (role == Roles.ECON && nearestEnemy != null) s += Math.sqrt(n.distanceSquaredTo(nearestEnemy.location)) * 0.5;
            if (role == Roles.ECON && MapState.nEnemy > 0) s += Math.sqrt(n.distanceSquaredTo(MapState.enemyEC[0])) * 0.3;
            if (role == Roles.SCOUT) s += nextInt(3);   // spread scouts
            if (role == Roles.GUARD && nearestEnemy != null) s -= Math.sqrt(n.distanceSquaredTo(nearestEnemy.location)) * 0.5;
            if (role == Roles.CAPTURE) { MapLocation tg = captureTargetIdx >= 0 ? MapState.neutralEC[captureTargetIdx] : (MapState.nEnemy > 0 ? MapState.enemyEC[0] : null); if (tg != null) s -= Math.sqrt(n.distanceSquaredTo(tg)) * 0.7; }
            if (s > bs) { bs = s; best = d; }
        }
        return best;
    }

    private int countAlive() {
        // recount children cheaply via canGetFlag (5 bytecodes each) -- also compacts the list
        int k = 0; int sl = 0, g = 0, sc = 0, cap = 0;
        for (int i = 0; i < nChild; i++) {
            if (rc.canGetFlag(childId[i])) { childId[k] = childId[i]; childType[k] = childType[i]; k++;
                switch (childType[i]) { case Roles.SCOUT: sc++; break; case Roles.GUARD: g++; break; case Roles.ECON: sl++; break; case Roles.CAPTURE: cap++; break; default: break; } }
        }
        nChild = k; slanderers = sl; guards = g; scouts = sc; capturers = cap;
        return k;
    }

    // ---------------------------------------------------------------- bidding
    private void doBid() throws GameActionException {
        int votes = rc.getTeamVotes();
        if (votes > 751) { lastVotes = votes; return; }        // majority secured, stop paying and stop adapting
        if (round > 1) { if (votes > lastVotes) { votesWon++; bid = Math.max(1, bid - (bid / 8 + 1) / 2); } else { votesLost++; bid = bid + bid / 3 + 1; } }
        lastVotes = votes;
        int inf = rc.getInfluence();
        int cap = Math.max(1, inf / (C.ARCHETYPE == 2 ? 2 : C.BID_CAP_DIV));
        if (C.ARCHETYPE == 2 && round > 1 && votes == lastVotes) bid = bid * 2 + 1;
        if (round < 20) cap = Math.min(cap, 3);
        if (bid > cap) bid = cap;
        if (rc.canBid(bid) && bid > 0) rc.bid(bid);
    }

    // ---------------------------------------------------------------- comms
    private void readChildren() throws GameActionException {
        // budget: read up to 24 child flags per turn, round-robin
        int n = nChild; if (n == 0) return;
        int start = (round * 24) % n;
        for (int k = 0; k < 24 && k < n; k++) {
            int i = (start + k) % n;
            if (!rc.canGetFlag(childId[i])) continue;
            int f = rc.getFlag(childId[i]);
            if (Comms.type(f) != Comms.IDLE) absorb(f, loc);
        }
    }

    @Override protected void absorb(int f, MapLocation ref) {
        super.absorb(f, ref);
        if (Comms.type(f) == Comms.ENEMY_EC && Comms.extra(f) > 0) enemyEcInf = Comms.unbucket(Comms.extra(f));
    }

    private void updateBroadcast(boolean danger) throws GameActionException {
        // priority: a fresh spawn order (1 round) > enemy EC > neutral EC > status
        int f;
        if (round == pendingOrderRound || round + 1 == pendingOrderRound) f = pendingOrder;
        else if (MapState.nEnemy > 0 && (round / 3) % 2 == 0) f = Comms.encode(Comms.ENEMY_EC, 0, MapState.enemyEC[(round / 6) % MapState.nEnemy]);
        else if (MapState.nNeutral > 0 && (round / 3) % 2 == 1) f = Comms.encode(Comms.NEUTRAL_EC, Comms.bucket8(MapState.neutralInf[(round / 6) % MapState.nNeutral]), MapState.neutralEC[(round / 6) % MapState.nNeutral]);
        else {
            f = -1;
            for (int k = 0; k < 4 && f < 0; k++) {
                int e = (round + k) % 4;
                if (e == 0 && MapState.minX >= 0) f = Comms.encode(Comms.MAP_EDGE, 0, new MapLocation(MapState.minX, loc.y));
                if (e == 1 && MapState.maxX >= 0) f = Comms.encode(Comms.MAP_EDGE, 1, new MapLocation(MapState.maxX, loc.y));
                if (e == 2 && MapState.minY >= 0) f = Comms.encode(Comms.MAP_EDGE, 2, new MapLocation(loc.x, MapState.minY));
                if (e == 3 && MapState.maxY >= 0) f = Comms.encode(Comms.MAP_EDGE, 3, new MapLocation(loc.x, MapState.maxY));
            }
            if (f < 0) f = Comms.encode(Comms.STATUS, (danger ? 1 : 0), loc);
        }
        setFlag(f);
    }
}
