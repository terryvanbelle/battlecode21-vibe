package g_iter2;

import battlecode.common.*;

/**
 * Movement. Cooldown is charged by the passability of the tile the robot is
 * standing on when it acts, so stepping onto swamp costs on the NEXT action.
 * The greedy step minimises  moves_remaining(newLoc) + 1/passability(newLoc)
 * (both in "turns"), forbids the last few positions (oscillation guard) and,
 * when no step reduces the remaining distance for several turns, switches to
 * bug wall-following with a per-robot handedness until progress resumes.
 */
public final class Nav {
    private final RobotController rc;
    private final Robot bot;
    private MapLocation target;
    private final MapLocation[] recent = new MapLocation[6]; private int recentI = 0;
    private int stuck = 0;                 // turns without distance progress
    private int bestDist = 1 << 30;        // best Chebyshev distance reached toward the current target
    private boolean bugging = false; private Direction bugDir; private final boolean rightHanded;

    Nav(RobotController rc, Robot bot) { this.rc = rc; this.bot = bot; rightHanded = bot.nextInt(2) == 0; }

    static int cheb(MapLocation a, MapLocation b) { int dx = a.x - b.x, dy = a.y - b.y; if (dx < 0) dx = -dx; if (dy < 0) dy = -dy; return dx > dy ? dx : dy; }

    public void setTarget(MapLocation t) {
        if (t == null) { target = null; return; }
        if (target == null || !target.equals(t)) { target = t; bestDist = 1 << 30; stuck = 0; bugging = false; }
    }
    public MapLocation target() { return target; }

    private boolean isRecent(MapLocation l) { for (int i = 6; --i >= 0;) if (recent[i] != null && recent[i].equals(l)) return true; return false; }
    private void remember(MapLocation l) { recent[recentI] = l; recentI = (recentI + 1) % 6; }

    /** One step toward the target. Returns true if moved. */
    public boolean step() throws GameActionException {
        if (target == null || !rc.isReady()) return false;
        MapLocation me = rc.getLocation();
        int d0 = cheb(me, target);
        if (d0 == 0) return false;
        if (bugging) return bugStep(me);
        Direction best = null; double bestScore = 1e9;
        for (int i = 8; --i >= 0;) {
            Direction d = Robot.DIRS[i];
            if (!rc.canMove(d)) continue;
            MapLocation n = me.add(d);
            if (isRecent(n)) continue;
            double score = cheb(n, target) + 1.0 / rc.sensePassability(n);
            // relative tie-break: prefer the step whose euclidean distance to target is smaller
            score += n.distanceSquaredTo(target) * 1e-4;
            if (score < bestScore) { bestScore = score; best = d; }
        }
        if (best == null) { stuck++; if (stuck >= 2) { bugging = true; bugDir = me.directionTo(target); } return false; }
        MapLocation n = me.add(best);
        int d1 = cheb(n, target);
        if (d1 < bestDist) { bestDist = d1; stuck = 0; } else if (++stuck >= 4) { bugging = true; bugDir = me.directionTo(target); }
        remember(me);
        rc.move(best);
        return true;
    }

    /** Bug: walk with the wall on one side. Leaves bug mode once closer than ever before. */
    private boolean bugStep(MapLocation me) throws GameActionException {
        Direction toT = me.directionTo(target);
        // try to head toward the target first, else rotate away from the wall
        Direction d = toT;
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(d) && !isRecent(me.add(d))) {
                MapLocation n = me.add(d);
                remember(me); rc.move(d);
                int d1 = cheb(n, target);
                if (d1 < bestDist) { bestDist = d1; stuck = 0; bugging = false; }
                bugDir = d;
                return true;
            }
            d = rightHanded ? d.rotateRight() : d.rotateLeft();
        }
        // completely boxed in: allow a recent tile
        d = toT;
        for (int i = 0; i < 8; i++) { if (rc.canMove(d)) { rc.move(d); return true; } d = rightHanded ? d.rotateRight() : d.rotateLeft(); }
        stuck++;
        return false;
    }

    /** Move away from a point (flee). */
    public boolean fleeFrom(MapLocation threat) throws GameActionException {
        if (!rc.isReady()) return false;
        MapLocation me = rc.getLocation();
        Direction best = null; double bestScore = -1e9;
        for (int i = 8; --i >= 0;) {
            Direction d = Robot.DIRS[i];
            if (!rc.canMove(d)) continue;
            MapLocation n = me.add(d);
            double score = n.distanceSquaredTo(threat) - 2.0 / rc.sensePassability(n);
            if (score > bestScore) { bestScore = score; best = d; }
        }
        if (best == null) return false;
        rc.move(best); return true;
    }
}
