# The patcher

An installable APK needs Rockstar's `libGame.so`, which cannot be redistributed.
The patcher splits the client into a half that can be published and a half that
comes from the person's own game install.

```
dist/samp-client-framework.apk   ours, 28 MB, no libGame.so   <- publishable
        +  lib/arm64-v8a/libGame.so  from the user's GTA SA APK
        =  APK that installs and runs
```

Nothing from the game APK is copied anywhere except that one file, and the result
never leaves the machine that ran the tool.

## Usage

```bash
# once, or after changing client code
bin/android-build.sh framework
#   -> dist/samp-client-framework.apk (28 MB, signed, no libGame.so)

# single APK install
bin/patch-apk.sh --gta-apk /path/to/GTA.apk --out dist/samp-client-0.2.0.apk

# split install (base.apk + split_config.*.apk), the normal Play Store layout
bin/patch-apk.sh --gta-split-dir /path/to/split --out dist/samp-client-0.2.0.apk
```

`--framework <path>` overrides the framework default. `--out` is required. Pass
one of `--gta-apk` or `--gta-split-dir`, never both.

With `--gta-split-dir` the tool walks every `.apk` in the directory, takes the
package name from whichever one declares `com.rockstargames.gtasa` (that is
`base.apk`) and the library from whichever one actually carries
`lib/arm64-v8a/libGame.so` (normally `split_config.arm64_v8a.apk`). It fails if no
file carries the library, that means the wrong ABI split was extracted.

## What it does, in order

1. Resolves the game install: either one APK, or a split directory where the
   package and the library can live in different files. Checks that the library
   APK contains `lib/arm64-v8a/libGame.so` and that the package APK declares
   `package com.rockstargames.gtasa`. Dying here is cheaper than a 36 MB dead APK.
2. Checks the framework: must contain `libsamp.so` and must **not** contain
   `libGame.so`.
3. Extracts `libGame.so` to a temp dir and confirms it is
   `ELF 64-bit LSB shared object, ARM aarch64`.
4. Injects it with `zip -0`, stored, not deflated. A native library must be
   mmap-able straight out of the APK; a deflated one is not, and the app dies with
   `UnsatisfiedLinkError` before any UI.
5. `zipalign -p -f 4`: page alignment for `.so`, 4-byte alignment for the rest.
6. Signs with `~/.samp-client/patcher.keystore`, created on first run
   (`keytool`, RSA 2048, 10 000 days, mode 600).
7. Verifies the artifact, not the exit codes: `apksigner verify`, both `.so`
   present and readable, both `Stored` in the zip, `zipalign -c -p 4` still passes
   after signing, and `aapt2 dump badging` reports `com.ferzdevzgta.game`.

Measured on this machine: framework 28 MB, patched output 37 MB, end to end in
about 20 s once the framework exists.

## On the phone (Termux)

`bin/patch-termux.sh` does the same job without a PC. It exists because the phone is
where the result is needed and because `zipalign` and `aapt2` are x86-64 Android SDK
binaries that cannot run on an aarch64 device.

```bash
pkg install openjdk-21 apksigner zip unzip python     # once

bin/patch-termux.sh --gta-apk ~/storage/downloads/GTA.apk
bin/patch-termux.sh --gta-split-dir ~/gta-split --out ~/samp-client.apk
```

What replaces each PC-only step:

| PC step | On the phone |
|---|---|
| `zip -0` injection | same `zip -0 -X`, stored |
| `zipalign -p -f 4` | `apksigner --lib-page-alignment 4096` page aligns the libraries while signing |
| `apksigner verify`, `aapt2 dump badging` | `apksigner verify` plus `bin/apk-verify.py`, which reads the zip structures itself |

`bin/apk-verify.py` opens the local header of `lib/arm64-v8a/libGame.so` and asserts
that the compression method is 0, that the data offset is a multiple of 4096, that the
bytes match the file that was inserted, and that the package name is in the APK. It
needs no SDK.

Measured: framework 28 MB in, 36 MB out. The output was checked on a machine with the
real SDK: `zipalign -c -p 4` passes, `apksigner verify` passes, `aapt2 dump badging`
reports `com.ferzdevzgta.game`.

The framework APK still has to come from somewhere. Today that is your own build
machine, or a copy of `dist/samp-client-framework.apk`, because the framework is not
published yet (see [LEGAL.md](LEGAL.md) and [OSWRAPPER.md](OSWRAPPER.md)). Termux
removes the PC from the patching step, not from the framework build.

`bin/build-termux.sh` is the one command to run instead of remembering that. It reports
which tools are present, fetches the framework when you give it a URL, refuses a framework
that was built from a different source tree than the one next to it, and then calls `bin/patch-termux.sh`.

```bash
bin/build-termux.sh --from-url http://100.85.253.77:8000/samp-client-framework.apk \
  --gta-apk ~/storage/downloads/GTA.apk --out ~/samp-client.apk
bin/build-termux.sh --check-only
```

The staleness check exists because a patched APK built from a framework older than your
last Java change looks fine and runs the old UI. `bin/android-build.sh framework` records a
fingerprint of the source tree inside `dist/samp-client-framework.apk.src`, and this script
compares content, not timestamps: `git checkout` rewrites every mtime, so a clock comparison
would call a fresh framework stale right after switching branches. Pass `--force` when you
know the difference does not matter. Measured on this machine with the real framework and a
synthetic GTA split: exit 0, `PASS`, package `com.ferzdevzgta.game`, `libGame.so` stored at
an offset that is a multiple of 4096.

## Verifications that were run

| Case | Expected | Result |
|---|---|---|
| game APK + framework | PASS | PASS, 37 MB, `libGame.so` byte-identical to the source (`sha256` compared) |
| framework that still has `libGame.so` | refuse | `FAIL: … already contains libGame.so` |
| APK without `libGame.so` | refuse | `FAIL: … has no lib/arm64-v8a/libGame.so` |
| APK with the wrong package | refuse | `FAIL: … is not GTA San Andreas (package 'com.ferzdevzgta.game')` |
| `build-termux.sh --check-only` | a report | 0 missing tools, "pc needed: none" |
| `build-termux.sh` with a framework built from another tree | refuse | `FAIL: the source tree changed since this framework was built` |
| `build-termux.sh --force` with a synthetic GTA split | PASS | exit 0, `PASS`, 36 MB, library offset a multiple of 4096 |

## Limits

- **arm64-v8a only.** The game APK must carry an arm64 `libGame.so`; if it ships
  only `armeabi-v7a`, the tool refuses rather than producing a broken APK.
- **Split installs.** Pass `--gta-split-dir` with the directory that holds
  `base.apk` and the config splits. The package and the library are resolved by
  content, so a base APK with the manifest and an ABI split with `libGame.so` is a
  supported combination.
- **Assets are separate.** The APK still expects `/storage/emulated/0/GTA/`; build
  that tree with `bin/build-cache.sh` from the same game install.
- **No obfuscation, no rename.** If the user's APK has a differently named
  `libGame.so` (a repack), the inject check will refuse it.
