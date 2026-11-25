# Game data: where the client gets it, and why this repo does not host it

Short answer: **the game data is not downloadable from this repo, and it never
will be.** It is Rockstar Games' copyrighted GTA San Andreas asset set. Each
player supplies it from a copy they own; this repo only ships the client code
that reads it.

## Where the client looks

Hardcoded in the native client, not configurable per build:

| Path | What lives there |
|---|---|
| `/storage/emulated/0/GTA/` | the asset tree (models, textures, audio, data) |
| `/storage/emulated/0/GTA/SAMP/` | `settings.ini`, `fonts/`, logs |

Source: `client/app/src/main/cpp/samp/main.cpp:36` (`g_pszStorage`) and
`client/app/src/main/cpp/samp/game/hooks.cpp:1385`.

## Where the player gets the assets

Any one of these, all legitimate because you already bought the game:

1. **Play Store install** on the device, then pull the assets:
   ```bash
   # app + expansion files of a purchase you own
   adb shell pm path com.rockstargames.gtasa
   adb pull /sdcard/Android/obb/com.rockstargames.gtasa/ ./obb/
   ```
2. **An APK + OBB you dumped from your own device** (same purchase, different
   transport).
3. **A cache you already built** for a previous client — as long as it came from
   your own copy.

Then normalise it into the layout the client expects:

```bash
bin/build-cache.sh --apk GTA.apk --obb main.8.com.rockstargames.gtasa.obb --out cache
adb push cache/full/. /storage/emulated/0/GTA/
```

## "full" vs "lite"

Both are produced by `bin/build-cache.sh` from the same owned source:

| Variant | Contents | Approx size | Use when |
|---|---|---|---|
| `full` | everything: radio audio, cutscene movies, textures, models | ~1.5–2.5 GB | single-player-grade fidelity, radio stations work |
| `lite` | `audio/` and `movies/` removed | ~0.4–0.9 GB | storage-tight phones, gameplay only, no radio/cutscenes |

The sizes are community ballpark, not measured here — this machine has no GTA SA
install. `bin/build-cache.sh` prints the real numbers for your own files, and
writes them into `manifest-full.json` / `manifest-lite.json`.

Every build also writes a SHA-256 manifest, so a transfer can be proven intact:

```bash
bin/build-cache.sh --out cache --verify
```

That is the whole point of the manifests: **checksums are distributable, the
bytes they describe are not.** You can hand someone `manifest-lite.json` and say
"your cache must look exactly like this"; you cannot hand them the cache.

## Why it is not on GitHub

Three independent blockers, any one of which is fatal:

1. **GitHub limits.** 100 MB hard cap per file; repos are soft-capped around
   2 GB; Releases allow 2 GB per asset. A `full` cache is one 1.5–2.5 GB blob.
2. **DMCA.** Rockstar actively issues takedowns for redistributed GTA assets.
   Repos and accounts carrying them disappear; a taken-down account takes the
   source with it.
3. **It is simply not ours.** This repo's licence covers the client code. It
   never covered the game.

`lite` does not dodge any of this — stripping audio changes the size, not the
ownership.

## What *is* published from here

| Artifact | Where | Legal basis |
|---|---|---|
| client source (`client/`) | this repo | our code |
| `libsamp.so` (arm64-v8a) | CI artifact on every push | our code — compiles with zero Rockstar bytes present |
| build + cache gates | `bin/android-build.sh`, `bin/build-cache.sh` | tooling |
| a full installable APK | **not published** | requires `libGame.so` (Rockstar) + the game cache |

`libGame.so` is deliberately git-ignored. `bin/fetch-blobs.sh` copies it out of
the upstream reference tree on a machine that is allowed to have it; it is not
redistributed from here either.
