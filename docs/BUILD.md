# Build

## Prerequisites

```bash
sudo apt install -y openjdk-17-jdk-headless unzip
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install \
  'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' \
  'ndk;26.2.11394342' 'cmake;3.22.1'
```

Versions are pinned because the tree was authored against them: Gradle 8.13,
AGP 8.13.1, Kotlin 2.2.0, NDK r26c, cmake 3.22.1. A different NDK usually works;
a different cmake often does not.

## Commands

| Command | Produces | Needs `libGame.so` |
|---|---|---|
| `bin/fetch-blobs.sh` | copies `libGame.so` + BASS `.so` from the upstream reference | - |
| `bin/android-build.sh native` | `libsamp.so` only | no |
| `bin/android-build.sh` | debug APK | yes |
| `bin/android-build.sh release` | release APK (unsigned without a keystore) | yes |
| `bin/android-build.sh clean` | wipes build dirs, then debug APK | yes |

First run on a fresh clone:

```bash
bin/fetch-blobs.sh
bin/android-build.sh clean
```

## What the gate proves

`bin/android-build.sh` does not trust gradle's exit code. It:

1. checks the JDK, NDK and cmake paths before starting;
2. writes `client/local.properties` (AGP reads the SDK path from there, not from env);
3. sets the exec bit on `client/gradlew` (the repo stores it without one);
4. appends a trailing newline to `client/gradle.properties` if it is missing
   (appending to a file whose last line has no newline silently corrupts that key);
5. builds, then unzips the APK and asserts `lib/arm64-v8a/libsamp.so` **and**
   `lib/arm64-v8a/libGame.so` are inside it.

In `native` mode step 5 becomes: find `libsamp.so` under
`app/build/intermediates/cxx` and fail if it is absent. That mode is the only one
that can run without a copyrighted binary, so it is what CI runs.

Measured on this machine (openjdk 17.0.21.1, NDK r26c, cmake 3.22.1):

| Run | Time | Output |
|---|---|---|
| `clean` (full) | 2m08s–2m50s | APK 36 MB, `libsamp.so` 3.5 MB, 0 warnings |
| `native` | 10s | `libsamp.so` 3.5 MB |

"0 warnings" is a literal `grep -ci 'warning:'` over the build log returning 0.

## Output

```
client/app/build/outputs/apk/debug/samp-client-debug.apk
client/app/build/intermediates/cxx/Debug/<hash>/obj/arm64-v8a/libsamp.so
```

`dist/` holds a copy of the last APK built here, plus its SHA-256. `dist/` is
git-ignored: the APK embeds `libGame.so`.

```bash
sha256sum -c dist/samp-client-0.2.0-debug.apk.sha256
adb install -r dist/samp-client-0.2.0-debug.apk
```

Installing is not the same as launching. First launch needs the asset tree at
`/storage/emulated/0/GTA/`, see [DATA.md](DATA.md).

## CI

`.github/workflows/ci.yml` runs on push and pull request:

- asserts no proprietary binary is tracked in git (`libGame.so`, BASS `.so`);
- runs `bin/android-build.sh native`;
- asserts the result is `ELF 64-bit LSB shared object, ARM aarch64`;
- asserts SA-MP symbols are present in the binary;
- uploads `libsamp.so` as a build artifact.

It never assembles or publishes an APK, because that would redistribute
Rockstar's binary. See [LEGAL.md](LEGAL.md).

## Known build traps

| Symptom | Cause | Fix |
|---|---|---|
| `fatal error: samp_widgets/playerTabList.h` | header is `playertablist.h`; tree authored on Windows | case fix already applied in `client/` |
| `fatal error: RGBA.h` | same case problem | fixed in `client/` |
| duplicate `libbass.so` / `libbass_ssl.so` link error | the same `.so` exists in `jniLibs/` **and** in the CMake link line | remove both from `jniLibs/` (done) |
| gradle property silently ignored | last line of `gradle.properties` has no newline and the next append glues to it | the gate appends a newline first |
| `./gradlew: Permission denied` | git stores no exec bit for it | the gate chmods it |
| build "passes" but APK has no native libs | gradle exit code is not evidence | the gate inspects the APK |
