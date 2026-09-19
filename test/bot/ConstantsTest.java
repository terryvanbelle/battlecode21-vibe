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

        System.out.println(fails == 0 ? "OK ConstantsTest" : fails + " FAILURES in ConstantsTest");
        if (fails > 0) System.exit(1);
    }
}
