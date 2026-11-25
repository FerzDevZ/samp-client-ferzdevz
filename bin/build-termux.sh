#!/usr/bin/env bash
# One command on the phone: our framework APK plus your own GTA SA install, nothing else.
#
# This is the entry point for Termux. It resolves the two halves, refuses a framework that is
# older than the source you are looking at, reports which tools are missing, and then hands the
# work to bin/patch-termux.sh, which is the only place that knows how to patch.
#
# Why a separate script exists: bin/patch-apk.sh needs the Android SDK (zipalign, aapt2 are
# x86-64), bin/patch-termux.sh needs none, but until now a phone user had to know which one to
# call and had no way to notice they were patching an APK built before their last change.
#
# Usage:
#   bin/build-termux.sh --gta-apk ~/storage/downloads/GTA.apk
#   bin/build-termux.sh --gta-split-dir ~/gta-split --out ~/samp-client.apk
#   bin/build-termux.sh --from-url http://100.85.253.77:8000/samp-client-framework.apk --gta-apk GTA.apk
#   bin/build-termux.sh --check-only
#
# Flags:
#   --framework PATH   framework APK (default: dist/samp-client-framework.apk)
#   --from-url URL     download the framework first when it is not on disk
#   --gta-apk PATH     a GTA SA APK you own
#   --gta-split-dir D  a directory holding base.apk + split_config.arm64_v8a.apk
#   --out PATH         where the finished APK goes
#   --force            patch even when the framework is older than the source
#   --check-only       report, change nothing
#
# Exit codes: 0 built and verified, 1 bad input, stale framework or failed verification.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRAMEWORK="$ROOT/dist/samp-client-framework.apk"
FROM_URL=""
GTA=""
SPLIT_DIR=""
OUT="$ROOT/dist/samp-client.apk"
FORCE=0
CHECK_ONLY=0
SRC="$ROOT/client/app/src/main"

die() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
log() { printf '  %s\n' "$*"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --framework)      FRAMEWORK="${2:-}"; shift 2 ;;
    --from-url)       FROM_URL="${2:-}"; shift 2 ;;
    --gta-apk|--gta)  GTA="${2:-}"; shift 2 ;;
    --gta-split-dir)  SPLIT_DIR="${2:-}"; shift 2 ;;
    --out)            OUT="${2:-}"; shift 2 ;;
    --force)          FORCE=1; shift ;;
    --check-only)     CHECK_ONLY=1; shift ;;
    -h|--help)        sed -n '2,29p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

echo "== who needs a pc =="
need=0
for tool in apksigner zip unzip keytool python3; do
  if command -v "$tool" >/dev/null 2>&1; then
    log "$tool present"
  else
    log "$tool MISSING (pkg install openjdk-21 apksigner zip unzip python)"
    need=1
  fi
done
if command -v aapt2 >/dev/null 2>&1; then
  log "aapt2 present, but this path never uses it"
else
  log "aapt2 absent, which is expected on a phone and does not matter here"
fi

echo "== framework =="
if [ ! -f "$FRAMEWORK" ] && [ -n "$FROM_URL" ]; then
  command -v curl >/dev/null 2>&1 || die "need curl to download the framework"
  mkdir -p "$(dirname "$FRAMEWORK")"
  log "downloading $FROM_URL"
  curl -fL --retry 2 -o "$FRAMEWORK.part" "$FROM_URL" || die "download failed"
  mv "$FRAMEWORK.part" "$FRAMEWORK"
fi
[ -f "$FRAMEWORK" ] || die "no framework APK at $FRAMEWORK (pass --framework or --from-url)"
log "framework: $FRAMEWORK ($(wc -c <"$FRAMEWORK" | tr -d ' ') bytes)"

# A framework built before the last edit ships the old UI with a new library inside, and nothing
# during the patch would notice. Compare times and refuse, unless the caller says otherwise.
if [ "$FORCE" -eq 0 ]; then
  newest="$(find "$SRC" -type f \( -name '*.java' -o -name '*.xml' -o -name '*.html' -o -name '*.cpp' -o -name '*.h' \) -newer "$FRAMEWORK" -print -quit 2>/dev/null || true)"
  if [ -n "$newest" ]; then
    printf 'FAIL: %s is newer than the framework APK\n' "${newest#$ROOT/}" >&2
    printf '  build the framework again (bin/android-build.sh framework), or pass --force\n' >&2
    exit 1
  fi
  log "framework is newer than every source file"
fi

if [ "$CHECK_ONLY" -eq 1 ]; then
  echo "== inputs =="
  [ -n "$GTA" ] && log "gta apk: $GTA"
  [ -n "$SPLIT_DIR" ] && log "gta split dir: $SPLIT_DIR"
  [ -z "$GTA$SPLIT_DIR" ] && log "no GTA install given yet"
  printf 'ok: %d missing tool(s), pc needed: none\n' "$need"
  exit 0
fi

[ "$need" -eq 0 ] || die "install the missing tools first"

echo "== patch =="
args=(--framework "$FRAMEWORK" --out "$OUT")
[ -n "$GTA" ] && args+=(--gta-apk "$GTA")
[ -n "$SPLIT_DIR" ] && args+=(--gta-split-dir "$SPLIT_DIR")
bash "$ROOT/bin/patch-termux.sh" "${args[@]}"
