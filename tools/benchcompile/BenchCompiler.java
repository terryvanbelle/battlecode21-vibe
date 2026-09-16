package benchcompile;

import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Compile an external bot repository package-by-package inside ONE JVM, never
 * printing source. Used by tools/bench-compile.sh.
 *
 *   java benchcompile.BenchCompiler <repoDir> <outDir> <classpath> <logFile>
 *
 * Strategy: group every .java file that mentions `battlecode.common` (tests,
 * map makers and unrelated code excluded) by its directory; compile the whole
 * set first; if that fails, compile each directory separately with the output
 * dir on the classpath so shared helper packages compiled earlier are found,
 * iterating until no new directory compiles. Diagnostics go to the log file
 * only; stdout gets one summary line: packages compiled / failed, error count.
 */
public class BenchCompiler {
    public static void main(String[] a) throws Exception {
        Path repo = Paths.get(a[0]), out = Paths.get(a[1]); String cp = a[2]; Path log = Paths.get(a[3]);
        Files.createDirectories(out);
        // Source roots: the parent of every directory holding a RobotPlayer.java.
        // Everything under a source root (minus tests) is compiled, so helper
        // packages without a battlecode import are not dropped.
        Set<Path> roots = new TreeSet<>();
        try (Stream<Path> s = Files.walk(repo)) {
            s.filter(p -> p.getFileName().toString().equals("RobotPlayer.java") && !p.toString().contains("/.git/") && !p.toString().contains("/test/"))
             .forEach(p -> roots.add(p.getParent().getParent()));
        }
        List<Path> files = new ArrayList<>();
        for (Path root : roots) try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> p.toString().endsWith(".java"))
             .filter(p -> { String t = p.toString().toLowerCase(); return !t.contains("/test/") && !t.contains("/tests/") && !t.contains("/.git/") && !p.getFileName().toString().endsWith("Test.java"); })
             .forEach(files::add);
        }
        files = files.stream().distinct().collect(Collectors.toList());
        // group by TOP-LEVEL package directory under its source root
        Map<Path, List<Path>> byDir = new TreeMap<>();
        for (Path f : files) {
            Path root = roots.stream().filter(f::startsWith).max(Comparator.comparingInt(Path::getNameCount)).get();
            Path rel = root.relativize(f);
            Path top = rel.getNameCount() > 1 ? root.resolve(rel.getName(0)) : root;
            byDir.computeIfAbsent(top, k -> new ArrayList<>()).add(f);
        }
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        try (PrintWriter lw = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8))) {
            if (files.isEmpty()) { System.out.println("packages=0 failed=0 errors=0 (no battlecode sources)"); return; }
            int[] errs = {0};
            if (compile(jc, files, out, cp, lw, errs)) { System.out.printf("packages=%d failed=0 errors=0 (single pass)%n", byDir.size()); return; }
            // per-directory passes until fixpoint
            Set<Path> done = new HashSet<>(); errs[0] = 0;
            String cp2 = cp + File.pathSeparator + out;
            boolean progress = true;
            while (progress) {
                progress = false;
                for (Map.Entry<Path, List<Path>> e : byDir.entrySet()) {
                    if (done.contains(e.getKey())) continue;
                    int[] er = {0};
                    if (compile(jc, e.getValue(), out, cp2, lw, er)) { done.add(e.getKey()); progress = true; }
                }
            }
            // final pass to record error counts of the failures
            for (Map.Entry<Path, List<Path>> e : byDir.entrySet()) if (!done.contains(e.getKey())) { lw.println("FAILED " + repo.relativize(e.getKey())); compile(jc, e.getValue(), out, cp2, lw, errs); }
            System.out.printf("packages=%d failed=%d errors=%d%n", done.size(), byDir.size() - done.size(), errs[0]);
        }
    }

    static boolean compile(JavaCompiler jc, List<Path> files, Path out, String cp, PrintWriter log, int[] errs) {
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        StandardJavaFileManager fm = jc.getStandardFileManager(diags, null, StandardCharsets.UTF_8);
        Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(files.stream().map(Path::toFile).collect(Collectors.toList()));
        List<String> opts = Arrays.asList("-nowarn", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-proc:none", "-d", out.toString(), "-cp", cp);
        boolean ok = jc.getTask(new PrintWriter(new StringWriter()), fm, diags, opts, null, units).call();
        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) { errs[0]++; log.println(d.getSource() == null ? "?" : d.getSource().getName() + ":" + d.getLineNumber() + ": " + d.getMessage(null)); }
        }
        return ok;
    }
}
