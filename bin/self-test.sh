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
#   bin/self-test.sh pins     only the wrapper name check (needs compiled classes)
#   bin/self-test.sh jni      only the native-vs-symbol check (needs libGame.so)
#   bin/self-test.sh strings  only the English/Indonesian resource parity check
#   bin/self-test.sh contrast only the palette contrast check (no device needed)
#   bin/self-test.sh icons    only the launcher icon wiring check (no device needed)
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
                client/app/src/main/java/com/ferzdevzgta/game/diag \
                client/app/src/main/java/com/ferzdevzgta/game/log \
                client/app/src/main/java/com/ferzdevzgta/game/media \
                client/app/src/main/java/com/ferzdevzgta/game/servers \
                client/app/src/main/java/com/ferzdevzgta/game/settings \
                client/app/src/main/java/com/ferzdevzgta/game/input/GamepadMap.java \
                client/app/src/main/java/com/ferzdevzgta/game/ui/Language.java \
                client/app/src/main/java/com/ferzdevzgta/game/ui/Community.java \
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

  # apk-verify must report a missing entry as a failure, not die with a Python traceback: the
  # framework APK has no libGame.so on purpose and the build asks about it.
  if python3 ./bin/apk-verify.py --apk "$ROOT/dist/samp-client-framework.apk" >"$tmp/v.log" 2>&1; then
    bad "apk-verify accepted a framework with no libGame.so"
  elif grep -qi "traceback" "$tmp/v.log"; then
    bad "apk-verify crashed instead of reporting the missing entry"
  elif grep -q "is missing from" "$tmp/v.log"; then
    ok "apk-verify reports a missing entry as a failure"
  else
    bad "apk-verify said something unexpected: $(head -1 "$tmp/v.log")"
  fi

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

run_pins() {
  head_ "wrapper names the engine looks up"
  local classes="client/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
  if [ ! -d "$classes" ]; then
    printf '  skip  no compiled classes yet (cd client && ./gradlew :app:assembleDebug)\n'
    return 0
  fi
  if ./bin/wrapper-pins.sh --classes "$classes" >"${TMPDIR:-/tmp}/pins.log" 2>&1; then
    ok "$(tail -1 "${TMPDIR:-/tmp}/pins.log")"
  else
    bad "pinned name lost: $(grep -m1 missing "${TMPDIR:-/tmp}/pins.log" | tr -d ' ')"
  fi

  # the check has to fail when a name really is gone, so break one on purpose
  local tmp
  tmp="$(mktemp)"
  { printf 'com.rockstargames.oswrapper.GamePlatformServices quit\n'
    printf 'com.rockstargames.oswrapper.GamePlatformServices thisNameDoesNotExist\n'; } >"$tmp"
  if ./bin/wrapper-pins.sh --classes "$classes" --pins "$tmp" >/dev/null 2>&1; then
    bad "the pin check accepted a name that does not exist"
  else
    ok "flags an invented name"
  fi
  rm -f "$tmp"
}

run_jni() {
  head_ "natives against the library symbols"
  local so="client/app/src/main/jniLibs/arm64-v8a/libGame.so"
  local src="client/app/src/main/java/com/rockstargames/oswrapper"
  if [ ! -f "$so" ] || ! command -v nm >/dev/null 2>&1; then
    printf '  skip  needs libGame.so and nm (bin/fetch-blobs.sh)\n'
    return 0
  fi
  if ./bin/jni-pins.py --library "$so" --source "$src" >"${TMPDIR:-/tmp}/jni.log" 2>&1; then
    ok "$(grep -m1 'declared natives all resolve' "${TMPDIR:-/tmp}/jni.log" | tr -s ' ')"
  else
    bad "a declared native has no symbol: $(grep -m1 'GameNative.java' "${TMPDIR:-/tmp}/jni.log" | tr -s ' ')"
  fi

  # a declaration with no symbol has to fail, so try one on purpose
  local tmp
  tmp="$(mktemp -d)"
  mkdir -p "$tmp/com/rockstargames/oswrapper"
  printf 'package com.rockstargames.oswrapper;\npublic class Invented {\n    public static native void notThere(int x);\n}\n' \
    >"$tmp/com/rockstargames/oswrapper/Invented.java"
  if ./bin/jni-pins.py --library "$so" --source "$tmp" >/dev/null 2>&1; then
    bad "the native check accepted a method with no symbol"
  else
    ok "flags a native with no symbol"
  fi
  rm -rf "$tmp"
}

run_strings() {
  head_ "translation parity"
  if python3 - <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

BASE = "client/app/src/main/res"


def keys(path):
    root = ET.parse(path).getroot()
    out = {}
    for node in root:
        if not isinstance(node.tag, str):
            continue
        name = node.get("name")
        if name is None:
            continue
        out[name] = "".join(node.itertext()).strip()
    return out


def placeholders(text):
    return sorted(re.findall(r"%[0-9]+[a-z]|%[a-z]", text))


problems = []
en = keys(BASE + "/values/strings.xml")
idn = keys(BASE + "/values-in/strings.xml")

if len(en) < 40:
    print("  parsed only %d strings, the resource files are not where this test expects" % len(en))
    sys.exit(1)

for name in sorted(set(en) - set(idn)):
    problems.append("missing in values-in: " + name)
for name in sorted(set(idn) - set(en)):
    problems.append("missing in values: " + name)
for name in sorted(set(en) & set(idn)):
    if placeholders(en[name]) != placeholders(idn[name]):
        problems.append("placeholder mismatch: " + name)
    if not idn[name]:
        problems.append("empty translation: " + name)

if problems:
    for line in problems:
        print("  " + line)
    sys.exit(1)
print("%d strings, key sets match" % len(en))
PY
  then ok "English and Indonesian use the same keys"; else bad "translation drift"; fi
}

run_contrast() {
  head_ "palette contrast"
  local out
  if out="$(python3 bin/ui-contrast.py 2>&1)"; then
    ok "$out"
  else
    bad "contrast below WCAG AA"
    printf '%s\n' "$out" | sed -n '1,20p'
  fi
}

run_icons() {
  head_ "launcher icon wiring"
  local out
  if out="$(python3 bin/icon-check.py 2>&1)"; then
    ok "$out"
  else
    bad "the icon is not wired end to end"
    printf '%s\n' "$out" | sed -n '1,10p'
  fi

  # The real bug this catches: the manifest pointing at one layer of the icon. Rebuild that
  # mistake in a scratch tree and require the check to reject it.
  local tmp
  tmp="$(mktemp -d)"
  cp -a client/app/src/main/res "$tmp/res"
  sed 's|android:icon="@mipmap/ic_launcher"|android:icon="@drawable/ic_launcher_background"|' \
    client/app/src/main/AndroidManifest.xml >"$tmp/AndroidManifest.xml"
  if python3 bin/icon-check.py --res "$tmp/res" --manifest "$tmp/AndroidManifest.xml" \
      >"$tmp/out.log" 2>&1; then
    bad "the icon check accepted a manifest pointing at the background layer"
  else
    ok "flags an icon that points at the background layer"
  fi
  rm -rf "$tmp"
}

run_guide() {
  head_ "offline guide bridge"
  if python3 bin/guide-check.py >"${TMPDIR:-/tmp}/guide.log" 2>&1; then
    ok "$(cat "${TMPDIR:-/tmp}/guide.log")"
  else
    bad "$(cat "${TMPDIR:-/tmp}/guide.log")"
  fi

  # A page that calls something outside the bridge has to be refused, or the surface drifts.
  local tmp
  tmp="$(mktemp -d)"
  cp client/app/src/main/assets/help/help.html "$tmp/help.html"
  printf '\n<script>Panel.stealFiles()</script>\n' >>"$tmp/help.html"
  if python3 bin/guide-check.py --page "$tmp/help.html" >/dev/null 2>&1; then
    bad "the guide check accepted a method outside the bridge"
  else
    ok "flags a page that calls a method outside the bridge"
  fi
  rm -rf "$tmp"
}

run_hud() {
  head_ "in-game hud editor"
  if python3 bin/hud-check.py >"${TMPDIR:-/tmp}/hud.log" 2>&1; then
    ok "entry point, load order, frame order, own ini section"
  else
    bad "$(cat "${TMPDIR:-/tmp}/hud.log")"
  fi

  # The editor must not be able to write an engine key. Break that and the gate has to say so.
  local tmp
  tmp="$(mktemp -d)"
  mkdir -p "$tmp/client/app/src/main/cpp/samp/gui/samp_widgets" "$tmp/docs/id"
  cp client/app/src/main/cpp/samp/gui/hudeditor.cpp "$tmp/client/app/src/main/cpp/samp/gui/"
  cp client/app/src/main/cpp/samp/gui/hudeditor.h "$tmp/client/app/src/main/cpp/samp/gui/"
  cp client/app/src/main/cpp/samp/gui/gui.cpp "$tmp/client/app/src/main/cpp/samp/gui/"
  cp client/app/src/main/cpp/samp/gui/gui.h "$tmp/client/app/src/main/cpp/samp/gui/"
  cp client/app/src/main/cpp/samp/gui/samp_widgets/buttonpanel.cpp "$tmp/client/app/src/main/cpp/samp/gui/samp_widgets/"
  cp client/app/src/main/cpp/samp/settings.cpp "$tmp/client/app/src/main/cpp/samp/"
  cp docs/ARCHITECTURE.md "$tmp/docs/"
  cp docs/id/ARCHITECTURE.md "$tmp/docs/id/"
  printf '\nini.SetValue("gui", "ChatPosX", "1");\n' >>"$tmp/client/app/src/main/cpp/samp/gui/hudeditor.cpp"
  if HUD_CHECK_ROOT="$tmp" python3 bin/hud-check.py >/dev/null 2>&1; then
    bad "the hud check accepted an editor that writes [gui]"
  else
    ok "refuses an editor that writes an engine key"
  fi
  rm -rf "$tmp"
}

run_artconfig() {
  head_ "0.3.DL artconfig"
  local tmp
  tmp="$(mktemp -d)"
  mkdir -p "$tmp/models"
  printf 'dff' >"$tmp/models/cop.dff"
  printf 'txd' >"$tmp/models/cop.txd"
  cat >"$tmp/models/artconfig.txt" <<'INI'
AddCharModel(280, 25000, "cop.dff", "cop.txd");
AddSimpleModel(-1, 19379, -2000, "cop.dff", "cop.txd");
INI
  if python3 bin/artconfig.py "$tmp/models/artconfig.txt" --models "$tmp/models" --check \
       --manifest "$tmp/models.json" >"$tmp/log" 2>&1; then
    ok "$(head -1 "$tmp/log") plus a manifest"
  else
    bad "$(cat "$tmp/log")"
  fi
  python3 -c "import json,sys; d=json.load(open('$tmp/models.json')); sys.exit(0 if d['count']==2 and d['total_bytes']==6 else 1)" \
    && ok "manifest counts both files" || bad "manifest counts are wrong"

  # Each of these is a mistake that a real server accepts at startup and then loses a model over.
  expect_bad() {
    local label="$1" line="$2"
    cp "$tmp/models/artconfig.txt" "$tmp/models/artconfig.txt.bak"
    printf '%s\n' "$line" >>"$tmp/models/artconfig.txt"
    if python3 bin/artconfig.py "$tmp/models/artconfig.txt" --models "$tmp/models" --check >/dev/null 2>&1; then
      bad "accepted $label"
    else
      ok "refuses $label"
    fi
    mv "$tmp/models/artconfig.txt.bak" "$tmp/models/artconfig.txt"
  }

  expect_bad "a skin id below 20000" 'AddCharModel(1, 19999, "cop.dff", "cop.txd");'
  expect_bad "an object id above -1000" 'AddSimpleModel(-1, 19379, 2000, "cop.dff", "cop.txd");'
  expect_bad "a repeated id" 'AddCharModel(1, 25000, "cop.dff", "cop.txd");'
  expect_bad "a file that is not in models" 'AddCharModel(1, 26000, "ghost.dff", "cop.txd");'
  expect_bad "a name that climbs out of models" 'AddCharModel(1, 27000, "../settings.ini.dff", "cop.txd");'
  expect_bad "a line with no semicolon" 'AddCharModel(1, 28000, "cop.dff", "cop.txd")'

  rm -rf "$tmp"
}

run_staleness() {
  head_ "stale framework guard"
  local tmp
  tmp="$(mktemp -d)"
  mkdir -p "$tmp/src/com/x" "$tmp/dist"
  printf 'class A {}\n' >"$tmp/src/com/x/A.java"
  printf 'not a real apk\n' >"$tmp/dist/framework.apk"

  fingerprint() {
    ( cd "$1" && find . -type f \( -name '*.java' -o -name '*.xml' -o -name '*.html' \
        -o -name '*.cpp' -o -name '*.h' -o -name '*.c' -o -name '*.gradle' -o -name '*.kts' \) \
        -not -path './build/*' -print0 | sort -z | xargs -0 sha256sum | sha256sum | cut -d' ' -f1 )
  }

  run() { SAMP_SRC="$tmp/src" ./bin/build-termux.sh --framework "$tmp/dist/framework.apk" --check-only; }

  fingerprint "$tmp/src" >"$tmp/dist/framework.apk.src"
  if run >"$tmp/log" 2>&1; then
    ok "accepts a framework whose fingerprint matches the tree"
  else
    bad "refused a matching framework: $(tail -2 "$tmp/log" | tr '\n' ' ')"
  fi

  # Content changed, clock did not: the case a timestamp cannot see.
  printf 'class A { int b; }\n' >"$tmp/src/com/x/A.java"
  touch -r "$tmp/dist/framework.apk" "$tmp/src/com/x/A.java"
  if run >"$tmp/log" 2>&1; then
    bad "accepted a changed tree when the clock agreed"
  else
    grep -q 'source tree changed' "$tmp/log" && ok "refuses a changed tree even when no mtime is newer" \
      || bad "refused for the wrong reason: $(tail -2 "$tmp/log" | tr '\n' ' ')"
  fi

  # Clock moved, content did not: the case a git checkout produces on every branch switch.
  fingerprint "$tmp/src" >"$tmp/dist/framework.apk.src"
  touch "$tmp/src/com/x/A.java"
  if run >"$tmp/log" 2>&1; then
    ok "accepts a touched file that did not change"
  else
    bad "refused an unchanged tree after touch: $(tail -2 "$tmp/log" | tr '\n' ' ')"
  fi

  # No sidecar at all: an older framework, fall back to the clock and refuse.
  rm -f "$tmp/dist/framework.apk.src"
  if run >"$tmp/log" 2>&1; then
    bad "accepted a framework with no fingerprint and older sources"
  else
    grep -q 'no .src fingerprint' "$tmp/log" && ok "refuses an unprovable framework by time" \
      || bad "refused for the wrong reason: $(tail -2 "$tmp/log" | tr '\n' ' ')"
  fi

  if SAMP_SRC="$tmp/src" ./bin/build-termux.sh --framework "$tmp/dist/framework.apk" --check-only --force \
       >"$tmp/log" 2>&1; then
    ok "--force skips the guard"
  else
    bad "--force did not skip the guard: $(tail -2 "$tmp/log" | tr '\n' ' ')"
  fi

  rm -rf "$tmp"
}

case "${1:-all}" in
  jvm)     run_jvm ;;
  cache)   run_cache ;;
  patcher) run_patcher ;;
  termux)  run_termux ;;
  pins)    run_pins ;;
  jni)     run_jni ;;
  strings) run_strings ;;
  icons)   run_icons ;;
  contrast) run_contrast ;;
  guide)   run_guide ;;
  hud)     run_hud ;;
  artconfig) run_artconfig ;;
  staleness) run_staleness ;;
  all)     run_jvm; run_strings; run_contrast; run_icons; run_guide; run_hud; run_artconfig; run_staleness; run_cache; run_patcher; run_termux; run_pins; run_jni ;;
  *)       echo "usage: $0 [all|jvm|strings|contrast|icons|guide|hud|artconfig|staleness|cache|patcher|termux|pins|jni]" >&2; exit 2 ;;
esac

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[[ $fail -eq 0 ]] || exit 1
