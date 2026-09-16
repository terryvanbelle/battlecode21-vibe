package g_iter0;

import battlecode.common.*;

/** Iteration 0: build a single 1-influence muckraker, then idle. */
public strictfp class EC extends Robot {
    private boolean builtOne = false;
    EC(RobotController rc) { super(rc); }

    @Override protected void turn() throws GameActionException {
        if (!builtOne && rc.isReady()) {
            // pick the spawn tile with the highest passability (relative tie-break: towards map "centre" unknown yet, so passability then rng)
            Direction best = null; double bp = -1;
            for (int i = 8; --i >= 0;) {
                Direction d = DIRS[i];
                if (!rc.canBuildRobot(RobotType.MUCKRAKER, d, 1)) continue;
                double p = rc.sensePassability(rc.adjacentLocation(d));
                if (p > bp || (p == bp && nextInt(2) == 0)) { bp = p; best = d; }
            }
            if (best != null) { rc.buildRobot(RobotType.MUCKRAKER, best, 1); builtOne = true; Debug.log("@spawn t=3 inf=1 dir=" + best); }
        }
    }
}
