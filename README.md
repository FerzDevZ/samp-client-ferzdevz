# samp-client-ferzdevz

Android SA-MP client workbench — builds a native SA-MP client (`libsamp.so`, arm64-v8a)
from source, on Linux, with reproducible one-command gates.

Source is tracked in `client/`, forked from the open-source reference client
[`kuzia15/SAMP-Mobile`](https://github.com/kuzia15/SAMP-Mobile) (branch `GTA-2.11`).
What had to change for Linux, and why, is in `.patches/linux-port.patch` and `MEMORY.md`.

## Status

| Item | State |
|---|---|
| Toolchain (JDK 17, SDK, NDK r26c, cmake 3.22.1) | installed and pinned |
| `libsamp.so` (arm64-v8a) builds from source | **yes** — 3.5 MB, 0 warnings |
| Debug APK assembles | **yes** — 36 MB, `com.ferzdevzgta.game` |
| Clean rebuild from zero | **yes** — ~2m50s, 0 warnings |
| Telemetry | **stripped** — Firebase/AppLovin/Splunk removed, APK 43 MB → 36 MB |
| Game data (`/storage/emulated/0/GTA/`) | **not hosted here** — see [`docs/CACHE.md`](docs/CACHE.md) |
| Distributable APK | **no** — needs Rockstar's `libGame.so`, see *Legal boundary* |

## Build

```bash
bin/fetch-blobs.sh            # once: copies libGame.so + BASS .so from upstream
bin/android-build.sh          # debug APK
bin/android-build.sh clean    # wipe + full rebuild
bin/android-build.sh release  # release APK
bin/android-build.sh native   # C++ only (libsamp.so) — no libGame.so needed, CI mode
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

## Game data

The client reads assets from `/storage/emulated/0/GTA/`. Those are Rockstar's
files, so this repo does not carry them and you cannot download them here.
`bin/build-cache.sh` turns a copy you already own into the layout the client
expects, in both community variants:

```bash
bin/build-cache.sh --apk GTA.apk --obb main.8.com.rockstargames.gtasa.obb --out cache
bin/build-cache.sh --out cache --verify          # SHA-256 manifest check
adb push cache/full/. /storage/emulated/0/GTA/
```

| Variant | Contents | Rough size |
|---|---|---|
| `full` | everything — radio audio, cutscene movies, textures, models | ~1.5–2.5 GB |
| `lite` | `audio/` + `movies/` removed | ~0.4–0.9 GB |

Each variant gets a SHA-256 manifest, so a transfer can be proven intact.
Full rationale, limits and DMCA reality: [`docs/CACHE.md`](docs/CACHE.md).

## What git does and does not hold

Tracked: all client source (1322 files), build gates, CI.

Deliberately ignored, because neither can be redistributed:

| Ignored | Why | How to get it |
|---|---|---|
| `client/app/src/main/jniLibs/arm64-v8a/libGame.so` | Rockstar's game library | `bin/fetch-blobs.sh` (from a reference tree) or your own install |
| `client/app/src/main/cpp/samp/vendor/bass/libs/**/*.so` | BASS licence forbids redistribution | `bin/fetch-blobs.sh` |
| `client/app/src/main/cpp/cpp.zip` | 7.2 MB zip of files already on disk | not needed |
| `client/**/build/`, `.cxx/`, `.gradle/` | build output | `bin/android-build.sh` |

CI (`.github/workflows/ci.yml`) proves only what is ours: that `libsamp.so`
compiles to a real ARM64 shared object with zero warnings **with no Rockstar
binary present**, and it fails the build if a proprietary binary is ever staged
into git. It does not assemble or publish a full APK — that needs `libGame.so`.

## Legal boundary

This repo contains **no Rockstar code and no Rockstar assets**. `libGame.so`, the
`com.rockstargames.*` Java classes and the game cache live outside git and must
never be redistributed from here. What is ours: the client code, the `libsamp.so`
we compile, and the scripts that operate on a game the user already owns.

Grand Theft Auto, San Andreas and SA-MP are trademarks of their respective owners.
