package bot;

import battlecode.common.*;

/** Base controller: the turn loop, bytecode monitor, per-robot RNG. */
public abstract strictfp class Robot {
    protected final RobotController rc;
    protected final Team us, them;
    protected final RobotType type;
    protected final int id;
    protected int round;
    protected MapLocation loc;
    protected int rng;                     // per-robot LCG state, seeded from the id: deterministic, uncorrelated with team

    // bytecode monitor
    private int bcMax = 0, bcOver = 0, bcNear = 0, turns = 0;
    private final int bcLimit;

    static final Direction[] DIRS = Direction.allDirections();  // includes CENTER last; never iterate for tie-breaks

    Robot(RobotController rc) {
        this.rc = rc; us = rc.getTeam(); them = us.opponent(); type = rc.getType(); id = rc.getID();
        rng = id * 1103515245 + 12345;
        bcLimit = type.bytecodeLimit;
    }

    /** Uniform-ish int in [0, n). */
    protected int nextInt(int n) { rng = rng * 1103515245 + 12345; return ((rng >>> 8) & 0x7fffffff) % n; }

    public final void loop() {
        while (true) {
            int r0 = rc.getRoundNum();
            round = r0; loc = rc.getLocation();
            try {
                turn();
            } catch (GameActionException e) { Debug.exception(e);
            } catch (Exception e) { Debug.exception(e); }
            // --- bytecode monitor: confirmed overrun if the round advanced under us; near-miss if within 10% of the limit
            int used = Clock.getBytecodeNum();
            turns++;
            if (rc.getRoundNum() != r0) { bcOver++; }
            else { if (used > bcMax) bcMax = used; if (used > bcLimit - bcLimit / 10) bcNear++; }
            if (turns % C.BC_REPORT_EVERY == 0 || bcOver > 0 && rc.getRoundNum() != r0)
                Debug.log("@bc t=" + type.ordinal() + " used=" + used + " max=" + bcMax + " near=" + bcNear + " over=" + bcOver);
            Clock.yield();
        }
    }

    /** One turn of this robot's logic. */
    protected abstract void turn() throws GameActionException;

    // ---- shared helpers ----
    protected boolean tryMove(Direction d) throws GameActionException {
        if (d == null || d == Direction.CENTER) return false;
        if (rc.canMove(d)) { rc.move(d); loc = rc.getLocation(); return true; }
        return false;
    }
}
