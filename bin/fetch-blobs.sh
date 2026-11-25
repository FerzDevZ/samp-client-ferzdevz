#!/usr/bin/env bash
# Fetch the two binary sets that are deliberately NOT in this repo, from the
# upstream reference tree:
#
#   libGame.so       — Rockstar's game library (copyrighted, not ours to ship)
#   libbass*.so      — BASS audio (un4seen licence forbids redistribution)
#
# Everything else (cpp, java, gradle, resources) is tracked in client/.
#
# Idempotent: files already present are left alone unless --force is passed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT="$ROOT/client"
BRANCH="GTA-2.11"
UPSTREAM="https://github.com/kuzia15/SAMP-Mobile.git"
FORCE="${1:-}"

[[ -d "$CLIENT" ]] || { echo "FAIL: client/ missing" >&2; exit 1; }

BASS_ABIS=(armeabi-v7a arm64-v8a x86 x86_64)
need=0
[[ -f "$CLIENT/app/src/main/jniLibs/arm64-v8a/libGame.so" ]] || need=1
for abi in "${BASS_ABIS[@]}"; do
  [[ -f "$CLIENT/app/src/main/cpp/samp/vendor/bass/libs/$abi/libbass.so" ]] || need=1
done

if [[ $need -eq 0 && "$FORCE" != "--force" ]]; then
  echo "== blobs already present, nothing to do (use --force to refetch) =="
  exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "== cloning $UPSTREAM ($BRANCH) to fetch binaries =="
git clone --depth 1 -b "$BRANCH" "$UPSTREAM" "$TMP/ref" >/dev/null 2>&1 \
  || { echo "FAIL: clone failed (no network or branch gone)" >&2; exit 1; }

echo "== copying libGame.so =="
install -D -m 0644 \
  "$TMP/ref/app/src/main/jniLibs/arm64-v8a/libGame.so" \
  "$CLIENT/app/src/main/jniLibs/arm64-v8a/libGame.so"

for abi in "${BASS_ABIS[@]}"; do
  src="$TMP/ref/app/src/main/cpp/samp/vendor/bass/libs/$abi"
  [[ -d "$src" ]] || continue
  for f in "$src"/*.so; do
    [[ -f "$f" ]] || continue
    install -D -m 0644 "$f" "$CLIENT/app/src/main/cpp/samp/vendor/bass/libs/$abi/$(basename "$f")"
    echo "  bass/$abi/$(basename "$f")"
  done
done

echo
echo "blobs ready."
echo "NOTE: libGame.so is Rockstar's binary. It stays out of git on purpose —"
echo "      a distributable build must obtain it from the user's own GTA SA"
echo "      install, not from this script."
echo "next: $ROOT/bin/android-build.sh"
