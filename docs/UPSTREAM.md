# Delta vs the upstream reference

Base: [`kuzia15/SAMP-Mobile`](https://github.com/kuzia15/SAMP-Mobile), branch
`GTA-2.11`, commit `0a805d4`. `client/` is that tree with the changes below and
nothing else. The changes are in the source; there is no patch file to apply.

## Ported to Linux

The tree was authored on Windows and did not compile on Linux at all.

| Change | File | Why |
|---|---|---|
| `#include "samp_widgets/playerTabList.h"` → `playertablist.h` | `cpp/samp/gui/gui.h` | Linux filesystems are case-sensitive |
| `#include "RGBA.h"` → `rgba.h` | `cpp/samp/game/rgba.cpp` | same |
| `gradlew` exec bit | `client/gradlew` | git cannot store it; the build gate sets it |
| trailing newline in `gradle.properties` | `client/gradle.properties` | the last key had none, so the next append was glued to it and ignored |
| deleted `jniLibs/arm64-v8a/libbass.so`, `libbass_ssl.so` | — | duplicated the CMake link line, causing a duplicate-library error at link |

## Rebranded

| Before | After |
|---|---|
| `applicationId` / `namespace` `com.gta.game` | `com.ferzdevzgta.game` |
| Java package `com.gta.game`, `com.gta.launcher` | `com.ferzdevzgta.game`, `com.ferzdevzgta.launcher` |
| 5 JNI symbols `Java_com_gta_game_*` | `Java_com_ferzdevzgta_game_*` |
| app label `GTA 2.11` | `SA-MP FerzDevZ` |
| APK name `GTA-2.11-<variant>.apk` | `samp-client-<variant>.apk` |
| `versionName 1.0.0` | `0.1.0` |
| `rootProject.name "GTA-2.11"` | `samp-client-ferzdevz` |

`com.rockstargames.*` and `com.nvidia.devtech` were **not** renamed: a prebuilt
binary pins them. Evidence and commands in
[ARCHITECTURE.md](ARCHITECTURE.md#namespaces-that-must-not-be-renamed).

## Telemetry removed

The reference shipped crash and usage reporting to a Firebase project that is not
ours. All of it is gone:

| Removed | From |
|---|---|
| `com.google.gms.google-services` plugin, classpath, `google-services.json` | `build.gradle.kts`, `app/build.gradle.kts`, `app/` |
| `com.google.firebase.crashlytics` plugin + `firebaseCrashlytics {}` release block | same |
| `firebase-analytics`, `firebase-messaging`, `firebase-config`, firebase BOM | `app/build.gradle.kts` |
| `firebase::crashlytics::Log()` call and `crashlytics.h` include | `cpp/samp/main.cpp` |
| AppLovin and Splunk Mint maven repositories | `build.gradle.kts` |

Effect on the artifact: APK 43 MB → 36 MB, and `unzip -l … | grep -ci firebase`
returns 0.

## Added in this repo

| Path | Purpose |
|---|---|
| `bin/fetch-blobs.sh` | copies the two binary sets that cannot be tracked |
| `bin/build-cache.sh` | builds `full` and `lite` asset trees with SHA-256 manifests |
| `bin/patch-apk.sh` | completes the framework APK with a game-owned `libGame.so` |
| `bin/android-build.sh` | build gate with `native` (CI) and `framework` (publishable) modes |
| `.github/workflows/ci.yml` | compile-and-verify, fails if a proprietary binary is staged |
| `docs/` | this documentation |

## Not done here

- no change to the SA-MP netcode, hooks, chat, GUI or voice code;
- no second ABI (`armeabi-v7a`, `x86_64` were never built);
- no signature for release builds — `release` output is unsigned until you add a
  keystore and `keystore.properties`.
