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
| `libsamp.so` arm64-v8a dari source | **ya** — 3,5 MB, 0 warning |
| APK debug | **ya** — 36 MB, `com.ferzdevzgta.game`, label `SA-MP FerzDevZ`, versionName `0.1.0` |
| Rebuild bersih tanpa cache | **ya** — 2m08s, 0 warning |
| Telemetri | **dihapus** — Firebase, AppLovin, Splunk; APK 43 MB → 36 MB |
| `libsamp.so` terbangun tanpa binary Rockstar | **ya** — 10s, inilah yang dijalankan CI |
| Data game | **tidak ada di repo ini** — lihat [docs/id/DATA.md](docs/id/DATA.md) |
| APK di GitHub Release | **tidak** — APK membawa `libGame.so` milik Rockstar, lihat [docs/id/LEGAL.md](docs/id/LEGAL.md) |

## Unduhan

Tidak ada tautan unduhan. APK-nya tidak bisa diterbitkan dari sini, dan tautan
tanpa tree aset hanya akan terpasang lalu langsung tertutup.

Bangun sendiri, sekitar 3 menit:

```bash
git clone https://github.com/FerzDevZ/samp-client-ferzdevz
cd samp-client-ferzdevz
bin/fetch-blobs.sh        # libGame.so + BASS .so dari reference upstream
bin/android-build.sh      # -> client/app/build/outputs/apk/debug/samp-client-debug.apk
adb install -r client/app/build/outputs/apk/debug/samp-client-debug.apk
```

Lalu taruh tree aset di `/storage/emulated/0/GTA/`, kalau tidak aplikasi tertutup
saat dibuka — [docs/id/DATA.md](docs/id/DATA.md).

Kalau hanya ingin memastikan kodenya terompilasi, lewati keduanya:

```bash
bin/android-build.sh native     # libsamp.so saja, tanpa binary Rockstar
```

## Apa yang dibuktikan tiap perintah

| Perintah | Bukti |
|---|---|
| `bin/android-build.sh clean` | seluruh klien terompilasi dan terpaket; memeriksa isi APK, tidak mempercayai exit code gradle |
| `bin/android-build.sh native` | C++ kita menghasilkan ELF ARM64 nyata tanpa file Rockstar |
| `bin/build-cache.sh …` | tree aset lengkap dan cocok dengan manifest SHA-256 |
| `bin/build-cache.sh --verify` | cache yang ada identik byte-per-byte dengan manifest-nya |

## Struktur

```
client/     source klien (1322 file) — lihat client/README.md
bin/        gate build, fetch blob, dan cache — lihat bin/README.md
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

`MEMORY.md` adalah catatan invarian: setiap kegagalan non-trivial yang pernah
terjadi di proyek ini, akar penyebabnya, dan aturan yang mencegahnya terulang.

## Kebutuhan

JDK 17, Android SDK dengan `platforms;android-36`, `build-tools;36.0.0`,
`ndk;26.2.11394342`, `cmake;3.22.1`. Rincian dan mode gagalnya di
[docs/id/BUILD.md](docs/id/BUILD.md).

## Legal

Tidak ada kode Rockstar dan tidak ada aset Rockstar yang di-track di sini.
`libGame.so`, library audio BASS, dan data game permanen di luar git. Grand Theft
Auto dan San Andreas adalah merek dagang Take-Two Interactive; SA-MP adalah mod
multiplayer terpisah. Proyek ini tidak berafiliasi dengan keduanya.
