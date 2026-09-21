package replaydump;

import battlecode.schema.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Battlecode 2021 replay (.bc21) -> text. The project's microscope.
 *
 * Reads the gzipped FlatBuffers GameWrapper, reconstructs every robot's state
 * round by round from the engine's own event stream, and prints only
 * engine-level facts. Everything a robot did is in the stream: spawns (with
 * influence paid), moves, deaths, EMPOWER/EXPOSE/PLACE_BID/SET_FLAG actions,
 * CHANGE_INFLUENCE / CHANGE_CONVICTION deltas, CAMOUFLAGE, CHANGE_TEAM, plus per
 * robot bytecodes used and each team's vote/bidder/buff totals per round.
 *
 * Usage: ReplayDump <file.bc21> [flags]
 *   --every N        team aggregate line every N rounds (default 50; 0 = off)
 *   --from R --to R  print every event in this round window
 *   --robot ID       per-turn track of one robot (state after each round)
 *   --map N          ASCII board every N rounds
 *   --map-at R       ASCII board once at round R (repeatable)
 *   --logs PATTERN   print robot System.out lines matching PATTERN (regex),
 *                    inside the --from/--to window (default: whole game)
 *   --logs-team A|B  restrict --logs to one team
 *   --metrics        CSV of per-round team aggregates instead of the narrative
 *   --bytecode       per-type bytecode summary (max used, rounds over limit)
 *   --quiet          suppress aggregates (use with --map or --logs)
 *   --speeches       per team: speeches, conviction spent, share landing on enemies / enemy ECs / friendlies, empty speeches
 *   --hits           every enemy speech that reaches an EC: attacker conviction, distance, n (robots
 *                    sharing the speech), wall (that EC's own units on its 8 adjacent tiles), influence before -> after
 *
 * Team ids in the file: 0 neutral, 1 = A, 2 = B. Locations are absolute (the
 * map origin is random per map); the board is drawn relative to minCorner.
 */
public class ReplayDump {
    // ---- flags ----
    static int every = 50, mapEvery = 0, fromRound = -1, toRound = -1, trackId = -1;
    static boolean metrics = false, quiet = false, bytecodeSummary = false;
    static boolean speeches = false; static long[] spSpent = new long[3], spEnemy = new long[3], spFriend = new long[3], spEmpty = new long[3], spCount = new long[3], spEmptyCount = new long[3], spEnemyEC = new long[3];
    static boolean hits = false; static final List<int[]> pendingHits = new ArrayList<>(); static final Map<Integer, Integer> ecInfStart = new HashMap<>();
    static Pattern logPat = null; static int logsTeam = -1;
    static TreeSet<Integer> mapAt = new TreeSet<>();

    // ---- robot state ----
    static final class Robot {
        int id, team, x, y, influence, conviction, flag, spawnRound;
        byte type; boolean alive = true;
        int bytecodes, bcOver;           // last round's bytecodes; rounds over limit
        int empowers, exposes, moves, bidsPlaced; long bidTotal;
    }
    static final String[] TYPE = {"EC", "POL", "SLA", "MUC"};
    static final int[] LIMIT = {20000, 15000, 7500, 15000};
    static final char[][] GLYPH = {{'N','n','n','n'}, {'E','P','S','M'}, {'e','p','s','m'}};
    static Map<Integer, Robot> bots = new HashMap<>();
    static int minX, minY, width, height, maxRounds;
    static double[] pass;
    static String mapName = "?";
    static String[] teamName = {"neutral", "A", "B"}, teamPkg = {"", "?", "?"};

    // ---- per-team cumulative counters ----
    static int[] votes = new int[3], buffs = new int[3];
    static long[] spawned = new long[3], spawnInfluence = new long[3], died = new long[3],
                  empowers = new long[3], exposes = new long[3], bidsPlaced = new long[3],
                  bidInfluence = new long[3], votesWonInfluence = new long[3], conversions = new long[3],
                  camouflages = new long[3], embezzled = new long[3], moves = new long[3],
                  bcOverRounds = new long[3];
    static int[][] bcMax = new int[3][4];
    // navigation statistics (--navstats): A-B-A oscillations, coverage of visited tiles, first contact with an enemy EC
    static boolean navStats = false;
    static int threatTeam = 0;   // --threat: whose ECs to watch
    static int knowTeam = 0;     // --knowledge: whose map knowledge to reconstruct
    static java.util.Set<Long> everSeen = new java.util.HashSet<>();   // neutral centres this team has sensed
    static int neutralAtStart = 0;   // the true denominator, read from the map at round 0
    /** Sensor radius^2 by type index: EC, POL, SLA, MUC (battlecode 2021 RobotType). */
    static final int[] SENSOR = {40, 25, 20, 30};
    static long[] unitsLong = new long[3], unitsIdle = new long[3], unitMoves = new long[3]; static int lastRound = 0;
    static long[] aba = new long[3]; static boolean[][] visited; static int[] firstContact = {-1, -1, -1}; static long[] swampMoves = new long[3];
    static Map<Integer, int[]> prev2 = new HashMap<>();   // id -> {x2,y2,x1,y1}
    static long[][] bcOverByType = new long[3][4];
    static int[] winnerByMatch = new int[0];

    public static void main(String[] args) throws Exception {
        if (args.length < 1) { System.err.println("usage: ReplayDump <file.bc21> [flags]"); System.exit(2); }
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--every": every = Integer.parseInt(args[++i]); break;
                case "--map": mapEvery = Integer.parseInt(args[++i]); break;
                case "--map-at": mapAt.add(Integer.parseInt(args[++i])); break;
                case "--from": fromRound = Integer.parseInt(args[++i]); break;
                case "--to": toRound = Integer.parseInt(args[++i]); break;
                case "--robot": trackId = Integer.parseInt(args[++i]); break;
                case "--logs": logPat = Pattern.compile(args[++i]); break;
                case "--logs-team": logsTeam = args[++i].equals("A") ? 1 : 2; break;
                case "--metrics": metrics = true; quiet = true; every = Math.max(every, 1); break;
                case "--bytecode": bytecodeSummary = true; break;
                case "--quiet": quiet = true; break;
                case "--hits": hits = true; quiet = true; break;
                case "--speeches": speeches = true; quiet = true; break;
                case "--navstats": navStats = true; break;
                case "--threat": threatTeam = args[++i].equals("A") ? 1 : 2; quiet = true; break;
                case "--knowledge": knowTeam = args[++i].equals("A") ? 1 : 2; quiet = true; break;
                default: System.err.println("unknown flag " + args[i]); System.exit(2);
            }
        }
        if (metrics && every == 50) every = 10;
        byte[] raw = Files.readAllBytes(new File(args[0]).toPath());
        if ((raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b) {
            try (GZIPInputStream g = new GZIPInputStream(new ByteArrayInputStream(raw)); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
                byte[] b = new byte[1 << 16]; int n; while ((n = g.read(b)) > 0) o.write(b, 0, n); raw = o.toByteArray();
            }
        }
        GameWrapper gw = GameWrapper.getRootAsGameWrapper(ByteBuffer.wrap(raw));
        for (int i = 0; i < gw.eventsLength(); i++) {
            EventWrapper ew = gw.events(i);
            switch (ew.eType()) {
                case Event.GameHeader: onGameHeader((GameHeader) ew.e(new GameHeader())); break;
                case Event.MatchHeader: onMatchHeader((MatchHeader) ew.e(new MatchHeader())); break;
                case Event.Round: onRound((Round) ew.e(new Round())); break;
                case Event.MatchFooter: onMatchFooter((MatchFooter) ew.e(new MatchFooter())); break;
                case Event.GameFooter: break;
                default: break;
            }
        }
    }

    // ------------------------------------------------------------------ headers
    static void onGameHeader(GameHeader h) {
        for (int i = 0; i < h.teamsLength(); i++) {
            TeamData t = h.teams(i);
            if (t.teamID() >= 1 && t.teamID() <= 2) { teamName[t.teamID()] = t.name(); teamPkg[t.teamID()] = t.packageName(); }
        }
        if (!metrics) System.out.printf("GAME spec=%s  A=%s (%s)  B=%s (%s)%n", h.specVersion(), teamName[1], teamPkg[1], teamName[2], teamPkg[2]);
    }

    static void onMatchHeader(MatchHeader mh) {
        GameMap m = mh.map();
        mapName = m.name(); maxRounds = mh.maxRounds();
        minX = m.minCorner().x(); minY = m.minCorner().y();
        width = m.maxCorner().x() - minX; height = m.maxCorner().y() - minY;
        pass = new double[m.passabilityLength()];
        for (int i = 0; i < pass.length; i++) pass[i] = m.passability(i);
        bots.clear();
        Arrays.fill(votes, 0); Arrays.fill(buffs, 0);
        visited = new boolean[3][width * height]; prev2.clear();
        SpawnedBodyTable sb = m.bodies();
        spawnBodies(sb, 0);
        if (knowTeam != 0) {
            everSeen.clear();
            // The denominator is the number of centres that were neutral AT THE START. Deriving it from
            // the live board (still-neutral + ever-sensed) undercounts every centre an opponent took
            // before we ever saw it, which flatters our share (corrected 2026-09-21).
            neutralAtStart = 0;
            for (Robot r : bots.values()) if (r.alive && r.type == 0 && r.team == 0) neutralAtStart++;
            System.out.println("round,neutralAtStart,neutralCentresSensed,stillNeutral,ourCentres"); return;
        }
        if (threatTeam != 0) { System.out.println("round,ourECs,enemyMuckWithin3tiles,enemyMuckInSensor,enemyPolInSensor"); return; }
        if (metrics) { printMetricsHeader(); return; }
        int[] ecs = new int[3]; long[] ecInf = new long[3];
        for (Robot r : bots.values()) { ecs[r.team]++; ecInf[r.team] += r.influence; }
        System.out.printf("MATCH map=%s size=%dx%d origin=(%d,%d) maxRounds=%d  ECs A=%d B=%d neutral=%d (neutral influence %d)%n",
            mapName, width, height, minX, minY, maxRounds, ecs[1], ecs[2], ecs[0], ecInf[0]);
        double sum = 0, mn = 1, mx = 0; for (double p : pass) { sum += p; mn = Math.min(mn, p); mx = Math.max(mx, p); }
        System.out.printf("  passability mean=%.3f min=%.2f max=%.2f   symmetry=%s%n", sum / pass.length, mn, mx, detectSymmetry());
        for (Robot r : sortedBots()) if (r.type == 0)
            System.out.printf("  EC #%d team=%s at (%d,%d) rel(%d,%d) influence=%d%n", r.id, teamName[r.team], r.x, r.y, r.x - minX, r.y - minY, r.influence);
    }

    static String detectSymmetry() {
        boolean rot = true, hor = true, ver = true;
        for (int x = 0; x < width && (rot || hor || ver); x++) for (int y = 0; y < height; y++) {
            double p = pass[x + y * width];
            if (Math.abs(p - pass[(width - 1 - x) + (height - 1 - y) * width]) > 1e-9) rot = false;
            if (Math.abs(p - pass[(width - 1 - x) + y * width]) > 1e-9) hor = false;   // mirror across vertical axis
            if (Math.abs(p - pass[x + (height - 1 - y) * width]) > 1e-9) ver = false;  // mirror across horizontal axis
        }
        // ECs must map onto ECs too; passability alone can be ambiguous on bland maps
        StringBuilder s = new StringBuilder();
        if (rot) s.append("rotation ");
        if (hor) s.append("mirror-x ");
        if (ver) s.append("mirror-y ");
        return s.length() == 0 ? "none?" : s.toString().trim();
    }

    /** navstats: units (not ECs) that lived >= 100 rounds; how many made < 5 moves in that time. */
    static void tallyUnit(Robot r, int round) {
        if (r.type == 0 || r.team == 0 || round - r.spawnRound < 100) return;
        unitsLong[r.team]++; unitMoves[r.team] += r.moves; if (r.moves < 5) unitsIdle[r.team]++;
    }
    static int d2(Robot a, Robot b) { return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y); }

    static void spawnBodies(SpawnedBodyTable sb, int round) {
        if (sb == null) return;
        VecTable locs = sb.locs();
        for (int i = 0; i < sb.robotIDsLength(); i++) {
            Robot r = new Robot();
            r.id = sb.robotIDs(i); r.team = sb.teamIDs(i); r.type = sb.types(i);
            r.x = locs.xs(i); r.y = locs.ys(i); r.influence = sb.influences(i);
            r.conviction = r.type == 3 ? (int) Math.ceil(0.7 * r.influence) : r.influence;
            r.spawnRound = round;
            bots.put(r.id, r);
            markVisit(r);
            spawned[r.team]++; spawnInfluence[r.team] += r.influence;
            if (inWindow(round)) System.out.printf("  r%d SPAWN %s%n", round, desc(r));
        }
    }

    // ------------------------------------------------------------------ rounds
    static void onRound(Round rd) {
        int round = rd.roundID();
        lastRound = round;
        if (hits) { ecInfStart.clear(); for (Robot b : bots.values()) if (b.type == 0) ecInfStart.put(b.id, b.influence); }
        // team info
        for (int i = 0; i < rd.teamIDsLength(); i++) {
            int t = rd.teamIDs(i);
            if (rd.teamVotes(i) > 0) { votes[t]++; }
            if (i < rd.teamNumBuffsLength()) buffs[t] = rd.teamNumBuffs(i);
        }
        // moves
        VecTable ml = rd.movedLocs();
        for (int i = 0; i < rd.movedIDsLength(); i++) {
            Robot r = bots.get(rd.movedIDs(i));
            if (r != null) {
                int[] pv = prev2.get(r.id);
                if (pv != null && pv[0] == ml.xs(i) && pv[1] == ml.ys(i)) aba[r.team]++;   // returned to where it was two moves ago
                if (pv == null) { pv = new int[4]; prev2.put(r.id, pv); }
                pv[0] = pv[2]; pv[1] = pv[3]; pv[2] = r.x; pv[3] = r.y;
                r.x = ml.xs(i); r.y = ml.ys(i); r.moves++; moves[r.team]++; markVisit(r);
                if (navStats) { int px = r.x - minX, py = r.y - minY; if (px >= 0 && py >= 0 && px < width && py < height && pass[py * width + px] < 0.35) swampMoves[r.team]++; }
                if (navStats && firstContact[r.team] < 0 && r.team > 0) for (Robot e : bots.values()) if (e.type == 0 && e.team != r.team && e.team > 0 && (e.x - r.x) * (e.x - r.x) + (e.y - r.y) * (e.y - r.y) <= 30) { firstContact[r.team] = round; break; }
                if (r.id == trackId && inWindow(round)) System.out.printf("  r%d MOVE #%d -> (%d,%d)%n", round, r.id, r.x - minX, r.y - minY); }
        }
        // spawns
        spawnBodies(rd.spawnedBodies(), round);
        // actions
        for (int i = 0; i < rd.actionIDsLength(); i++) {
            int id = rd.actionIDs(i); byte a = rd.actions(i); int tgt = rd.actionTargets(i);
            Robot r = bots.get(id);
            int team = r == null ? 0 : r.team;
            switch (a) {
                case Action.EMPOWER: empowers[team]++; if (r != null) r.empowers++;
                    if (speeches && r != null && r.team > 0 && r.type == 1) {
                        int conv = Math.max(0, r.conviction - 10); int n = 0, en = 0, fr = 0, ec = 0;
                        for (Robot b : bots.values()) { if (b == r || !b.alive || b.spawnRound >= round || d2(b, r) > tgt) continue; n++; if (b.team == r.team) fr++; else { en++; if (b.type == 0 && b.team > 0) ec++; } }
                        spCount[r.team]++; spSpent[r.team] += r.conviction;
                        if (n == 0) { spEmpty[r.team] += conv; spEmptyCount[r.team]++; }
                        else { spEnemy[r.team] += (long) conv * en / n; spFriend[r.team] += (long) conv * fr / n; spEnemyEC[r.team] += (long) conv * ec / n; }
                    }
                    if (hits && r != null) for (Robot e : bots.values()) if (e.type == 0 && e.team > 0 && e.team != r.team && d2(e, r) <= tgt) {
                        int n = 0, wall = 0;
                        for (Robot b : bots.values()) { if (b != r && b.alive && b.spawnRound < round && d2(b, r) <= tgt) n++; if (b.team == e.team && b.type != 0 && b.alive && Math.max(Math.abs(b.x - e.x), Math.abs(b.y - e.y)) == 1) wall++; }
                        pendingHits.add(new int[]{round, r.team, r.conviction, tgt, d2(e, r), n, e.id, ecInfStart.getOrDefault(e.id, e.influence), wall});
                    }
                    if (inWindow(round)) System.out.printf("  r%d EMPOWER %s radius2=%d%n", round, desc(r), tgt); break;
                case Action.EXPOSE: exposes[team]++; if (r != null) r.exposes++;
                    if (inWindow(round)) System.out.printf("  r%d EXPOSE %s -> #%d %s%n", round, desc(r), tgt, desc(bots.get(tgt))); break;
                case Action.PLACE_BID: bidsPlaced[team]++; bidInfluence[team] += tgt; if (r != null) { r.bidsPlaced++; r.bidTotal += tgt; }
                    if (inWindow(round) || (r != null && r.id == trackId && inWindow(round))) System.out.printf("  r%d BID %s amount=%d%n", round, desc(r), tgt); break;
                case Action.SET_FLAG: if (r != null) r.flag = tgt;
                    if (r != null && r.id == trackId && inWindow(round)) System.out.printf("  r%d FLAG #%d = %d (0x%06x)%n", round, id, tgt, tgt); break;
                case Action.SPAWN_UNIT: break; // the spawn itself is in spawnedBodies
                case Action.CHANGE_TEAM: conversions[team]++;
                    if (inWindow(round)) System.out.printf("  r%d CONVERT %s -> new id #%d%n", round, desc(r), tgt); break;
                case Action.CHANGE_INFLUENCE: if (r != null) r.influence += tgt; break;
                case Action.CHANGE_CONVICTION: if (r != null) r.conviction += tgt; break;
                case Action.CAMOUFLAGE: camouflages[team]++; if (r != null) r.type = 1;
                    if (inWindow(round)) System.out.printf("  r%d CAMOUFLAGE %s%n", round, desc(r)); break;
                case Action.EMBEZZLE: embezzled[team]++; break;
                case Action.DIE_EXCEPTION:
                    System.out.printf("  r%d DIE_EXCEPTION %s%n", round, desc(r)); break;
                default: break;
            }
        }
        // bytecodes
        for (int i = 0; i < rd.bytecodeIDsLength(); i++) {
            Robot r = bots.get(rd.bytecodeIDs(i));
            if (r == null) continue;
            r.bytecodes = rd.bytecodesUsed(i);
            int t = Math.min(r.type, 3);
            bcMax[r.team][t] = Math.max(bcMax[r.team][t], r.bytecodes);
            if (r.bytecodes >= LIMIT[t]) { r.bcOver++; bcOverRounds[r.team]++; bcOverByType[r.team][t]++;
                if (inWindow(round) || bytecodeSummary) System.out.printf("  r%d BYTECODE-OVERRUN %s used=%d limit=%d%n", round, desc(r), r.bytecodes, LIMIT[t]); }
        }
        // deaths
        for (int i = 0; i < rd.diedIDsLength(); i++) {
            Robot r = bots.get(rd.diedIDs(i));
            if (r != null) { r.alive = false; died[r.team]++; if (navStats) tallyUnit(r, round);
                if (inWindow(round)) System.out.printf("  r%d DIED %s (lived %d rounds)%n", round, desc(r), round - r.spawnRound);
                bots.remove(r.id); }
        }
        // logs
        if (logPat != null && inWindowOrAll(round)) {
            String logs = rd.logs();
            if (logs != null && !logs.isEmpty()) for (String line : logs.split("\n")) {
                if (logsTeam == 1 && !line.startsWith("[A:")) continue;
                if (logsTeam == 2 && !line.startsWith("[B:")) continue;
                if (logPat.matcher(line).find()) System.out.println("  LOG " + line);
            }
        }
        if (trackId >= 0 && inWindowOrAll(round)) { Robot r = bots.get(trackId); if (r != null) System.out.printf("  r%d TRACK %s bc=%d%n", round, desc(r), r.bytecodes); }
        if (hits && !pendingHits.isEmpty()) {
            for (int[] h : pendingHits) { Robot e = bots.get(h[6]); String after = e == null ? "CONVERTED" : Integer.toString(e.influence); int loss = e == null ? h[7] : h[7] - e.influence;
                System.out.printf("  r%d HIT by %s conv=%d r2=%d d2=%d n=%d wall=%d ec#%d inf %d -> %s loss=%d ratio=%.2f%n", h[0], teamName[h[1]], h[2], h[3], h[4], h[5], h[8], h[6], h[7], after, loss, h[2] > 10 ? (double) loss / (h[2] - 10) : 0.0); }
            pendingHits.clear();
        }
        if (knowTeam != 0) { trackKnowledge(); if (round % 50 == 0) printKnowledgeRow(round); return; }
        if (threatTeam != 0) { if (round % 25 == 0) printThreatRow(round); return; }
        if (metrics) { if (round % every == 0) printMetricsRow(round); return; }
        if (!quiet && every > 0 && round % every == 0) printAggregate(round);
        if ((mapEvery > 0 && round % mapEvery == 0) || mapAt.contains(round)) printBoard(round);
    }

    static void onMatchFooter(MatchFooter f) {
        int w = f.winner();
        if (metrics) { if (f.totalRounds() % every != 0) printMetricsRow(f.totalRounds()); System.out.printf("# winner=%s rounds=%d%n", teamName[w], f.totalRounds()); return; }
        if (every <= 0 || f.totalRounds() % every != 0) printAggregate(f.totalRounds());
        System.out.printf("RESULT winner=%s (%s) after %d rounds  votes A=%d B=%d%n", w == 1 ? "A" : w == 2 ? "B" : "?", teamName[w], f.totalRounds(), votes[1], votes[2]);
        if (navStats) { for (Robot r : bots.values()) tallyUnit(r, lastRound); printNavStats(); }
        if (speeches) for (int t = 1; t <= 2; t++) System.out.printf("  speeches %s: n=%d spent=%d toEnemy=%d (%.0f%%) toEnemyEC=%d (%.0f%%) toFriend=%d (%.0f%%) empty=%d speeches (%d conv)%n", teamName[t], spCount[t], spSpent[t], spEnemy[t], spSpent[t] > 0 ? 100.0 * spEnemy[t] / spSpent[t] : 0, spEnemyEC[t], spSpent[t] > 0 ? 100.0 * spEnemyEC[t] / spSpent[t] : 0, spFriend[t], spSpent[t] > 0 ? 100.0 * spFriend[t] / spSpent[t] : 0, spEmptyCount[t], spEmpty[t]);
        if (bytecodeSummary) for (int t = 1; t <= 2; t++) {
            StringBuilder s = new StringBuilder("  bytecode " + teamName[t] + ":");
            for (int k = 0; k < 4; k++) s.append(String.format(" %s max=%d over=%d", TYPE[k], bcMax[t][k], bcOverByType[t][k]));
            System.out.println(s);
        }
    }

    static void markVisit(Robot r) {
        if (visited == null) return;
        int x = r.x - minX, y = r.y - minY;
        if (x >= 0 && x < width && y >= 0 && y < height) visited[r.team][x + y * width] = true;
    }
    static void printNavStats() {
        for (int t = 1; t <= 2; t++) {
            int cov = 0; for (boolean b : visited[t]) if (b) cov++;
            System.out.printf("  nav %s: moves=%d aba=%d (%.1f%%) ontoSwamp=%d (%.1f%%) coverage=%.1f%% firstEnemyECContact=r%d unitsLived100=%d idle(<5 moves)=%d meanMoves=%.1f%n", teamName[t], moves[t], aba[t], moves[t] > 0 ? 100.0 * aba[t] / moves[t] : 0, swampMoves[t], moves[t] > 0 ? 100.0 * swampMoves[t] / moves[t] : 0, 100.0 * cov / (width * height), firstContact[t], unitsLong[t], unitsIdle[t], unitsLong[t] > 0 ? (double) unitMoves[t] / unitsLong[t] : 0);
        }
    }

    // ------------------------------------------------------------------ output
    static boolean inWindow(int round) { return fromRound >= 0 && round >= fromRound && (toRound < 0 || round <= toRound); }
    static boolean inWindowOrAll(int round) { return fromRound < 0 || inWindow(round); }

    static String desc(Robot r) {
        if (r == null) return "#? (gone)";
        return String.format("%s:%s#%d@(%d,%d) inf=%d conv=%d", teamName[r.team], TYPE[Math.min(r.type, 3)], r.id, r.x - minX, r.y - minY, r.influence, r.conviction);
    }

    static List<Robot> sortedBots() { List<Robot> l = new ArrayList<>(bots.values()); l.sort(Comparator.comparingInt(a -> a.id)); return l; }

    static final class Agg { int[] n = new int[4]; long inf, ecInf, unitInf; int ecs; long convSum; }
    static Agg[] aggregate() {
        Agg[] a = {new Agg(), new Agg(), new Agg()};
        for (Robot r : bots.values()) {
            Agg g = a[r.team]; int t = Math.min(r.type, 3);
            g.n[t]++; g.inf += r.influence; g.convSum += r.conviction;
            if (t == 0) { g.ecs++; g.ecInf += r.influence; } else g.unitInf += r.influence;
        }
        return a;
    }

    static void printAggregate(int round) {
        Agg[] a = aggregate();
        System.out.printf("r%-4d votes A=%d B=%d | ", round, votes[1], votes[2]);
        for (int t = 1; t <= 2; t++) {
            Agg g = a[t];
            System.out.printf("%s: EC=%d(inf %d) P=%d S=%d M=%d unitInf=%d buff=%d spawned=%d died=%d emp=%d exp=%d bids=%d/%d%s",
                teamName[t].equals("A") || teamName[t].equals("B") ? teamName[t] : (t == 1 ? "A" : "B"),
                g.ecs, g.ecInf, g.n[1], g.n[2], g.n[3], g.unitInf, buffs[t], spawned[t], died[t], empowers[t], exposes[t], bidsPlaced[t], bidInfluence[t],
                t == 1 ? " | " : "");
        }
        System.out.printf(" | neutralEC=%d%n", a[0].ecs);
    }

    static void printMetricsHeader() {
        StringBuilder s = new StringBuilder("round");
        for (String t : new String[]{"A", "B"})
            for (String c : new String[]{"votes", "ecs", "ecInf", "pol", "sla", "muc", "unitInf", "buff", "spawned", "spawnInf", "died", "empowers", "cov", "navMoves", "navAba", "navSwamp", "exposes", "bids", "bidInf", "conversions", "moves", "bcOver"})
                s.append(',').append(t).append('_').append(c);
        s.append(",neutralEcs");
        System.out.println(s);
    }
    /** Tiles this team has stood on so far, per mille of the board -- an integer so the CSV stays uniform. */
    static long coverageOf(int t) {
        if (visited == null || width * height == 0) return 0;
        int c = 0; for (boolean b : visited[t]) if (b) c++;
        return Math.round(1000.0 * c / (width * height));
    }

    /** --knowledge: which neutral centres this team has actually SENSED, cumulatively.
     *  A centre it has never had a unit near cannot be captured however much influence it banks,
     *  and no aggregate metric we collect carries that. Keyed by tile, so a centre that changes
     *  hands is still counted as discovered. */
    static void trackKnowledge() {
        for (Robot e : bots.values()) {
            if (!e.alive || e.team != 0 || e.type != 0) continue;          // neutral centres only
            long key = ((long) e.x << 20) | e.y;
            if (everSeen.contains(key)) continue;
            for (Robot r : bots.values()) {
                if (!r.alive || r.team != knowTeam) continue;
                int dx = r.x - e.x, dy = r.y - e.y;
                if (dx * dx + dy * dy <= SENSOR[r.type]) { everSeen.add(key); break; }
            }
        }
    }

    static void printKnowledgeRow(int round) {
        int neutralNow = 0, ours = 0;
        for (Robot r : bots.values()) {
            if (!r.alive || r.type != 0) continue;
            if (r.team == 0) neutralNow++;
            else if (r.team == knowTeam) ours++;
        }
        System.out.printf("%d,%d,%d,%d,%d%n", round, neutralAtStart, everSeen.size(), neutralNow, ours);
    }

    /** --threat: enemy units sitting close enough to the watched team's ECs to matter.
     *  d^2 <= 9 is the range at which a muckraker can expose a newborn slanderer (our econ gate);
     *  d^2 <= 40 is the EC's own sensor radius. Types: 0 EC, 1 POL, 2 SLA, 3 MUC. */
    static void printThreatRow(int round) {
        int muck9 = 0, muck40 = 0, pol40 = 0, ecs = 0;
        for (Robot e : bots.values()) {
            if (!e.alive || e.team != threatTeam || e.type != 0) continue;
            ecs++;
            for (Robot r : bots.values()) {
                if (!r.alive || r.team == threatTeam || r.team == 0 || r.type == 0) continue;
                int dx = r.x - e.x, dy = r.y - e.y, d2 = dx * dx + dy * dy;
                if (r.type == 3) { if (d2 <= 9) muck9++; if (d2 <= 40) muck40++; }
                else if (r.type == 1 && d2 <= 40) pol40++;
            }
        }
        System.out.printf("%d,%d,%d,%d,%d%n", round, ecs, muck9, muck40, pol40);
    }

    static void printMetricsRow(int round) {
        Agg[] a = aggregate();
        StringBuilder s = new StringBuilder().append(round);
        for (int t = 1; t <= 2; t++) {
            Agg g = a[t];
            for (long v : new long[]{votes[t], g.ecs, g.ecInf, g.n[1], g.n[2], g.n[3], g.unitInf, buffs[t], spawned[t], spawnInfluence[t], died[t], empowers[t], coverageOf(t), moves[t], aba[t], swampMoves[t], exposes[t], bidsPlaced[t], bidInfluence[t], conversions[t], moves[t], bcOverRounds[t]})
                s.append(',').append(v);
        }
        s.append(',').append(a[0].ecs);
        System.out.println(s);
    }

    static void printBoard(int round) {
        char[][] g = new char[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            double p = pass[x + y * width];
            g[y][x] = p >= 0.9 ? '.' : p >= 0.6 ? ':' : p >= 0.35 ? 'o' : '#';
        }
        for (Robot r : bots.values()) {
            int x = r.x - minX, y = r.y - minY;
            if (x >= 0 && x < width && y >= 0 && y < height) g[y][x] = GLYPH[r.team][Math.min(r.type, 3)];
        }
        System.out.printf("BOARD r%d %s (%dx%d)  A=UPPER B=lower N=neutral EC; E/P/S/M = EC/politician/slanderer/muckraker; terrain . >=0.9  : >=0.6  o >=0.35  # swamp%n", round, mapName, width, height);
        for (int y = height - 1; y >= 0; y--) {
            StringBuilder s = new StringBuilder(String.format("%3d ", y));
            for (int x = 0; x < width; x++) s.append(g[y][x]);
            System.out.println(s);
        }
        StringBuilder ax = new StringBuilder("    ");
        for (int x = 0; x < width; x++) ax.append(x % 10 == 0 ? (char) ('0' + (x / 10) % 10) : ' ');
        System.out.println(ax);
    }
}
