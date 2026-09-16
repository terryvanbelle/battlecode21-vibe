package bot;

import battlecode.common.*;

/** Iteration 0: wander with a persistent heading. */
public strictfp class Muckraker extends Robot {
    private Direction heading;
    Muckraker(RobotController rc) { super(rc); heading = DIRS[nextInt(8)]; }

    @Override protected void turn() throws GameActionException {
        if (!rc.isReady()) return;
        if (tryMove(heading)) return;
        // blocked: turn a random amount, consistently per robot
        heading = nextInt(2) == 0 ? heading.rotateLeft().rotateLeft() : heading.rotateRight().rotateRight();
        tryMove(heading);
    }
}
