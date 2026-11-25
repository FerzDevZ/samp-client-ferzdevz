#!/usr/bin/env bash
# Fetch the reference client tree (git-ignored, not part of this repo) and apply
# the Linux port patches. Idempotent: re-running re-applies cleanly.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REF="$ROOT/vendor/samp-mobile"
BRANCH="GTA-2.11"
PATCH="$ROOT/.patches/linux-port.patch"
UPSTREAM="https://github.com/kuzia15/SAMP-Mobile.git"

if [[ -d "$REF/.git" ]]; then
  echo "== reference tree present, syncing to origin/$BRANCH =="
  git -C "$REF" fetch --depth 1 origin "$BRANCH"
  git -C "$REF" checkout -q "$BRANCH"
  git -C "$REF" reset --hard -q "origin/$BRANCH"
else
  echo "== cloning $UPSTREAM ($BRANCH) =="
  mkdir -p "$ROOT/vendor"
  git clone --depth 1 -b "$BRANCH" "$UPSTREAM" "$REF"
fi

if [[ -f "$PATCH" ]]; then
  if git -C "$REF" apply --check "$PATCH" 2>/dev/null; then
    git -C "$REF" apply "$PATCH"
    echo "== applied $(basename "$PATCH") =="
  else
    echo "== patch already applied; tree left as-is =="
  fi
fi

# Binary duplicates cannot travel in a text patch: bass is linked by CMake from
# vendor/bass/libs/<abi>/, so the jniLibs copies make mergeDebugNativeLibs fail.
for lib in libbass.so libbass_ssl.so; do
  if [[ -f "$REF/app/src/main/jniLibs/arm64-v8a/$lib" ]]; then
    rm -f "$REF/app/src/main/jniLibs/arm64-v8a/$lib"
    echo "== removed duplicate jniLibs/$lib =="
  fi
done

# The repo ships gradlew without the exec bit.
chmod +x "$REF/gradlew"
echo
echo "reference ready: $REF  ($(du -sh "$REF" | cut -f1))"
echo "next: $ROOT/bin/android-build.sh"
