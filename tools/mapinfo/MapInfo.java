package mapinfo;

import battlecode.common.*;
import battlecode.world.GameMapIO;
import battlecode.world.LiveMap;
import java.io.File;

/** Print one CSV row per built-in map: name,w,h,rounds,ecsPerTeam,neutralEcs,neutralInf,meanPass,minPass,symmetry,ecDist */
public class MapInfo {
    public static void main(String[] a) throws Exception {
        System.out.println("map,w,h,rounds,ecsA,ecsB,neutral,neutralInf,meanPass,swampFrac,symmetry,homeToEnemyDist,area");
        for (String name : a) {
            LiveMap m = GameMapIO.loadMap(name, new File("engine/maps"));
            int w = m.getWidth(), h = m.getHeight(); double[] p = m.getPassabilityArray();
            double sum = 0; int swamp = 0; for (double v : p) { sum += v; if (v < 0.5) swamp++; }
            int ea = 0, eb = 0, en = 0; long ninf = 0; MapLocation la = null, lb = null;
            for (RobotInfo r : m.getInitialBodies()) { if (r.team == Team.A) { ea++; la = r.location; } else if (r.team == Team.B) { eb++; lb = r.location; } else { en++; ninf += r.influence; } }
            boolean rot = true, mx = true, my = true;
            for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) { double v = p[x + y * w];
                if (v != p[(w - 1 - x) + (h - 1 - y) * w]) rot = false; if (v != p[(w - 1 - x) + y * w]) mx = false; if (v != p[x + (h - 1 - y) * w]) my = false; }
            String sym = (rot ? "R" : "") + (mx ? "X" : "") + (my ? "Y" : "");
            double d = la != null && lb != null ? Math.sqrt(la.distanceSquaredTo(lb)) : -1;
            System.out.printf("%s,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%s,%.1f,%d%n", name, w, h, m.getRounds(), ea, eb, en, ninf, sum / p.length, (double) swamp / p.length, sym, d, w * h);
        }
    }
}
