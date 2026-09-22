package arch_lemon;

/**
 * Log lines for the replay dumper: `@tag k=v k=v`. Read them back with
 *   tools/replay-dump.sh game.bc21 --logs '@bc' --logs-team A
 * Tags: @bc (bytecode monitor), @exc (caught exception), @spawn, @nav, @econ.
 * Keep this class trivial: the sandbox rejects reflection-flavoured calls
 * (getClass, stack traces), and a rejected Debug class takes the whole bot down.
 */
public final class Debug {
    private Debug() {}
    public static void log(String line) { if (C.DEBUG) System.out.println(line); }
    public static void exception(Exception e) {
        if (C.DEBUG) System.out.println("@exc " + e);
    }
}
