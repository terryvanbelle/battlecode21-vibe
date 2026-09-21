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
    // The centre a live capturer is walking to, so the next one is not sent to the same place.
    // Stored as a location, not an index: removeNeutral compacts the array and every index shifts.
    private final MapLocation[] childTgt = new MapLocation[MAX_CHILDREN];
    private int scouts = 0, slanderers = 0, guards = 0, capturers = 0;
    // Diagnostic (Iteration 48): what does a centre actually HEAR? Counts of flags absorbed by type, and
    // of neutral reports refused because the tile is already in ownEC.
    private int heardNeutral = 0, heardOwn = 0, heardOwnId = 0, heardEnemy = 0, refusedNeutral = 0, nearbyReads = 0;
    // Iteration 49 counters and the presumed-own list (dose b): tiles a child announced it was about to flip
    private int flipIntents = 0, presumedSkips = 0, saveBidSkipped = 0, saveAbandonedRound = -1, saveWaitRound = -10;
    private final MapLocation[] presumed = new MapLocation[MapState.MAX_ECS]; private final int[] presumedRound = new int[MapState.MAX_ECS]; private int nPresumed = 0;
    private void presume(MapLocation l) {
        for (int i = nPresumed; --i >= 0;) if (presumed[i].equals(l)) { presumedRound[i] = round; return; }
        int i = nPresumed < MapState.MAX_ECS ? nPresumed++ : round % MapState.MAX_ECS;
        presumed[i] = l; presumedRound[i] = round;
        Debug.log("@presume r=" + round + " tile=" + (l.x - MapState.minX) + "," + (l.y - MapState.minY));
    }
    private boolean presumed(MapLocation l) {
        for (int i = nPresumed; --i >= 0;) if (presumed[i].equals(l) && round - presumedRound[i] < C.PRESUME_ROUNDS) return true;
        return false;
    }
    private int blockedRounds = 0;   // diagnostic: rounds a build was chosen but no adjacent tile was free
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
        if (C.HANDOFF == 1) readNearbyFriendlies();
        int inf = rc.getInfluence();
        boolean danger = nearestEnemyD2 < 1 << 30;   // any enemy in our 40 r2 sensor range (guards react to this)
        // Iteration 27: the economy stops only for a threat that can actually hurt, not for any passing muckraker
        boolean econDanger = false;
        for (int i = nEnemy; --i >= 0;) {
            RobotInfo r = enemies[i];
            if (r.type == RobotType.POLITICIAN && r.conviction >= C.ECON_DANGER_POL_CONV) { econDanger = true; break; }
            if (r.type == RobotType.MUCKRAKER && r.location.distanceSquaredTo(loc) <= C.ECON_DANGER_MUCK_D2) { econDanger = true; break; }
        }
        if (round > C.SAVE_UNTIL) saveDone = true;
        if (econDanger) econDangerRounds++;
        if (rc.isReady()) build(inf, danger, econDanger);
        doBid();
        updateBroadcast(danger);
        if (round % 50 == 0) Debug.log("@econ inf=" + rc.getInfluence() + " votes=" + rc.getTeamVotes() + " sl=" + slanderers + " g=" + guards + " sc=" + scouts + " cap=" + capturers + " idle=" + idleRounds + " noTile=" + blockedRounds + " spend=" + spendBuilds + " eDanger=" + (econDangerRounds) + " save=" + saveRounds + " bid=" + bid + " eVotes~" + enemyVotesEst + " sym=" + MapState.sym + " bounds=" + MapState.minX + "," + MapState.maxX + "," + MapState.minY + "," + MapState.maxY + " eEC=" + MapState.nEnemy + " nEC=" + MapState.nNeutral + " heardN=" + heardNeutral + " refusedN=" + refusedNeutral + " heardOwn=" + heardOwn + " heardOwnId=" + heardOwnId + " sibIds=" + MapState.nOwnId + " nearReads=" + nearbyReads + " orderRounds=" + orderRounds + " flipInt=" + flipIntents + " presumeSkip=" + presumedSkips + " saveBidSkip=" + saveBidSkipped + " saveAband=" + saveAbandonedRound);
    }

    // ---------------------------------------------------------------- production
    private void build(int inf, boolean danger, boolean econDanger) throws GameActionException {
        RobotType t; int cost; int role = 0;
        int alive = countAlive();
        if (C.ARCHETYPE == 1) {   // muckraker rush: a few slanderers for income, everything else 1-influence muckrakers sent at the enemy
            if (slanderers < 4 && Econ.bestSize(inf - 5) >= 21 && !econDanger) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - 5, 130)); role = Roles.ECON; }
            else if (inf >= 2) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.HUNT; }
            else return;
            Direction d0 = spawnDir(t, cost, role); if (d0 == null) return;
            rc.buildRobot(t, d0, cost); RobotInfo nb0 = rc.senseRobotAtLocation(loc.add(d0));
            if (nb0 != null && nChild < MAX_CHILDREN) { childId[nChild] = nb0.ID; childType[nChild] = role; childBirth[nChild] = round; childTgt[nChild] = null; nChild++; }
            if (role == Roles.ECON) slanderers++; else scouts++;
            pendingOrder = Comms.encode(Comms.ORDER, role, loc); pendingOrderRound = round + 1; return;
        }
        if (C.ARCHETYPE == 3) {   // politician rush: 2 scouts, 2 small slanderers, then every 100+ influence becomes a capture politician aimed at the enemy EC
            if (scouts < 2) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
            else if (slanderers < 2 && Econ.bestSize(inf - 5) >= 21 && !econDanger) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - 5, 85)); role = Roles.ECON; }
            else if (MapState.nEnemy > 0 && inf >= 100) { captureTargetIdx = -1; t = RobotType.POLITICIAN; cost = inf - 5; role = Roles.CAPTURE; }
            else if (MapState.nEnemy == 0 && scouts < 4 && inf >= 30) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
            else return;
        }
        else if (C.ARCHETYPE == 4) {   // expander: scouts to find the neutrals, a thin income core, and one capturer per neutral
            // Targets are spread across the known neutrals, not all aimed at the cheapest: six capturers
            // converging on one 103-influence centre took it once and then jammed the cap forever
            // (diagnostic 2026-09-20, cap=6 pinned from r100 with influence stuck at 173).
            // Keep a bidding float. Without one it spent every influence on captures and held 0-160
            // influence all game: it took 8 centres to g_iter8's 0 and still LOST on votes at r1500,
            // which would make any candidate's win against it a verdict on bidding, not on expansion.
            int float4 = Math.max(20, inf / 10);
            int budget = inf - 5 - float4;
            int nAfford = 0, pick = -1;
            for (int i = MapState.nNeutral; --i >= 0;) if (MapState.neutralInf[i] + 14 <= budget) nAfford++;
            if (nAfford > 0 && capturers < Math.min(4, nAfford)) {
                int want = capturers % nAfford, seen = 0;
                for (int i = MapState.nNeutral; --i >= 0;) {
                    if (MapState.neutralInf[i] + 14 > budget) continue;
                    if (seen++ == want) { pick = i; break; }
                }
            }
            if (scouts < 2) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
            else if (slanderers < 3 && !econDanger && Econ.bestSize(inf - 5) >= 41) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - 5, 130)); role = Roles.ECON; }
            else if (pick >= 0) { captureTargetIdx = pick; t = RobotType.POLITICIAN; cost = MapState.neutralInf[pick] + 14; role = Roles.CAPTURE; Debug.log("@arch4 capture r=" + round + " idx=" + pick + " inf=" + MapState.neutralInf[pick] + " cost=" + cost + " float=" + float4 + " cap=" + capturers + "/" + nAfford); }
            else if (scouts < 8 && inf >= 30) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
            else if (!econDanger && slanderers < 12 && Econ.bestSize(inf - 5) >= 41) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - 5, C.MAX_SLANDERER_SIZE)); role = Roles.ECON; }
            else if (inf >= 20 && guards < 4) { t = RobotType.POLITICIAN; cost = Math.min(inf - 5, 40); role = Roles.GUARD; }
            // Bank while a neutral is still known: saving for the next centre IS this archetype's behaviour,
            // and the 1-influence filler it used to build reached 609 muckrakers, which made it a swarm as
            // well as an expander and duplicated arch_muck. Only once there is nothing left to take does it
            // spend the spare action.
            else if (MapState.nNeutral > 0) return;
            else if (inf >= 2) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.HUNT; }
            else return;
        }
        else if (C.OPENING_SLANDERER_FIRST == 1 && slanderers == 0 && round < 10 && !econDanger && Econ.bestSize(inf - 5) >= 21) {
            t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - 5, C.MAX_SLANDERER_SIZE)); role = Roles.ECON;   // Iteration 34: income before scouting
            Debug.log("@open1 slanderer r=" + round + " size=" + cost);
        }
        else if (scouts < C.EARLY_SCOUTS && round < 60) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
        else if (danger && guards < 2 && inf >= 20) { t = RobotType.POLITICIAN; cost = Math.min(inf - 5, 30); role = Roles.GUARD; }
        else if (C.SAVE_MODE && !saveDone && round <= C.SAVE_UNTIL && cheapNeutral() >= 0) {
            int best = cheapNeutral(), price = MapState.neutralInf[best] + 14 + C.SAVE_BANK;
            if (inf - 5 >= price) {
                captureTargetIdx = best; t = RobotType.POLITICIAN; cost = price; role = Roles.CAPTURE; saveDone = true;
                Debug.log("@save capture r=" + round + " target=" + MapState.neutralInf[best] + " cost=" + cost + " saved=" + saveRounds);
            }
            else if (slanderers < C.SAVE_SLANDERERS && Econ.bestSize(inf - 5) >= C.MIN_SLANDERER_SIZE) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - 5, 85)); role = Roles.ECON; }
            else {
                saveRounds++; saveWaitRound = round;   // dose (a): doBid sees this and holds the bank
                if (C.SAVE_MAX_WAIT > 0 && saveRounds >= C.SAVE_MAX_WAIT) { saveDone = true; saveAbandonedRound = round; Debug.log("@save abandoned r=" + round + " price=" + price + " inf=" + inf); }
                return;
            }
        }
        else if (captureAffordable(inf) >= 0) { captureTargetIdx = captureAffordable(inf); t = RobotType.POLITICIAN; cost = MapState.neutralInf[captureTargetIdx] + 14; role = Roles.CAPTURE; Debug.log("@capbuild r=" + round + " cost=" + cost + " home=" + (birth <= 1)); }
        else if (MapState.nEnemy > 0 && enemyEcInf > 0 && inf - reserve() >= Math.max(200, enemyEcInf / 2) && capturers < 3 && !presumed(MapState.enemyEC[0])) { captureTargetIdx = -1; t = RobotType.POLITICIAN; cost = Math.min(inf - reserve(), enemyEcInf + 40); role = Roles.CAPTURE; }
        else if (!econDanger && slanderers < C.MAX_SLANDERERS && Econ.bestSize(inf - reserve()) >= C.MIN_SLANDERER_SIZE && (guards >= slanderers / 3)) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - reserve(), C.MAX_SLANDERER_SIZE)); role = Roles.ECON; }
        else if (inf >= 20 && (guards < C.GUARD_BASE + slanderers / 2 || (danger && guards < C.MAX_GUARDS))) { t = RobotType.POLITICIAN; cost = Math.min(Math.max(20, inf / 4), 60); role = Roles.GUARD; }
        else if (inf >= 30 && scouts < Math.min(C.SCOUT_MAX, C.SCOUT_BASE + round / C.SCOUT_PER_ROUND + (inf > 400 ? 3 : 0) + (MapState.nEnemy == 0 && round > 150 ? 2 : 0))) { t = RobotType.MUCKRAKER; cost = 1; role = Roles.SCOUT; }
        else if (!econDanger && slanderers < C.MAX_SLANDERERS && Econ.bestSize(inf - reserve()) >= C.MIN_SLANDERER_SIZE) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(inf - reserve(), C.MAX_SLANDERER_SIZE)); role = Roles.ECON; }
        else if (inf - reserve() >= 100 && guards < C.MAX_GUARDS) { t = RobotType.POLITICIAN; cost = Math.min(inf - reserve(), Math.max(50, inf / 3)); role = Roles.GUARD; }
        else if (MapState.nEnemy > 0 && inf - reserve() >= 300 && capturers < 3 && !presumed(MapState.enemyEC[0])) { captureTargetIdx = -1; t = RobotType.POLITICIAN; cost = inf - reserve(); role = Roles.CAPTURE; }   // rich and idle: throw everything at the enemy EC
        else if (inf - reserve() >= C.SPARE_MIN) {
            // never idle: every capped branch declined but influence is spare. Alternate bodies: a guard when guards
            // trail slanderers, else another slanderer up to the spare cap, else a 1-influence hunter.
            int spare = inf - reserve();
            if (!econDanger && slanderers < C.SPEND_SLANDERER_CAP && Econ.bestSize(spare) >= C.MIN_SLANDERER_SIZE) { t = RobotType.SLANDERER; cost = Econ.bestSize(Math.min(spare, C.MAX_SLANDERER_SIZE)); role = Roles.ECON; }   // Iteration 35: economy before standing bodies
            else if (guards < C.SPEND_GUARD_CAP) { t = RobotType.POLITICIAN; cost = Math.min(spare, Math.max(20, spare / 3)); role = Roles.GUARD; }   // Iteration 35 dose 2: no big-bank escape -- a big bank is what buys a centre
            else { t = RobotType.MUCKRAKER; cost = 1; role = Roles.HUNT; }
            spendBuilds++;
        }
        else { if (inf >= 21) idleRounds++; return; }
        if (cost <= 0 || cost > inf) return;
        Direction d = spawnDir(t, cost, role);
        // Hypothesis (2026-09-21): a centre ringed by its own units cannot build at all, and this
        // return is silent -- it does not even count as idle. Observed: centres ending a game with
        // ZERO slanderers, no threat, and 71,010 influence banked.
        if (d == null) { blockedRounds++; return; }
        rc.buildRobot(t, d, cost);
        RobotInfo nb = rc.senseRobotAtLocation(loc.add(d));
        if (nb != null && nChild < MAX_CHILDREN) { childId[nChild] = nb.ID; childType[nChild] = role; childBirth[nChild] = round; childTgt[nChild] = (role == Roles.CAPTURE && captureTargetIdx >= 0) ? MapState.neutralEC[captureTargetIdx] : null; nChild++; }
        switch (role) { case Roles.SCOUT: case Roles.HUNT: scouts++; break; case Roles.GUARD: guards++; break; case Roles.ECON: slanderers++; break; case Roles.CAPTURE: capturers++; break; default: break; }
        lastBuildRound = round;
        // tell the newborn its role via our flag for one round: ORDER with target
        MapLocation tgt = role == Roles.CAPTURE ? (captureTargetIdx >= 0 ? MapState.neutralEC[captureTargetIdx] : MapState.enemyEC[0]) : loc;
        // a robot spawned this round takes its FIRST turn next round (the engine iterates a snapshot of the
        // spawn order), so the order must still be on the flag next round; the EC cannot build again before that
        // Iteration 48 dose 4: only a CAPTURE needs an order -- it is the one build whose target the newborn
        // cannot infer, and only politicians read orders at all (default role GUARD; scouts and slanderers never
        // look). Every other build used to put a two-round ORDER on the flag anyway, and readers skip orders, so a
        // centre that builds most rounds broadcast its map knowledge to nobody. Measured: captured centres read
        // thousands of nearby flags and heard ZERO neutral reports in their whole lives; home's own scouts never
        // learned the list from home either.
        if (role == Roles.CAPTURE || C.HANDOFF == 0) { pendingOrder = Comms.encode(Comms.ORDER, role, tgt); pendingOrderRound = round + 1; }
        Debug.log("@spawn t=" + t.ordinal() + " inf=" + cost + " role=" + role + " dir=" + d + " ecInf=" + rc.getInfluence());
    }
    private int pendingOrder = 0, pendingOrderRound = -10;
    private int orderRounds = 0;   // diagnostic: rounds the flag carried an ORDER instead of a broadcast
    private int idleRounds = 0, spendBuilds = 0, saveRounds = 0, econDangerRounds = 0; private boolean saveDone = false;   // decision-point counters: ready with >= 21 influence and built nothing / built through the spare branch
    private int enemyEcInf = 0;   // last reported influence of the enemy EC we target (bucketed)

    /** Cheapest known neutral EC at or under SAVE_MAX_TARGET, or -1. No distance cap: the capturer walks. */
    private int cheapNeutral() {
        int best = -1, bestInf = C.SAVE_MAX_TARGET + 1;
        for (int i = MapState.nNeutral; --i >= 0;) { if (presumed(MapState.neutralEC[i])) { presumedSkips++; continue; } if (MapState.neutralInf[i] < bestInf) { bestInf = MapState.neutralInf[i]; best = i; } }
        return best;
    }
    private int reserve() { return Math.min(Math.max(bid * 2, 10), Math.max(10, rc.getInfluence() / 2)); }   // keep enough to bid next round, never more than half

    private int captureAffordable(int inf) {
        int best = -1, bestCost = 1 << 30;
        for (int i = MapState.nNeutral; --i >= 0;) {
            int c = MapState.neutralInf[i] + 14;
            if (c > inf - reserve() || c >= bestCost || capturers >= C.MAX_CAPTURERS) continue;
            if (claimed(MapState.neutralEC[i])) continue;   // a live capturer is already walking there
            if (presumed(MapState.neutralEC[i])) { presumedSkips++; continue; }   // Iteration 49: a child announced its flip
            best = i; bestCost = c;
        }
        return best;
    }

    /** Is a live capturer already aimed at this centre? Raising the cap without this just sends
     *  every extra capturer to the same cheapest target: with the cap at 4, aborts rose 4 -> 30. */
    private boolean claimed(MapLocation l) {
        for (int i = nChild; --i >= 0;) if (childType[i] == Roles.CAPTURE && childTgt[i] != null && childTgt[i].equals(l)) return true;
        return false;
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
            if (rc.canGetFlag(childId[i])) { childId[k] = childId[i]; childType[k] = childType[i]; childBirth[k] = childBirth[i]; childTgt[k] = childTgt[i]; k++;
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
        if (C.SAVE_NO_BID == 1 && !saveDone && round - saveWaitRound <= 2) { saveBidSkipped++; return; }   // Iteration 49 dose (a): the bank is for a centre
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
    /** Iteration 48: absorb what any friendly unit within sensor range is broadcasting. This is how a
     *  freshly captured centre learns anything at all -- it has no children and knows no sibling ids. */
    private void readNearbyFriendlies() throws GameActionException {
        int n = nearby.length; if (n == 0) return;
        int start = (round * C.HANDOFF_READS) % n, reads = 0;
        for (int k = 0; k < n && reads < C.HANDOFF_READS; k++) {
            RobotInfo r = nearby[(start + k) % n];
            if (r.team != us || r.type == RobotType.ENLIGHTENMENT_CENTER) continue;
            if (!rc.canGetFlag(r.ID)) continue;
            int f = rc.getFlag(r.ID); reads++; nearbyReads++;
            int t = Comms.type(f);
            if (t != Comms.IDLE && t != Comms.ORDER && t != Comms.STATUS) absorb(f, loc);
        }
    }

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
        int ty = Comms.type(f);
        if (ty == Comms.NEUTRAL_EC) { heardNeutral++; MapLocation l = Comms.loc(f, ref); for (int i = MapState.nOwn; --i >= 0;) if (MapState.ownEC[i].equals(l)) { refusedNeutral++; break; } }
        else if (ty == Comms.OWN_EC) heardOwn++;
        else if (ty == Comms.OWN_EC_ID) heardOwnId++;
        else if (ty == Comms.ENEMY_EC) heardEnemy++;
        else if (ty == Comms.FLIP_INTENT) { flipIntents++; presume(Comms.loc(f, ref)); return; }
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
        if (round == pendingOrderRound || round + 1 == pendingOrderRound) { f = pendingOrder; orderRounds++; }
        // Iteration 36's mechanism, restored as half of Iteration 37: a captured centre puts its own
        // location on its flag, so siblings call addOwnEC and stop treating it as neutral. Alone it was
        // rejected at 45.1%; the cap raise is only meaningful if extra capturers go to distinct REAL targets.
        else if (birth > 1 && (round / 3) % 3 == 2) f = Comms.encode(Comms.OWN_EC, 0, loc);
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
