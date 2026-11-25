# Batas legal

Bukan nasihat hukum. Ini garis yang diambil repo ini, dan alasan tiap bagiannya.

## Yang ada di repo ini

| Di-track | Kenapa boleh |
|---|---|
| source `client/` — 1322 file, C++ dan Java | kode klien open-source, dimodifikasi |
| `libsamp.so` hasil build kita (artifact CI) | hasil build kita, tidak butuh byte Rockstar untuk memproduksinya |
| `bin/`, `docs/`, `.github/` | tooling dan teks |

## Yang tidak boleh ada

| Di-ignore | Pemilik | Alasan |
|---|---|---|
| `client/app/src/main/jniLibs/arm64-v8a/libGame.so` (8,5 MB) | Rockstar | library game hasil kompilasi mereka |
| `client/app/src/main/cpp/samp/vendor/bass/libs/**/*.so` (7,9 MB) | un4seen | lisensi BASS melarang redistribusi |
| tree aset (`/storage/emulated/0/GTA/`) | Rockstar | data game |
| `dist/*.apk` | campuran | APK membawa `libGame.so` |

`bin/fetch-blobs.sh` menyalin dua yang pertama dari reference upstream di mesin
yang boleh menyimpannya. Itu tidak membuatnya boleh didistribusikan.

## Kenapa APK tidak diterbitkan di sini

APK-nya 36 MB dan berisi `libGame.so`. Mengunggahnya ke GitHub Release sama dengan
mendistribusikan binary Rockstar. Itu juga sebabnya CI berhenti di `libsamp.so`:
artifact itu hanya berisi kode dari repo ini.

Kalau kamu memilih mendistribusikan APK-nya sendiri, itu akunmu dan keputusanmu;
tidak ada bagian di repo ini yang mengotomatiskan itu.

## Nama yang dibiarkan

Tiga package Java memakai nama aslinya karena binary prebuilt merujuknya lewat path
— mengganti namanya merusak aplikasi, bukan status hak cipta:

| Package | Bukti |
|---|---|
| `com.rockstargames.oswrapper` | 38 simbol `Java_com_rockstargames_oswrapper_GameNative_*` di `libGame.so`; string `DeviceInfo`, `GamePlatformServices` |
| `com.nvidia.devtech` | string `com/nvidia/devtech/NvUtil` di `libGame.so` |
| `com.rockstargames.gtasa` | menempel langsung di wrapper yang terkunci; shell activity `GameActivity`, `DownloaderActivity` |

Namespace milik klien sendiri: `com.ferzdevzgta.game` / `com.ferzdevzgta.launcher`.
Perintah yang menghasilkan bukti di atas ada di
[ARCHITECTURE.md](ARCHITECTURE.md#namespace-yang-tidak-boleh-di-rename).

## Merek dagang

Grand Theft Auto dan San Andreas adalah merek dagang Take-Two Interactive. SA-MP
adalah nama mod multiplayer terpisah. Proyek ini tidak berafiliasi dengan, tidak
didukung oleh, dan tidak disponsori keduanya.
