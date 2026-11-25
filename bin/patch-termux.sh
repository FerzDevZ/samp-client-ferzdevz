#!/usr/bin/env bash
# Build an installable client APK on the phone, with no PC in the loop.
#
# Same contract as bin/patch-apk.sh: the only thing taken from the game APK you own is
# lib/arm64-v8a/libGame.so. It is added stored (uncompressed), page aligned by
# apksigner itself, and the result is signed and verified.
#
# Why not zipalign: zipalign and aapt2 are x86-64 Android SDK binaries and do not run on
# an aarch64 phone. `apksigner --lib-page-alignment 4096` page aligns the native
# libraries while signing, so the separate alignment step disappears. The result is
# checked by bin/apk-verify.py, which reads the zip structures instead of shelling out
# to the SDK.
#
# One-time setup in Termux:
#   pkg install openjdk-21 apksigner zip unzip python
#
# Usage:
#   bin/patch-termux.sh --gta-apk ~/storage/downloads/GTA.apk
#   bin/patch-termux.sh --gta-split-dir ~/gta-split --out ~/samp-client.apk
#
# Exit codes: 0 patched and verified, 1 bad input or failed verification.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PY="${PYTHON:-python3}"

GTA=""
SPLIT_DIR=""
FRAMEWORK="$ROOT/dist/samp-client-framework.apk"
OUT="$ROOT/dist/samp-client-termux.apk"
KSDIR="${PATCHER_KEYSTORE_DIR:-$HOME/.samp-client}"
KS="$KSDIR/patcher.keystore"
KS_PASS="${PATCHER_STOREPASS:-patchit}"
KS_ALIAS="${PATCHER_KEYALIAS:-patcher}"
PKG="com.ferzdevzgta.game"
ENTRY="lib/arm64-v8a/libGame.so"

die() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
log() { printf '  %s\n' "$*"; }

usage() { sed -n '2,24p' "${BASH_SOURCE[0]}"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --gta-apk|--gta) GTA="${2:-}"; shift 2 ;;
    --gta-split-dir) SPLIT_DIR="${2:-}"; shift 2 ;;
    --framework) FRAMEWORK="${2:-}"; shift 2 ;;
    --out) OUT="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[ -n "$GTA" ] || [ -n "$SPLIT_DIR" ] || die "pass --gta-apk or --gta-split-dir (a GTA SA install you own)"
[ -n "$GTA" ] && [ -n "$SPLIT_DIR" ] && die "pass --gta-apk or --gta-split-dir, not both"
if [ -n "$GTA" ]; then
  [ -f "$GTA" ] || die "no such file: $GTA"
else
  [ -d "$SPLIT_DIR" ] || die "not a directory: $SPLIT_DIR"
fi
[ -f "$FRAMEWORK" ] || die "no framework APK at $FRAMEWORK (build it, or copy it from the release page)"

echo "== tools =="
missing=""
for tool in apksigner zip unzip keytool "$PY"; do
  command -v "$tool" >/dev/null 2>&1 || missing="$missing $tool"
done
if [ -n "$missing" ]; then
  printf 'FAIL: missing tools:%s\n' "$missing" >&2
  printf '  in Termux:  pkg install openjdk-21 apksigner zip unzip python\n' >&2
  exit 1
fi
log "apksigner, zip, unzip, keytool, python3 present"

echo "== finding the two halves =="
# A modern install is split: the manifest lives in base.apk, the library in
# split_config.arm64_v8a.apk. Resolve by content, never by file name.
GAME_APK=""
PKG_APK=""
if [ -n "$GTA" ]; then
  unzip -l "$GTA" 2>/dev/null | grep -q " $ENTRY\$" || die "$GTA does not contain $ENTRY"
  GAME_APK="$GTA"
  PKG_APK="$GTA"
else
  for file in "$SPLIT_DIR"/*.apk; do
    [ -f "$file" ] || continue
    if unzip -l "$file" 2>/dev/null | grep -q " $ENTRY\$"; then
      GAME_APK="$file"
    elif unzip -l "$file" 2>/dev/null | grep -q "AndroidManifest.xml"; then
      PKG_APK="$file"
    fi
  done
  [ -n "$GAME_APK" ] || die "no APK under $SPLIT_DIR contains $ENTRY"
  [ -n "$PKG_APK" ] || die "no APK under $SPLIT_DIR contains AndroidManifest.xml"
fi
log "game library: ${GAME_APK##*/}"
log "manifest    : ${PKG_APK##*/}"

if unzip -l "$FRAMEWORK" | grep -q " $ENTRY\$"; then
  die "$FRAMEWORK already contains $ENTRY; that is a finished APK, not a framework"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/patch-termux.XXXXXX")"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT INT TERM

echo "== injecting $ENTRY =="
unzip -o -j -q "$GAME_APK" "$ENTRY" -d "$TMP/fetch" || die "cannot extract $ENTRY"
SO="$TMP/fetch/libGame.so"
mkdir -p "$TMP/stage/lib/arm64-v8a"
cp "$SO" "$TMP/stage/lib/arm64-v8a/libGame.so"
cp "$FRAMEWORK" "$TMP/work.apk"
# -0 stores, -X drops the extra attributes zip adds on different filesystems.
( cd "$TMP/stage" && zip -0 -X -q "$TMP/work.apk" "$ENTRY" ) || die "zip could not add $ENTRY"
log "$ENTRY added stored, $(wc -c <"$SO" | tr -d ' ') bytes"

echo "== signing =="
if [ ! -f "$KS" ]; then
  mkdir -p "$KSDIR"
  keytool -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=SA-MP Client Patcher, O=local, C=ID" >/dev/null 2>&1 \
    || die "keytool could not create $KS"
  chmod 600 "$KS"
  log "key: $KS (alias $KS_ALIAS)"
fi

mkdir -p "$(dirname "$OUT")"
apksigner sign \
  --min-sdk-version 28 \
  --lib-page-alignment 4096 \
  --ks "$KS" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$OUT" "$TMP/work.apk" || die "apksigner sign failed"

echo "== verifying the result =="
apksigner verify --min-sdk-version 28 --print-certs "$OUT" >/dev/null 2>&1 \
  || die "signature verification failed"
"$PY" "$ROOT/bin/apk-verify.py" --apk "$OUT" --entry "$ENTRY" --so "$SO" --expect-package "$PKG" \
  || die "artifact verification failed"

if command -v sha256sum >/dev/null 2>&1; then
  ( cd "$(dirname "$OUT")" && sha256sum "$(basename "$OUT")" >"$(basename "$OUT").sha256" )
  SHA="$(cut -d' ' -f1 "$(dirname "$OUT")/$(basename "$OUT").sha256")"
else
  SHA="unavailable"
fi

SIZE="$(wc -c <"$OUT" | tr -d ' ')"
echo
echo "PASS"
echo "  apk      : $OUT ($((SIZE / 1048576)) MB)"
echo "  package  : $PKG"
echo "  sha256   : $SHA"
echo "  next     : termux-open --content-type application/vnd.android.package-archive $OUT"
echo "             then keep the asset tree at /storage/emulated/0/GTA/ (docs/DATA.md)"
