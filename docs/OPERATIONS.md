# Operations

## Daily loop

```bash
git pull
bin/fetch-blobs.sh                 # only needed on a fresh clone or after a clean
bin/android-build.sh clean         # or: native / release
adb install -r client/app/build/outputs/apk/debug/samp-client-debug.apk
adb push cache/full/. /storage/emulated/0/GTA/
adb logcat -s SAMP:* AndroidRuntime:E
```

With `separate-process` logging off, the useful log lines are the ones the client
writes to `/storage/emulated/0/GTA/logcat.txt`.

## Symptom → cause

| Symptom | Cause | Check |
|---|---|---|
| installs, closes immediately | asset tree missing at `/storage/emulated/0/GTA/` | `adb shell ls /storage/emulated/0/GTA/` |
| black screen after the loading bar | `libGame.so` missing from the APK | `unzip -l apk \| grep libGame` |
| `UnsatisfiedLinkError: initializeSAMP` | Java package and JNI symbol were renamed apart | `nm -D libsamp.so \| grep Java_` vs your `.java` package |
| `UnsatisfiedLinkError: implOnDrawFrame` | `com.rockstargames.oswrapper` was renamed | restore the package; it is pinned by `libGame.so` |
| native UI fonts missing or blank | `SAMP/fonts/` absent from the asset tree | `adb shell ls /storage/emulated/0/GTA/SAMP/fonts/` |
| chat renders, server list empty | no network permission, or the client did not reach the master list | `adb logcat` for `SAMP` |
| gradle fails on a fresh clone with a `.so` duplicate | BASS libs exist both in `jniLibs/` and in the CMake link line | `bin/fetch-blobs.sh` does not restore the duplicates; do not copy them back |
| build green, APK has no `arm64-v8a` | gradle exit code trusted | run `bin/android-build.sh` and read the `PASS` block |

## Renaming anything

Order matters, or you ship a crash:

1. `git mv` the Java package directory;
2. rewrite `package` and `import` lines;
3. rewrite the matching `Java_…` symbol in `client/app/src/main/cpp` if the class
   declares `native` methods;
4. rebuild and confirm: `nm -D --defined-only libsamp.so | grep Java_` lists the
   new names, and `aapt2 dump badging apk | grep launchable-activity` names the
   new activity;
5. update `android:name` in `AndroidManifest.xml` — an activity name must match the
   real `.java` package, never the `applicationId`.

Never rename `com.rockstargames.oswrapper` or `com.nvidia.devtech`; see
[ARCHITECTURE.md](ARCHITECTURE.md#namespaces-that-must-not-be-renamed).

## Invariants worth keeping

Each one was a real failure at least once. `MEMORY.md` holds the full ledger with
the root cause of every entry; the short version:

- an APK gate inspects the artifact, never the build exit code;
- an activity name in the manifest follows the Java package, not the `applicationId`;
- `libGame.so` and BASS stay out of git, permanently;
- a cache manifest is shareable, the cache is not;
- `android-build.sh native` must stay green with `libGame.so` absent, or CI cannot
  prove anything.

## Re-verifying everything

```bash
bin/android-build.sh clean                                   # 0 warnings, APK 36 MB
mv client/app/src/main/jniLibs/arm64-v8a/libGame.so /tmp/    # prove CI mode is honest
bin/android-build.sh native                                  # 0 warnings, libsamp.so 3.5 MB
mv /tmp/libGame.so client/app/src/main/jniLibs/arm64-v8a/
bin/build-cache.sh --out cache --verify                      # cache matches its manifests
```
