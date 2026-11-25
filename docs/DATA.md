# Game data

The client renders GTA San Andreas. The assets are Rockstar's, so they are not in
this repo and cannot be downloaded from it. Every player supplies them from a copy
they already own; this repo ships the client that reads them and the tool that
normalises them.

## Where the client reads from

| Path | Contents |
|---|---|
| `/storage/emulated/0/GTA/` | the asset tree |
| `/storage/emulated/0/GTA/SAMP/` | `settings.ini`, `fonts/`, logs |

Hardcoded in `client/app/src/main/cpp/samp/main.cpp:36` and
`client/app/src/main/cpp/samp/game/hooks.cpp:1385`. There is no setting for it.

## Getting the assets legitimately

From a purchased install:

```bash
adb shell pm path com.rockstargames.gtasa                 # the split APKs
adb pull /sdcard/Android/obb/com.rockstargames.gtasa/ ./obb/
```

From a single APK + OBB pair you dumped yourself, same thing: you need the two
files, not a download link.

## Normalising them

```bash
bin/build-cache.sh --apk GTA.apk --obb main.8.com.rockstargames.gtasa.obb --out cache
bin/build-cache.sh --out cache --verify
adb push cache/full/. /storage/emulated/0/GTA/
```

Inputs it accepts: `--apk` (assets are read from `assets/`), `--obb` (the
expansion file), or `--dir` (an already-extracted tree).

## full and lite

| Variant | Contents | Rough size | Trade-off |
|---|---|---|---|
| `full` | everything: radio audio, cutscene movies, textures, models | ~1.5–2.5 GB | radio works, cutscenes play |
| `lite` | `audio/` and `movies/` removed | ~0.4–0.9 GB | no radio, no cutscenes; gameplay and SA-MP unaffected |

Both sizes are community ballpark, no GTA SA install exists on the machine this
was written on, so they were not measured here. The tool prints the real numbers
for your files and writes them into the manifests.

Stripping is a size change, not a licensing change. `lite` is exactly as much
Rockstar's data as `full` is.

## Manifests

Every build writes `manifest-full.json` and `manifest-lite.json`:

```json
{
 "variant": "lite",
 "target_path": "/storage/emulated/0/GTA/",
 "file_count": 1187,
 "total_bytes": 812345678,
 "files": [{"path": "models/gta3.img", "size": 1234567, "sha256": "…"}]
}
```

`--verify` recomputes size and SHA-256 for every entry and names each file that
does not match:

```
== verifying lite ==
  CORRUPT  data/handling.cfg
FAIL: lite: 1 file(s) do not match the manifest
```

That is the point: a manifest can be shared, the bytes behind it cannot. Send
someone `manifest-lite.json` and their cache can be proven identical without
transferring a single Rockstar file.

## Downloading it in the app

The upstream `DownloaderActivity` used Play Asset Delivery, which does nothing for a
sideloaded APK. That activity and the `com.google.android.play:asset-delivery` dependency
are gone: 824 lines of Java, two layouts, four strings and a manifest entry, with no
`com/google/android/play/core` class left in the dex. In their place, a sideloaded build
opens `DataSetupActivity` when `/storage/emulated/0/GTA/data` is missing:

| Step | What happens |
|---|---|
| Pick a variant | `full` or `lite`, chosen on the screen, not at build time |
| Read the manifest | `GTA/manifest-<variant>.json`, produced by `bin/build-cache.sh` |
| Verify the tree | size and SHA-256 for every entry, nothing trusted by name alone |
| Download the gaps | only files that are missing or wrong; `Range` resume from `.part` |
| Verify again | the tree must match the manifest before the screen says done |

Point it at a server that hosts the tree **you** built:

```ini
; /storage/emulated/0/GTA/SAMP/settings.ini
[download]
url = https://your-host/gtasa/
```

The client then asks for `https://your-host/gtasa/<relative path>`. It retries a
transfer three times with exponential backoff and a little jitter, deletes a file
whose hash does not match instead of keeping it, and refuses manifest paths that
escape the target directory (`..`, leading `/`).

## Why not hosted here

| Blocker | Detail |
|---|---|
| GitHub file limit | 100 MB per file, hard |
| Release asset limit | 2 GB per asset; `full` is one 1.5–2.5 GB blob |
| Repo soft cap | ~2 GB, and git keeps every historic copy of a blob |
| DMCA | Rockstar issues takedowns for redistributed GTA assets; a takedown takes the source repo with it |

`lite` dodges none of these.

The legal way to give players a one-tap install is to host a cache **you built
from your own copy** on your own server and point the client's downloader at it
(see *Downloading it in the app* above). That is your call, your server, your
risk, not something this repo does.
