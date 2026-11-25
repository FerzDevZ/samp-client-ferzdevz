# Arsitektur

Baca ini sebelum mengubah nama package, simbol JNI, atau path penyimpanan.

## Struktur

```
client/                               source klien yang di-track (fork kuzia15/SAMP-Mobile GTA-2.11)
  app/src/main/java/
    com/ferzdevzgta/game/             entry Java milik kita (SAMP, GTASA, ui/*), 7 file
    com/ferzdevzgta/launcher/         activity launcher, 3 file
    com/rockstargames/oswrapper/      wrapper game, 32 file, NAMA PACKAGE TERKUNCI
    com/rockstargames/gtasa/          shell activity/downloader, 5 file
    com/nvidia/devtech/               glue NVIDIA, 3 file, NAMA PACKAGE TERKUNCI
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
| `/storage/emulated/0/GTA/SAMP/servers.txt` | daftar server untuk browser | `servers/GameConfig.java` |
| `/storage/emulated/0/GTA/SAMP/fonts/` | font UI native | `samp/main.cpp:295` |
| `/storage/emulated/0/GTA/logcat.txt` | log klien | `samp/main.cpp:397` |

Tree asetnya sendiri tidak dikirim, lihat [DATA.md](DATA.md).

## Memilih server

Klien native membaca `[client] host`, `port`, `name` dan `password` dari
`SAMP/settings.ini` sekali, pada frame pertama, dan tidak pernah lagi. Jadi semua yang
menentukan koneksi harus menulis berkas itu sebelum game jalan:

| Bagian | Berkas | Tugas |
|---|---|---|
| Layar browser | `game/ui/ServerBrowserActivity.java` | menampilkan daftar, menambah server, menyimpan nama pemain, menjalankan game |
| Berkas daftar | `game/servers/ServerList.java` | `SAMP/servers.txt`, satu server per baris: nama tampilan, lalu `host:port`, dipisah garis vertikal |
| Aturan alamat | `game/servers/ServerEndpoint.java` | validasi host dan port, batas nama pemain |
| Penulis berkas | `game/servers/GameConfig.java` | membuat `settings.ini` saat instal baru, menulis `[client]` |

Pemain tidak pernah mengedit ini secara manual. Dua penjaga mencegah berkas rusak
mengakhiri sesi:

1. `settings.cpp` mencatat peringatan, bukan memanggil `std::terminate()`, ketika berkas
   tidak ada atau tidak terbaca, dan setiap key jatuh ke nilai bawaan.
2. `main.cpp` melewati koneksi ketika `[client] host` kosong dan menuliskannya di log,
   bukan mengarahkan RakNet ke alamat kosong.

Set key yang ditulis `GameConfig` diperiksa terhadap `settings.cpp` oleh
`GameConfigTest`, jadi menambah key di engine tanpa menambahkannya di template akan
menggagalkan `bin/self-test.sh`.

## Layar dan bahasa

Ada lima layar di depan engine. Semuanya dibangun di Java memakai `ui/Ui.java`, bukan berkas
layout, jadi palette, jarak dan ukuran sentuh tidak bisa berbeda antar layar:

| Layar | Kelas | Tugas |
|---|---|---|
| Launcher | `launcher/activity/MainActivity.java` | menampilkan status data, server dan nama pemain, membuka game atau layar setup |
| Data | `game/ui/DataSetupActivity.java` | pilih varian, verifikasi, unduh dengan resume, bagikan log |
| Server | `game/ui/ServerBrowserActivity.java` | daftar server, tambah, hapus, sambung |
| Pengaturan | `game/ui/UiSettingsActivity.java` | key `settings.ini` yang dibaca engine |
| Bahasa | bagian dari launcher, data dan pengaturan | Indonesia atau Inggris |

Bahasa disimpan oleh aplikasi, bukan oleh engine:

- `game/ui/Language.java` memegang aturannya dan tanpa import Android, jadi
  `tests/jvm/src/com/ferzdevzgta/game/ui/LanguageTest.java` bisa menjalankannya dengan
  `javac` biasa. `id`, `id-ID`, `in` dan `IN_br` semuanya menjadi `in`; bahasa yang tidak
  didukung jatuh ke Inggris; nilai kosong berarti "ikut perangkat".
- `game/ui/LanguageStore.java` menyimpan pilihan di `ui_language` lalu memanggil
  `AppCompatDelegate.setApplicationLocales`, yang membuat ulang layar.
- `game/SampApplication.java` memakai pilihan tersimpan di `onCreate`, sebelum activity
  pertama, jadi launcher tidak pernah berkedip dengan bahasa yang salah.

`res/values/strings.xml` berisi bahasa Inggris, `res/values-in/strings.xml` berisi bahasa
Indonesia, dan `bin/self-test.sh strings` gagal ketika sebuah key hanya ada di salah
satunya, atau ketika kedua terjemahan memakai placeholder format yang berbeda.

Engine punya bahasanya sendiri (`gui.Language` di `settings.ini`, dipetakan oleh
`services/LanguageServices.java`) dan pemilih ini tidak menyentuhnya: UI game dan UI
launcher diterjemahkan terpisah, dan `language_note` menjelaskannya di layar.

Warna hanya ada di satu tempat, `ui/Ui.java`, dan `bin/ui-contrast.py` membaca berkas itu
lalu menghitung rasio WCAG 2.2 AA untuk tujuh belas pasangan yang benar-benar dipakai
layar. Warna hint yang terlalu gelap akan gagal di pemeriksaan itu, bukan menjadi tidak
terbaca di HP.

Aplikasi menargetkan SDK 36, dan Android 15 menggambar aplikasi seperti ini sampai ke tepi
layar: jendela memanjang ke bawah jam dan navigation bar, mau temanya meminta atau tidak.
Setiap layar memanggil `Ui.applyInsets` pada content view, yang menambahkan inset system bar
dan cutout ke padding yang sudah ada. Tanpa itu, baris pertama sebuah layar menimpa status
bar, persis jenis tumpang tindih yang tidak punya error kompilasi.

Launcher menaruh tombol aksinya di bilah bawah, bukan di ujung scroll, jadi tombol utama
tetap terjangkau jempol dan layar pendek tidak berakhir dengan ruang kosong. Kartu statusnya
menampilkan satu titik per syarat, dan tombol utamanya berganti label, Mainkan atau Siapkan
dulu, bukan mengulang tiga kata yang sama di tiga kontrol berbeda.
