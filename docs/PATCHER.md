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

# for any device
bin/patch-apk.sh --gta-apk /path/to/GTA.apk --out dist/samp-client-0.1.0.apk
```

`--framework <path>` overrides the framework default. `--out` is required.

## What it does, in order

1. Checks the game APK: must contain `lib/arm64-v8a/libGame.so` and must declare
   `package com.rockstargames.gtasa`. Dying here is cheaper than a 36 MB dead APK.
2. Checks the framework: must contain `libsamp.so` and must **not** contain
   `libGame.so`.
3. Extracts `libGame.so` to a temp dir and confirms it is
   `ELF 64-bit LSB shared object, ARM aarch64`.
4. Injects it with `zip -0` — stored, not deflated. A native library must be
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

## Verifications that were run

| Case | Expected | Result |
|---|---|---|
| game APK + framework | PASS | PASS, 37 MB, `libGame.so` byte-identical to the source (`sha256` compared) |
| framework that still has `libGame.so` | refuse | `FAIL: … already contains libGame.so` |
| APK without `libGame.so` | refuse | `FAIL: … has no lib/arm64-v8a/libGame.so` |
| APK with the wrong package | refuse | `FAIL: … is not GTA San Andreas (package 'com.ferzdevzgta.game')` |

## Limits

- **arm64-v8a only.** The game APK must carry an arm64 `libGame.so`; if it ships
  only `armeabi-v7a`, the tool refuses rather than producing a broken APK.
- **Base APK only.** Split installs (`base.apk` + `config.arm64_v8a.apk`) need the
  ABI split; bundle the base plus the arm64 config split and pass the base.
- **Assets are separate.** The APK still expects `/storage/emulated/0/GTA/`; build
  that tree with `bin/build-cache.sh` from the same game install.
- **No obfuscation, no rename.** If the user's APK has a differently named
  `libGame.so` (a repack), the inject check will refuse it.
