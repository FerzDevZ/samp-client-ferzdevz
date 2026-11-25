# Verifikasi di perangkat

`bin/self-test.sh` membuktikan logika dan format. Skrip itu tidak bisa membuktikan layar tampak
benar di HP, bahwa alur izin penyimpanan bertahan di ROM nyata, atau bahwa `libGame.so` mau
start dengan pohon data yang benar-benar dimiliki pemain. Berkas ini adalah daftar periksa untuk
bagian itu.

Jalankan sekali di perangkat asli, simpan tabel yang sudah diisi di commit yang memperbaiki
temuannya, lalu jalankan ulang setiap kali launcher, layar diagnostik, atau patcher berubah.

## 0. Sebelum peluncuran pertama

| Periksa | Harapan |
|---|---|
| APK terpasang menimpa versi sebelumnya | pemasangan berhasil, tidak ada `INSTALL_FAILED_UPDATE_INCOMPATIBLE` |
| sha256 berkas yang kamu pasang | sama dengan yang tertulis di `samp-download/index.html` |
| `Setelan → Aplikasi → SA-MP FerzDevZ → Penyimpanan` | izin diberikan, atau aplikasi meminta sekali |
| Versi Android | 9 atau lebih baru; tata letak edge-to-edge baru berpengaruh dari 15 ke atas |

## 1. Frame pertama

| Langkah | Harapan | Kalau gagal |
|---|---|---|
| Ketuk ikon | latar bermerek, tanpa kilatan putih, lalu launcher | window background splash tidak terpakai: periksa `Theme.SampLauncher` di manifest |
| Header | judul berada **di bawah** jam dan baterai, bukan tertimpa | `Ui.applyInsets` tidak dipanggil atau dipanggil sebelum `setContentView` |
| Bar bawah | `Mainkan` atau `Siapkan dulu`, plus `Data` dan `Server`, terjangkau ibu jari | bar berada di dalam scroll view |
| Kartu status | tiga baris, masing-masing dengan titik berwarna, dan satu pil `Siap` atau `Belum siap` | `refreshStatus()` tidak jalan |

Pada instalasi bersih tiga baris harus berbunyi: data `tidak ada`, server `kosong`, nickname
`kosong`, dan tombol utama berbunyi `Siapkan dulu`.

## 2. Bahasa

| Langkah | Harapan |
|---|---|
| `Tampilan → Bahasa Indonesia` | seluruh label di layar itu langsung berubah |
| Tutup dari daftar recent, buka lagi | aplikasi start dalam bahasa Indonesia, tanpa satu frame bahasa Inggris |
| Buka `Diagnostik` saat bahasa Indonesia | label diagnostik juga bahasa Indonesia |
| Kembali ke English | tidak ada layar yang campur bahasa |

Mesin game tetap memakai bahasanya sendiri. Sakelar ini hanya untuk layar aplikasi, dan catatan
di bawah pemilih bahasa menyebutkan itu.

## 3. Diagnostik, sebelum data ada

Buka `Tampilan → Diagnostik`. Layar inilah yang mengubah laporan bug menjadi bukti.

| Baris | Harapan pada instalasi bersih | Harapan setelah setup |
|---|---|---|
| Versi aplikasi | `0.2.0 (2)` | sama |
| Pustaka native | `2/2 libGame.so …, libsamp.so …` | sama |
| Titik masuk JNI | `5/5` | sama |
| Data game | **gagal**, `0 files, 0 B` | lolos, jumlah berkas dan ukuran nyata |
| Manifest data | `tidak ada` sampai kamu menyalinnya | `lite, N entries, M B` |
| Koneksi | `kosong` | `host:port` yang tersimpan |
| Berkas log | `tidak ada` sebelum jalan pertama | ada ukurannya |
| Crash terakhir | `tidak ada` | `tidak ada`, kecuali memang pernah crash |

Ketuk `Salin laporan` dan tempel ke catatan. Teks itulah yang membuat laporan bisa ditindak.

## 4. Data game

| Langkah | Harapan |
|---|---|
| Salin isi cache ke `/sdcard/GTA/` (`cp -a …/lite/. /sdcard/GTA/`) | baris `Data` jadi hijau setelah diperiksa ulang |
| `Data → Periksa` | semua berkas cocok dengan manifest, tidak ada hash yang meleset |
| Isi `[download] url` di `SAMP/settings.ini`, lalu `Data → Sinkronkan` | progres bergerak, ringkasan melaporkan apa yang diunduh |
| Matikan aplikasi di tengah sinkronisasi, buka lagi | sinkronisasi lanjut dari offset byte, bukan dari nol |
| Hapus URL lalu tekan Sinkronkan | aplikasi menolak dengan pesan yang terbaca, tidak crash |

## 5. Server

| Langkah | Harapan |
|---|---|
| `Server → Tambah`, isi host dan port | baris muncul dan ditandai sebagai target aktif |
| Tunggu sampai dua detik | teks hidup muncul: latensi, pemain dari slot, `offline` bila alamat mati |
| `Segarkan` | semua baris di-probe ulang, tidak ada yang tertinggal di nilai lama |
| Tambah server kedua lalu pilih | `SAMP/settings.ini` berubah, baris di launcher ikut |
| Isi nickname | baris nickname berhenti berbunyi `kosong` |

Alamat mati harus memakan satu timeout per baris dan tidak pernah membekukan daftar.

## 6. Game-nya sendiri

| Langkah | Harapan |
|---|---|
| `Mainkan` | mesin start, splash lalu menu muncul |
| Masuk ke server | daftar server menjawab dengan nama dan jumlah pemain |
| Keluar, kembali | launcher utuh, setelan tetap |
| Putar layar di launcher | tidak ada teks terpotong, tidak ada baris bertumpuk |
| Ukuran font `Terbesar` di setelan sistem | tidak ada label tombol yang terpotong |

## 7. Saat ada yang gagal

Kumpulkan tiga hal ini, berurutan:

1. `Diagnostik → Salin laporan` (seluruh teks, bukan tangkapan layar).
2. `Diagnostik → Bagikan log`, atau `GTA/logcat.txt` langsung.
3. Langkah persis dari berkas ini yang gagal, dan apa yang muncul di layar sebagai gantinya.

Blok crash di `logcat.txt` bentuknya seperti ini, dan baris `version:` itulah yang membuatnya bisa
direproduksi:

```
[app crash]
time   : 2026-11-25 06:20:11 UTC
version: 0.2.0 (2)
thread : main
java.lang.IllegalStateException: boom
    at …
[/app crash]
```

## 8. Hasil

| Tanggal | Perangkat, Android | Versi | Langkah lolos | Kegagalan pertama | Diperbaiki di |
|---|---|---|---|---|---|
| | | | | | |

## 9. Yang tidak dicakup berkas ini

- **Instalasi split APK.** `bin/patch-apk.sh --gta-split-dir` baru diuji pada fixture sintetis.
  Instalasi split asli dari Play Store adalah hal berikutnya setelah langkah 6 lolos.
- **Beban.** Ratusan pemain, sesi panjang dan drift timer butuh server dan waktu, bukan daftar
  periksa.
- **Perangkat lain.** Android 15 dan 16 mengubah inset dan perilaku tombol kembali. Satu HP yang
  lolos adalah satu HP yang lolos.

## Tambahan 0.2.0

| Langkah | Lulus kalau |
|---|---|
| Colok stik saat game jalan | stik langsung dipakai dalam sedetik, tanpa restart, tanpa baris `[pad]` gagal di `GTA/logcat.txt` |
| Tekan Home, lalu balik | stik masih jalan. Stik yang mati setelah pause pertama berarti `implOnGamepadResume` terlewat |
| Buka Diagnostik dengan stik terpasang | barisnya menyebut nama dan id stik, jadi app tidak menebak |
| Pengaturan, Ekspor, simpan zip | pemilih file muncul dan zip-nya ada setelah disimpan |
| Install baru, Pengaturan, Pulihkan, pilih zip itu | pengaturan kembali dan daftar server menampilkan entri lama |
| Pengaturan, Buka panduan | halaman terbuka offline, bahasanya ikut app, dan dua tombolnya sampai ke Diagnostik dan Discord |

## Tambahan 0.3.0

1. Tempel `samp://<ip>:<port>` di aplikasi chat di HP, tekan, dan pastikan launcher terbuka dengan
   server itu terpilih serta toast menyebut alamatnya.
2. Buka browser server dan tekan lama salah satu server: tautannya harus ada di clipboard dan toast
   menampilkannya.
3. Pengaturan, nyalakan Voice chat, lalu terima izin mikrofon. Tombol voice harus muncul di game;
   menolak izin harus meninggalkan saklar mati, bukan hidup tapi tidak jalan.
4. Di dalam game, tekan tombol kamera. File harus muncul di `GTA/Screenshots` dan menu bagikan
   terbuka dengan gambar itu.
5. Force stop aplikasi saat di dalam game, buka Diagnostik, dan cek baris sesi terakhir melaporkan
   tidak ditutup normal. Tutup game dengan normal dan pastikan barisnya bilang ditutup normal.
6. Pengaturan, nyalakan saklar FPS, dan pastikan penghitung dari mesin muncul (itu sudah ada dari
   awal).
