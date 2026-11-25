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
| `libsamp.so` arm64-v8a from source | **yes** — 3.5 MB, 0 warnings |
| Debug APK | **yes** — 36 MB, `com.ferzdevzgta.game`, label `SA-MP FerzDevZ`, versionName `0.1.0` |
| Clean rebuild, no cache | **yes** — 2m08s, 0 warnings |
| Telemetry | **removed** — Firebase, AppLovin, Splunk; APK 43 MB → 36 MB |
| `libsamp.so` builds without Rockstar binary | **yes** — 10s, that is what CI runs |
| Game data | **not in this repo** — see [docs/DATA.md](docs/DATA.md) |
| APK in a GitHub Release | **no** — it embeds Rockstar's `libGame.so`, see [docs/LEGAL.md](docs/LEGAL.md) |

## Download

There is no download link. The APK cannot be published from here, and a link
without the asset tree would install and then close itself.

Build it, ~3 minutes:

```bash
git clone https://github.com/FerzDevZ/samp-client-ferzdevz
cd samp-client-ferzdevz
bin/fetch-blobs.sh        # libGame.so + BASS .so from the upstream reference
bin/android-build.sh      # -> client/app/build/outputs/apk/debug/samp-client-debug.apk
adb install -r client/app/build/outputs/apk/debug/samp-client-debug.apk
```

Then put the asset tree at `/storage/emulated/0/GTA/`, or the app closes on
launch — [docs/DATA.md](docs/DATA.md).

If you only want to check that the code compiles, skip both:

```bash
bin/android-build.sh native     # libsamp.so only, no Rockstar binary needed
```

## What each command proves

| Command | Proves |
|---|---|
| `bin/android-build.sh clean` | the whole client compiles and packages; inspects the APK instead of trusting gradle's exit code |
| `bin/android-build.sh native` | our C++ compiles to a real ARM64 ELF with no Rockstar file present |
| `bin/build-cache.sh …` | the asset tree is complete and matches a SHA-256 manifest |
| `bin/build-cache.sh --verify` | an existing cache is byte-identical to its manifest |

## Layout

```
client/     the client source (1322 files) — see client/README.md
bin/        build, blob-fetch and cache gates — see bin/README.md
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

`MEMORY.md` is the invariant ledger: every non-trivial failure this project hit,
with its root cause and the rule that prevents a repeat.

## Requirements

JDK 17, Android SDK with `platforms;android-36`, `build-tools;36.0.0`,
`ndk;26.2.11394342`, `cmake;3.22.1`. Details and failure modes in
[docs/BUILD.md](docs/BUILD.md).

## Legal

No Rockstar code and no Rockstar assets are tracked here. `libGame.so`, the BASS
audio library and the game data stay out of git permanently. Grand Theft Auto and
San Andreas are trademarks of Take-Two Interactive; SA-MP is a separate
multiplayer mod. This project is not affiliated with either.
