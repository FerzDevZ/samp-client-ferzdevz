# samp-client-ferzdevz

Android SA-MP client. Native code (`libsamp.so`, arm64-v8a) builds from source on
Linux, joins SA-MP servers, and runs the GTA San Andreas engine through hooks into
a game library the device already has.

Source: `client/`, forked from [`kuzia15/SAMP-Mobile`](https://github.com/kuzia15/SAMP-Mobile)
branch `GTA-2.11`, then ported to Linux, rebranded and stripped of telemetry.
Every change is listed in [docs/UPSTREAM.md](docs/UPSTREAM.md).

**Bahasa Indonesia: [README.id.md](README.id.md)**

## Status

Everything below was executed on this machine, not assumed.

| Item | State |
|---|---|
| `libsamp.so` arm64-v8a from source | **yes**, 3.5 MB, 0 warnings |
| Debug APK | **yes**, 37 MB, `com.ferzdevzgta.game`, label `SA-MP FerzDevZ`, versionName `0.1.0` |
| Clean rebuild, no cache | **yes**, 2m08s, 0 warnings |
| Telemetry | **removed**, Firebase, AppLovin, Splunk; APK 43 MB → 37 MB |
| `libsamp.so` builds without Rockstar binary | **yes**, 10s, that is what CI runs |
| Game data | **not in this repo**, the app downloads it from your own host, see [docs/DATA.md](docs/DATA.md) |
| On-device data setup | **yes**, variant pick, SHA-256 verify, resumable download, share log |
| APK in a GitHub Release | **no**, it embeds Rockstar's `libGame.so`, see [docs/LEGAL.md](docs/LEGAL.md) |

## Getting an installable APK

There is no published APK. An installable one contains Rockstar's `libGame.so`
(8.5 MB) and cannot be redistributed from this repository, so it is built on your
machine from a game APK you own:

```bash
bin/android-build.sh framework        # 1. publishable half -> dist/samp-client-framework.apk (28 MB)
bin/patch-apk.sh --gta-apk GTA.apk \
                 --out dist/samp-client-0.1.0.apk      # 2. add libGame.so, realign, re-sign (37 MB)
adb install -r dist/samp-client-0.1.0.apk              # 3. install
adb push cache/full/. /storage/emulated/0/GTA/         # 4. assets, or the app closes on launch
```

Full flow, measured timings and the four refusal cases: [docs/PATCHER.md](docs/PATCHER.md).

If you want to compile without any game file at all:

```bash
bin/android-build.sh native     # libsamp.so only, 3.5 MB, that is what CI runs
```

## What each command proves

| Command | Proves |
|---|---|
| `bin/android-build.sh clean` | the whole client compiles and packages; inspects the APK instead of trusting gradle's exit code |
| `bin/android-build.sh framework` | the publishable half: same APK with Rockstar's `libGame.so` removed and re-signed |
| `bin/android-build.sh native` | our C++ compiles to a real ARM64 ELF with no Rockstar file present |
| `bin/build-cache.sh …` | the asset tree is complete and matches a SHA-256 manifest |
| `bin/build-cache.sh --verify` | an existing cache is byte-identical to its manifest |
| `bin/patch-apk.sh` | the completed APK installs: both native libs stored, aligned, signature valid |
| `bin/self-test.sh` | manifest parsing, the downloader against a local HTTP server, log rotation, the INI editor, the cache round trip and the patcher refusals, 15 checks, no device |

## Layout

```
client/     the client source (1322 files), see client/README.md
bin/        build, blob-fetch and cache gates, see bin/README.md
docs/       architecture, build, data, legal, operations, upstream delta
.github/    CI: compiles libsamp.so, refuses to ship proprietary binaries
dist/       last APK built here + SHA-256, git-ignored
```

## Documentation

English | Indonesian
---|---
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | [docs/id/ARCHITECTURE.md](docs/id/ARCHITECTURE.md)
[docs/BUILD.md](docs/BUILD.md) | [docs/id/BUILD.md](docs/id/BUILD.md)
[docs/DATA.md](docs/DATA.md) | [docs/id/DATA.md](docs/id/DATA.md)
[docs/OPERATIONS.md](docs/OPERATIONS.md) | [docs/id/OPERATIONS.md](docs/id/OPERATIONS.md)
[docs/LEGAL.md](docs/LEGAL.md) | [docs/id/LEGAL.md](docs/id/LEGAL.md)
[docs/UPSTREAM.md](docs/UPSTREAM.md) | [docs/id/UPSTREAM.md](docs/id/UPSTREAM.md)

## Requirements

JDK 17, Android SDK with `platforms;android-36`, `build-tools;36.0.0`,
`ndk;26.2.11394342`, `cmake;3.22.1`. Details and failure modes in
[docs/BUILD.md](docs/BUILD.md).

## Legal

No Rockstar code and no Rockstar assets are tracked here. `libGame.so`, the BASS
audio library and the game data stay out of git permanently. Grand Theft Auto and
San Andreas are trademarks of Take-Two Interactive; SA-MP is a separate
multiplayer mod. This project is not affiliated with either.
