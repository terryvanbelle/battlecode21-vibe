package bot;

/** Unit roles carried in ORDER flags. */
public final class Roles {
    private Roles() {}
    public static final int SCOUT = 1;
    public static final int GUARD = 2;
    public static final int ECON = 3;
    public static final int CAPTURE = 4;
    public static final int HUNT = 5;
    public static final int ATTACK = 7;     // Iteration 55: a capturer aimed at an enemy centre (counted apart from neutral capturers)
}
