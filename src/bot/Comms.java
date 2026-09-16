package bot;

import battlecode.common.*;

/**
 * 24-bit flag protocol.  layout: [type:4][extra:6][x&127:7][y&127:7]
 *
 * A location is sent modulo 128 on each axis and decoded to the candidate
 * nearest the READER, which is unique while the map is at most 64 wide.
 * `extra` carries 6 bits of type-specific payload (an influence bucket, an edge
 * id, a role).
 */
public final class Comms {
    private Comms() {}

    public static final int IDLE = 0;
    public static final int ENEMY_EC = 1;     // extra = influence bucket
    public static final int NEUTRAL_EC = 2;   // extra = influence bucket
    public static final int MAP_EDGE = 3;     // extra = edge id (0 minX,1 maxX,2 minY,3 maxY); loc = a tile ON that edge
    public static final int ENEMY_UNIT = 4;   // extra = 0 politician, 1 slanderer, 2 muckraker
    public static final int ORDER = 5;        // extra = role; loc = target
    public static final int STATUS = 6;       // extra = bits: [sym:2][bounds known:1][danger:1] ; loc = home EC
    public static final int OWN_EC = 7;       // extra = influence bucket
    public static final int OWN_EC_ID = 8;    // payload20 = robot id of a friendly EC (so ECs can read each other's flags)

    public static int encodeRaw(int type, int payload20) { return (type << 20) | (payload20 & 0xFFFFF); }
    public static int payload(int f) { return f & 0xFFFFF; }

    public static int encode(int type, int extra, MapLocation l) {
        return (type << 20) | ((extra & 63) << 14) | ((l.x & 127) << 7) | (l.y & 127);
    }
    public static int encode(int type, int extra) { return (type << 20) | ((extra & 63) << 14); }
    public static int type(int f) { return f >>> 20; }
    public static int extra(int f) { return (f >>> 14) & 63; }

    /** Decode the location in flag f relative to the reader's position. */
    public static MapLocation loc(int f, MapLocation me) {
        int xm = (f >>> 7) & 127, ym = f & 127;
        int x = (me.x & ~127) | xm, y = (me.y & ~127) | ym;
        if (x - me.x > 64) x -= 128; else if (me.x - x > 64) x += 128;
        if (y - me.y > 64) y -= 128; else if (me.y - y > 64) y += 128;
        return new MapLocation(x, y);
    }

    /** Influence -> 6-bit bucket, roughly logarithmic (for enemy ECs, which can hold thousands). unbucket rounds UP. */
    public static int bucket(int influence) {
        if (influence < 32) return influence;
        int b = 32; int v = 32;
        while (v * 5 / 4 + 1 <= influence && b < 63) { v = v * 5 / 4 + 1; b++; }
        return b;
    }
    public static int unbucket(int b) {
        if (b < 32) return b;
        int v = 32; for (int i = 32; i < b; i++) v = v * 5 / 4 + 1;
        return v * 5 / 4 + 1;   // upper edge of the bucket: never under-estimate a target
    }
    /** Neutral EC influence is 50..500: linear /8 buckets, unbucket rounds up to the bucket's top. */
    public static int bucket8(int influence) { int b = influence / 8; return b > 63 ? 63 : b; }
    public static int unbucket8(int b) { return b * 8 + 7; }
}
