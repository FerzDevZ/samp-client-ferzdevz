# Arsitektur

Baca ini sebelum mengubah nama package, simbol JNI, atau path penyimpanan.

## Struktur

```
client/                               source klien yang di-track (fork kuzia15/SAMP-Mobile GTA-2.11)
  app/src/main/java/
    com/ferzdevzgta/game/             entry Java milik kita (SAMP, GTASA, ui/*), 7 file
    com/ferzdevzgta/launcher/         activity launcher, 3 file
    com/rockstargames/oswrapper/      wrapper game, 32 file, NAMA PACKAGE TERKUNCI
    com/rockstargames/gtasa/          shell activity/downloader, 5 file
    com/nvidia/devtech/               glue NVIDIA, 3 file, NAMA PACKAGE TERKUNCI
  app/src/main/cpp/
    samp/                             netcode SA-MP, hook game, chat, GUI  -> libsamp.so
    opus/ bass/ raknet/ imgui/ ...    third-party yang di-vendor
  app/src/main/jniLibs/arm64-v8a/     .so prebuilt, sebagian besar di-ignore (lihat LEGAL.md)
bin/                                  gate build, fetch blob, dan cache
docs/                                 direktori ini
```

Satu ABI saja: `arm64-v8a`. `minSdk 28`, `targetSdk 36`, `compileSdk 36`, NDK 26.2.11394342.

## Alur start

```
MainActivity (com.ferzdevzgta.launcher.activity)
  └─ System.loadLibrary("multi")
SAMP          (com.ferzdevzgta.game)      System.loadLibrary("Game"), lalu "samp"
  └─ native initializeSAMP()               libsamp.so mem-patch libGame.so, lalu ambil alih
GameActivity  (com.rockstargames.gtasa)
  └─ GameNative (com.rockstargames.oswrapper) -> libGame.so merender frame
       libsamp.so menghook game loop, menambah layer net SA-MP, chat, dan GUI di atasnya
```

`libsamp.so` tidak pernah di-link ke `libGame.so` saat build. Library game dicari
saat runtime (`CUtil::FindLib`) lalu dihook. Itulah sebabnya
`bin/android-build.sh native` bisa mengompilasi seluruh klien tanpa binary Rockstar,
dan juga sebabnya APK tetap butuh binary itu untuk berjalan.

## Kontrak JNI

Lima simbol di `libsamp.so` di-resolve lewat aturan penamaan simbol Java, jadi
package Java dan simbol C harus diubah bersamaan atau aplikasi melempar
`UnsatisfiedLinkError` pada pemanggilan pertama.

| Method Java | Simbol yang diekspor |
|---|---|
| `com.ferzdevzgta.game.SAMP.initializeSAMP()` | `Java_com_ferzdevzgta_game_SAMP_initializeSAMP` |
| `...game.ui.dialog.DialogManager.sendDialogResponse(int,int,int,byte[])` | `Java_com_ferzdevzgta_game_ui_dialog_DialogManager_sendDialogResponse` |
| `...game.ui.AttachEdit.Exit()` | `Java_com_ferzdevzgta_game_ui_AttachEdit_Exit` |
| `...game.ui.AttachEdit.AttachClick(int,boolean)` | `Java_com_ferzdevzgta_game_ui_AttachEdit_AttachClick` |
| `...game.ui.AttachEdit.Save()` | `Java_com_ferzdevzgta_game_ui_AttachEdit_Save` |

Cek setelah rename apa pun:

```bash
SO=$(find client/app/build/intermediates/cxx -name libsamp.so -path '*arm64-v8a*' | head -1)
nm -D --defined-only "$SO" | grep Java_
```

## Namespace yang tidak boleh di-rename

`libGame.so` adalah binary prebuilt. Isinya referensi hardcoded, jadi tiga nama
Java dibekukan:

```bash
nm -D --defined-only client/app/src/main/jniLibs/arm64-v8a/libGame.so | grep -c Java_
# 38   semuanya Java_com_rockstargames_oswrapper_GameNative_*

strings -a client/app/src/main/jniLibs/arm64-v8a/libGame.so | grep -E '^com/'
# com/nvidia/devtech/NvUtil
# com/rockstargames/oswrapper/DeviceInfo
# com/rockstargames/oswrapper/GamePlatformServices
```

| Dibekukan | Alasan |
|---|---|
| `com.rockstargames.oswrapper` | 38 simbol JNI ada di dalam binary |
| `com.nvidia.devtech` | `NvUtil` dicari lewat path dari dalam binary |

`com.ferzdevzgta.*` milik kita: di situ simbol SAMP dan launcher, jadi di-rename
dari `com.gta.*`. `com.rockstargames.gtasa` tidak punya simbol yang dikunci binary,
tapi menempel langsung di wrapper yang terkunci, jadi dibiarkan.

## Path penyimpanan

Hardcoded, tidak bisa diubah dari build:

| Path | Isi | Didefinisikan di |
|---|---|---|
| `/storage/emulated/0/GTA/` | aset game | `samp/main.cpp:36`, `samp/game/hooks.cpp:1385` |
| `/storage/emulated/0/GTA/SAMP/settings.ini` | setting klien | `samp/settings.cpp:15` |
| `/storage/emulated/0/GTA/SAMP/servers.txt` | daftar server untuk browser | `servers/GameConfig.java` |
| `/storage/emulated/0/GTA/SAMP/fonts/` | font UI native | `samp/main.cpp:295` |
| `/storage/emulated/0/GTA/logcat.txt` | log klien | `samp/main.cpp:397` |

Tree asetnya sendiri tidak dikirim, lihat [DATA.md](DATA.md).

## Memilih server

Klien native membaca `[client] host`, `port`, `name` dan `password` dari
`SAMP/settings.ini` sekali, pada frame pertama, dan tidak pernah lagi. Jadi semua yang
menentukan koneksi harus menulis berkas itu sebelum game jalan:

| Bagian | Berkas | Tugas |
|---|---|---|
| Layar browser | `game/ui/ServerBrowserActivity.java` | menampilkan daftar, menambah server, menyimpan nama pemain, menjalankan game |
| Berkas daftar | `game/servers/ServerList.java` | `SAMP/servers.txt`, satu server per baris: nama tampilan, lalu `host:port`, dipisah garis vertikal |
| Aturan alamat | `game/servers/ServerEndpoint.java` | validasi host dan port, batas nama pemain |
| Penulis berkas | `game/servers/GameConfig.java` | membuat `settings.ini` saat instal baru, menulis `[client]` |

Pemain tidak pernah mengedit ini secara manual. Dua penjaga mencegah berkas rusak
mengakhiri sesi:

1. `settings.cpp` mencatat peringatan, bukan memanggil `std::terminate()`, ketika berkas
   tidak ada atau tidak terbaca, dan setiap key jatuh ke nilai bawaan.
2. `main.cpp` melewati koneksi ketika `[client] host` kosong dan menuliskannya di log,
   bukan mengarahkan RakNet ke alamat kosong.

Set key yang ditulis `GameConfig` diperiksa terhadap `settings.cpp` oleh
`GameConfigTest`, jadi menambah key di engine tanpa menambahkannya di template akan
menggagalkan `bin/self-test.sh`.

## Layar dan bahasa

Ada lima layar di depan engine. Semuanya dibangun di Java memakai `ui/Ui.java`, bukan berkas
layout, jadi palette, jarak dan ukuran sentuh tidak bisa berbeda antar layar:

| Layar | Kelas | Tugas |
|---|---|---|
| Launcher | `launcher/activity/MainActivity.java` | menampilkan status data, server dan nama pemain, membuka game atau layar setup |
| Data | `game/ui/DataSetupActivity.java` | pilih varian, verifikasi, unduh dengan resume, bagikan log |
| Server | `game/ui/ServerBrowserActivity.java` | daftar server, tambah, hapus, sambung |
| Pengaturan | `game/ui/UiSettingsActivity.java` | key `settings.ini` yang dibaca engine |
| Bahasa | bagian dari launcher, data dan pengaturan | Indonesia atau Inggris |

Bahasa disimpan oleh aplikasi, bukan oleh engine:

- `game/ui/Language.java` memegang aturannya dan tanpa import Android, jadi
  `tests/jvm/src/com/ferzdevzgta/game/ui/LanguageTest.java` bisa menjalankannya dengan
  `javac` biasa. `id`, `id-ID`, `in` dan `IN_br` semuanya menjadi `in`; bahasa yang tidak
  didukung jatuh ke Inggris; nilai kosong berarti "ikut perangkat".
- `game/ui/LanguageStore.java` menyimpan pilihan di `ui_language` lalu memanggil
  `AppCompatDelegate.setApplicationLocales`, yang membuat ulang layar.
- `game/SampApplication.java` memakai pilihan tersimpan di `onCreate`, sebelum activity
  pertama, jadi launcher tidak pernah berkedip dengan bahasa yang salah.

`res/values/strings.xml` berisi bahasa Inggris, `res/values-in/strings.xml` berisi bahasa
Indonesia, dan `bin/self-test.sh strings` gagal ketika sebuah key hanya ada di salah
satunya, atau ketika kedua terjemahan memakai placeholder format yang berbeda.

Engine punya bahasanya sendiri (`gui.Language` di `settings.ini`, dipetakan oleh
`services/LanguageServices.java`) dan pemilih ini tidak menyentuhnya: UI game dan UI
launcher diterjemahkan terpisah, dan `language_note` menjelaskannya di layar.

Warna hanya ada di satu tempat, `ui/Ui.java`, dan `bin/ui-contrast.py` membaca berkas itu
lalu menghitung rasio WCAG 2.2 AA untuk tujuh belas pasangan yang benar-benar dipakai
layar. Warna hint yang terlalu gelap akan gagal di pemeriksaan itu, bukan menjadi tidak
terbaca di HP.

Aplikasi menargetkan SDK 36, dan Android 15 menggambar aplikasi seperti ini sampai ke tepi
layar: jendela memanjang ke bawah jam dan navigation bar, mau temanya meminta atau tidak.
Setiap layar memanggil `Ui.applyInsets` pada content view, yang menambahkan inset system bar
dan cutout ke padding yang sudah ada. Tanpa itu, baris pertama sebuah layar menimpa status
bar, persis jenis tumpang tindih yang tidak punya error kompilasi.

Launcher menaruh tombol aksinya di bilah bawah, bukan di ujung scroll, jadi tombol utama
tetap terjangkau jempol dan layar pendek tidak berakhir dengan ruang kosong. Kartu statusnya
menampilkan satu titik per syarat, dan tombol utamanya berganti label, Mainkan atau Siapkan
dulu, bukan mengulang tiga kata yang sama di tiga kontrol berbeda.

### Ikon, splash dan diagnostik

Kontrol tetap mengutamakan teks, dengan satu ikon vektor dari `res/drawable/ic_*.xml` bila
membantu. Ikonnya diwarnai mengikuti warna label lewat `Ui.icon`, jadi tombol sekunder tidak
pernah muncul dengan ikon putih di permukaan terang. Ikon peluncur adalah ikon adaptif dengan
foreground berupa bitmap per densitas, dibuat dari gambar sumber lewat `bin/make-icons.py`, dan
`Theme.SampLauncher` memakai `splash_window` sebagai window background, yang menghilangkan kilatan
putih antara ketukan dan frame pertama.

Layar server menanyakan info ke setiap alamat lewat `servers/ServerQuery` (murni, diuji di JVM)
dan `net/ServerQueryClient` (UDP, satu thread per probe, timeout 1200 ms, hasil dikirim ke main
thread). Alamat mati hanya memakan satu timeout di latar belakang, bukan membekukan daftar.

`ui/DiagnosticsActivity` membaca HP, bukan skrip build: versi aplikasi, dua pustaka native beserta
ukurannya, lima titik masuk JNI yang dideklarasikan, pohon data, manifest, target tersimpan dan
ekor `GTA/logcat.txt`, lengkap dengan salin dan bagikan. Aturan formatnya ada di `diag/Report`
supaya `bin/self-test.sh` mengujinya tanpa perangkat.

Tidak ada wizard pertama kali, dan itu disengaja: launcher sudah menampilkan tiga syarat, dan
tiap baris adalah jalan pintas ke layar yang memperbaikinya. Wizard hanya akan mengulang tiga
aksi yang sama satu per satu.

### Loading screen saat masuk game

`ui/LoadingScreen` di-inflate dari `res/layout/loadingscreen.xml` di atas permukaan game dan
bertahan sampai mesin melaporkan frame pertama sudah tampil. Tiga janji yang dijaga:

- Tidak pernah mengarang persentase. Mesin tidak melaporkan jumlah byte, jadi bar-nya marquee dan
  baris status menyebut fasenya (`Menyiapkan mesin`, `Membaca data game`, `Menyambung ke dunia`).
  Bar yang mencapai 100 persen sebelum dunia bisa dimainkan adalah klaim yang kelihatan bohong.
- Tidak ada timer yang menutupnya. Hanya `hide()` yang mengakhirinya, dipanggil klien saat
  pemuatan selesai. Versi sebelumnya menjalankan hitung mundur palsu dan menutup layar setelah 4,5
  detik, yang bisa memperlihatkan dunia yang masih hitam.
- Tunggu yang lama menjelaskan diri. Setelah dua puluh detik baris tip diganti kalimat yang
  menunjuk `GTA/logcat.txt`, dan bar tetap bergerak, jadi pemuatan lambat tidak terlihat macet.

Satu baris tip nyata berganti tiap tujuh detik, mati bila skala animasi sistem nol. Blok teks
diberi padding untuk display cutout, karena game berjalan landscape dan lubang kamera akan
menutupi blok identitas.

### Umpan balik sentuh dan empty state

`Ui.pressable` menambah getaran pendek saat ditekan dan skala tekan dua persen, keduanya mati bila
skala animasi nol. Semua tombol dari `Ui.button` mendapatkannya, begitu juga baris status di
launcher, jadi kontrol yang terlihat seperti tombol juga terasa seperti tombol.

`Ui.emptyState` menampilkan ikon, judul, satu kalimat, dan satu aksi yang mengisi daftar. Daftar
server kosong adalah layar pertama pemain baru, jadi ia bertanya dan menawarkan tombolnya, bukan
mencetak satu kalimat di tengah kartu.

`res/values/samp_colors.xml` menyimpan warna yang dibutuhkan XML sebelum Java berjalan. Itu bukan
palet kedua: `bin/ui-contrast.py` gagal bila ada entri yang berbeda dari konstanta di `Ui.java`.

### Palet dan ikon berasal dari gambar yang sama

Ikon aplikasi digambar tangan: bidang navy, cakram matahari terbenam, pohon palem, garis hitam.
Token desain di `ui/Ui.java` mengikutinya, jadi aplikasi dan ikonnya terbaca sebagai satu produk,
bukan poster dengan antarmuka yang tidak berhubungan:

| Token | Nilai | Asalnya |
|---|---|---|
| `BG` | `#0B111B` | bidang navy, digelapkan supaya teks bisa mencapai 16:1 |
| `SURFACE` | `#151F32` | bidang navy itu sendiri |
| `SURFACE_2` | `#1F2C45` | navy lebih terang di sekitar matahari |
| `ACCENT` | `#EF7D2B` | oranye matahari terbenam |
| `WARN` | `#F2C14E` | kuning dari hurufnya |
| `TEXT` | `#F3EEE6` | putih hangat dari langitnya |

`WARN` tetap berbeda dari `ACCENT` lewat hue dan terang, jadi titik status tidak pernah disangka
tombol, dan `bin/ui-contrast.py` menjaga semua pasangan di atas AA. `res/drawable-*/ic_brand.png`
adalah gambar yang sama dipotong jadi kotak bersudut bulat pada 96 dp, jadi ikon di home screen,
splash, dan loading screen menampilkan satu tanda di semua ukuran.

Ikon dikirim sebagai bitmap, bukan vektor hasil tracing, karena gambarnya ilustrasi pena: tracing
akan membuang garis dan hurufnya. `bin/make-icons.py --source <gambar>` mencari kotak ikon di dalam
screenshot, memasang masker sudut bulat, lalu menulis `mipmap-*/ic_launcher_foreground.png` pada
viewport 108 dp milik ikon adaptif dengan gambar sebesar 78 persen, yang masih di dalam safe zone
masker. Angkanya ada di satu tempat di skrip itu, bukan di lima file hasil crop manual.

`bin/icon-check.py` menjaga sambungannya, karena gambar bisa sempurna tapi tetap tidak terlihat.
Manifest pernah menulis `android:icon="@drawable/ic_launcher_background"`, yaitu layer latar ikon
itu sendiri: build tetap hijau, semua PNG ada, dan semua ponsel menampilkan kotak navy kosong. Pemeriksa
itu membandingkan rujukan di manifest, dua file XML adaptif, lima densitas, tidak adanya
`ic_launcher.webp` lama, dan rujukan splash serta loading, lalu `bin/self-test.sh icons` membuat
kesalahan yang sama di direktori sementara untuk membuktikan pemeriksa menolaknya.

### Melaporkan bug

`ui/Community` menyimpan invite Discord di satu konstanta, tanpa import Android, jadi
`CommunityTest` bisa menjaga bentuknya di suite JVM: https, host `discord.gg`, kode alfanumerik,
tanpa query string. Invite-nya bisa dijangkau dari tiga tempat, karena kanal dukungan yang tidak
bisa ditemukan hanya jadi hiasan:

- catatan di bawah layar launcher, bergaris bawah dan bisa ditekan
- Tampilan, di bagian Dukungan
- Diagnostik, di mana tombolnya menyalin laporan ke clipboard sebelum membuka Discord, jadi
  pemain datang dengan bukti yang siap ditempel

## Input: gamepad

Tiga callback stik sudah ada di `libGame.so` sebelum apa pun di Java mendeklarasikannya, jadi stik
terpasang, Android melaporkannya, dan game tidak pernah dengar. Deklarasinya baru ditulis setelah
tanda tangan dibaca dari binary:

```
implOnGamepadConnected(int)      -> GamepadProvider::onConnect(int)
implOnGamepadDisconnected(int)   -> GamepadProvider::onDisconnect(int)
implOnGamepadResume(int[])       -> GamepadProvider::onResume(std::vector<int>)
```

`bin/jni-pins.py` sekarang melaporkan 0 ekspor tanpa pemakai, dan itu buktinya ketiganya bukan
tebakan. Sambungannya ada tiga bagian:

- `com.rockstargames.oswrapper.input.GamepadInput` mendaftarkan `InputManager.InputDeviceListener`,
  jadi stik yang dicolok saat game sudah jalan langsung dilaporkan, bukan menunggu start berikutnya.
- Kelas yang sama mengirim ulang daftar id lengkap setiap resume. Mesin menyusun ulang tabel stiknya
  dari daftar itu, jadi kalau dilewati, stik tetap terpasang tapi tidak bisa dipakai setelah pause
  pertama.
- `com.ferzdevzgta.game.input.GamepadMap` menyimpan angkanya tanpa import Android: deadzone 0.15
  yang diskalakan lagi ke travel penuh, trigger diterima sebagai 0..1 atau -1..1, enam axis sesuai
  urutan tanda tangan mesin, dan tes `SOURCE_JOYSTICK` supaya tombol keyboard tidak dianggap stik.
  `GamepadMapTest` menjaga semuanya di suite JVM.

Semua panggilan dibungkus penjaga `UnsatisfiedLinkError`. APK framework-only punya kelas Java tanpa
mesin, dan satu event stik tidak boleh mematikannya.

## Pengaturan yang bisa pindah HP

`settings/ProfileBackup` menulis satu zip berisi `settings.ini` dan `servers.txt`, dan itulah yang
membuat pindah HP jadi pekerjaan sebentar, bukan semalaman mengetik ulang. Arsipnya zip biasa dengan
tiga nama entri tetap, dan saat dibaca hanya nama itu yang diterima:

- arsip tanpa `settings.ini` ditolak, bukan dipasang setengah
- tiap entri dibatasi 512 KiB, jadi file rusak atau jahat tidak bisa memenuhi penyimpanan bersama
- entri bernama `../../settings.ini` diabaikan, karena path tujuan datang dari kode dan tidak pernah
  dari arsip

`ProfileBackupTest` menguji perjalanan penuh, dua penolakan itu, dan kasus traversal di direktori
sementara. Ekspor dan Pulihkan lewat pemilih file sistem, jadi zip-nya mendarat di tempat pemain
menyimpan backup.

## Layar panduan dan kenapa WebView

Client punya tiga lapis UI dan tidak ada yang bisa memuat teks bantuan berformat: layar app itu view
Java, HUD dalam game itu ImGui, dan TextDraw milik server. Android membawa WebView di setiap
perangkat, jadi `ui/WebPanelActivity` menggambar `assets/help/help.html` tanpa menambah ukuran APK.

Wewenangnya sengaja sempit: JavaScript hidup untuk aset itu, akses file dari file URL mati, akses
universal mati, DOM storage mati, dan tautan selain aset diserahkan ke browser. Halaman itu hanya
boleh memanggil tiga hal: bahasa aktif, versi, dan buka diagnostik atau Discord. `openDiagnostics`
dipakai supaya tidak ada jalur salin-ke-clipboard kedua, karena laporannya sudah dibangun di satu
tempat dan menduplikasinya membuat dua versi bisa berbeda.

Semua ini tidak menggantikan TextDraw server. Itu data RPC yang harus digambar mesin, dan
menerjemahkannya ke HTML adalah proyek lain dengan hasil yang tidak konsisten antar server.
