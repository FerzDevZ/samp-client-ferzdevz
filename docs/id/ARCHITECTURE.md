# Arsitektur

Baca ini sebelum mengubah nama package, simbol JNI, atau path penyimpanan.

## Struktur

```
client/                               source klien yang di-track (fork kuzia15/SAMP-Mobile GTA-2.11)
  app/src/main/java/
    com/ferzdevzgta/game/             entry Java milik kita (SAMP, GTASA, ui/*) — 7 file
    com/ferzdevzgta/launcher/         activity launcher — 3 file
    com/rockstargames/oswrapper/      wrapper game — 32 file, NAMA PACKAGE TERKUNCI
    com/rockstargames/gtasa/          shell activity/downloader — 5 file
    com/nvidia/devtech/               glue NVIDIA — 3 file, NAMA PACKAGE TERKUNCI
  app/src/main/cpp/
    samp/                             netcode SA-MP, hook game, chat, GUI  -> libsamp.so
    opus/ bass/ raknet/ imgui/ ...    third-party yang di-vendor
  app/src/main/jniLibs/arm64-v8a/     .so prebuilt, sebagian besar di-ignore (lihat LEGAL.md)
bin/                                  gate build, fetch blob, dan cache
docs/                                 direktori ini
```

Satu ABI saja: `arm64-v8a`. `minSdk 28`, `targetSdk 36`, `compileSdk 36`, NDK 26.2.11394342.

## Alur start

```
MainActivity (com.ferzdevzgta.launcher.activity)
  └─ System.loadLibrary("multi")
SAMP          (com.ferzdevzgta.game)      System.loadLibrary("Game"), lalu "samp"
  └─ native initializeSAMP()               libsamp.so mem-patch libGame.so, lalu ambil alih
GameActivity  (com.rockstargames.gtasa)
  └─ GameNative (com.rockstargames.oswrapper) -> libGame.so merender frame
       libsamp.so menghook game loop, menambah layer net SA-MP, chat, dan GUI di atasnya
```

`libsamp.so` tidak pernah di-link ke `libGame.so` saat build. Library game dicari
saat runtime (`CUtil::FindLib`) lalu dihook. Itulah sebabnya
`bin/android-build.sh native` bisa mengompilasi seluruh klien tanpa binary Rockstar,
dan juga sebabnya APK tetap butuh binary itu untuk berjalan.

## Kontrak JNI

Lima simbol di `libsamp.so` di-resolve lewat aturan penamaan simbol Java, jadi
package Java dan simbol C harus diubah bersamaan atau aplikasi melempar
`UnsatisfiedLinkError` pada pemanggilan pertama.

| Method Java | Simbol yang diekspor |
|---|---|
| `com.ferzdevzgta.game.SAMP.initializeSAMP()` | `Java_com_ferzdevzgta_game_SAMP_initializeSAMP` |
| `...game.ui.dialog.DialogManager.sendDialogResponse(int,int,int,byte[])` | `Java_com_ferzdevzgta_game_ui_dialog_DialogManager_sendDialogResponse` |
| `...game.ui.AttachEdit.Exit()` | `Java_com_ferzdevzgta_game_ui_AttachEdit_Exit` |
| `...game.ui.AttachEdit.AttachClick(int,boolean)` | `Java_com_ferzdevzgta_game_ui_AttachEdit_AttachClick` |
| `...game.ui.AttachEdit.Save()` | `Java_com_ferzdevzgta_game_ui_AttachEdit_Save` |

Cek setelah rename apa pun:

```bash
SO=$(find client/app/build/intermediates/cxx -name libsamp.so -path '*arm64-v8a*' | head -1)
nm -D --defined-only "$SO" | grep Java_
```

## Namespace yang tidak boleh di-rename

`libGame.so` adalah binary prebuilt. Isinya referensi hardcoded, jadi tiga nama
Java dibekukan:

```bash
nm -D --defined-only client/app/src/main/jniLibs/arm64-v8a/libGame.so | grep -c Java_
# 38   semuanya Java_com_rockstargames_oswrapper_GameNative_*

strings -a client/app/src/main/jniLibs/arm64-v8a/libGame.so | grep -E '^com/'
# com/nvidia/devtech/NvUtil
# com/rockstargames/oswrapper/DeviceInfo
# com/rockstargames/oswrapper/GamePlatformServices
```

| Dibekukan | Alasan |
|---|---|
| `com.rockstargames.oswrapper` | 38 simbol JNI ada di dalam binary |
| `com.nvidia.devtech` | `NvUtil` dicari lewat path dari dalam binary |

`com.ferzdevzgta.*` milik kita: di situ simbol SAMP dan launcher, jadi di-rename
dari `com.gta.*`. `com.rockstargames.gtasa` tidak punya simbol yang dikunci binary,
tapi menempel langsung di wrapper yang terkunci, jadi dibiarkan.

## Path penyimpanan

Hardcoded, tidak bisa diubah dari build:

| Path | Isi | Didefinisikan di |
|---|---|---|
| `/storage/emulated/0/GTA/` | aset game | `samp/main.cpp:36`, `samp/game/hooks.cpp:1385` |
| `/storage/emulated/0/GTA/SAMP/settings.ini` | setting klien | `samp/settings.cpp:15` |
| `/storage/emulated/0/GTA/SAMP/fonts/` | font UI native | `samp/main.cpp:295` |
| `/storage/emulated/0/GTA/logcat.txt` | log klien | `samp/main.cpp:397` |

Tree asetnya sendiri tidak dikirim — lihat [DATA.md](DATA.md).
