#!/usr/bin/env bash
# One command that proves the tooling still works. No device, no game data, no
# Android SDK build: every check runs on synthetic input and exits non-zero on the
# first real failure.
#
#   bin/self-test.sh          run everything
#   bin/self-test.sh jvm      only the JVM unit tests
#   bin/self-test.sh cache    only the cache builder round trip
#   bin/self-test.sh patcher  only the patcher argument contract
#   bin/self-test.sh termux   only the on-phone patcher (needs apksigner)
#
# What it does NOT prove: that the game runs. That needs a phone and a GTA install.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

pass=0
fail=0
ok()   { pass=$((pass + 1)); printf '  ok   %s\n' "$*"; }
bad()  { fail=$((fail + 1)); printf '  FAIL %s\n' "$*"; }
head_() { printf '\n== %s ==\n' "$*"; }

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
command -v "$JAVA_BIN" >/dev/null || { echo "FAIL: no java (set JAVA_HOME)" >&2; exit 1; }

run_jvm() {
  head_ "jvm unit tests"
  local out
  out="$(mktemp -d)"
  local srcs
  srcs="$(find client/app/src/main/java/com/ferzdevzgta/game/data \
                client/app/src/main/java/com/ferzdevzgta/game/log \
                client/app/src/main/java/com/ferzdevzgta/game/settings \
                tests/jvm/src -name '*.java')"
  if ! "$JAVAC_BIN" -Xlint:all,-serial -d "$out/classes" $srcs 2>"$out/javac.log"; then
    bad "javac failed"
    sed -n '1,20p' "$out/javac.log"
    return
  fi
  ok "compiled with -Xlint:all"
  if "$JAVA_BIN" -cp "$out/classes" com.ferzdevzgta.game.TestMain >"$out/run.log" 2>&1; then
    ok "$(grep -o 'checks: .*' "$out/run.log" | tail -1)"
  else
    bad "assertions failed"
    sed -n '1,40p' "$out/run.log"
  fi
  rm -rf "$out"
}

run_cache() {
  head_ "cache builder round trip"
  local tmp
  tmp="$(mktemp -d)"

  # a stand-in asset tree: flat files, a directory the lite variant must drop
  mkdir -p "$tmp/src/data" "$tmp/src/audio" "$tmp/src/movies" "$tmp/src/models"
  printf 'DATA-MAP' >"$tmp/src/data/gta.dat"
  printf 'STREAM' >"$tmp/src/models/a.dff"
  printf 'RADIO' >"$tmp/src/audio/radio.wav"
  printf 'CUT' >"$tmp/src/movies/intro.mpg"

  if ! ./bin/build-cache.sh --dir "$tmp/src" --out "$tmp/cache" >"$tmp/build.log" 2>&1; then
    bad "build-cache.sh failed"
    sed -n '1,20p' "$tmp/build.log"
    rm -rf "$tmp"
    return
  fi
  ok "built full and lite"

  [[ -f "$tmp/cache/manifest-full.json" && -f "$tmp/cache/manifest-lite.json" ]] \
    && ok "both manifests written" || bad "missing manifest"

  if grep -q '"path": "audio/radio.wav"' "$tmp/cache/manifest-full.json"; then
    ok "full keeps audio"
  else
    bad "full lost audio"
  fi
  if grep -q 'audio/' "$tmp/cache/manifest-lite.json"; then
    bad "lite still contains audio"
  else
    ok "lite drops audio and movies"
  fi

  if ./bin/build-cache.sh --out "$tmp/cache" --verify >"$tmp/verify.log" 2>&1; then
    ok "verify accepts an intact cache"
  else
    bad "verify rejected an intact cache"
    sed -n '1,20p' "$tmp/verify.log"
  fi

  # one flipped byte must be caught, not rounded off to "close enough"
  printf 'X' >>"$tmp/cache/full/data/gta.dat"
  if ./bin/build-cache.sh --out "$tmp/cache" --verify >"$tmp/tamper.log" 2>&1; then
    bad "verify accepted a tampered file"
  else
    ok "verify rejects a tampered file"
  fi

  # and an extra file on disk must not be silently ignored
  rm -rf "$tmp/cache"
  mkdir -p "$tmp/src2/data"
  printf 'A' >"$tmp/src2/data/one.dat"
  ./bin/build-cache.sh --dir "$tmp/src2" --out "$tmp/cache2" >"$tmp/b2.log" 2>&1 || bad "second build failed"
  printf 'B' >"$tmp/cache2/full/data/one.dat"
  if ./bin/build-cache.sh --out "$tmp/cache2" --verify >/dev/null 2>&1; then
    bad "verify accepted a rewritten file"
  else
    ok "verify catches a rewritten file"
  fi

  rm -rf "$tmp"
}

run_patcher() {
  head_ "patcher argument contract"
  local tmp
  tmp="$(mktemp -d)"
  mkdir -p "$tmp/empty"

  expects_fail() {
    local what="$1"; shift
    if ./bin/patch-apk.sh "$@" >"$tmp/out.log" 2>&1; then
      bad "accepted: $what"
    else
      ok "refused: $what"
    fi
  }

  expects_fail "no input at all" --out "$tmp/x.apk"
  expects_fail "both --gta-apk and --gta-split-dir" \
    --gta-apk "$tmp/none.apk" --gta-split-dir "$tmp/empty" --out "$tmp/x.apk"
  expects_fail "missing apk" --gta-apk "$tmp/none.apk" --out "$tmp/x.apk"
  expects_fail "missing split dir" --gta-split-dir "$tmp/none" --out "$tmp/x.apk"
  expects_fail "empty split dir" --gta-split-dir "$tmp/empty" --out "$tmp/x.apk"
  expects_fail "no --out" --gta-split-dir "$tmp/empty"

  rm -rf "$tmp"
}

run_termux() {
  head_ "on-phone patcher"
  if ! command -v apksigner >/dev/null 2>&1; then
    printf '  skip  apksigner not on PATH, so the signing path is not covered here\n'
    return 0
  fi
  local tmp
  tmp="$(mktemp -d)"

  # Synthetic halves: a framework without a game library, and a "game" APK that has
  # one. Neither is a real GTA install, they only carry the paths the patcher looks for.
  python3 - "$tmp" <<'PYEOF'
import sys, zipfile
tmp = sys.argv[1]
manifest = b"\x03\x00\x08\x00" + b"com.ferzdevzgta.game" + b"\x00" * 16
elf = b"\x7fELF\x02\x01\x01" + b"\x00" * 11 + b"\xb7\x00" + b"\x00" * 4096
with zipfile.ZipFile(tmp + "/framework.apk", "w", zipfile.ZIP_STORED) as z:
    z.writestr("AndroidManifest.xml", manifest)
    z.writestr("lib/arm64-v8a/libsamp.so", elf)
with zipfile.ZipFile(tmp + "/game.apk", "w", zipfile.ZIP_STORED) as z:
    z.writestr("AndroidManifest.xml", manifest)
    z.writestr("lib/arm64-v8a/libGame.so", elf * 3)
PYEOF

  if ./bin/patch-termux.sh --gta-apk "$tmp/game.apk" --framework "$tmp/framework.apk" \
      --out "$tmp/out.apk" >"$tmp/patch.log" 2>&1; then
    ok "patched a synthetic framework with apksigner"
  else
    bad "patching failed: $(tail -2 "$tmp/patch.log" | tr '\n' ' ')"
  fi
  if [ -f "$tmp/out.apk" ]; then
    if python3 bin/apk-verify.py --apk "$tmp/out.apk" --so <(unzip -p "$tmp/game.apk" lib/arm64-v8a/libGame.so) \
        --expect-package com.ferzdevzgta.game >"$tmp/verify.log" 2>&1; then
      ok "verified: stored, 4096 aligned, bytes intact"
    else
      bad "verification failed: $(tail -1 "$tmp/verify.log")"
    fi
  fi

  if ./bin/patch-termux.sh --gta-apk "$tmp/game.apk" --framework "$tmp/out.apk" \
      --out "$tmp/x.apk" >"$tmp/refuse.log" 2>&1; then
    bad "accepted a framework that already contains the game library"
  else
    ok "refused: finished APK passed as framework"
  fi
  if ./bin/patch-termux.sh --gta-apk "$tmp/framework.apk" --framework "$tmp/framework.apk" \
      --out "$tmp/x.apk" >"$tmp/refuse2.log" 2>&1; then
    bad "accepted a game APK without the game library"
  else
    ok "refused: game APK without libGame.so"
  fi

  rm -rf "$tmp"
}

case "${1:-all}" in
  jvm)     run_jvm ;;
  cache)   run_cache ;;
  patcher) run_patcher ;;
  termux)  run_termux ;;
  all)     run_jvm; run_cache; run_patcher; run_termux ;;
  *)       echo "usage: $0 [all|jvm|cache|patcher|termux]" >&2; exit 2 ;;
esac

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[[ $fail -eq 0 ]] || exit 1
