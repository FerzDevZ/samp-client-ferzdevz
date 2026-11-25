# MEMORY.md — atomic invariants

Format: `- **[YY-MM-DD · Component]**: <symptom> → **Root Cause**: <exact cause> → **Invariant Rule**: <permanent rule>.`

## Android client (SAMP-Mobile fork, vendor/samp-mobile)

- **[26-11-25 · gradle.properties]**: `Cannot parse project property android.nonTransitiveRClass='trueorg.gradle...' as boolean` → **Root Cause**: file had no trailing newline, `echo >>` appended onto the last property → **Invariant Rule**: never append with `>>` to a properties/ini file; rewrite the last line with a Python/sed pass that guarantees `\n`.

- **[26-11-25 · native build]**: `fatal error: 'samp_widgets/playerTabList.h' file not found` on Linux only → **Root Cause**: repo authored on Windows; on-disk name is `playertablist.h`, include said `playerTabList.h`. Same class in `game/rgba.cpp` (`RGBA.h` vs `rgba.h`) → **Invariant Rule**: after adding any new .h, run the case-mismatch scanner (`.patches/linux-port.patch` holds the two known fixes); a green Windows build is not evidence of a green Linux build.

- **[26-11-25 · mergeDebugNativeLibs]**: `2 files found with path 'lib/arm64-v8a/libbass.so' from inputs: merged_jni_libs / cxx obj` → **Root Cause**: `libbass.so` shipped in `jniLibs/` *and* linked via CMake from `vendor/bass/libs/<abi>/` → **Invariant Rule**: any `.so` linked in CMakeLists must not also exist under `jniLibs/`; jniLibs is only for blobs nothing links against (libGame.so, libopenal, libz, libc++_shared).

- **[26-11-25 · toolchain]**: no APK can exist without `sdk.dir` → **Root Cause**: Android Gradle Plugin resolves SDK from `local.properties` / `ANDROID_HOME`, not from PATH → **Invariant Rule**: `bin/android-build.sh` exports `JAVA_HOME` + `ANDROID_HOME` and writes `local.properties` before invoking gradlew; never rely on the caller's shell.

## Legal / distribution boundary (not a bug, a hard constraint)
- **vendor/samp-mobile ships `app/src/main/jniLibs/arm64-v8a/libGame.so` (8.9 MB, Rockstar binary) and reimplements `com.rockstargames.gtasa.*` / `com.rockstargames.oswrapper.*`.** It also bundles Firebase Crashlytics/Analytics/Messaging/Remote Config, AppLovin and Splunk Mint.
- **Invariant Rule**: this tree is a *reference build only* — never publish its APK, never ship `libGame.so`, never keep the telemetry SDKs in an artifact we hand to players. Our product is `libsamp.so` + a patcher that operates on the user's own legally-owned GTA SA install.

## Rebrand + telemetry removal (26-11-25 · applicationId)

- **[26-11-25 · AndroidManifest]**: changing `namespace`/`applicationId` while the manifest still names an own activity as `com.<newid>.SAMP` → **Root Cause**: that activity is a *Java class in package `com.gta.game`*, not generated from the namespace; renaming the manifest reference breaks the launch → **Invariant Rule**: an activity name in the manifest must match the real `.java` package, never the applicationId. Only `R` class references (10 files) follow the namespace.

- **[26-11-25 · firebase]**: removing `id("com.google.gms.google-services")` without deleting `app/google-services.json` → **Root Cause**: the plugin is what validates `package_name` against applicationId; deleting one but not the other leaves a mismatched config in the APK → **Invariant Rule**: strip the plugin, the classpath, the dependency block and the JSON together; the `release { firebaseCrashlytics {} }` block and the native `firebase::crashlytics::Log()` call go in the same change or the native link fails.

- **[26-11-25 · artifact size]**: APK 43 MB → 36 MB after the strip → **Invariant Rule**: the delta is the measure that the telemetry is actually gone, not the grep. Verify with `aapt2 dump badging` (applicationId) **and** `unzip -l | grep -ci firebase` (0 hits).

## Full source in git + data pipeline (26-11-25 · layout)

- **[26-11-25 · vendoring]**: `vendor/samp-mobile` held a nested `.git` (139 MB) and 7.2 MB `cpp.zip` that duplicated files already on disk → **Root Cause**: cloning a reference *into* the repo drags its history and its own archives → **Invariant Rule**: track the tree as `client/` with `rm -rf client/.git`; a nested repo makes `git add` either skip the source or create a submodule, never what you want.

- **[26-11-25 · CI]**: `assembleDebug` needs `libGame.so` only for *packaging*, not for compiling — CMake never references it (`CUtil::FindLib` resolves at runtime) → **Root Cause**: mixing "compiles" with "packages" made the build gate unusable without a copyrighted binary → **Invariant Rule**: `bin/android-build.sh native` runs `externalNativeBuildDebug` and gates on `libsamp.so` alone; that is the only unit CI may publish.

- **[26-11-25 · data]**: players kept asking where to download the cache → **Root Cause**: the cache is Rockstar's asset set; `full` vs `lite` is a size difference, not an ownership difference → **Invariant Rule**: publish the *manifest* (`sha256` per file) and the builder (`bin/build-cache.sh`), never the bytes. 100 MB/file + DMCA make hosting impossible anyway.

- **[26-11-25 · verify]**: manifest verification must load the JSON with `python3`, not `grep`, and must compare size **and** sha → **Root Cause**: a truncated copy keeps the same name and a plausible size → **Invariant Rule**: `--verify` fails with the exact relative path of every file that does not match, and a 1-byte tamper must make it exit non-zero.

## Rebrand v2: own namespace, frozen namespaces (26-11-25 · rename)

- **[26-11-25 · JNI]**: renaming a Java class that declares `native` methods without renaming the matching `Java_…` symbol in C++ → **Root Cause**: JNI resolves by symbol name, not by registration; the Java side compiles fine and fails at runtime with `UnsatisfiedLinkError` → **Invariant Rule**: rename the package, the `package`/`import` lines and the `Java_…` symbol in one change, then prove it with `nm -D --defined-only libsamp.so | grep Java_`.

- **[26-11-25 · frozen names]**: `com.rockstargames.oswrapper` and `com.nvidia.devtech` cannot be renamed → **Root Cause**: `libGame.so` is prebuilt and hardcodes 38 `Java_com_rockstargames_oswrapper_GameNative_*` symbols plus `com/nvidia/devtech/NvUtil`, `…/DeviceInfo`, `…/GamePlatformServices` → **Invariant Rule**: a vendored binary owns every name it mentions; `nm -D | grep Java_` and `strings | grep '^com/'` decide the rename boundary, not taste.

- **[26-11-25 · activity name]**: `android:name` must keep pointing at the real `.java` package after an `applicationId` change → **Invariant Rule**: manifest component names follow source packages; only the `R` class follows `namespace`.

- **[26-11-25 · artfiact]**: the 5 exported JNI symbols in the shipped `libsamp.so` are the cheapest identity check there is → **Invariant Rule**: after any rename, assert the shipped `.so` exports the new names AND `aapt2 dump badging` reports the new launchable activity, before calling the rename done.

- **[26-11-25 · patcher]**: injecting a native library into an existing APK produces an app that dies with `UnsatisfiedLinkError` before any UI → **Root Cause**: a compressed entry cannot be mmap'd, and `zip -r` deflates by default; alignment is also lost → **Invariant Rule**: inject with `zip -0`, then `zipalign -p -f 4`, then sign, then assert both `.so` entries report `Stored` and `zipalign -c -p 4` still passes on the signed file.

- **[26-11-25 · patcher]**: a payload APK must be checked by package and by content before assembly → **Root Cause**: `aapt2 dump badging` on a file with no manifest fails with "could not identify format of APK", and any zip can carry `lib/arm64-v8a/`, so silence there means shipping a 37 MB brick → **Invariant Rule**: refuse unless the game APK reports `com.rockstargames.gtasa` and the framework reports `com.ferzdevzgta.game` with `libsamp.so` present and `libGame.so` absent.

- **[26-11-25 · patcher split]**: `--gta-apk` alone fails on a modern Play Store install → **Root Cause**: the install is split, so `base.apk` has the package but `split_config.arm64_v8a.apk` holds `libGame.so`; reading only one file means either no manifest or no library → **Invariant Rule**: resolve package and library separately — package from the APK whose badging says `com.rockstargames.gtasa`, library from the APK whose zip listing has `lib/arm64-v8a/libGame.so` — and fail when either is missing.

- **[26-11-25 · manifest trust]**: a manifest is data from a server, so paths in it are untrusted input → **Root Cause**: `manifest-<variant>.json` is fetched or copied, and `../` or `/etc/...` in a `path` would write outside `/storage/emulated/0/GTA/` → **Invariant Rule**: reject any manifest entry whose path is absolute, contains `..`, or whose sha256 is not 64 hex chars, before a single byte is written.

- **[26-11-25 · log growth]**: the native `Log()` appends to `GTA/logcat.txt` forever → **Root Cause**: `main.cpp` opens the file once with `sprintf` and never truncates, so months of play grow without bound → **Invariant Rule**: rotate before the game starts (Java side, 2 MiB, keep two backups) and never let a rotation failure stop the launch.

- **[26-11-25 · settings.ini]**: `CSettings` calls `std::terminate()` when `INIReader` cannot parse the file → **Root Cause**: the native loader aborts on a malformed line instead of defaulting → **Invariant Rule**: edit `settings.ini` line by line, preserve unknown keys, comments, blank lines and CRLF, and only write keys the loader actually reads.

- **[26-11-25 · JVM tests]**: the classes worth testing are the ones with no Android imports → **Root Cause**: anything touching `Context`, `FileProvider` or `SharedPreferences` needs a device or Robolectric, which turns a 5 s check into a Gradle job → **Invariant Rule**: keep data, log and settings logic free of Android imports so `bin/self-test.sh` can run it with plain `javac`/`java`, and let activities be thin.
