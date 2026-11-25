# client/

The client source: 1322 files, arm64-v8a only, forked from
`kuzia15/SAMP-Mobile` branch `GTA-2.11`. Read `docs/ARCHITECTURE.md` before
renaming a package or a JNI symbol.

## Map

```
app/build.gradle.kts              applicationId com.ferzdevzgta.game, versionName 0.1.0
app/src/main/AndroidManifest.xml  activities, permissions
app/src/main/java/
  com/ferzdevzgta/game/           our Java entry: SAMP, GTASA, ui/ (chat, dialogs, attach-edit)
  com/ferzdevzgta/launcher/       launcher activity, MainActivity
  com/rockstargames/oswrapper/    game wrapper (32 files), PINNED by libGame.so
  com/rockstargames/gtasa/        GameActivity, DownloaderActivity, GLES3JNIView
  com/nvidia/devtech/             NVIDIA glue (3 files), PINNED by libGame.so
app/src/main/cpp/
  samp/                           netgame, playerpool, vehiclepool, hooks, chat, gui, voice
  opus/ bass/ raknet/ imgui/      vendored third-party
app/src/main/jniLibs/arm64-v8a/   prebuilt .so, mostly git-ignored
app/src/main/res/                 layouts, drawables, fonts, values/strings.xml
```

## Tracked vs ignored

Tracked: all C++, all Java, gradle, CMake, resources, and the redistributable
prebuilts (`libc++_shared.so`, `libopenal.so`, `libz.so`, `libVendor_mpg123.so`,
GlossHook).

Ignored, fetched by `bin/fetch-blobs.sh`:

| File | Owner |
|---|---|
| `app/src/main/jniLibs/arm64-v8a/libGame.so` (8.5 MB) | Rockstar |
| `app/src/main/cpp/samp/vendor/bass/libs/**/*.so` (7.9 MB) | un4seen |

Also ignored: `app/build/`, `.gradle/`, `.cxx/`, `local.properties`,
`app/src/main/cpp/cpp.zip` (a redundant 7.2 MB archive of files already present),
`app/google-services.json` (Firebase was removed).

## Facts a change must not break

| Fact | Check |
|---|---|
| 5 JNI symbols follow the `com.ferzdevzgta.game` package | `nm -D --defined-only libsamp.so \| grep Java_` |
| `libGame.so` pins `com.rockstargames.oswrapper` (38 symbols) and `com.nvidia.devtech.NvUtil` | `nm -D libGame.so \| grep Java_` · `strings libGame.so \| grep '^com/'` |
| `libsamp.so` never links against `libGame.so` at build time | `bin/android-build.sh native` with `libGame.so` moved away |
| assets are read from `/storage/emulated/0/GTA/` | `cpp/samp/main.cpp:36` |

---

# client/ (Bahasa Indonesia)

Source klien: 1322 file, hanya arm64-v8a, fork dari `kuzia15/SAMP-Mobile` branch
`GTA-2.11`. Baca `docs/id/ARCHITECTURE.md` sebelum me-rename package atau simbol
JNI.

## Peta

Isi direktori sama seperti di atas: `app/src/main/java/` berisi empat namespace
(dua milik kita, dua terkunci binary), `app/src/main/cpp/samp/` berisi netcode
SA-MP, hook game, chat, GUI, dan voice.

## Di-track vs di-ignore

Di-track: seluruh C++, seluruh Java, gradle, CMake, resource, dan prebuilt yang
boleh didistribusikan (`libc++_shared.so`, `libopenal.so`, `libz.so`,
`libVendor_mpg123.so`, GlossHook).

Di-ignore, diambil lewat `bin/fetch-blobs.sh`:

| File | Pemilik |
|---|---|
| `app/src/main/jniLibs/arm64-v8a/libGame.so` (8,5 MB) | Rockstar |
| `app/src/main/cpp/samp/vendor/bass/libs/**/*.so` (7,9 MB) | un4seen |

Juga di-ignore: `app/build/`, `.gradle/`, `.cxx/`, `local.properties`,
`app/src/main/cpp/cpp.zip` (arsip 7,2 MB yang isinya duplikat file yang sudah ada),
`app/google-services.json` (Firebase sudah dihapus).

## Fakta yang tidak boleh rusak oleh perubahan

| Fakta | Cara cek |
|---|---|
| 5 simbol JNI mengikuti package `com.ferzdevzgta.game` | `nm -D --defined-only libsamp.so \| grep Java_` |
| `libGame.so` mengunci `com.rockstargames.oswrapper` (38 simbol) dan `com.nvidia.devtech.NvUtil` | `nm -D libGame.so \| grep Java_` · `strings libGame.so \| grep '^com/'` |
| `libsamp.so` tidak pernah di-link ke `libGame.so` saat build | `bin/android-build.sh native` dengan `libGame.so` dipindahkan |
| aset dibaca dari `/storage/emulated/0/GTA/` | `cpp/samp/main.cpp:36` |
