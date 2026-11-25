# Legal boundary

Not legal advice. It is the line this repository draws, and the reason for each
part of it.

## What this repo contains

| Tracked | Why it is allowed |
|---|---|
| `client/` source, 1322 files, C++ and Java | forked open-source client code, modified |
| our compiled `libsamp.so` (CI artifact) | our build output, no Rockstar bytes needed to produce it |
| `bin/`, `docs/`, `.github/` | tooling and text |

## What it must not contain

| Ignored | Owner | Reason |
|---|---|---|
| `client/app/src/main/jniLibs/arm64-v8a/libGame.so` (8.5 MB) | Rockstar | their compiled game library |
| `client/app/src/main/cpp/samp/vendor/bass/libs/**/*.so` (7.9 MB) | un4seen | BASS licence forbids redistribution |
| the asset tree (`/storage/emulated/0/GTA/`) | Rockstar | game data |
| `dist/*.apk` | mixed | the APK embeds `libGame.so` |

`bin/fetch-blobs.sh` copies the first two from the upstream reference tree on a
machine that is allowed to hold them. It does not make them distributable.

## Why the APK is not published here

The APK is 36 MB and contains `libGame.so`. Uploading it to a GitHub Release is
redistribution of Rockstar's binary. It is also why CI stops at `libsamp.so`:
that artifact contains only code from this repo.

If you choose to distribute the APK yourself, that is your account and your
decision; nothing in this repo automates it.

## The framework APK, and what is still unresolved about it

`bin/android-build.sh framework` produces an APK with Rockstar's `libGame.so`
removed. It contains no Rockstar binary, but it does contain the
`com/rockstargames/oswrapper/` Java classes, which came out of the game's DEX via
jadx. Those are a derivative of Rockstar's code, not a clean-room rewrite.

That distinction matters if you publish it: removing a binary is not the same as
replacing decompiled source. Treat the framework as something to build locally,
and treat a clean-room rewrite of the wrapper as outstanding work. The patcher and
`libsamp.so` are unaffected, they are ours.

## Names left in place

Three Java packages keep their original names because a prebuilt binary refers to
them by path, renaming them breaks the app, not the copyright status:

| Package | Evidence |
|---|---|
| `com.rockstargames.oswrapper` | 38 `Java_com_rockstargames_oswrapper_GameNative_*` symbols in `libGame.so`; `DeviceInfo`, `GamePlatformServices` strings |
| `com.nvidia.devtech` | `com/nvidia/devtech/NvUtil` string in `libGame.so` |
| `com.rockstargames.gtasa` | sits directly on the frozen wrapper; reimplemented activity shell `GameActivity`, `DownloaderActivity` |

The client's own namespace is `com.ferzdevzgta.game` / `com.ferzdevzgta.launcher`.
See [ARCHITECTURE.md](ARCHITECTURE.md#namespaces-that-must-not-be-renamed) for the
commands that produce that evidence.

## Trademarks

Grand Theft Auto and San Andreas are trademarks of Take-Two Interactive. SA-MP is
the name of a separate multiplayer mod. This project is not affiliated with,
endorsed by, or supported by either.
