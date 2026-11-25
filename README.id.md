# samp-client-ferzdevz

Klien SA-MP untuk Android. Kode native (`libsamp.so`, arm64-v8a) dibangun dari
source di Linux, bisa masuk server SA-MP, dan menjalankan engine GTA San Andreas
lewat hook ke library game yang sudah ada di perangkat.

Source: `client/`, fork dari [`kuzia15/SAMP-Mobile`](https://github.com/kuzia15/SAMP-Mobile)
branch `GTA-2.11`, lalu di-port ke Linux, di-rebrand, dan telemetrinya dicabut.
Semua perubahan ada di [docs/id/UPSTREAM.md](docs/id/UPSTREAM.md).

**English: [README.md](README.md)**

## Status

Semua baris di bawah dijalankan di mesin ini, bukan diasumsikan.

| Item | Kondisi |
|---|---|
| `libsamp.so` arm64-v8a dari source | **ya**, 3,5 MB, 0 warning |
| APK debug | **ya**, 37 MB, `com.ferzdevzgta.game`, label `SA-MP FerzDevZ`, versionName `0.4.0` |
| Rebuild bersih tanpa cache | **ya**, 2m08s, 0 warning |
| Telemetri | **dihapus**, Firebase, AppLovin, Splunk; APK 43 MB → 37 MB |
| `libsamp.so` terbangun tanpa binary Rockstar | **ya**, 10s, inilah yang dijalankan CI |
| Data game | **tidak ada di repo ini**, aplikasi mengunduhnya dari hostmu sendiri, lihat [docs/id/DATA.md](docs/id/DATA.md) |
| Setup data di HP | **ada**, pilih varian, verifikasi SHA-256, unduh dengan resume, bagikan log |
| APK di GitHub Release | **tidak**, APK membawa `libGame.so` milik Rockstar, lihat [docs/id/LEGAL.md](docs/id/LEGAL.md) |
| Tulis ulang wrapper | **berjalan**, 20 dari 21 file, tinggal `GameThread`, sisanya dilaporkan `bin/oswrapper-audit.sh`, namanya dijaga `bin/wrapper-pins.sh` dan `bin/jni-pins.py` |
| Patch di HP | **ya**, `bin/patch-termux.sh` dengan apksigner, tanpa binary SDK |
| Memilih server | **ya**, `ServerBrowserActivity` menulis `SAMP/settings.ini` dari `SAMP/servers.txt`, tanpa edit manual |
| Bahasa | **ya**, Indonesia atau Inggris, dipilih di dalam aplikasi, disimpan di `ui_language`, `bin/self-test.sh strings` menolak kalau kedua file resource berbeda |
| Status server langsung | **ya**, ping dan jumlah pemain per server di daftar, satu query UDP per baris dengan timeout 1200 ms, tidak pernah di main thread |
| Diagnostik | **ya**, `DiagnosticsActivity` membaca HP: pustaka native, lima titik masuk JNI, pohon data, manifest, target, 20 baris log terakhir, salin dan bagikan |
| Catatan crash | **ya**, pengecualian Java yang tidak tertangani ditambahkan ke `GTA/logcat.txt` sebagai blok bertanda dengan waktu UTC dan versi aplikasi, diputar pada 2 MiB, lalu tampil di Diagnostik |
| Layar launcher | **didesain ulang**, menampilkan status data, server dan nickname sebelum menekan Mulai, `bin/ui-contrast.py` menjaga palet di atas WCAG AA |
| Gamepad | **ya**, tiga callback stik yang sudah diekspor `libGame.so` tapi belum dideklarasikan sekarang disambung, jadi stik tersambung, tersambung lagi setelah pause, dan terlihat di Diagnostik, `bin/jni-pins.py` melaporkan 0 ekspor tanpa pemakai |
| Backup profil | **ya**, Pengaturan mengekspor satu zip berisi `settings.ini` dan `servers.txt` lalu memulihkannya di HP baru, nama entri tetap, batas 512 KiB per entri |
| Panduan offline | **ya**, `assets/help/help.html` digambar WebView bawaan sistem, dua bahasa, tanpa menambah ukuran APK |
| Tautan server | **ya**, `samp://host:port` membuka launcher dengan server itu terpilih, tekan lama server untuk menyalin tautannya |
| Voice chat | **ya**, saklar `gui/VoiceChatEnable` di Pengaturan meminta mikrofon dulu, dan launcher memeriksa lagi sebelum menjalankan game |
| Tangkapan layar | **ya**, tombol kamera di layar game menyimpan ke `GTA/Screenshots` dan membuka menu bagikan |
| Penanda sesi | **ya**, Diagnostik memberi tahu kalau sesi terakhir tidak ditutup normal, satu-satunya jejak yang ditinggalkan crash native |
| Editor HUD | **ya**, panel tombol di dalam game membuka editor geser untuk kotak chat, panel tombol dan tombol voice, disimpan ke `[hud]` di `settings.ini` |
| Daftar model 0.3.DL | **baru pemeriksa**, `bin/artconfig.py` memvalidasi `artconfig.txt` dan menulis manifest; client belum bisa menggambar model kustom, lihat [docs/id/DL.md](docs/id/DL.md) |
| Catatan perubahan | **ya**, [CHANGELOG.md](CHANGELOG.md) dan [CHANGELOG.id.md](CHANGELOG.id.md) |

## Mendapatkan APK yang bisa dipasang

Tidak ada APK yang diterbitkan. APK yang bisa dipasang membawa `libGame.so` milik
Rockstar (8,5 MB) dan tidak boleh didistribusikan dari repo ini, jadi dibangun di
mesinmu dari APK game yang kamu miliki:

```bash
bin/android-build.sh framework        # 1. separuh yang boleh diterbitkan -> dist/samp-client-framework.apk (28 MB)
bin/patch-apk.sh --gta-apk GTA.apk \
                 --out dist/samp-client-0.4.0.apk      # 2. tambah libGame.so, realign, tanda tangan ulang (37 MB)
adb install -r dist/samp-client-0.4.0.apk              # 3. pasang
adb push cache/full/. /storage/emulated/0/GTA/         # 4. aset, kalau tidak aplikasi tertutup saat dibuka
```

Tanpa PC, patching langsung di HP:

```bash
pkg install openjdk-21 apksigner zip unzip python     # Termux, sekali saja
bin/patch-termux.sh --gta-apk ~/storage/downloads/GTA.apk --out ~/samp-client.apk
```

Alur lengkap, waktu terukur, dan empat kasus penolakan: [docs/id/PATCHER.md](docs/id/PATCHER.md).

Kalau hanya ingin mengompilasi tanpa file game sama sekali:

```bash
bin/android-build.sh native     # libsamp.so saja, 3,5 MB, inilah yang dijalankan CI
```

## Apa yang dibuktikan tiap perintah

| Perintah | Bukti |
|---|---|
| `bin/android-build.sh clean` | seluruh klien terompilasi dan terpaket; memeriksa isi APK, tidak mempercayai exit code gradle |
| `bin/android-build.sh framework` | separuh yang boleh diterbitkan: APK sama tanpa `libGame.so` milik Rockstar, ditandatangani ulang |
| `bin/android-build.sh native` | C++ kita menghasilkan ELF ARM64 nyata tanpa file Rockstar |
| `bin/build-cache.sh …` | tree aset lengkap dan cocok dengan manifest SHA-256 |
| `bin/build-cache.sh --verify` | cache yang ada identik byte-per-byte dengan manifest-nya |
| `bin/patch-apk.sh` | APK hasil rakitan bisa dipasang: kedua library native stored, aligned, signature valid |
| `bin/self-test.sh` | parsing manifest, downloader terhadap server HTTP lokal, rotasi log, editor INI, round trip cache, dan penolakan patcher, 15 cek, tanpa HP |

## Struktur

```
client/     source klien (1322 file), lihat client/README.md
bin/        gate build, fetch blob, dan cache, lihat bin/README.md
docs/       arsitektur, build, data, legal, operasional, selisih upstream
.github/    CI: mengompilasi libsamp.so, menolak mengirim binary proprietary
dist/       APK terakhir yang dibangun di sini + SHA-256, di-ignore git
```

## Dokumentasi

English | Indonesia
---|---
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | [docs/id/ARCHITECTURE.md](docs/id/ARCHITECTURE.md)
[docs/BUILD.md](docs/BUILD.md) | [docs/id/BUILD.md](docs/id/BUILD.md)
[docs/DATA.md](docs/DATA.md) | [docs/id/DATA.md](docs/id/DATA.md)
[docs/OPERATIONS.md](docs/OPERATIONS.md) | [docs/id/OPERATIONS.md](docs/id/OPERATIONS.md)
[docs/LEGAL.md](docs/LEGAL.md) | [docs/id/LEGAL.md](docs/id/LEGAL.md)
[docs/UPSTREAM.md](docs/UPSTREAM.md) | [docs/id/UPSTREAM.md](docs/id/UPSTREAM.md)
[docs/OSWRAPPER.md](docs/OSWRAPPER.md) | [docs/id/OSWRAPPER.md](docs/id/OSWRAPPER.md)

## Kebutuhan

JDK 17, Android SDK dengan `platforms;android-36`, `build-tools;36.0.0`,
`ndk;26.2.11394342`, `cmake;3.22.1`. Rincian dan mode gagalnya di
[docs/id/BUILD.md](docs/id/BUILD.md).

## Legal

Tidak ada kode Rockstar dan tidak ada aset Rockstar yang di-track di sini.
`libGame.so`, library audio BASS, dan data game permanen di luar git. Grand Theft
Auto dan San Andreas adalah merek dagang Take-Two Interactive; SA-MP adalah mod
multiplayer terpisah. Proyek ini tidak berafiliasi dengan keduanya.
