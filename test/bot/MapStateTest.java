package bot;

import battlecode.common.MapLocation;
import java.lang.reflect.Field;

/** Map knowledge: the EC registry and the symmetry hypotheses. Bugs here are the expensive
 *  silent kind -- a neutral recorded twice, or dropped, changes every capture decision.
 *  Run via tools/unit-tests.sh */
public class MapStateTest {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL " + what); } }

    /** MapState is all static; reset it between cases. */
    static void reset() throws Exception {
        for (String f : new String[]{"nEnemy", "nNeutral", "nOwn", "nOwnId"}) set(f, 0);
        set("minX", -1); set("maxX", -1); set("minY", -1); set("maxY", -1); set("sym", 7);
    }
    static void set(String name, int v) throws Exception {
        Field f = MapState.class.getDeclaredField(name); f.setAccessible(true); f.setInt(null, v);
    }

    public static void main(String[] a) throws Exception {
        // --- the neutral registry
        reset();
        MapLocation p = new MapLocation(10, 10), q = new MapLocation(20, 20);
        check(MapState.addNeutralEC(p, 150), "first add is new");
        check(MapState.nNeutral == 1, "one neutral recorded");
        check(!MapState.addNeutralEC(p, 150), "adding the same tile again is not new");
        check(MapState.nNeutral == 1, "duplicates must not grow the list");
        check(MapState.neutralInf[0] == 150, "influence stored");

        // A centre we own can never be neutral again: centres are neutral only at the start of the
        // game. Without this the captured-centre broadcast ping-pongs against a stale scout report
        // and the centre keeps buying capturers for ground it already holds (131 aborts in one game).
        reset();
        MapLocation cap = new MapLocation(30, 30);
        check(MapState.addNeutralEC(cap, 200), "a neutral centre is recorded");
        MapState.claimOwn(cap, 1);
        // Iteration 49: claims are stamped; the newer sighting wins whoever relays it
        MapState.claimOwn(cap, 5);
        check(!MapState.claimEnemy(cap, 3) && !MapState.known(MapState.enemyEC, MapState.nEnemy, cap), "an older enemy claim is refused");
        check(!MapState.claimEnemy(cap, 5) && MapState.known(MapState.ownEC, MapState.nOwn, cap), "an equally old enemy claim is refused");
        check(MapState.claimEnemy(cap, 7) && MapState.known(MapState.enemyEC, MapState.nEnemy, cap) && !MapState.known(MapState.ownEC, MapState.nOwn, cap), "a newer enemy sighting overrides ownership");
        check(!MapState.claimOwn(cap, 6) && !MapState.known(MapState.ownEC, MapState.nOwn, cap), "an older own echo cannot resurrect ownership");
        check(MapState.claimOwn(cap, 8) && MapState.known(MapState.ownEC, MapState.nOwn, cap) && !MapState.known(MapState.enemyEC, MapState.nEnemy, cap), "a newer own sighting overrides the enemy listing");
        check(!MapState.addNeutralEC(cap, 100), "still never neutral again");
        check(MapState.stamp(1499) <= 63 && MapState.stamp(0) == 0, "stamps fit six bits over a whole game");
        check(MapState.nNeutral == 0, "capturing it drops it from the neutral list");
        check(!MapState.addNeutralEC(cap, 200), "a stale neutral report about it is refused");
        check(MapState.nNeutral == 0, "and does not put it back");
        reset();
        MapLocation p2 = new MapLocation(10, 10);
        check(MapState.addNeutralEC(p2, 150), "unrelated neutrals still register");
        check(MapState.nNeutral == 1, "one neutral recorded again");
        check(MapState.addNeutralEC(new MapLocation(20, 20), 150) && MapState.nNeutral == 2, "and a second");
        reset();
        check(MapState.addNeutralEC(p, 150), "first add is new (restored)");
        check(MapState.neutralInf[0] == 150, "influence stored (restored)");

        // a re-report with a better influence estimate should update in place, not duplicate
        MapState.addNeutralEC(p, 333);
        check(MapState.nNeutral == 1, "re-report must not duplicate");
        check(MapState.neutralInf[0] == 333, "re-report updates the influence");

        MapState.addNeutralEC(q, 70);
        check(MapState.nNeutral == 2, "second distinct neutral");
        MapState.removeNeutral(p);
        check(MapState.nNeutral == 1, "removal shrinks the list");
        check(MapState.neutralEC[0].equals(q), "the survivor is kept");
        check(MapState.neutralInf[0] == 70, "its influence travels with it");
        MapState.removeNeutral(new MapLocation(99, 99));
        check(MapState.nNeutral == 1, "removing an unknown tile is harmless");

        // the arrays are fixed-size: overflowing must not throw or corrupt
        reset();
        for (int i = 0; i < MapState.MAX_ECS + 6; i++) MapState.addNeutralEC(new MapLocation(i, 0), 100 + i);
        check(MapState.nNeutral <= MapState.MAX_ECS, "neutral count is capped at MAX_ECS, got " + MapState.nNeutral);

        // --- symmetry images (need bounds)
        reset();
        set("minX", 0); set("maxX", 31); set("minY", 0); set("maxY", 63);
        MapLocation l = new MapLocation(4, 10);
        check(MapState.image(l, 0).equals(new MapLocation(27, 53)), "rotation image");
        check(MapState.image(l, 1).equals(new MapLocation(27, 10)), "mirror-x keeps y");
        check(MapState.image(l, 2).equals(new MapLocation(4, 53)), "mirror-y keeps x");
        for (int h = 0; h < 3; h++)
            check(MapState.image(MapState.image(l, h), h).equals(l), "image is its own inverse for hyp " + h);

        // a corner maps to the opposite corner under rotation
        check(MapState.image(new MapLocation(0, 0), 0).equals(new MapLocation(31, 63)), "corner rotates to corner");

        // --- symCount tracks the live hypothesis bits
        reset();
        check(MapState.symCount() == 3, "all three hypotheses alive initially");
        set("sym", 1); check(MapState.symCount() == 1, "one hypothesis");
        set("sym", 5); check(MapState.symCount() == 2, "two hypotheses");
        set("sym", 0); check(MapState.symCount() == 0, "none left");

        // --- bounds
        reset();
        check(!MapState.boundsKnown(), "bounds unknown at the start");
        set("minX", 0); set("maxX", 31); set("minY", 0);
        check(!MapState.boundsKnown(), "three edges is not enough");
        set("maxY", 63);
        check(MapState.boundsKnown(), "four edges is enough");
        check(MapState.width() == 32 && MapState.height() == 64, "width/height from the edges");

        System.out.println(fails == 0 ? "OK MapStateTest" : fails + " FAILURES in MapStateTest");
        if (fails > 0) System.exit(1);
    }
}
