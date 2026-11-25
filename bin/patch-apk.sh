#!/usr/bin/env bash
# Build an installable client APK from two things you are allowed to have:
#
#   --framework  our APK without Rockstar's game library (bin/android-build.sh framework)
#   --gta-apk    a GTA San Andreas APK you own
#
# The only thing taken from the game APK is lib/arm64-v8a/libGame.so. It is
# re-inserted as an uncompressed, page-aligned native library, the whole APK is
# realigned and re-signed. No file from the game is copied anywhere else, and
# nothing leaves this machine.
#
# A modern GTA SA install is split: base.apk plus per-ABI splits, with the game
# library inside split_config.arm64_v8a.apk. Both layouts are accepted.
#
# Usage
#   bin/patch-apk.sh --gta-apk GTA.apk --out client.apk
#   bin/patch-apk.sh --gta-split-dir /path/to/split --out client.apk
#   bin/patch-apk.sh --gta-split-dir SPLIT --framework dist/samp-client-framework.apk --out client.apk
#
# Exit codes: 0 patched + verified, 1 bad input or failed verification.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

GTA=""
SPLIT_DIR=""
FRAMEWORK="$ROOT/dist/samp-client-framework.apk"
OUT=""
KSDIR="${PATCHER_KEYSTORE_DIR:-$HOME/.samp-client}"
KS="$KSDIR/patcher.keystore"
KS_PASS="${PATCHER_STOREPASS:-patchit}"
KS_ALIAS="${PATCHER_KEYALIAS:-patcher}"

die() { echo "FAIL: $*" >&2; exit 1; }
log() { echo "  $*"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --gta-apk)   GTA="${2:-}"; shift 2 ;;
    --gta-split-dir) SPLIT_DIR="${2:-}"; shift 2 ;;
    --framework) FRAMEWORK="${2:-}"; shift 2 ;;
    --out)       OUT="${2:-}"; shift 2 ;;
    -h|--help)   sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$GTA" || -n "$SPLIT_DIR" ]] || die "one of --gta-apk or --gta-split-dir is required (a GTA SA install you own)"
[[ -n "$OUT" ]] || die "--out is required"
if [[ -n "$GTA" && -n "$SPLIT_DIR" ]]; then die "pass --gta-apk or --gta-split-dir, not both"; fi
if [[ -n "$GTA" ]]; then
  [[ -f "$GTA" ]] || die "no such file: $GTA"
else
  [[ -d "$SPLIT_DIR" ]] || die "not a directory: $SPLIT_DIR"
fi
[[ -f "$FRAMEWORK" ]] || die "no framework APK at $FRAMEWORK (run: bin/android-build.sh framework)"

# --- tools ------------------------------------------------------------------
BT="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
[[ -n "$BT" ]] || die "no build-tools in $ANDROID_HOME"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
AAPT2="$BT/aapt2"
KEYTOOL="$JAVA_HOME/bin/keytool"
for t in "$ZIPALIGN" "$APKSIGNER" "$AAPT2" "$KEYTOOL"; do
  [[ -x "$t" ]] || die "missing tool: $t"
done
command -v unzip >/dev/null || die "missing unzip"
command -v zip >/dev/null || die "missing zip"

# --- inputs: what is actually in there? -------------------------------------
echo "== inspecting the game install =="
# Which APK actually carries the library, and which one declares the package?
PKG_APK=""
GAME_APK=""
if [[ -n "$GTA" ]]; then
  PKG_APK="$GTA"
  GAME_APK="$GTA"
else
  shopt -s nullglob
  candidates=("$SPLIT_DIR"/*.apk)
  shopt -u nullglob
  [[ ${#candidates[@]} -gt 0 ]] || die "no .apk files in $SPLIT_DIR"
  for f in "${candidates[@]}"; do
    pkg="$("$AAPT2" dump badging "$f" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
    if [[ "$pkg" == "com.rockstargames.gtasa" && -z "$PKG_APK" ]]; then
      PKG_APK="$f"
    fi
    if unzip -l "$f" 2>/dev/null | grep -q "lib/arm64-v8a/libGame.so" && [[ -z "$GAME_APK" ]]; then
      GAME_APK="$f"
    fi
  done
  [[ -n "$PKG_APK" ]] || die "no APK in $SPLIT_DIR declares com.rockstargames.gtasa"
  [[ -n "$GAME_APK" ]] || die "none of the ${#candidates[@]} APKs in $SPLIT_DIR carries lib/arm64-v8a/libGame.so (wrong ABI split?)"
  log "package APK : $(basename "$PKG_APK")"
  log "library APK : $(basename "$GAME_APK")"
fi

unzip -l "$GAME_APK" | grep -q "lib/arm64-v8a/libGame.so" \
  || die "$(basename "$GAME_APK") has no lib/arm64-v8a/libGame.so — wrong APK (arm64-v8a build required)"
GTA_PKG="$("$AAPT2" dump badging "$PKG_APK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
[[ "$GTA_PKG" == "com.rockstargames.gtasa" ]] \
  || die "$(basename "$PKG_APK") is not GTA San Andreas (package '$GTA_PKG')"
log "source package: $GTA_PKG"

echo "== inspecting framework APK =="
FW_LIST="$(unzip -l "$FRAMEWORK")"
grep -q "lib/arm64-v8a/libsamp.so" <<<"$FW_LIST" || die "$FRAMEWORK has no libsamp.so"
if grep -q "lib/arm64-v8a/libGame.so" <<<"$FW_LIST"; then
  die "$FRAMEWORK already contains libGame.so — pass a framework APK, not a full client"
fi
FW_PKG="$("$AAPT2" dump badging "$FRAMEWORK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
[[ "$FW_PKG" == "com.ferzdevzgta.game" ]] || die "unexpected framework package: $FW_PKG"
log "framework package: $FW_PKG"

# --- stage ------------------------------------------------------------------
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "== extracting libGame.so from the game APK =="
unzip -o -j -q "$GAME_APK" "lib/arm64-v8a/libGame.so" -d "$TMP/fetch" || die "cannot extract libGame.so"
GAME_SO="$TMP/fetch/libGame.so"
[[ -s "$GAME_SO" ]] || die "extracted libGame.so is empty"
log "libGame.so: $(du -h "$GAME_SO" | cut -f1)"
file "$GAME_SO" | grep -q 'ELF 64-bit LSB shared object, ARM aarch64' \
  || die "libGame.so in that APK is not an arm64 shared object"

echo "== assembling =="
cp "$FRAMEWORK" "$TMP/work.apk"
mkdir -p "$TMP/inject/lib/arm64-v8a"
cp "$GAME_SO" "$TMP/inject/lib/arm64-v8a/libGame.so"
# -0 = store, not deflate: an uncompressed native library is mmap'd straight from
# the APK, a compressed one cannot be. -X drops the extra fields that break alignment.
( cd "$TMP/inject" && zip -0 -X -q "$TMP/work.apk" lib/arm64-v8a/libGame.so ) \
  || die "cannot inject libGame.so"

echo "== aligning (page boundary for .so, 4 bytes for the rest) =="
"$ZIPALIGN" -p -f 4 "$TMP/work.apk" "$TMP/aligned.apk" >/dev/null || die "zipalign failed"
"$ZIPALIGN" -c -p -v 4 "$TMP/aligned.apk" >/dev/null || die "alignment check failed"

# --- signing key ------------------------------------------------------------
if [[ ! -f "$KS" ]]; then
  echo "== creating a local signing key (first run only) =="
  mkdir -p "$KSDIR"
  chmod 700 "$KSDIR"
  "$KEYTOOL" -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=SA-MP Client Patcher, O=local, C=ID" >/dev/null 2>&1 \
    || die "keytool failed to create $KS"
  chmod 600 "$KS"
  log "key: $KS (alias $KS_ALIAS)"
fi

echo "== signing =="
"$APKSIGNER" sign --ks "$KS" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$TMP/signed.apk" "$TMP/aligned.apk" || die "apksigner sign failed"

# --- verify the artifact, not the exit codes --------------------------------
echo "== verifying the result =="
"$APKSIGNER" verify --print-certs "$TMP/signed.apk" >/dev/null 2>&1 \
  || die "signature verification failed"
OUT_LIST="$(unzip -l "$TMP/signed.apk")"
for lib in libsamp.so libGame.so; do
  grep -q "lib/arm64-v8a/$lib" <<<"$OUT_LIST" || die "patched APK is missing $lib"
done
# stored, not deflated: zipalign -c -p above proves alignment, this proves storage
for lib in libsamp.so libGame.so; do
  INFO="$(unzip -v "$TMP/signed.apk" "lib/arm64-v8a/$lib")"
  grep -q "lib/arm64-v8a/$lib" <<<"$INFO" || die "cannot read $lib inside the patched APK"
  if ! grep -q 'Stored' <<<"$INFO"; then
    die "$lib is deflated in the patched APK; an uncompressed native library is mmap'd directly, a compressed one is not"
  fi
  log "$lib: stored, uncompressed"
done
# signing must not have broken the alignment done before it
"$ZIPALIGN" -c -p 4 "$TMP/signed.apk" >/dev/null || die "the signed APK is no longer aligned"
PATCHED_PKG="$("$AAPT2" dump badging "$TMP/signed.apk" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
[[ "$PATCHED_PKG" == "com.ferzdevzgta.game" ]] || die "patched APK has package $PATCHED_PKG"

mkdir -p "$(dirname "$OUT")"
cp "$TMP/signed.apk" "$OUT"
( cd "$(dirname "$OUT")" && sha256sum "$(basename "$OUT")" > "$(basename "$OUT").sha256" )

echo
echo "PASS"
echo "  apk      : $OUT ($(du -h "$OUT" | cut -f1))"
echo "  package  : $PATCHED_PKG"
echo "  sha256   : $(cut -d' ' -f1 "$OUT.sha256")"
echo "  next     : adb install -r $OUT"
echo "             then put the asset tree at /storage/emulated/0/GTA/ (bin/build-cache.sh)"
