# Data game

Klien ini merender GTA San Andreas. Asetnya milik Rockstar, jadi tidak ada di repo
ini dan tidak bisa diunduh dari sini. Setiap pemain menyediakan asetnya dari
salinan yang mereka miliki; repo ini mengirim klien yang membacanya beserta tool
yang menormalkannya.

## Dari mana klien membaca

| Path | Isi |
|---|---|
| `/storage/emulated/0/GTA/` | tree aset |
| `/storage/emulated/0/GTA/SAMP/` | `settings.ini`, `fonts/`, log |

Hardcoded di `client/app/src/main/cpp/samp/main.cpp:36` dan
`client/app/src/main/cpp/samp/game/hooks.cpp:1385`. Tidak ada setting untuk ini.

## Mendapatkan aset secara sah

Dari instalasi yang dibeli:

```bash
adb shell pm path com.rockstargames.gtasa                 # split APK-nya
adb pull /sdcard/Android/obb/com.rockstargames.gtasa/ ./obb/
```

Dari pasangan APK + OBB yang kamu dump sendiri, sama saja: yang dibutuhkan dua
file itu, bukan tautan unduhan.

## Menormalkannya

```bash
bin/build-cache.sh --apk GTA.apk --obb main.8.com.rockstargames.gtasa.obb --out cache
bin/build-cache.sh --out cache --verify
adb push cache/full/. /storage/emulated/0/GTA/
```

Input yang diterima: `--apk` (aset dibaca dari `assets/`), `--obb` (file expansion),
atau `--dir` (tree yang sudah diekstrak).

## full dan lite

| Varian | Isi | Ukuran kasar | Kompromi |
|---|---|---|---|
| `full` | semuanya: audio radio, film cutscene, tekstur, model | ~1,5–2,5 GB | radio jalan, cutscene main |
| `lite` | folder `audio/` dan `movies/` dibuang | ~0,4–0,9 GB | tanpa radio, tanpa cutscene; gameplay dan SA-MP tidak terpengaruh |

Dua ukuran itu perkiraan komunitas — tidak ada instalasi GTA SA di mesin tempat ini
ditulis, jadi tidak diukur di sini. Tool mencetak angka asli dari file kamu dan
menuliskannya ke manifest.

Menghapus audio mengubah ukuran, bukan status lisensi. `lite` sama-sama milik
Rockstar seperti `full`.

## Manifest

Setiap build menulis `manifest-full.json` dan `manifest-lite.json`:

```json
{
 "variant": "lite",
 "target_path": "/storage/emulated/0/GTA/",
 "file_count": 1187,
 "total_bytes": 812345678,
 "files": [{"path": "models/gta3.img", "size": 1234567, "sha256": "…"}]
}
```

`--verify` menghitung ulang ukuran dan SHA-256 tiap entri lalu menyebut file yang
tidak cocok:

```
== verifying lite ==
  CORRUPT  data/handling.cfg
FAIL: lite: 1 file(s) do not match the manifest
```

Itu intinya: manifest boleh dibagikan, byte-nya tidak. Kirim `manifest-lite.json`
ke orang lain dan cache mereka bisa dibuktikan identik tanpa memindahkan satu file
Rockstar pun.

## Mengunduhnya dari dalam aplikasi

`DownloaderActivity` bawaan memakai Play Asset Delivery, yang tidak berbuat apa pun
pada APK hasil sideload. Sebagai gantinya, build sideload membuka
`DataSetupActivity` saat `/storage/emulated/0/GTA/data` belum ada:

| Langkah | Yang terjadi |
|---|---|
| Pilih varian | `full` atau `lite`, dipilih di layar, bukan saat build |
| Baca manifest | `GTA/manifest-<varian>.json` hasil `bin/build-cache.sh` |
| Periksa isi | ukuran dan SHA-256 tiap entri, tidak ada yang dipercaya dari namanya |
| Unduh sisanya | hanya file yang hilang atau salah; resume `Range` dari `.part` |
| Periksa lagi | isi harus cocok dengan manifest sebelum layar menyatakan selesai |

Arahkan ke server yang menghosting tree buatanmu sendiri:

```ini
; /storage/emulated/0/GTA/SAMP/settings.ini
[download]
url = https://host-kamu/gtasa/
```

Klien lalu meminta `https://host-kamu/gtasa/<path relatif>`. Transfer diulang tiga
kali dengan backoff eksponensial dan sedikit jitter, file yang hash-nya tidak cocok
dihapus (bukan disimpan), dan path manifest yang keluar dari direktori tujuan
(`..`, garis miring di depan) ditolak.

## Kenapa tidak di-host di sini

| Penghalang | Rincian |
|---|---|
| Batas file GitHub | 100 MB per file, keras |
| Batas asset Release | 2 GB per asset; `full` itu satu blob 1,5–2,5 GB |
| Batas lunak repo | ~2 GB, dan git menyimpan setiap salinan historis sebuah blob |
| DMCA | Rockstar rutin menurunkan aset GTA yang didistribusikan ulang; repo source ikut hilang |

`lite` tidak menghindari satu pun dari itu.

Cara legal memberi pemain instalasi sekali klik: host cache yang **kamu bangun dari
salinanmu sendiri** di servermu sendiri, lalu arahkan downloader klien ke sana
(lihat *Mengunduhnya dari dalam aplikasi* di atas). Itu keputusanmu, servermu,
risikomu — bukan sesuatu yang dilakukan repo ini.
