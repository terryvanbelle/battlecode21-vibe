package bot;

/** Slanderer economics: the breakpoint table is the basis of every build-size decision,
 *  so an error here silently mis-sizes every slanderer we ever build. Run via tools/unit-tests.sh */
public class EconTest {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL " + what); } }

    public static void main(String[] a) {
        // bestSize must return a real breakpoint, never more than the budget
        for (int budget = 0; budget <= 3000; budget += 7) {
            int s = Econ.bestSize(budget);
            check(s <= budget, "bestSize(" + budget + ")=" + s + " exceeds budget");
            if (s > 0) {
                boolean isBreak = false;
                for (int b : Econ.BREAK) if (b == s) isBreak = true;
                check(isBreak, "bestSize(" + budget + ")=" + s + " is not a breakpoint");
            }
        }
        check(Econ.bestSize(0) == 0, "no slanderer with nothing");
        check(Econ.bestSize(20) == 0, "below the first breakpoint buys nothing");
        check(Econ.bestSize(21) == 21, "exactly the first breakpoint");
        check(Econ.bestSize(22) == 21, "between breakpoints rounds down");
        check(Econ.bestSize(1 << 20) == Econ.BREAK[Econ.BREAK.length - 1], "huge budget caps at the table");

        // bestSize is monotone: more money never buys a smaller slanderer
        int prev = 0;
        for (int budget = 0; budget <= 3000; budget++) {
            int s = Econ.bestSize(budget);
            check(s >= prev, "bestSize dipped at " + budget);
            prev = s;
        }

        // the table itself must be strictly increasing, or bestSize's scan is meaningless
        for (int i = 1; i < Econ.BREAK.length; i++)
            check(Econ.BREAK[i] > Econ.BREAK[i-1], "BREAK not increasing at " + i);

        // incomeOf must agree with the table: BREAK[k] yields exactly k per round
        for (int k = 1; k < Econ.BREAK.length; k++) {
            check(Econ.incomeOf(Econ.BREAK[k]) == k, "incomeOf(BREAK[" + k + "]) != " + k);
            if (Econ.BREAK[k] - 1 >= Econ.BREAK[k-1])
                check(Econ.incomeOf(Econ.BREAK[k] - 1) == k - 1, "income should not rise before the breakpoint at k=" + k);
        }
        check(Econ.incomeOf(0) == 0, "nothing earns nothing");

        // the property every sizing decision leans on: income per influence FALLS as size rises,
        // so a big slanderer is only right when build actions, not influence, are scarce
        double prevRatio = Double.MAX_VALUE;
        for (int k = 2; k < 20; k++) {
            double ratio = (double) k / Econ.BREAK[k];
            check(ratio < prevRatio, "income per influence should fall with size, broke at k=" + k);
            prevRatio = ratio;
        }

        // EC passive income: positive, monotone, and matching the engine's ceil(0.2*sqrt(round))
        check(Econ.passive(1) == 1, "passive(1)");
        check(Econ.passive(100) == 2, "passive(100) = ceil(2.0)");
        check(Econ.passive(2500) == 10, "passive(2500) = ceil(10.0)");
        int last = 0;
        for (int r = 1; r <= 1500; r++) {
            int p = Econ.passive(r);
            check(p >= last, "passive dipped at round " + r);
            check(p > 0, "passive should stay positive at " + r);
            last = p;
        }

        System.out.println(fails == 0 ? "OK EconTest" : fails + " FAILURES in EconTest");
        if (fails > 0) System.exit(1);
    }
}
