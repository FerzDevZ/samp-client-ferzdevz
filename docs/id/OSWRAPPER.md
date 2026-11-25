# Permukaan wrapper (`com.rockstargames.oswrapper`)

**English: [../OSWRAPPER.md](../OSWRAPPER.md)**

Client tidak mengirim library game milik Rockstar. Yang dikirim adalah APK framework
plus patcher yang menambahkan `libGame.so` milik pengguna sendiri (lihat
[PATCHER.md](PATCHER.md)). Dari paket asli, framework masih membawa sekumpulan kelas
Java di `com.rockstargames.oswrapper` dan `com.nvidia.devtech`. Halaman ini mencatat
apa yang dipatok oleh binary, apa yang bebas ditulis ulang, dan sejauh mana
penulisan ulangnya sudah jalan.

## Kenapa namanya tidak boleh berubah

`libGame.so` mencari 38 native dengan nama:

```
$ nm -D --defined-only libGame.so | grep -c Java_
38
$ nm -D --defined-only libGame.so | grep Java_ | head -2
Java_com_rockstargames_oswrapper_GameNative_implIsInitialized
Java_com_rockstargames_oswrapper_GameNative_implOnDrawFrame
```

JNI berbasis nama membuat nama package, kelas, dan method di sisi Java menjadi bagian
dari antarmuka binary. Tiga kelas lain dicari lewat path:

```
$ strings libGame.so | grep -oE 'com/(rockstargames|nvidia)/[A-Za-z0-9/$_]+' | sort -u
com/nvidia/devtech/NvUtil
com/rockstargames/oswrapper/DeviceInfo
com/rockstargames/oswrapper/GamePlatformServices
```

Nama method ikut jalur yang sama. `libGame.so` membawa string yang akan dicarinya, jadi
bagian yang dipatok bisa didaftar dari binary, bukan ditebak:

```
$ for n in getInstance getParameter getAppLocalValue setAppLocalValue hasAppLocalValue hasTouchScreen
> do printf '%-20s %s\n' "$n" "$(strings -n 4 libGame.so | grep -cx "$n")"; done
getInstance          1
getParameter         1
getAppLocalValue     1
setAppLocalValue     1
hasAppLocalValue     1
hasTouchScreen       1
```

Jadi ada dua lapis:

| Lapis | Contoh | Boleh diubah |
|---|---|---|
| Antarmuka binary: package, nama kelas, nama method, tipe argumen, nama field publik | `GameNative.implOnDrawFrame(float)`, `DeviceInfo.osVersion` | tidak |
| Isi method | struktur loop, helper, alokasi, logging, penanganan error | ya |

Penulisan ulang mempertahankan baris pertama tabel apa adanya dan mengganti isinya.

## Ini apa dan bukan apa

Yang diganti adalah Java hasil dekompilasi dex yang pernah dikirim, dengan kode yang
ditulis terhadap antarmuka yang diminta binary dan manifest. Artinya sumber turunan
Rockstar hilang dari repo ini dan dari APK framework.

Ini bukan clean room legal: sesi yang sama sudah membaca file hasil dekompilasi, jadi
pemisahan "penulis spesifikasi belum melihat aslinya" tidak berlaku. Anggap pertanyaan
hukumnya terbuka dan minta pendapat sendiri sebelum bersandar pada ini untuk
menerbitkan. Yang bisa diukur sekarang: APK framework tidak lagi berisi bytecode hasil
dekompilasi untuk kelas-kelas ini, hanya implementasi ulang antarmukanya.

## Keadaan sekarang

`bin/oswrapper-audit.sh` memisahkan tree berdasarkan penanda di baris pertama dan
mencetak jumlah baris. Pengukuran terakhir:

```
rewritten: 20 files, 3038 lines
derived:    1 file,  1224 lines
  todo  com/rockstargames/oswrapper/GameThread.java
```

Jalankan `bin/oswrapper-audit.sh` untuk daftar per file; semua path `ok` dicetak sebelum
satu `todo`. Sembilan file dihapus, bukan ditulis ulang: enam kelas Play Asset Delivery,
`AssetUtils`, `UtilsKt` dan `BuildConfig`. Masing-masing terukur tidak dirujuk di sumber
kita maupun di `libGame.so`, dan ekstraksi asset yang dulu dilakukan `AssetUtils` sekarang
menjadi tugas `bin/build-cache.sh`.

### Yang ditemukan iris keempat

`GameActivityBase`, `GameView` dan `view/*` adalah bagian wrapper yang benar-benar dilewati
kode kita sendiri, jadi iris ini dibaca baris per baris, bukan sekadar diganti nama. Empat
bug keluar dari situ:

| Gejala | Penyebab | Perbaikan |
|---|---|---|
| akselerometer tetap mengirim ke engine setelah game di background | `enableSensors()` tidak pernah mengisi flag `sensorsActive`, jadi `disableSensors()` selalu melewati unregister | hasil `registerListener` disimpan, unregister mengosongkannya |
| sentuhan yang ditahan tetap hidup setelah pause, engine tidak pernah menerima release | `InputHandler.onPause()` kosong | pause melepas setiap pointer yang dilacak lewat `onTouchEnd` |
| intro movie membocorkan file descriptor setiap kali mulai | hasil `getAssets().openFd()` tidak pernah ditutup | descriptor dipakai dalam try-with-resources |
| pause musik menu mengulang lagu dari nol | `pause()` memanggil `MediaPlayer.stop()` yang membuang posisi | `pause()` menyimpan posisi, resume melakukan seek kembali |

Sisanya soal penamaan: `setFormat(-3)` jadi `FORMAT_TRANSLUCENT`, `source & 16` jadi
`SOURCE_CLASS_JOYSTICK`, indeks axis `18`, `19`, `22` jadi `AXIS_RTRIGGER`,
`AXIS_THROTTLE`, `AXIS_GAS`, tujuh belas key code jadi `KeyEvent.KEYCODE_BUTTON_*`, dan id
tombol gamepad diberi nama. `GameActivityBase` juga kehilangan field mati dan tiga blok
komentar, sementara dua root penyimpanan dan root data engine tetap ada sebagai konstanta
yang dijelaskan.

Satu hal yang sengaja tidak diubah: `GetGameBaseDirectory()` tetap mengembalikan folder
`VICE` milik engine, bukan folder SAMP `/storage/emulated/0/GTA/`. Dua konsumen berbeda,
dua path. Menggabungkannya butuh uji di HP, bukan sekadar merapikan, jadi konstantanya
diberi komentar yang menyatakan itu.

### Dua deklarasi native yang tidak mungkin ketemu

Deklarasi `native` tidak gagal sampai method-nya dipanggil, jadi iris ketiga dimulai
dengan perbandingan, bukan penulisan ulang: `bin/jni-pins.py` membaca semua deklarasi
`native` di wrapper dan semua simbol `Java_...` yang diekspor `libGame.so`, lalu gagal
kalau ada deklarasi tanpa simbol. Hasilnya dua:

| Deklarasi | Dipanggil dari | Kenyataan |
|---|---|---|
| `GameNative.initializeSAMP()` | hanya baris komentar di `GameActivityBase` | tidak ada simbolnya di mana pun; entry point asli ada di `SAMP.initializeSAMP` dalam libsamp.so |
| `GameNative.implOnGamepadCountChanged(int)` | `GameThread.onGamepadCountChanged` | tidak ada simbolnya; `libGame.so` justru mengekspor `implOnGamepadConnected`, `implOnGamepadDisconnected`, `implOnGamepadResume` |

Keduanya sudah dihapus. Titik panggilan gamepad tetap menyimpan baris log dan menjelaskan
kenapa tidak memanggil apa pun lagi: mencolok controller sudah sampai ke engine lewat
`implOnGamepadAxesChanged` dan `implOnGamepadButtonDown`. Tiga ekspor yang tidak dipakai
dilaporkan sebagai kode mati, bukan kegagalan, karena tidak ada yang merujuknya dari Java.

Pemeriksaan ini jalan di `bin/self-test.sh` dengan kasus negatif, dan itulah alasan bagian
ini ada: kedua bug tidak terlihat oleh compiler dan akan menunggu HP menyentuh jalur itu.

Iris kedua berisi dua hal, dan satu di antaranya penghapusan, bukan penulisan ulang.
`GamePlatformServices` dulu fasad di atas enam interface `I*Services` yang tugasnya cuma
menyalin vtable C++; `strings -n 6 libGame.so | grep -cx ILanguageServices` menghasilkan
0 untuk keenamnya, jadi tidak ada apa pun di luar source kita yang menyebutnya. Interface
itu dihapus, enam service tetap dengan nama dan isinya, dan engine tetap menemukan
ke-34 nama callback karena nama itulah yang dipatok.

Daftar pin sekarang jadi gerbang, bukan paragraf: `tests/pins/oswrapper-pins.txt` memuat
setiap nama yang terukur di `libGame.so`, dan `bin/wrapper-pins.sh` menjalankan `javap`
atas class hasil kompilasi lalu gagal kalau ada yang hilang. `bin/self-test.sh`
menjalankannya, termasuk kasus negatif dengan nama karangan, karena pemeriksaan yang
tidak bisa gagal bukan pemeriksaan.

Jalankan sendiri untuk angka terbaru; tabel di bawah adalah urutan kerja, bukan janji
tanggal.

| Urutan | File | Alasan urutan |
|---|---|---|
| 1 | `DeviceInfo`, `NvUtil` | kecil, tanpa dependensi, dan dibaca engine paling awal |
| 2 | `GamePlatformServices` | selesai: kelas ketiga yang disebut `libGame.so`, 34 nama callback dipatok |
| 3 | `GameNative` | selesai: 35 deklarasi yang ketemu, dua yang mati dihapus |
| 4 | `GameActivityBase`, `GameView`, `view/*` | selesai: activity, surface dan lima handler, empat bug diperbaiki |
| 5 | `GameThread` (1221 baris) | berikutnya: loop frame; terakhir karena semua di atas harus stabil dulu |
| 6 | `DownloaderActivityBase`, `DownloaderConfig`, `DownloaderItem`, `DownloaderPackConfig`, `DownloaderPackType`, `DownloaderActivity` | selesai, dihapus: cangkang Play Asset Delivery yang sudah digantikan downloader sendiri, 824 baris dan dependensi `asset-delivery` hilang |
| 7 | `services/*`, `NvAPKFile*`, `GameViewHandler` | selesai: enam adapter dan dua kelas asset, `AssetUtils`/`UtilsKt`/`BuildConfig` dihapus karena tidak dirujuk |

## Cara menambah satu penulisan ulang

1. Baca file saat ini dan bukti binary yang mengikatnya.
2. Tulis ulang isinya; pertahankan nama yang dipatok, daftar argumen, dan nama field publik.
3. Letakkan baris ini di paling atas file supaya audit melihatnya:

   ```java
   // Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
   ```

4. `bin/oswrapper-audit.sh` harus menampilkan file itu di bagian `rewritten`.
5. `cd client && ./gradlew :app:assembleDebug` harus tetap hijau.
6. `bin/wrapper-pins.sh` harus lolos; alat itu membaca class hasil kompilasi, jadi rename
   yang diterima compiler tetap tertangkap.
6. Sebutkan di commit kalau ada perubahan perilaku. Penulisan ulang yang setara tidak
   perlu catatan perilaku; yang memperbaiki crash null perlu.

## Yang tetap belum terverifikasi

Tidak ada bagian ini yang jalan tanpa HP dengan data game dan APK hasil patch. Yang
bisa dibuktikan di mesin build hanya compile, audit, dan pemeriksaan simbol. Sebuah
penulisan ulang disebut selesai kalau `bin/oswrapper-audit.sh --strict` keluar dengan 0
**dan** build hasil patch berhasil membuka game di HP sungguhan.
