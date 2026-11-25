# Catatan perubahan

Versi Inggris: [CHANGELOG.md](CHANGELOG.md).

## 0.3.0

Server masuk lewat tautan, voice punya saklar yang benar-benar bisa hidup, dan sesi yang mati
meninggalkan jejak.

### Tautan

- `samp://host:port` membuka launcher dengan server itu sudah terpilih. Parsernya ada di
  `servers/DeepLink.java` dan memakai aturan host dan port yang sama dengan `servers.txt`, jadi
  tautan dan baris di daftar tidak bisa berbeda pendapat soal alamat yang bisa dipakai.
- Tautan hanya memindahkan host dan port. Nama panggilan dan password yang sudah tersimpan tidak
  disentuh, karena tautan dari room chat datang setelah pemain mengetik nama.
- Tekan lama server di browser untuk menyalin tautannya. Itu cara membagikan alamat tanpa mengetik
  ulang angka.

### Voice

- Mesin membaca `gui/VoiceChatEnable` dan hanya menggambar tombol voice kalau key itu true, jadi
  saklarnya sekarang ada di Pengaturan bersama key asli lainnya.
- Android menolak mikrofon tanpa izin runtime, dan saklar sebelumnya cuma janji yang tidak bisa
  ditepati aplikasi. Mencentangnya sekarang meminta `RECORD_AUDIO` lebih dulu, dan launcher
  memeriksa lagi sebelum game dijalankan. Izin ditolak berarti key tetap mati, bukan tombol mati.

### Tangkapan layar

- Tombol kamera 44dp di layar game menyalin surface lewat `PixelCopy` ke
  `GTA/Screenshots/samp-<tanggal>-<jam>.png` lalu membuka menu bagikan. Isi filenya sama dengan
  yang dilihat pemain, termasuk HUD mesin dan TextDraw dari server, dan tidak ada JNI baru.

### Penanda sesi

- Crash native di `libGame.so` tidak pernah sampai ke crash handler Java. Game sekarang menulis
  `SAMP/session.open` saat mulai dan menghapusnya saat ditutup normal, jadi Diagnostik bisa
  mengatakan sesi terakhir tidak ditutup normal. Sesi yang dimatikan sistem terlihat sama dari
  sini, jadi teksnya tidak menyebutnya crash.

### Catatan

- Penghitung FPS ternyata sudah ada: `gui/fps` dibaca `cpp/samp/settings.cpp` dan digambar
  `CDebugInfo`, jadi itu saklar, bukan fitur yang harus ditulis.
- Gate: 351 assertion JVM, 25 suite, 17 pasangan kontras, 38 native JNI dengan 0 ekspor tanpa
  pemakai.

## 0.2.0

Stik sekarang jalan, pengaturan bisa dibawa pindah HP, dan client bisa menjelaskan dirinya sendiri
tanpa internet.

### Gamepad

- `implOnGamepadConnected`, `implOnGamepadDisconnected` dan `implOnGamepadResume` sudah diekspor
  `libGame.so` sejak build pertama tapi tidak pernah dipanggil dari Java, jadi stik cuma jadi
  aksesoris mati. Tanda tangannya dibaca dari binary (`onConnect(int)`, `onDisconnect(int)`,
  `onResume(std::vector<int>)`) sebelum dideklarasikan, dan `bin/jni-pins.py` sekarang melaporkan
  **0 ekspor tanpa pemakai**.
- Stik yang dicolok saat game sudah jalan dikenali lewat `InputManager`, dan daftar id dikirim ulang
  setiap resume, karena mesin menyusun ulang tabelnya dan stik biasanya mati setelah pause pertama.
- Stik punya deadzone 0.15 yang diskalakan lagi ke travel penuh. Trigger dibaca dari
  `AXIS_LTRIGGER` / `AXIS_RTRIGGER`, dan kalau stiknya tidak punya trigger analog dipakai
  `AXIS_Z` / `AXIS_RZ`.
- Semua panggilan stik dibungkus penjaga `UnsatisfiedLinkError`, jadi APK framework-only tetap aman.

### Backup profil

- Di Pengaturan ada **Ekspor** dan **Pulihkan**: satu zip berisi `settings.ini` dan `servers.txt`.
- Arsipnya ditulis dan dibaca `settings/ProfileBackup.java`, yang hanya menerima tiga nama entri
  miliknya sendiri, membatasi tiap entri 512 KiB dan menolak arsip tanpa `settings.ini`. Entri
  berbentuk traversal diabaikan, bukan diekstrak, dan ada tes yang membuktikan tidak ada file yang
  mendarat di luar folder tujuan.

### Panduan offline

- Layar **Panduan** baru, digambar oleh WebView bawaan sistem dari `assets/help/help.html`.
  Tidak menambah ukuran APK karena mesinnya sudah ada di Android, dan ini satu-satunya tempat teks
  bantuan berformat muat: layar app itu Java, HUD itu ImGui, dan TextDraw milik server.
- Halaman itu hanya dapat tiga jembatan: bahasa aktif, versi, dan buka diagnostik atau Discord.
  Akses file dari file URL, akses universal dan DOM storage semuanya dimatikan, dan tautan selain
  aset dibuka di browser.

### Berubah

- `versionName` 0.2.0, `versionCode` 2.
- Layar pengaturan dapat kartu Backup profil dan tombol Panduan.

## 0.1.0

Build pertama yang diberi tag.

- Client native Android arm64 (`libsamp.so`, 3583408 byte) dirilis sebagai aset release, terpisah
  dari APK.
- Rebrand ke `com.ferzdevzgta.game`, telemetri dibuang, source client masuk repo.
- `bin/android-build.sh`, `bin/self-test.sh`, pembangun cache varian `full` dan `lite`.
- Rewrite clean-room `com.rockstargames.oswrapper`, 20 dari 21 file, namanya dipatok
  `tests/pins/oswrapper-pins.txt`.
- Pemilih server yang menulis `settings.ini` untuk pemain, patcher APK di HP untuk Termux.
