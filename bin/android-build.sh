#!/usr/bin/env bash
# Gate 1 for the Android SA-MP client: build libsamp.so + APK, or fail loudly.
#
#   bin/android-build.sh            # debug APK
#   bin/android-build.sh release    # release APK (unsigned unless keystore set)
#   bin/android-build.sh clean      # wipe build dirs first
#   bin/android-build.sh native     # C++ only: libsamp.so, no APK, no libGame.so
#                                   #   (CI mode: proves our own code compiles
#                                   #    without touching Rockstar binaries)
#   bin/android-build.sh framework  # APK with libGame.so removed and re-signed:
#                                   #   the distributable half, completed later by
#                                   #   bin/patch-apk.sh using a game APK you own
#
# Requires: JDK 17, Android SDK with ndk;26.2.11394342 + cmake;3.22.1.
# Verified green on: openjdk 17.0.21.1, NDK r26c, cmake 3.22.1, gradle 8.13.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJ="$ROOT/client"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

VARIANT="debug"
CLEAN=0
NATIVE_ONLY=0
FRAMEWORK=0
case "${1:-debug}" in
  debug)     VARIANT="debug" ;;
  release)   VARIANT="release" ;;
  clean)     CLEAN=1 ;;
  native)    NATIVE_ONLY=1 ;;
  framework) FRAMEWORK=1 ;;
  *) echo "usage: $0 [debug|release|clean|native|framework]" >&2; exit 2 ;;
esac

die() { echo "FAIL: $*" >&2; exit 1; }

# --- preflight -------------------------------------------------------------
[[ -x "$JAVA_HOME/bin/java" ]] || die "no JDK at JAVA_HOME=$JAVA_HOME (apt install openjdk-17-jdk-headless)"
[[ -d "$ANDROID_HOME/ndk/26.2.11394342" ]] || die "NDK 26.2.11394342 missing in $ANDROID_HOME (sdkmanager --install 'ndk;26.2.11394342')"
[[ -x "$ANDROID_HOME/cmake/3.22.1/bin/ninja" ]] || die "cmake 3.22.1 missing in $ANDROID_HOME"
[[ -d "$PROJ" ]] || die "client/ not found"

# AGP reads the SDK path from local.properties, never from env alone.
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$PROJ/local.properties"

# The repo ships gradlew without the exec bit.
chmod +x "$PROJ/gradlew"

# Properties file must end in a newline; appending with >> corrupts the last key.
# (no pipeline into grep -q here: SIGPIPE + pipefail turns a success into a failure)
if [[ "$(tail -c1 "$PROJ/gradle.properties" | od -An -c | tr -d ' \n')" != "\\n" ]]; then
  printf '\n' >> "$PROJ/gradle.properties"
fi

# --- build -----------------------------------------------------------------
cd "$PROJ"
if [[ $CLEAN -eq 1 ]]; then
  ./gradlew clean --no-daemon
fi

echo "== building :app:assemble${VARIANT^} (arm64-v8a) =="
if [[ $NATIVE_ONLY -eq 1 ]]; then
  # externalNativeBuildDebug runs CMake/Ninja and links libsamp.so but does not
  # package an APK, so Rockstar's libGame.so is not needed at all.
  ./gradlew ":app:externalNativeBuild${VARIANT^}" --no-daemon
else
  ./gradlew ":app:assemble${VARIANT^}" --no-daemon
fi

# --- verify the artifact, not the exit code --------------------------------
if [[ $NATIVE_ONLY -eq 1 ]]; then
  SO="$(find app/build/intermediates/cxx -name libsamp.so -path '*arm64-v8a*' | head -1)"
  [[ -n "$SO" ]] || die "gradle exited 0 but no libsamp.so under app/build/intermediates/cxx"
  echo
  echo "PASS (native only)"
  echo "  libsamp  : $SO ($(du -h "$SO" | cut -f1))"
  echo "  abi      : arm64-v8a"
  echo "  apk      : skipped: an installable APK needs libGame.so (bin/fetch-blobs.sh)"
  exit 0
fi

APK="$(find app/build/outputs/apk -name "*-${VARIANT}.apk" -newermt '-10 minutes' | head -1)"
[[ -n "$APK" ]] || die "gradle exited 0 but no ${VARIANT} APK under app/build/outputs/apk"

# unzip once into a variable: piping into `grep -q` raises SIGPIPE, which pipefail
# turns into a spurious failure even when the match succeeded.
APK_INDEX="$(unzip -l "$APK")"
for lib in libsamp.so libGame.so; do
  grep -q "lib/arm64-v8a/$lib" <<<"$APK_INDEX" || die "$APK is missing lib/arm64-v8a/$lib"
done

SO="$(find app/build/intermediates/cxx -name libsamp.so -path '*arm64-v8a*' | head -1)"
echo
echo "PASS"
echo "  apk      : $APK ($(du -h "$APK" | cut -f1))"
echo "  libsamp  : $SO ($(du -h "$SO" 2>/dev/null | cut -f1))"
echo "  abi      : arm64-v8a"

# --- framework mode: strip the game library, re-sign, publishable -----------
# An APK containing libGame.so cannot be redistributed. The framework is the same
# app with that one file removed and signed with the local patcher key, so
# bin/patch-apk.sh can put it back from a game APK the user owns.
if [[ $FRAMEWORK -eq 1 ]]; then
  BT="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
  [[ -x "$BT/zipalign" && -x "$BT/apksigner" ]] || die "no zipalign/apksigner in $BT"
  KSDIR="${PATCHER_KEYSTORE_DIR:-$HOME/.samp-client}"
  KS="$KSDIR/patcher.keystore"
  KS_PASS="${PATCHER_STOREPASS:-patchit}"
  KS_ALIAS="${PATCHER_KEYALIAS:-patcher}"
  if [[ ! -f "$KS" ]]; then
    mkdir -p "$KSDIR"; chmod 700 "$KSDIR"
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
      -storepass "$KS_PASS" -keypass "$KS_PASS" -keyalg RSA -keysize 2048 \
      -validity 10000 -dname "CN=SA-MP Client Patcher, O=local, C=ID" >/dev/null 2>&1 \
      || die "keytool failed to create $KS"
    chmod 600 "$KS"
  fi
  mkdir -p "$ROOT/dist"
  FW="$ROOT/dist/samp-client-framework.apk"
  cp "$APK" "$FW"
  zip -q -d "$FW" "lib/arm64-v8a/libGame.so" || die "cannot remove libGame.so from the copy"
  "$BT/zipalign" -p -f 4 "$FW" "$FW.aligned" >/dev/null || die "zipalign failed"
  "$BT/apksigner" sign --ks "$KS" --ks-key-alias "$KS_ALIAS" \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out "$FW.signed" "$FW.aligned" || die "apksigner sign failed"
  mv "$FW.signed" "$FW"; rm -f "$FW.aligned" "$FW.signed.idsig"
  "$BT/apksigner" verify "$FW" >/dev/null 2>&1 || die "framework signature does not verify"
  FW_INDEX="$(unzip -l "$FW")"
  grep -q 'lib/arm64-v8a/libsamp.so' <<<"$FW_INDEX" || die "framework is missing libsamp.so"
  if grep -q 'lib/arm64-v8a/libGame.so' <<<"$FW_INDEX"; then
    die "framework still contains libGame.so, it is not distributable"
  fi
  ( cd "$ROOT/dist" && sha256sum "$(basename "$FW")" > "$(basename "$FW").sha256" )
  echo "  framework: $FW ($(du -h "$FW" | cut -f1)), no libGame.so, signed"
  echo "  complete : bin/patch-apk.sh --gta-apk <your GTA SA apk> --out client.apk"
fi
exit 0
