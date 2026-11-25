# samp-client-ferzdevz

Android SA-MP client workbench — builds a native SA-MP client (`libsamp.so`, arm64-v8a)
from source, on Linux, with a reproducible one-command gate.

Built on the open-source reference client [`kuzia15/SAMP-Mobile`](https://github.com/kuzia15/SAMP-Mobile)
(branch `GTA-2.11`), which is not vendored into this repo — see *Reference checkout* below.

## Status

| Item | State |
|---|---|
| Toolchain (JDK 17, SDK, NDK r26c, cmake 3.22.1) | installed and pinned |
| `libsamp.so` (arm64-v8a) builds from source | **yes** — 3.5 MB |
| Debug APK assembles | **yes** — 43 MB, 1011 entries |
| Clean rebuild from zero | **yes** — 2m12s, 0 warnings |
| Patches required for Linux | 3 text + 1 binary duplicate removal, see `.patches/linux-port.patch` |
| Telemetry stripped | **no** — reference tree still carries Firebase/AppLovin/Splunk |
| Distributable artifact | **no** — not yet, see *Legal boundary* |

## Build

```bash
bin/android-build.sh          # debug APK
bin/android-build.sh clean    # wipe + full rebuild
bin/android-build.sh release  # release APK
```

The script is the gate: it preflights the toolchain, writes `local.properties`,
fixes the `gradlew` exec bit, then **verifies the artifact** (`libsamp.so` and
`libGame.so` present inside the APK) instead of trusting gradle's exit code.

Prerequisites:

```bash
sudo apt install -y openjdk-17-jdk-headless
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install \
  'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' \
  'ndk;26.2.11394342' 'cmake;3.22.1'
```

## Reference checkout

`vendor/samp-mobile` is a git-ignored reference tree:

```bash
mkdir -p vendor && git clone --depth 1 -b GTA-2.11 \
  https://github.com/kuzia15/SAMP-Mobile vendor/samp-mobile
bin/fetch-ref.sh   # applies .patches/linux-port.patch + removes duplicate bass libs
```

What `fetch-ref.sh` changes (all four documented in `MEMORY.md`):

1. two case-mismatched `#include`s — the tree was authored on Windows
2. `libbass.so` / `libbass_ssl.so` deleted from `jniLibs/` (duplicate of the CMake link)
3. `org.gradle.jvmargs` appended as a new line, not glued to the previous key
4. `gradlew` made executable

## Legal boundary

This repo contains **no Rockstar code and no Rockstar assets**. The reference tree
ships `libGame.so` and the `com.rockstargames.*` Java classes; those are never
committed here and must never be redistributed. What we ship is our own compiled
`libsamp.so` plus a patcher that operates on a game the user already owns.

Grand Theft Auto, San Andreas and SA-MP are trademarks of their respective owners.
