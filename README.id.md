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
| APK debug | **ya**, 37 MB, `com.ferzdevzgta.game`, label `SA-MP FerzDevZ`, versionName `0.1.0` |
| Rebuild bersih tanpa cache | **ya**, 2m08s, 0 warning |
| Telemetri | **dihapus**, Firebase, AppLovin, Splunk; APK 43 MB → 37 MB |
| `libsamp.so` terbangun tanpa binary Rockstar | **ya**, 10s, inilah yang dijalankan CI |
| Data game | **tidak ada di repo ini**, aplikasi mengunduhnya dari hostmu sendiri, lihat [docs/id/DATA.md](docs/id/DATA.md) |
| Setup data di HP | **ada**, pilih varian, verifikasi SHA-256, unduh dengan resume, bagikan log |
| APK di GitHub Release | **tidak**, APK membawa `libGame.so` milik Rockstar, lihat [docs/id/LEGAL.md](docs/id/LEGAL.md) |
| Tulis ulang wrapper | **berjalan**, 3 dari 29 file, sisanya dilaporkan `bin/oswrapper-audit.sh`, namanya dijaga `bin/wrapper-pins.sh` |
| Patch di HP | **ya**, `bin/patch-termux.sh` dengan apksigner, tanpa binary SDK |

## Mendapatkan APK yang bisa dipasang

Tidak ada APK yang diterbitkan. APK yang bisa dipasang membawa `libGame.so` milik
Rockstar (8,5 MB) dan tidak boleh didistribusikan dari repo ini, jadi dibangun di
mesinmu dari APK game yang kamu miliki:

```bash
bin/android-build.sh framework        # 1. separuh yang boleh diterbitkan -> dist/samp-client-framework.apk (28 MB)
bin/patch-apk.sh --gta-apk GTA.apk \
                 --out dist/samp-client-0.1.0.apk      # 2. tambah libGame.so, realign, tanda tangan ulang (37 MB)
adb install -r dist/samp-client-0.1.0.apk              # 3. pasang
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
