#!/usr/bin/env bash
# Measure how much of the wrapper surface is still upstream-derived.
#
# The 38 native entry points in libGame.so are addressed through
# com.rockstargames.oswrapper and com.nvidia.devtech, so those package names cannot
# change. Their *bodies* can, one file at a time. A rewritten file carries the marker
# below on its first line; anything without it is still derived from the shipped dex.
#
# Usage:
#   bin/oswrapper-audit.sh            report only
#   bin/oswrapper-audit.sh --strict   exit 1 while any derived file remains
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC="$ROOT/client/app/src/main/java"
MARKER='Clean-room rewrite'
STRICT=0

for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=1 ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) printf 'unknown argument: %s\n' "$arg" >&2; exit 2 ;;
  esac
done

rewritten=()
derived=()

while IFS= read -r file; do
  if head -1 "$file" | grep -qF "$MARKER"; then
    rewritten+=("$file")
  else
    derived+=("$file")
  fi
done < <(find "$SRC/com/rockstargames/oswrapper" "$SRC/com/nvidia/devtech" -name '*.java' | sort)

lines_of() {
  local total=0 file count
  for file in "$@"; do
    count=$(wc -l <"$file")
    total=$((total + count))
  done
  printf '%s' "$total"
}

rewritten_lines=$(lines_of ${rewritten[@]+"${rewritten[@]}"})
derived_lines=$(lines_of ${derived[@]+"${derived[@]}"})

printf 'rewritten: %s files, %s lines\n' "${#rewritten[@]}" "$rewritten_lines"
for file in ${rewritten[@]+"${rewritten[@]}"}; do
  printf '  ok    %s\n' "${file#"$SRC"/}"
done

printf 'derived:   %s files, %s lines\n' "${#derived[@]}" "$derived_lines"
for file in ${derived[@]+"${derived[@]}"}; do
  printf '  todo  %s\n' "${file#"$SRC"/}"
done

if [ "$STRICT" = 1 ] && [ "${#derived[@]}" -gt 0 ]; then
  printf '\nstrict mode: %s file(s) still carry upstream code\n' "${#derived[@]}" >&2
  exit 1
fi
