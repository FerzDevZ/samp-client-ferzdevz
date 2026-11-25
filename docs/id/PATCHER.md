# Patcher

APK yang bisa dipasang butuh `libGame.so` milik Rockstar, dan itu tidak boleh
didistribusikan. Patcher memecah klien jadi dua bagian: satu yang boleh
diterbitkan, satu yang diambil dari instalasi game orang itu sendiri.

```
dist/samp-client-framework.apk   milik kita, 28 MB, tanpa libGame.so   <- boleh diterbitkan
        +  lib/arm64-v8a/libGame.so  dari APK GTA SA milik pengguna
        =  APK yang terpasang dan jalan
```

Tidak ada file lain dari APK game yang disalin, dan hasilnya tidak pernah keluar
dari mesin yang menjalankan alat ini.

## Pemakaian

```bash
# sekali, atau setelah kode klien berubah
bin/android-build.sh framework
#   -> dist/samp-client-framework.apk (28 MB, sudah ditandatangani, tanpa libGame.so)

# untuk perangkat mana pun
bin/patch-apk.sh --gta-apk /path/to/GTA.apk --out dist/samp-client-0.2.0.apk

# instalasi split (base.apk + split_config.*.apk), bentuk normal dari Play Store
bin/patch-apk.sh --gta-split-dir /path/to/split --out dist/samp-client-0.2.0.apk
```

`--framework <path>` menimpa lokasi framework default. `--out` wajib. Pilih salah
satu: `--gta-apk` atau `--gta-split-dir`, jangan keduanya.

Dengan `--gta-split-dir` tool menyisir semua `.apk` di direktori itu, mengambil nama
package dari file yang mendeklarasikan `com.rockstargames.gtasa` (yaitu `base.apk`)
dan mengambil library dari file yang benar-benar memuat
`lib/arm64-v8a/libGame.so` (biasanya `split_config.arm64_v8a.apk`). Kalau tidak ada
file yang memuat library, ia gagal, artinya split ABI yang salah yang disalin.

## Urutan kerjanya

1. Tentukan sumber instalasi game: satu APK, atau direktori split di mana package
dan library bisa berada di file berbeda. Pastikan APK library memuat
`lib/arm64-v8a/libGame.so` dan APK package mendeklarasikan
`package com.rockstargames.gtasa`. Gagal di sini jauh lebih murah daripada
menghasilkan APK mati berukuran 36 MB.
2. Periksa framework: harus punya `libsamp.so` dan **tidak** punya `libGame.so`.
3. Keluarkan `libGame.so` ke direktori sementara, pastikan isinya
   `ELF 64-bit LSB shared object, ARM aarch64`.
4. Suntik dengan `zip -0`, disimpan, bukan dipadatkan. Library native harus bisa
   di-mmap langsung dari APK; yang dipadatkan tidak bisa, dan aplikasi mati dengan
   `UnsatisfiedLinkError` sebelum UI muncul.
5. `zipalign -p -f 4`: alignment halaman untuk `.so`, 4 byte untuk sisanya.
6. Tanda tangani dengan `~/.samp-client/patcher.keystore`, dibuat saat pertama
   dijalankan (`keytool`, RSA 2048, 10.000 hari, mode 600).
7. Verifikasi artifact, bukan exit code: `apksigner verify`, kedua `.so` ada dan
   terbaca, keduanya `Stored` di zip, `zipalign -c -p 4` masih lolos setelah
   ditandatangani, dan `aapt2 dump badging` melaporkan `com.ferzdevzgta.game`.

Terukur di mesin ini: framework 28 MB, hasil patch 37 MB, sekitar 20 detik setelah
framework-nya ada.

## Di HP (Termux)

`bin/patch-termux.sh` mengerjakan hal yang sama tanpa PC. Alasannya: hasilnya dipakai
di HP, dan `zipalign` serta `aapt2` adalah binary Android SDK x86-64 yang tidak bisa
jalan di perangkat aarch64.

```bash
pkg install openjdk-21 apksigner zip unzip python     # sekali saja

bin/patch-termux.sh --gta-apk ~/storage/downloads/GTA.apk
bin/patch-termux.sh --gta-split-dir ~/gta-split --out ~/samp-client.apk
```

Yang menggantikan tiap langkah khusus PC:

| Langkah di PC | Di HP |
|---|---|
| suntik `zip -0` | `zip -0 -X` yang sama, stored |
| `zipalign -p -f 4` | `apksigner --lib-page-alignment 4096` meratakan library saat menandatangani |
| `apksigner verify`, `aapt2 dump badging` | `apksigner verify` plus `bin/apk-verify.py` yang membaca struktur zip sendiri |

`bin/apk-verify.py` membuka local header `lib/arm64-v8a/libGame.so` dan memastikan
metode kompresinya 0, offset datanya kelipatan 4096, isinya sama dengan file yang
disuntikkan, dan nama package ada di APK. Tidak butuh SDK.

Terukur: framework 28 MB masuk, 36 MB keluar. Hasilnya diperiksa di mesin yang punya
SDK asli: `zipalign -c -p 4` lolos, `apksigner verify` lolos, `aapt2 dump badging`
melaporkan `com.ferzdevzgta.game`.

APK framework tetap harus datang dari suatu tempat. Hari ini itu mesin build kamu
sendiri, atau salinan `dist/samp-client-framework.apk`, karena framework belum
diterbitkan (lihat [LEGAL.md](LEGAL.md) dan [OSWRAPPER.md](OSWRAPPER.md)). Termux
menghapus PC dari langkah patch, bukan dari langkah build framework.

`bin/build-termux.sh` adalah satu perintah yang dijalankan, bukan mengingat-ingat hal di atas.
Script ini melaporkan tool mana yang ada, mengunduh framework kalau kamu beri URL, menolak
framework yang lebih tua dari source di sebelahnya, lalu memanggil `bin/patch-termux.sh`.

```bash
bin/build-termux.sh --from-url http://100.85.253.77:8000/samp-client-framework.apk \
  --gta-apk ~/storage/downloads/GTA.apk --out ~/samp-client.apk
bin/build-termux.sh --check-only
```

Pemeriksaan umur file ada karena APK hasil patch dari framework yang lebih tua dari perubahan Java
terakhir terlihat baik-baik saja dan menjalankan UI lama. Pakai `--force` kalau perbedaannya memang
tidak penting. Diukur di mesin ini dengan framework asli dan split GTA buatan: exit 0, `PASS`,
package `com.ferzdevzgta.game`, `libGame.so` tersimpan di offset kelipatan 4096.

## Pengujian yang dijalankan

| Kasus | Harus | Hasil |
|---|---|---|
| APK game + framework | PASS | PASS, 37 MB, `libGame.so` identik byte-per-byte dengan sumber (`sha256` dibandingkan) |
| framework yang masih membawa `libGame.so` | tolak | `FAIL: … already contains libGame.so` |
| APK tanpa `libGame.so` | tolak | `FAIL: … has no lib/arm64-v8a/libGame.so` |
| APK dengan package salah | tolak | `FAIL: … is not GTA San Andreas (package 'com.ferzdevzgta.game')` |
| `build-termux.sh` hanya cek | laporan | 0 tool hilang, "pc needed: none" |
| `build-termux.sh` dengan framework lebih tua dari source | tolak | `FAIL: client/app/src/main/... is newer than the framework APK` |
| `build-termux.sh --force` + split GTA buatan | PASS | exit 0, `PASS`, 36 MB, offset library kelipatan 4096 |

## Batasan

- **Hanya arm64-v8a.** APK game harus membawa `libGame.so` arm64; kalau hanya ada
  `armeabi-v7a`, alat ini menolak daripada menghasilkan APK rusak.
- **Instalasi split.** Berikan `--gta-split-dir` berisi `base.apk` dan split
  config-nya. Package dan library dicari berdasarkan isi, jadi base APK yang memuat
  manifest dan split ABI yang memuat `libGame.so` adalah kombinasi yang didukung.
- **Aset terpisah.** APK tetap membaca `/storage/emulated/0/GTA/`; bangun tree itu
  dengan `bin/build-cache.sh` dari instalasi game yang sama.
- **Tanpa obfuscation, tanpa rename.** Kalau APK pengguna menamai library-nya lain
  (hasil repack), pemeriksaan suntik akan menolaknya.
