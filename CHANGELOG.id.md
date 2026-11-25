# Catatan perubahan

Versi Inggris: [CHANGELOG.md](CHANGELOG.md).

## 0.4.0

Widget yang kamu lihat sepanjang game bisa digeser sendiri, daftar model server 0.3.DL bisa diperiksa
sebelum ada yang masuk, dan di HP ada satu perintah build, bukan dua yang harus diingat.

### Editor HUD

- Tombol `HUD` di panel tombol dalam game membuka editor untuk kotak chat, panel tombol dan tombol
  voice. Geser salah satu, tekan `Simpan`, dan posisinya bertahan: `gui/hudeditor.cpp` menulis `[hud]`
  di `settings.ini` dan `HudEditor::Load` menerapkannya sebelum frame pertama.
- Posisi disimpan di ruang koordinat yang sudah dipakai widget, jadi tidak ada konversi 640x480 yang
  diciptakan untuk ini.
- Editor menulis section sendiri, tidak pernah `[gui]`. Bug di dalamnya tidak bisa mengubah ukuran
  chat, font atau saklar FPS, dan `bin/hud-check.py` menggagalkan build kalau editor menyebut section
  gui.
- Satu hal yang ikut diperbaiki: `gui/ChatPosX`, `gui/VoiceChatPosX` dan `gui/SpawnScreenPosX` dibaca
  `settings.cpp` lalu tidak pernah dipakai, jadi menulis key itu tidak mengubah apa pun. Key baru yang
  dipakai sekarang adalah yang benar-benar diterapkan.

### Daftar model 0.3.DL

- `bin/artconfig.py` membaca `models/artconfig.txt` milik server dan menolak yang diterima server saat
  start lalu modelnya hilang: id skin di luar 20000..30000, id object di luar -30000..-1000, base skin
  bawaan di luar 0..311, id dobel, file yang tidak ada di `models/`, nama dengan `../`, baris tanpa
  titik koma.
- `--manifest` menulis inventaris yang dibutuhkan cache: sha256 dan ukuran kedua file per entri,
  dengan total dihitung per file unik karena beberapa skin biasanya memakai satu tekstur.
- Client belum bisa menggambar model ini. Tidak ada bagian engine yang tahu tentang id model kustom
  atau handshake unduhan 0.3.DL. [docs/id/DL.md](docs/id/DL.md) menyebut tiga bagian yang belum ada
  dan urutan pengerjaannya.

### Satu perintah di HP

- `bin/build-termux.sh` adalah pintu masuk di Termux: melaporkan tool yang ada, bisa mengunduh
  framework APK dari URL, menolak framework yang lebih tua dari source di sebelahnya, lalu memanggil
  `bin/patch-termux.sh`.
- Pemeriksaan umur file itu bagian yang penting. APK hasil patch dari framework lama terlihat normal
  dan menjalankan UI lama, dan tidak ada langkah patch yang akan menyadarinya.
- Diukur dengan split GTA buatan di mesin ini: exit 0, `PASS`, package `com.ferzdevzgta.game`,
  `libGame.so` tersimpan di offset kelipatan 4096.

### Gate

`bin/self-test.sh all` mencakup dua pemeriksaan baru: `hud` (tombol pembuka, urutan load, urutan frame,
section ini sendiri, plus kasus negatif yang menulis `[gui]`) dan `artconfig` (file benar plus tujuh
penolakan). `CMakeLists.txt` sekarang memakai `CONFIGURE_DEPENDS`, jadi file native baru ikut
dikompilasi tanpa menghapus cache CMake lebih dulu.

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
