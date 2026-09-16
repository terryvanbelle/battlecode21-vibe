#!/usr/bin/env bash
# Build the Battlecode 2021 engine from source and stage it under engine/.
#
# WHY: the official artefacts are gone. The scaffold fetched the engine from
# GitHub Packages with a read token published at 2021.battlecode.org/access.txt;
# that URL is dead and the package needs a `read:packages` token we do not have.
# The engine source is public (battlecode/battlecode21), so we build it. Two
# dependencies rotted with it: `jcenter()` (shut down) and `net.sf.jsi:jsi:
# 1.1.0-SNAPSHOT` (sonatype snapshots purged). jcenter is replaced by
# mavenCentral, and jsi is compiled from aled/jsi's source into a flatDir jar.
#
#   tools/build-engine.sh            # clone (if needed), patch, build, stage
#   ENGINE_REF=<sha> tools/build-engine.sh
#
# Output: engine/engine.jar, engine/lib/*.jar (runtime deps), engine/maps/ (the
# 76 built-in .map21 files, extracted for tools that want to read them),
# engine/VERSION. All gitignored; re-run this script on a fresh checkout.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${ENGINE_SRC:-$HOME/projects/vibe/reference/battlecode21}"
JSI_SRC="${JSI_SRC:-$HOME/projects/vibe/reference/jsi}"
ENGINE_REF="${ENGINE_REF:-ed39c1a49574db57e5463d720736220506280294}"   # master, 2022-01-11
JSI_REF="${JSI_REF:-master}"
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk/jdk8u504-b01}"
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | grep -q '1\.8' || { echo "!! need JDK 8 at $JAVA_HOME" >&2; exit 1; }

[ -d "$SRC/.git" ] || git clone -q https://github.com/battlecode/battlecode21.git "$SRC"
[ -d "$JSI_SRC/.git" ] || git clone -q https://github.com/aled/jsi.git "$JSI_SRC"
( cd "$SRC" && git fetch -q --depth 1 origin "$ENGINE_REF" 2>/dev/null || true; git checkout -q "$ENGINE_REF" 2>/dev/null || true )

# --- jsi -------------------------------------------------------------------
LIBS="$SRC/engine/libs"; mkdir -p "$LIBS"
mvn_get () { # group/path artifact version
  local f="$LIBS/$2-$3.jar"
  [ -f "$f" ] || curl -fsSL -o "$f" "https://repo1.maven.org/maven2/$1/$2/$3/$2-$3.jar"
}
mvn_get net/sf/trove4j trove4j 3.0.3
mvn_get org/slf4j slf4j-api 1.7.21
if [ ! -f "$LIBS/jsi-1.1.0-SNAPSHOT.jar" ]; then
  tmp="$(mktemp -d)"
  javac -nowarn -d "$tmp" -cp "$LIBS/trove4j-3.0.3.jar:$LIBS/slf4j-api-1.7.21.jar" \
    $(find "$JSI_SRC/src/main/java" -name '*.java')
  ( cd "$tmp" && jar cf "$LIBS/jsi-1.1.0-SNAPSHOT.jar" net )
  rm -rf "$tmp"
fi

# --- patch the build (idempotent) --------------------------------------------
cd "$SRC"
python3 - <<'PY'
import re
def patch(p, subs):
    s = open(p).read(); o = s
    for a, b in subs: s = s.replace(a, b)
    if s != o: open(p, 'w').write(s)
patch('engine/build.gradle', [
    ('  jcenter()\n  mavenCentral()\n', '  mavenCentral()\n  flatDir { dirs "libs" }\n'),
    ("[group: 'net.sf.jsi', name: 'jsi', version: '1.1.0-SNAPSHOT'],", "[name: 'jsi-1.1.0-SNAPSHOT'],"),
])
s = open('engine/build.gradle').read()
if 'printClasspath' not in s:
    open('engine/build.gradle', 'a').write('\ntask printClasspath {\n  doLast { println sourceSets.main.runtimeClasspath.getAsPath() }\n}\n')
patch('build.gradle', [('repositories {\n    jcenter()\n}', 'repositories {\n    mavenCentral()\n}'),
                       ("project(':internal-test-bots')", "project(':example-bots')"),
                       ("':internal-test-bots:build'", "':example-bots:build'")])
patch('example-bots/build.gradle', [('  jcenter()\n', '')])
s = open('settings.gradle').read()
s = '\n'.join(l for l in s.splitlines() if 'internal-test-bots' not in l) + '\n'
open('settings.gradle', 'w').write(s)
gp = open('gradle.properties').read()
if 'org.gradle.jvmargs' not in gp:
    open('gradle.properties', 'a').write('org.gradle.jvmargs=-Xmx700m\n')
PY

# --- build -------------------------------------------------------------------
./gradlew --no-daemon -q :engine:build -x test -x javadoc 2>&1 | grep -v '^warning\|^Note:' || true
[ -f engine/build/libs/engine.jar ] || { echo "!! engine build failed" >&2; exit 1; }
CP="$(./gradlew --no-daemon -q :engine:printClasspath | tail -1)"

# --- stage -------------------------------------------------------------------
OUT="$REPO/engine"; rm -rf "$OUT"; mkdir -p "$OUT/lib" "$OUT/maps"
cp engine/build/libs/engine.jar "$OUT/engine.jar"
echo "$CP" | tr ':' '\n' | grep '\.jar$' | grep -v 'tools.jar' | while read -r j; do cp "$j" "$OUT/lib/"; done
cp engine/src/main/battlecode/world/resources/*.map21 "$OUT/maps/"
ls engine/src/main/battlecode/world/resources/*.map21 | xargs -n1 basename | sed 's/\.map21$//' | sort > "$REPO/tools/bc21-maps.txt"
{ echo "engine source: battlecode/battlecode21 @ $(git rev-parse HEAD)"; echo "spec version: $(grep -o 'Current version: [0-9.]*' specs/specs.md.html | head -1)"; echo "built: $(date -u +%FT%TZ) with $(java -version 2>&1 | head -1)"; } > "$OUT/VERSION"
cat "$OUT/VERSION"; ls "$OUT/lib" | wc -l; echo "maps: $(wc -l < "$REPO/tools/bc21-maps.txt")"
