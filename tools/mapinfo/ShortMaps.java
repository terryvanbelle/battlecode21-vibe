package mapinfo;

import battlecode.world.GameMapIO;
import battlecode.world.LiveMap;
import java.io.File;

/** Write copies of built-in maps with a shorter round limit for smoke tests: ShortMaps <rounds> <outDir> <map>... */
public class ShortMaps {
    public static void main(String[] a) throws Exception {
        int rounds = Integer.parseInt(a[0]); File out = new File(a[1]); out.mkdirs();
        for (int i = 2; i < a.length; i++) {
            LiveMap m = GameMapIO.loadMap(a[i], new File("engine/maps"));
            LiveMap s = new LiveMap(m.getWidth(), m.getHeight(), m.getOrigin(), m.getSeed(), rounds, a[i] + "_s" + rounds, m.getInitialBodies(), m.getPassabilityArray());
            GameMapIO.writeMap(s, out);
        }
        System.out.println("wrote " + (a.length - 2) + " maps with " + rounds + " rounds to " + out);
    }
}
