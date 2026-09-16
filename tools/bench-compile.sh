#!/usr/bin/env bash
# Compile every external benchmark repo WITHOUT reading its code.
#
#   tools/bench-compile.sh              # all repos under $BENCH_ROOT
#   tools/bench-compile.sh <repo-dir>   # one
#
# Uses tools/benchcompile/BenchCompiler.java (one JVM per repo, per-package
# fallback inside it). Diagnostics go to $BENCH_ROOT/_logs/<repo>.log only.
# Discovered bot packages (output dirs holding RobotPlayer.class) are written
# to $BENCH_ROOT/manifest.tsv as  name  package  classdir  repo  commit
# with name = <github-owner>.<package>.
set -uo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$REPO/tools/lib.sh"
BENCH_ROOT="${BENCH_ROOT:-$HOME/projects/vibe/bc21-benchmarks}"
CLASSES="$BENCH_ROOT/_classes"; LOGS="$BENCH_ROOT/_logs"; MANIFEST="$BENCH_ROOT/manifest.tsv"
mkdir -p "$CLASSES" "$LOGS"
CP="$(engine_cp)" || exit 1
DRV="$REPO/build/benchcompile"
if [ ! -f "$DRV/benchcompile/BenchCompiler.class" ]; then mkdir -p "$DRV"; javac -nowarn -d "$DRV" "$REPO/tools/benchcompile/BenchCompiler.java" || exit 1; fi

compile_repo () {
  local d="$1" tag; tag="$(basename "$d")"
  local out="$CLASSES/$tag" log="$LOGS/$tag.log"
  rm -rf "$out"; mkdir -p "$out"
  local summary; summary=$(java -Xmx600m -cp "$DRV" benchcompile.BenchCompiler "$d" "$out" "$CP" "$log" 2>>"$log")
  local pk; pk=$(find "$out" -name 'RobotPlayer.class' | sed "s#^$out/##; s#/RobotPlayer.class##" | tr '/' '.' | sort)
  local commit; commit=$(git -C "$d" rev-parse --short HEAD 2>/dev/null || echo '?')
  local owner="${tag%%_*}"
  # drop stale rows for this repo, then append
  [ -f "$MANIFEST" ] && grep -v -P "\t$tag\t" "$MANIFEST" > "$MANIFEST.tmp" && mv "$MANIFEST.tmp" "$MANIFEST"
  for p in $pk; do
    [ "$p" = examplefuncsplayer ] && continue
    printf '%s\t%s\t%s\t%s\t%s\n' "${owner}.${p}" "$p" "$out" "$tag" "$commit" >> "$MANIFEST"
  done
  printf '%-48s %s  bots=%s\n' "$tag" "$summary" "$(echo "$pk" | grep -c . || true)"
}

[ -f "$MANIFEST" ] || printf 'name\tpackage\tclassdir\trepo\tcommit\n' > "$MANIFEST"
if [ $# -gt 0 ]; then for d in "$@"; do compile_repo "$d"; done
else for d in "$BENCH_ROOT"/*/; do d=${d%/}; case "$(basename "$d")" in _*) continue;; esac; compile_repo "$d"; done; fi
{ head -1 "$MANIFEST"; tail -n +2 "$MANIFEST" | sort -u -t$'\t' -k1,1; } > "$MANIFEST.tmp" && mv "$MANIFEST.tmp" "$MANIFEST"
echo "manifest: $(($(wc -l < "$MANIFEST") - 1)) bot packages"
