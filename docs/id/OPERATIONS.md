# Operasional

## Siklus harian

```bash
git pull
bin/fetch-blobs.sh                 # hanya perlu di clone baru atau setelah clean
bin/android-build.sh clean         # atau: native / release
adb install -r client/app/build/outputs/apk/debug/samp-client-debug.apk
adb push cache/full/. /storage/emulated/0/GTA/
adb logcat -s SAMP:* AndroidRuntime:E
```

Log yang berguna juga ditulis klien ke `/storage/emulated/0/GTA/logcat.txt`.

## Layar

| Layar | Dibuka saat | Fungsinya |
|---|---|---|
| `MainActivity` | aplikasi mulai | izin penyimpanan, lalu game atau setup data |
| `DataSetupActivity` | `GTA/data` belum ada | pilih varian, periksa, unduh, bagikan log |
| `UiSettingsActivity` | dari setup data | kotak chat, font, batas FPS, sakelar GUI |

`MainActivity` merotasi `GTA/logcat.txt` pada 2 MiB dan menyimpan dua cadangan
(`.1`, `.2`) sebelum logger native menulis apa pun, jadi HP yang dipakai berbulan-
bulan tidak memenuhi penyimpanan. Tombol *Bagikan log* di kedua layar mengirim file
terkini lewat `FileProvider` yang sudah ada, ditambah versi aplikasi dan perangkat.

## Membuktikan tooling masih jalan

```bash
bin/self-test.sh            # semuanya, ~5 detik, tanpa HP, tanpa data game
bin/self-test.sh jvm        # 71 assertion JVM saja
bin/self-test.sh cache      # round trip build-cache dan deteksi manipulasi
bin/self-test.sh patcher    # penolakan patcher
```

Tes JVM memakai `javac`/`java` biasa (tanpa JUnit, tanpa Gradle) karena kelas yang
diuji memang sengaja tidak mengimpor Android. Cakupannya: parser dan verifier
manifest, downloader terhadap server HTTP lokal sungguhan (jalan dingin, jalan
hangat, resume `Range`, retry payload rusak, 404), rotasi log, dan editor INI yang
tidak boleh membuang key yang dibutuhkan loader native.

## Gejala → penyebab

| Gejala | Penyebab | Cara cek |
|---|---|---|
| terpasang, langsung tertutup | tree aset tidak ada di `/storage/emulated/0/GTA/` | `adb shell ls /storage/emulated/0/GTA/` |
| layar hitam setelah loading bar | `libGame.so` tidak ada di APK | `unzip -l apk \| grep libGame` |
| `UnsatisfiedLinkError: initializeSAMP` | package Java dan simbol JNI di-rename tidak bersamaan | `nm -D libsamp.so \| grep Java_` vs package di `.java` |
| `UnsatisfiedLinkError: implOnDrawFrame` | `com.rockstargames.oswrapper` di-rename | kembalikan package-nya; dikunci `libGame.so` |
| font UI native hilang atau kosong | `SAMP/fonts/` tidak ada di tree aset | `adb shell ls /storage/emulated/0/GTA/SAMP/fonts/` |
| chat tampil, daftar server kosong | izin jaringan tidak ada, atau klien tidak sampai ke master list | `adb logcat` untuk tag `SAMP` |
| gradle gagal karena `.so` ganda di clone baru | lib BASS ada di `jniLibs/` dan di baris link CMake | `bin/fetch-blobs.sh` tidak mengembalikan duplikat itu; jangan salin balik |
| build hijau tapi APK tanpa `arm64-v8a` | exit code gradle dipercaya | jalankan `bin/android-build.sh` dan baca blok `PASS` |

## Kalau me-rename sesuatu

Urutannya penting, kalau tidak yang dikirim adalah crash:

1. `git mv` direktori package Java;
2. tulis ulang baris `package` dan `import`;
3. tulis ulang simbol `Java_…` yang cocok di `client/app/src/main/cpp` bila class
   mendeklarasikan method `native`;
4. build lalu pastikan: `nm -D --defined-only libsamp.so | grep Java_` menampilkan
   nama baru, dan `aapt2 dump badging apk | grep launchable-activity` menampilkan
   activity baru;
5. perbarui `android:name` di `AndroidManifest.xml` — nama activity harus mengikuti
   package `.java` yang nyata, bukan `applicationId`.

Jangan pernah me-rename `com.rockstargames.oswrapper` atau `com.nvidia.devtech`;
lihat [ARCHITECTURE.md](ARCHITECTURE.md#namespace-yang-tidak-boleh-di-rename).

## Invarian yang perlu dijaga

Masing-masing pernah jadi kegagalan nyata. `MEMORY.md` menyimpan catatan lengkap
beserta akar penyebabnya; versi singkatnya:

- gate APK memeriksa artifact, bukan exit code build;
- nama activity di manifest mengikuti package Java, bukan `applicationId`;
- `libGame.so` dan BASS tetap di luar git, permanen;
- manifest cache boleh dibagikan, cache-nya tidak;
- `android-build.sh native` harus tetap hijau tanpa `libGame.so`, kalau tidak CI
  tidak membuktikan apa pun.

## Verifikasi ulang semuanya

```bash
bin/android-build.sh clean                                   # 0 warning, APK 36 MB
mv client/app/src/main/jniLibs/arm64-v8a/libGame.so /tmp/    # buktikan mode CI jujur
bin/android-build.sh native                                  # 0 warning, libsamp.so 3.5 MB
mv /tmp/libGame.so client/app/src/main/jniLibs/arm64-v8a/
bin/build-cache.sh --out cache --verify                      # cache cocok dengan manifest
```
