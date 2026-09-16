#!/usr/bin/env bash
# Compile every external benchmark repo WITHOUT reading its code.
#
#   tools/bench-compile.sh              # all repos under $BENCH_ROOT
#   tools/bench-compile.sh <repo-dir>   # one
#
# For each repo: javac all .java under it (excluding tests/mapmakers) against
# the engine jar into $BENCH_ROOT/_classes/<repo>/. Compiler diagnostics are
# written to $BENCH_ROOT/_logs/<repo>.log and ONLY their count is printed, so
# no source text reaches the terminal. Discovered bot packages are those output
# directories that contain RobotPlayer.class; they are appended to
# $BENCH_ROOT/manifest.tsv as
#   name<TAB>package<TAB>classdir<TAB>repo<TAB>commit
# where name = <repo-tag>.<package> and repo-tag is the GitHub owner.
set -uo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$REPO/tools/lib.sh"
BENCH_ROOT="${BENCH_ROOT:-$HOME/projects/vibe/bc21-benchmarks}"
CLASSES="$BENCH_ROOT/_classes"; LOGS="$BENCH_ROOT/_logs"; MANIFEST="$BENCH_ROOT/manifest.tsv"
mkdir -p "$CLASSES" "$LOGS"
[ -f "$MANIFEST" ] || printf 'name\tpackage\tclassdir\trepo\tcommit\n' > "$MANIFEST"
CP="$(engine_cp)" || exit 1

compile_repo () {
  local d="$1" tag; tag="$(basename "$d")"
  local out="$CLASSES/$tag" log="$LOGS/$tag.log"
  rm -rf "$out"; mkdir -p "$out"
  # sources: every .java except tests, map makers, and anything not declaring a battlecode import
  local files
  files=$(find "$d" -name '*.java' -not -path '*/test/*' -not -path '*/tests/*' -not -path '*/.git/*' \
           -not -iname '*Test*.java' -not -path '*/maps/*' -not -path '*/mapmaker*' 2>/dev/null \
           | xargs -r grep -l 'battlecode.common' 2>/dev/null || true)
  if [ -z "$files" ]; then echo "$tag: no battlecode sources"; return; fi
  # Compile with -implicit:none so a package that fails does not poison the rest;
  # try the whole set first, then per-package on failure.
  if javac -nowarn -encoding UTF-8 -source 8 -target 8 -d "$out" -cp "$CP" $files >"$log" 2>&1; then
    :
  else
    # fall back: compile each package directory separately, keep what compiles
    rm -rf "$out"; mkdir -p "$out"
    local pkgdirs; pkgdirs=$(echo "$files" | xargs -n1 dirname | sort -u)
    : > "$log"
    for pd in $pkgdirs; do
      local pf; pf=$(find "$pd" -maxdepth 1 -name '*.java' | grep -v -i 'Test' || true)
      [ -n "$pf" ] || continue
      javac -nowarn -encoding UTF-8 -source 8 -target 8 -d "$out" -cp "$CP:$out" $pf >>"$log" 2>&1 || echo "PKGFAIL $pd" >> "$log"
    done
  fi
  local errs; errs=$(grep -c 'error:' "$log" 2>/dev/null || echo 0)
  local pk; pk=$(find "$out" -name 'RobotPlayer.class' | sed "s#^$out/##; s#/RobotPlayer.class##" | tr '/' '.' | sort)
  local n; n=$(echo "$pk" | grep -c . || true)
  local commit; commit=$(git -C "$d" rev-parse --short HEAD 2>/dev/null || echo '?')
  local owner="${tag%%__*}"
  for p in $pk; do
    [ "$p" = examplefuncsplayer ] && continue
    printf '%s\t%s\t%s\t%s\t%s\n' "${owner}.${p}" "$p" "$out" "$tag" "$commit" >> "$MANIFEST"
  done
  printf '%-48s packages=%-3s javac-errors=%s\n' "$tag" "$n" "$errs"
}

if [ $# -gt 0 ]; then for d in "$@"; do compile_repo "$d"; done
else for d in "$BENCH_ROOT"/*/; do d=${d%/}; case "$(basename "$d")" in _*) continue;; esac; compile_repo "$d"; done; fi
sort -u -t$'\t' -k1,1 "$MANIFEST" -o "$MANIFEST"
echo "manifest: $(($(wc -l < "$MANIFEST") - 1)) bot packages"
