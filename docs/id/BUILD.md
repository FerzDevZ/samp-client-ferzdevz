# Build

## Prasyarat

```bash
sudo apt install -y openjdk-17-jdk-headless unzip
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install \
  'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' \
  'ndk;26.2.11394342' 'cmake;3.22.1'
```

Versi dipatok karena tree ini ditulis terhadap versi tersebut: Gradle 8.13,
AGP 8.13.1, Kotlin 2.2.0, NDK r26c, cmake 3.22.1. NDK lain biasanya masih jalan;
cmake lain sering tidak.

## Perintah

| Perintah | Menghasilkan | Butuh `libGame.so` |
|---|---|---|
| `bin/fetch-blobs.sh` | menyalin `libGame.so` + BASS `.so` dari reference upstream | - |
| `bin/android-build.sh native` | `libsamp.so` saja | tidak |
| `bin/android-build.sh` | APK debug | ya |
| `bin/android-build.sh release` | APK release (unsigned tanpa keystore) | ya |
| `bin/android-build.sh clean` | hapus build dir, lalu APK debug | ya |

Pertama kali di clone baru:

```bash
bin/fetch-blobs.sh
bin/android-build.sh clean
```

## Apa yang dibuktikan gate

`bin/android-build.sh` tidak mempercayai exit code gradle. Yang dilakukan:

1. cek path JDK, NDK, dan cmake sebelum mulai;
2. menulis `client/local.properties` (AGP membaca path SDK dari sana, bukan dari env);
3. memasang exec bit pada `client/gradlew` (repo menyimpannya tanpa bit itu);
4. menambah newline di akhir `client/gradle.properties` bila belum ada
   (append ke file yang baris terakhirnya tanpa newline akan menempelkan key baru
   ke key lama, dan key itu diabaikan tanpa error);
5. build, lalu unzip APK dan memastikan `lib/arm64-v8a/libsamp.so` **dan**
   `lib/arm64-v8a/libGame.so` ada di dalamnya.

Di mode `native`, langkah 5 menjadi: cari `libsamp.so` di
`app/build/intermediates/cxx`, gagal bila tidak ada. Hanya mode ini yang bisa
jalan tanpa binary berhak cipta, jadi itulah yang dipakai CI.

Terukur di mesin ini (openjdk 17.0.21.1, NDK r26c, cmake 3.22.1):

| Run | Waktu | Hasil |
|---|---|---|
| `clean` (penuh) | 2m08s–2m50s | APK 36 MB, `libsamp.so` 3.5 MB, 0 warning |
| `native` | 10s | `libsamp.so` 3.5 MB |

"0 warning" artinya `grep -ci 'warning:'` atas log build mengembalikan 0.

## Output

```
client/app/build/outputs/apk/debug/samp-client-debug.apk
client/app/build/intermediates/cxx/Debug/<hash>/obj/arm64-v8a/libsamp.so
```

`dist/` menyimpan salinan APK terakhir yang dibangun di sini beserta SHA-256-nya.
`dist/` di-ignore git: APK-nya membawa `libGame.so`.

```bash
sha256sum -c dist/samp-client-0.2.0-debug.apk.sha256
adb install -r dist/samp-client-0.2.0-debug.apk
```

Terpasang belum berarti bisa dibuka. Pembukaan pertama butuh tree aset di
`/storage/emulated/0/GTA/`, lihat [DATA.md](DATA.md).

## CI

`.github/workflows/ci.yml` jalan pada push dan pull request:

- memastikan tidak ada binary proprietary yang ter-track di git (`libGame.so`, BASS `.so`);
- menjalankan `bin/android-build.sh native`;
- memastikan hasilnya `ELF 64-bit LSB shared object, ARM aarch64`;
- memastikan simbol SA-MP ada di dalam binary;
- mengunggah `libsamp.so` sebagai artifact.

CI tidak pernah merakit atau menerbitkan APK, karena itu berarti mendistribusikan
binary Rockstar. Lihat [LEGAL.md](LEGAL.md).

## Jebakan build yang sudah diketahui

| Gejala | Penyebab | Perbaikan |
|---|---|---|
| `fatal error: samp_widgets/playerTabList.h` | nama header `playertablist.h`; tree ditulis di Windows | sudah diperbaiki di `client/` |
| `fatal error: RGBA.h` | masalah huruf besar/kecil yang sama | sudah diperbaiki di `client/` |
| error link `libbass.so` ganda | `.so` yang sama ada di `jniLibs/` **dan** di baris link CMake | hapus keduanya dari `jniLibs/` (sudah) |
| properti gradle diabaikan diam-diam | baris terakhir `gradle.properties` tanpa newline | gate menambah newline lebih dulu |
| `./gradlew: Permission denied` | git tidak menyimpan exec bit | gate melakukan chmod |
| build "lulus" tapi APK tanpa native lib | exit code gradle bukan bukti | gate memeriksa isi APK |
| APK terpasang lalu crash saat `dlopen` `libGame.so` | AGP menulis `extractNativeLibs="false"` tetapi library-nya tidak di offset halaman, jadi loader memetakannya di alignment salah | setiap APK yang diselesaikan script ini lewat `zipalign -p 4` lalu ditandatangani ulang dengan key patcher, dan `bin/apk-verify.py` membuktikan offset-nya |
