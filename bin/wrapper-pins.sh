#!/usr/bin/env bash
# Prove the compiled wrapper still exposes every name libGame.so looks up.
#
# A rename here compiles fine and fails on a phone, so the check runs against the class
# files that Gradle actually produced, not against the source text.
#
#   bin/wrapper-pins.sh                       # after ./gradlew :app:assembleDebug
#   bin/wrapper-pins.sh --classes DIR         # explicit class output
#
# Exit codes: 0 every pinned name present, 1 missing name or no classes, 2 bad usage.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PINS="$ROOT/tests/pins/oswrapper-pins.txt"
CLASSES="$ROOT/client/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
JAVAP_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}javap"

while [ $# -gt 0 ]; do
  case "$1" in
    --classes) CLASSES="${2:-}"; shift 2 ;;
    --pins) PINS="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,10p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

die() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

command -v "$JAVAP_BIN" >/dev/null 2>&1 || die "no javap (set JAVA_HOME)"
[ -f "$PINS" ] || die "no pin list at $PINS"
[ -d "$CLASSES" ] || die "no compiled classes at $CLASSES (run: cd client && ./gradlew :app:assembleDebug)"

missing=0
checked=0
current=""
dump=""

flush() {
  [ -n "$current" ] || return 0
  if [ -z "$dump" ]; then
    printf '  missing class %s\n' "$current"
    missing=$((missing + 1))
    return 0
  fi
  while IFS= read -r member; do
    [ -n "$member" ] || continue
    checked=$((checked + 1))
    if ! grep -qE "[ .]$member[(;]" <<<"$dump"; then
      printf '  missing %s.%s\n' "$current" "$member"
      missing=$((missing + 1))
    fi
  done <<<"$pending"
}

pending=""
while read -r cls member; do
  case "$cls" in ''|'#'*) continue ;; esac
  if [ "$cls" != "$current" ]; then
    flush
    current="$cls"
    dump="$("$JAVAP_BIN" -p -classpath "$CLASSES" "$cls" 2>/dev/null || true)"
    pending=""
  fi
  pending="${pending}${member}
"
done <"$PINS"
flush

if [ "$missing" -gt 0 ]; then
  printf '\nFAIL: %d of %d pinned names are gone\n' "$missing" "$checked" >&2
  exit 1
fi
printf 'PASS: %d pinned names still present in the compiled wrapper\n' "$checked"
