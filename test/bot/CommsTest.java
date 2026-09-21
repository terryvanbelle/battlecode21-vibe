package bot;

import battlecode.common.MapLocation;

/** Plain-main test (no JUnit dependency): flag round trips including the mod-128 wrap. Run via tools/unit-tests.sh */
public class CommsTest {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL " + what); } }
    public static void main(String[] a) {
        int[] origins = {10000, 10050, 10100, 12345, 29999};
        for (int ox : origins) for (int oy : origins) {
            MapLocation reader = new MapLocation(ox + 7, oy + 40);
            for (int dx = 0; dx < 64; dx += 3) for (int dy = 0; dy < 64; dy += 5) {
                MapLocation target = new MapLocation(ox + dx, oy + dy);
                int f = Comms.encode(Comms.ENEMY_EC, 17, target);
                check(Comms.type(f) == Comms.ENEMY_EC, "type"); check(Comms.extra(f) == 17, "extra");
                check(Comms.loc(f, reader).equals(target), "loc " + target + " reader " + reader + " got " + Comms.loc(f, reader));
                check(f >= 0 && f <= 16777215, "range");
            }
        }
        // Iteration 49: FLIP_INTENT fits the 4-bit type field and round-trips its target like any located flag
        check(Comms.FLIP_INTENT <= 15 && Comms.FLIP_INTENT != Comms.OWN_EC && Comms.FLIP_INTENT != Comms.ORDER, "FLIP_INTENT is a distinct 4-bit type");
        check(Comms.ENEMY_EC_ECHO <= 15 && Comms.ENEMY_EC_ECHO != Comms.ENEMY_EC && Comms.ENEMY_EC_ECHO != Comms.FLIP_INTENT, "ENEMY_EC_ECHO is a distinct 4-bit type");
        { MapLocation rd = new MapLocation(12345, 23456), tg = new MapLocation(12345 + 40, 23456 - 20); int f = Comms.encode(Comms.FLIP_INTENT, 0, tg);
          check(Comms.type(f) == Comms.FLIP_INTENT && Comms.loc(f, rd).equals(tg), "FLIP_INTENT round trip"); }
        for (int inf = 0; inf < 5000; inf += 7) { int b = Comms.bucket(inf); check(Comms.unbucket(b) >= inf || b == 63, "bucket up " + inf + " -> " + b + " -> " + Comms.unbucket(b)); check(b <= 63, "bucket range"); }
        for (int inf = 50; inf <= 500; inf++) { int b = Comms.bucket8(inf); check(Comms.unbucket8(b) >= inf && Comms.unbucket8(b) - inf < 8, "bucket8 " + inf); }
        for (int x = 1; x < 700; x++) { int s = Econ.bestSize(x); check(s <= x, "bestSize<=budget " + x); if (x >= 21) check(s >= 21, "bestSize>=21 " + x); }
        System.out.println(fails == 0 ? "OK CommsTest" : fails + " FAILURES");
        if (fails > 0) System.exit(1);
    }
}
