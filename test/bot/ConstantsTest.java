package bot;

/** Guards on the tuning constants: relationships that must hold for the build order to make
 *  sense at all. These catch the "changed one number, broke an invariant" class of bug that a
 *  dose ladder invites. Run via tools/unit-tests.sh */
public class ConstantsTest {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL " + what); } }

    public static void main(String[] a) {
        check(C.MAX_SLANDERERS > 0, "a positive slanderer cap");
        check(C.SPEND_SLANDERER_CAP >= C.MAX_SLANDERERS,
              "the spare-branch cap must not be below the normal one, else surplus can never add economy");
        check(C.MAX_SLANDERER_SIZE >= Econ.BREAK[1], "the size cap must allow at least the smallest slanderer");
        boolean capIsBreak = false;
        for (int b : Econ.BREAK) if (b == C.MAX_SLANDERER_SIZE) capIsBreak = true;
        check(capIsBreak, "MAX_SLANDERER_SIZE should be a breakpoint, not a value between them");

        check(C.GUARD_BASE >= 0 && C.MAX_GUARDS >= C.GUARD_BASE, "guard caps are ordered");
        // Iteration 35: the spare branch may not become a guard factory again. The cap must be a real
        // bound (not tied to the slanderer count) and must leave room for the economy to fill first.
        check(C.SPEND_GUARD_CAP > 0, "spare-branch guard cap is positive");
        // Iteration 47: a speech must still clear some bar, or politicians fire at nothing.
        check(C.SPEECH_MIN_VALUE > 0, "a speech must deliver some value to be worth a politician");
        check(C.MAX_CAPTURERS >= 2, "the capturer cap never drops below the long-standing 2");
        // Iteration 38: the scout cap must stay a real ceiling and must grow with the round.
        check(C.SCOUT_BASE > 0 && C.SCOUT_PER_ROUND > 0, "scout cap terms are positive");
        // Iteration 49: a bounded save wait that ends before the save window does, and a positive presumption
        check(C.SAVE_MAX_WAIT >= 0 && C.SAVE_MAX_WAIT < C.SAVE_UNTIL, "the save wait bound must fall inside the save window");
        check(C.PRESUME_ROUNDS > 0 && C.PRESUME_ROUNDS <= 300, "a flip presumption lasts a bounded, positive time");
        check(C.SAVE_NO_BID == 0 || C.SAVE_MAX_WAIT > 0, "a silent save must at least be bounded");
        check(C.ATTACK_MARGIN_NUM >= C.ATTACK_MARGIN_DEN && C.ATTACK_MARGIN_DEN > 0, "an attack buys at least the centre's influence");
        check(C.ATTACK_CAP >= 300 && C.ATTACK_INF_AGE > 0, "attack sizing bounds are sane");
        check(C.SCOUT_MAX >= C.SCOUT_BASE, "the scout ceiling is not below its own base");
        check(C.SCOUT_BASE + 1500 / C.SCOUT_PER_ROUND >= C.SCOUT_MAX,
              "the cap actually reaches its ceiling within a game, or the ceiling is dead weight");
        check(C.SPEND_GUARD_CAP < C.SPEND_SLANDERER_CAP,
              "spare-branch guards are capped below the slanderer cap, so surplus reaches the economy");
        check(C.GUARD_RING_MIN < C.GUARD_LEASH_D2, "a guard's ring must fit inside its leash");
        check(C.SLANDERER_RING_MIN < C.SLANDERER_RING_MAX, "slanderer ring is a real interval");
        check(C.EARLY_SCOUTS >= 1, "at least one early scout, or the map is never seen");
        check(C.SPARE_MIN > 0, "the spare branch needs a positive threshold");
        check(C.BID_EARLY_DIV > 0, "bid divisor must be positive");

        // economy-blocking threat rules (Iteration 27)
        check(C.ECON_DANGER_POL_CONV > 0, "an econ-danger politician must carry real conviction");
        check(C.ECON_DANGER_MUCK_D2 > 0 && C.ECON_DANGER_MUCK_D2 <= 40,
              "the muckraker trigger must sit inside the EC's sensor radius");

        // saving mode (Iteration 22)
        check(C.SAVE_UNTIL > 0, "saving mode needs a window");
        check(C.SAVE_MAX_TARGET >= Econ.BREAK[1], "a target cap below the cheapest neutral would never fire");
        check(C.SAVE_SLANDERERS >= 1, "saving mode must keep some income");
        check(C.SAVE_BANK >= 0, "a negative bank is meaningless");

        // Iteration 34: a minimum slanderer size must be a real breakpoint and must not exceed
        // what the opening can ever afford, or the economy never starts
        boolean minIsBreak = false;
        for (int b : Econ.BREAK) if (b == C.MIN_SLANDERER_SIZE) minIsBreak = true;
        check(minIsBreak, "MIN_SLANDERER_SIZE must be a breakpoint");
        check(C.MIN_SLANDERER_SIZE <= 150, "a minimum above the starting influence would stall the opening");
        check(C.MIN_SLANDERER_SIZE <= C.MAX_SLANDERER_SIZE, "minimum must not exceed the maximum");

        System.out.println(fails == 0 ? "OK ConstantsTest" : fails + " FAILURES in ConstantsTest");
        if (fails > 0) System.exit(1);
    }
}
