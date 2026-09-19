package bot;

import battlecode.common.MapLocation;

/** Distance and geometry helpers. cheb() decides every "am I there yet" and every
 *  navigation score, so an off-by-one here mis-routes the whole army.
 *  Run via tools/unit-tests.sh */
public class NavTest {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL " + what); } }

    public static void main(String[] a) {
        MapLocation o = new MapLocation(10000, 20000);

        check(Nav.cheb(o, o) == 0, "distance to self is zero");
        check(Nav.cheb(o, o.translate(3, 0)) == 3, "pure x");
        check(Nav.cheb(o, o.translate(0, -4)) == 4, "pure negative y");
        check(Nav.cheb(o, o.translate(3, 4)) == 4, "diagonal takes the larger axis");
        check(Nav.cheb(o, o.translate(-7, 5)) == 7, "sign does not matter");

        // symmetry and the triangle inequality, over a grid
        for (int dx = -12; dx <= 12; dx += 3)
            for (int dy = -12; dy <= 12; dy += 3) {
                MapLocation p = o.translate(dx, dy);
                check(Nav.cheb(o, p) == Nav.cheb(p, o), "cheb is symmetric at " + dx + "," + dy);
                check(Nav.cheb(o, p) == Math.max(Math.abs(dx), Math.abs(dy)), "cheb equals the max axis");
                for (int ex = -6; ex <= 6; ex += 6) {
                    MapLocation r = p.translate(ex, 0);
                    check(Nav.cheb(o, r) <= Nav.cheb(o, p) + Nav.cheb(p, r), "triangle inequality");
                }
            }

        // a king can always close the distance by exactly one per move: cheb is the true move count
        for (int dx = -5; dx <= 5; dx++)
            for (int dy = -5; dy <= 5; dy++) {
                if (dx == 0 && dy == 0) continue;
                MapLocation target = o.translate(dx, dy);
                MapLocation cur = o; int steps = 0;
                while (!cur.equals(target) && steps < 20) {
                    int sx = Integer.signum(target.x - cur.x), sy = Integer.signum(target.y - cur.y);
                    cur = cur.translate(sx, sy); steps++;
                }
                check(cur.equals(target), "walked to " + dx + "," + dy);
                check(steps == Nav.cheb(o, target), "cheb predicts the move count for " + dx + "," + dy);
            }

        System.out.println(fails == 0 ? "OK NavTest" : fails + " FAILURES in NavTest");
        if (fails > 0) System.exit(1);
    }
}
