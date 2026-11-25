# Selisih terhadap reference upstream

Basis: [`kuzia15/SAMP-Mobile`](https://github.com/kuzia15/SAMP-Mobile), branch
`GTA-2.11`, commit `0a805d4`. `client/` adalah tree itu dengan perubahan di bawah
dan tidak ada yang lain. Perubahannya sudah ada di dalam source; tidak ada file
patch yang perlu diaplikasikan.

## Di-port ke Linux

Tree ini ditulis di Windows dan tidak terompilasi di Linux sama sekali.

| Perubahan | File | Alasan |
|---|---|---|
| `#include "samp_widgets/playerTabList.h"` → `playertablist.h` | `cpp/samp/gui/gui.h` | filesystem Linux membedakan huruf besar/kecil |
| `#include "RGBA.h"` → `rgba.h` | `cpp/samp/game/rgba.cpp` | sama |
| exec bit `gradlew` | `client/gradlew` | git tidak menyimpannya; gate build yang memasang |
| newline di akhir `gradle.properties` | `client/gradle.properties` | key terakhir tanpa newline, jadi append berikutnya menempel dan diabaikan |
| hapus `jniLibs/arm64-v8a/libbass.so`, `libbass_ssl.so` | — | menduplikasi baris link CMake, memicu error library ganda saat link |

## Rebrand

| Sebelum | Sesudah |
|---|---|
| `applicationId` / `namespace` `com.gta.game` | `com.ferzdevzgta.game` |
| package Java `com.gta.game`, `com.gta.launcher` | `com.ferzdevzgta.game`, `com.ferzdevzgta.launcher` |
| 5 simbol JNI `Java_com_gta_game_*` | `Java_com_ferzdevzgta_game_*` |
| label aplikasi `GTA 2.11` | `SA-MP FerzDevZ` |
| nama APK `GTA-2.11-<variant>.apk` | `samp-client-<variant>.apk` |
| `versionName 1.0.0` | `0.1.0` |
| `rootProject.name "GTA-2.11"` | `samp-client-ferzdevz` |

`com.rockstargames.*` dan `com.nvidia.devtech` **tidak** di-rename: binary prebuilt
mengunci nama itu. Bukti dan perintahnya di
[ARCHITECTURE.md](ARCHITECTURE.md#namespace-yang-tidak-boleh-di-rename).

## Telemetri dihapus

Reference mengirim laporan crash dan pemakaian ke project Firebase yang bukan milik
kita. Semuanya sudah dibuang:

| Dihapus | Dari |
|---|---|
| plugin `com.google.gms.google-services`, classpath, `google-services.json` | `build.gradle.kts`, `app/build.gradle.kts`, `app/` |
| plugin `com.google.firebase.crashlytics` + blok `firebaseCrashlytics {}` di release | sama |
| `firebase-analytics`, `firebase-messaging`, `firebase-config`, BOM firebase | `app/build.gradle.kts` |
| pemanggilan `firebase::crashlytics::Log()` dan include `crashlytics.h` | `cpp/samp/main.cpp` |
| repository maven AppLovin dan Splunk Mint | `build.gradle.kts` |

Efeknya pada artifact: APK 43 MB → 36 MB, dan `unzip -l … | grep -ci firebase`
mengembalikan 0.

## Ditambahkan di repo ini

| Path | Fungsi |
|---|---|
| `bin/fetch-blobs.sh` | menyalin dua set binary yang tidak boleh di-track |
| `bin/build-cache.sh` | membangun tree aset `full` dan `lite` dengan manifest SHA-256 |
| `bin/patch-apk.sh` | melengkapi APK framework dengan `libGame.so` milik pengguna |
| `bin/android-build.sh` | gate build dengan mode `native` (CI) dan `framework` (boleh diterbitkan) |
| `.github/workflows/ci.yml` | kompilasi dan verifikasi, gagal bila binary proprietary di-stage |
| `docs/` | dokumentasi ini |

## Yang tidak dikerjakan di sini

- tidak ada perubahan pada netcode SA-MP, hook, chat, GUI, atau voice;
- tidak ada ABI kedua (`armeabi-v7a`, `x86_64` tidak pernah dibangun);
- tidak ada tanda tangan untuk build release — output `release` masih unsigned
  sampai kamu menambahkan keystore dan `keystore.properties`.
