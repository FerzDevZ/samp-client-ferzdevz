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
