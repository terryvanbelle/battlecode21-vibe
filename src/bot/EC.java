package bot;

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
    private final int[] childId = new int[MAX_CHILDREN]; private final int[] childType = new int[MAX_CHILDREN]; private final int[] childBirth = new int[MAX_CHILDREN]; private int nChild = 0;
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
        readSiblings();
        int inf = rc.getInfluence();
        boolean danger = nearestEnemyD2 < 1 << 30;   // any enemy in our 40 r2 sensor range
        if (rc.isReady()) build(inf, danger);
        doBid();
        updateBroadcast(danger);
        if (round % 50 == 0) Debug.log("@econ inf=" + rc.getInfluence() + " votes=" + rc.getTeamVotes() + " sl=" + slanderers + " g=" + guards + " sc=" + scouts + " cap=" + capturers + " idle=" + idleRounds + " spend=" + spendBuilds + " bank=" + bankRounds + " bid=" + bid + " eVotes~" + enemyVotesEst + " sym=" + MapState.sym + " bounds=" + MapState.minX + "," + MapState.maxX + "," + MapState.minY + "," + MapState.maxY + " eEC=" + MapState.nEnemy + " nEC=" + MapState.nNeutral);
    }

    // ---------------------------------------------------------------- production
    private void build(int inf, boolean danger) throws GameActionException {
        RobotType t; int cost; int role = 0;
        int alive = countAlive();
        int threat = 0;   // largest enemy politician conviction in sensor range
        for (int i = nEnemy; --i >= 0;) if (enemies[i].type == RobotType.POLITICIAN && enemies[i].conviction > threat) threat = enemies[i].conviction;
        if (threat < C.THREAT_MIN) threat = 0;
        int need = threat > 0 ? threat + C.THREAT_MARGIN : 0;   // the guard that would beat it
        if (need > 0 && need > inf - 5) { bankRounds++; return; }   // cannot beat what it sees: build nothing, bank (influence is conviction)
        if (C.ARCHETYPE == 1) {   // muckraker rush: a few slanderers for income, everything else 1-influence muckrakers sent at the enemy
            if (slanderers < 4 && Econ.bestSize(inf - 5) >= 21 && !danger) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - 5, 130)); role = Roles.ECON; }
            else if (inf >= 2) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.HUNT; }
            else return;
            Direction d0 = spawnDir(t, cost, role); if (d0 == null) return;
            rc.buildRobot(t, d0, cost); RobotInfo nb0 = rc.senseRobotAtLocation(loc.add(d0));
            if (nb0 != null && nChild < MAX_CHILDREN) { childId[nChild] = nb0.ID; childType[nChild] = role; childBirth[nChild] = round; nChild++; }
            if (role == Roles.ECON) slanderers++; else scouts++;
            pendingOrder = Comms.encode(Comms.ORDER, role, loc); pendingOrderRound = round + 1; return;
        }
        if (C.ARCHETYPE == 3) {   // politician rush: 2 scouts, 2 small slanderers, then every 100+ influence becomes a capture politician aimed at the enemy EC
            if (scouts < 2) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
            else if (slanderers < 2 && Econ.bestSize(inf - 5) >= 21 && !danger) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - 5, 85)); role = Roles.ECON; }
            else if (MapState.nEnemy > 0 && inf >= 100) { captureTargetIdx = -1; t = RobotType.POLITICIAN; cost = inf - 5; role = Roles.CAPTURE; }
            else if (MapState.nEnemy == 0 && scouts < 4 && inf >= 30) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
            else return;
        }
        else if (scouts < C.EARLY_SCOUTS && round < 60) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
        else if (danger && guards < 2 && inf >= 20) {
            if (need > 0 && need > inf - 5) { bankRounds++; return; }   // cannot beat what it sees: bank (influence is conviction)
            t = RobotType.POLITICIAN; cost = need > 0 ? Math.min(inf - 5, need) : Math.min(inf - 5, 30); role = Roles.GUARD;
        }
        else if (captureAffordable(inf) >= 0) { captureTargetIdx = captureAffordable(inf); t = RobotType.POLITICIAN; cost = MapState.neutralInf[captureTargetIdx] + 14; role = Roles.CAPTURE; }
        else if (MapState.nEnemy > 0 && enemyEcInf > 0 && inf - reserve() >= Math.max(200, enemyEcInf / 2) && capturers < 3) { captureTargetIdx = -1; t = RobotType.POLITICIAN; cost = Math.min(inf - reserve(), enemyEcInf + 40); role = Roles.CAPTURE; }
        else if (!danger && slanderers < C.MAX_SLANDERERS && Econ.bestSize(inf - reserve()) >= 21 && (guards >= slanderers / 3)) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - reserve(), C.MAX_SLANDERER_SIZE)); role = Roles.ECON; }
        else if (inf >= 20 && (guards < C.GUARD_BASE + slanderers / 2 || (danger && guards < C.MAX_GUARDS))) {
            if (need > 0 && need > inf - 5) { bankRounds++; return; }
            t = RobotType.POLITICIAN; cost = need > 0 ? Math.min(inf - 5, need) : Math.min(Math.max(20, inf / 4), 60); role = Roles.GUARD;
        }
        else if (inf >= 30 && scouts < 3 + round / 300 + (inf > 400 ? 3 : 0) + (MapState.nEnemy == 0 && round > 150 ? 2 : 0)) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
        else if (!danger && slanderers < C.MAX_SLANDERERS && Econ.bestSize(inf - reserve()) >= 21) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - reserve(), C.MAX_SLANDERER_SIZE)); role = Roles.ECON; }
        else if (inf - reserve() >= 100 && guards < C.MAX_GUARDS) { t = RobotType.POLITICIAN; cost = Math.min(inf - reserve(), Math.max(50, inf / 3)); role = Roles.GUARD; }
        else if (MapState.nEnemy > 0 && inf - reserve() >= 300 && capturers < 3) { captureTargetIdx = -1; t = RobotType.POLITICIAN; cost = inf - reserve(); role = Roles.CAPTURE; }   // rich and idle: throw everything at the enemy EC
        else if (inf - reserve() >= C.SPARE_MIN) {
            // never idle: every capped branch declined but influence is spare. Alternate bodies: a guard when guards
            // trail slanderers, else another slanderer up to the spare cap, else a 1-influence hunter.
            int spare = inf - reserve();
            if (guards < slanderers + 2 || spare >= 300) { t = RobotType.POLITICIAN; cost = Math.min(spare, Math.max(20, spare / 3)); role = Roles.GUARD; }   // a big bank buys big guards whatever the ratio
            else if (!danger && slanderers < C.SPEND_SLANDERER_CAP && Econ.bestSize(spare) >= 21) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(spare, C.MAX_SLANDERER_SIZE)); role = Roles.ECON; }
            else { t = RobotType.MUCKRAKER; cost = 1; role = Roles.HUNT; }
            spendBuilds++;
        }
        else { if (inf >= 21) idleRounds++; return; }
        if (cost <= 0 || cost > inf) return;
        Direction d = spawnDir(t, cost, role);
        if (d == null) return;
        rc.buildRobot(t, d, cost);
        RobotInfo nb = rc.senseRobotAtLocation(loc.add(d));
        if (nb != null && nChild < MAX_CHILDREN) { childId[nChild] = nb.ID; childType[nChild] = role; childBirth[nChild] = round; nChild++; }
        switch (role) { case Roles.SCOUT: case Roles.HUNT: scouts++; break; case Roles.GUARD: guards++; break; case Roles.ECON: slanderers++; break; case Roles.CAPTURE: capturers++; break; default: break; }
        lastBuildRound = round;
        // tell the newborn its role via our flag for one round: ORDER with target
        MapLocation tgt = role == Roles.CAPTURE ? (captureTargetIdx >= 0 ? MapState.neutralEC[captureTargetIdx] : MapState.enemyEC[0]) : loc;
        // a robot spawned this round takes its FIRST turn next round (the engine iterates a snapshot of the
        // spawn order), so the order must still be on the flag next round; the EC cannot build again before that
        pendingOrder = Comms.encode(Comms.ORDER, role, tgt); pendingOrderRound = round + 1;
        Debug.log("@spawn t=" + t.ordinal() + " inf=" + cost + " role=" + role + " dir=" + d + " ecInf=" + rc.getInfluence());
    }
    private int pendingOrder = 0, pendingOrderRound = -10;
    private int idleRounds = 0, spendBuilds = 0, bankRounds = 0;   // decision-point counters: ready with >= 21 influence and built nothing / built through the spare branch
    private int enemyEcInf = 0;   // last reported influence of the enemy EC we target (bucketed)

    private int reserve() { return Math.min(Math.max(bid * 2, 10), Math.max(10, rc.getInfluence() / 2)); }   // keep enough to bid next round, never more than half

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
            // a slanderer becomes a politician at roundsAlive == 300 (engine CAMOUFLAGE); it then guards, so count it as one
            if (childType[i] == Roles.ECON && round - childBirth[i] >= 300) childType[i] = Roles.GUARD;
            if (rc.canGetFlag(childId[i])) { childId[k] = childId[i]; childType[k] = childType[i]; childBirth[k] = childBirth[i]; k++;
                switch (childType[i]) { case Roles.SCOUT: case Roles.HUNT: sc++; break; case Roles.GUARD: g++; break; case Roles.ECON: sl++; break; case Roles.CAPTURE: cap++; break; default: break; } }
        }
        nChild = k; slanderers = sl; guards = g; scouts = sc; capturers = cap;
        return k;
    }

    // ---------------------------------------------------------------- bidding
    private int enemyVotesEst = 0;   // rounds in which we did not gain a vote after round 1 (assumes the opponent bid; conservative)
    private void doBid() throws GameActionException {
        int votes = rc.getTeamVotes();
        int inf = rc.getInfluence();
        int remaining = 1500 - round;
        // Adapt only on rounds where we actually bid last round (otherwise a lost vote says nothing about our bid).
        if (round > 1 && bidLastRound) {
            if (votes > lastVotes) { votesWon++; bid = Math.max(1, bid - bid / 10); }
            else { votesLost++; bid = Math.min(bid + bid / 4 + 1, Math.max(1, inf)); }
        }
        if (round > 1 && votes == lastVotes) enemyVotesEst++;
        lastVotes = votes; bidLastRound = false;
        if (votes > 751) return;                                // majority secured
        // Are we safe without bidding? If the opponent cannot catch up even winning every remaining vote, stop.
        if (votes > enemyVotesEst + remaining) return;
        // Influence is worth more early (it compounds through slanderers), so the cap ramps up over the game and
        // rises further when we are behind on votes late.
        int div = round < 600 ? C.BID_EARLY_DIV : round < 1000 ? 5 : 3;   // Iteration 9: influence compounds early; the vote race is decided late
        if (round > 900 && enemyVotesEst > votes) div = 2;
        int cap = Math.max(1, inf / (C.ARCHETYPE == 2 ? 2 : div));
        if (C.ARCHETYPE == 2 && round > 1 && votes == lastVotes) bid = bid * 2 + 1;
        if (round < 20) cap = Math.min(cap, 3);
        if (bid > cap) bid = cap;
        if (bid > 0 && rc.canBid(bid)) { rc.bid(bid); bidLastRound = true; }
    }
    private boolean bidLastRound = false;

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

    /** ECs can read any flag: absorb what sibling ECs broadcast (bounds, enemy ECs, neutrals). */
    private void readSiblings() throws GameActionException {
        for (int i = MapState.nOwnId; --i >= 0;) {
            int sid = MapState.ownEcId[i];
            if (!rc.canGetFlag(sid)) { MapState.nOwnId--; MapState.ownEcId[i] = MapState.ownEcId[MapState.nOwnId]; continue; }   // sibling lost (converted)
            int f = rc.getFlag(sid);
            if (Comms.type(f) != Comms.IDLE && Comms.type(f) != Comms.ORDER && Comms.type(f) != Comms.STATUS) absorb(f, loc);
        }
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
