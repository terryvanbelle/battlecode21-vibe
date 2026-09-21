package g_iter11;

import battlecode.common.*;

/** Entry point. Picks the controller for this robot's type and runs it forever. */
public strictfp class RobotPlayer {
    public static void run(RobotController rc) {
        Robot r;
        switch (rc.getType()) {
            case ENLIGHTENMENT_CENTER: r = new EC(rc); break;
            case POLITICIAN:           r = new Politician(rc); break;
            case SLANDERER:            r = new Slanderer(rc); break;
            default:                   r = new Muckraker(rc); break;
        }
        r.loop();
    }
}
