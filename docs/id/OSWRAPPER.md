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
rewritten: 2 files, 162 lines
  ok    com/nvidia/devtech/NvUtil.java
  ok    com/rockstargames/oswrapper/DeviceInfo.java
derived:   33 files, 4958 lines
```

Jalankan sendiri untuk angka terbaru; tabel di bawah adalah urutan kerja, bukan janji
tanggal.

| Urutan | File | Alasan urutan |
|---|---|---|
| 1 | `DeviceInfo`, `NvUtil` | kecil, tanpa dependensi, dan dibaca engine paling awal |
| 2 | `GamePlatformServices` | kelas ketiga yang disebut `libGame.so`, callback ke Java |
| 3 | `GameNative` | hanya 38 deklarasi, tidak ada isi yang perlu dikarang |
| 4 | `GameActivityBase`, `GameView`, `view/*` | permukaan activity dan input yang dipakai kode kita sendiri |
| 5 | `GameThread` (1221 baris) | loop frame; terakhir karena semua di atas harus stabil dulu |
| 6 | `DownloaderActivityBase`, `DownloaderConfig`, `DownloaderItem` | cangkang Play Asset Delivery, sudah digantikan downloader sendiri, kandidat dihapus bukan ditulis ulang |
| 7 | `services/*`, `AssetUtils`, `NvAPKFile*` | adapter tipis, risiko rendah |

## Cara menambah satu penulisan ulang

1. Baca file saat ini dan bukti binary yang mengikatnya.
2. Tulis ulang isinya; pertahankan nama yang dipatok, daftar argumen, dan nama field publik.
3. Letakkan baris ini di paling atas file supaya audit melihatnya:

   ```java
   // Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
   ```

4. `bin/oswrapper-audit.sh` harus menampilkan file itu di bagian `rewritten`.
5. `cd client && ./gradlew :app:assembleDebug` harus tetap hijau.
6. Sebutkan di commit kalau ada perubahan perilaku. Penulisan ulang yang setara tidak
   perlu catatan perilaku; yang memperbaiki crash null perlu.

## Yang tetap belum terverifikasi

Tidak ada bagian ini yang jalan tanpa HP dengan data game dan APK hasil patch. Yang
bisa dibuktikan di mesin build hanya compile, audit, dan pemeriksaan simbol. Sebuah
penulisan ulang disebut selesai kalau `bin/oswrapper-audit.sh --strict` keluar dengan 0
**dan** build hasil patch berhasil membuka game di HP sungguhan.
