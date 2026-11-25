# Model kustom 0.3.DL

SA-MP 0.3.DL membuat server bisa membawa skin dan object sendiri. Dua baris di
`models/artconfig.txt` yang mengerjakannya:

```
AddCharModel(280, 25000, "cop.dff", "cop.txd");
AddSimpleModel(-1, 19379, -2000, "cop.dff", "cop.txd");
```

`AddCharModel(baseid, newid, dff, txd)` mendaftarkan skin: `baseid` adalah skin bawaan (0 sampai
311) yang animasi dan suaranya dipinjam, `newid` harus 20000 sampai 30000. `AddSimpleModel(vworld,
baseid, newid, dff, txd)` mendaftarkan object: `vworld` adalah virtual world atau -1 untuk semua,
`baseid` adalah object bawaan yang collision-nya diwarisi, `newid` harus -30000 sampai -1000. Setelah
itu client mengunduh file-nya dan menyimpannya di folder cache.

## Yang sudah bisa dilakukan client ini

`bin/artconfig.py` membaca dan memeriksa `artconfig.txt` milik server:

```bash
bin/artconfig.py models/artconfig.txt --models models --check
bin/artconfig.py models/artconfig.txt --models models --manifest models.json
```

Yang ditolak adalah kesalahan yang diterima server saat start lalu modelnya hilang begitu saja:

| Ditolak | Kenapa penting |
|---|---|
| id skin di luar 20000..30000 | server mendaftarkannya, skin-nya tidak pernah muncul |
| id object di luar -30000..-1000 | sama, di sisi object |
| base id skin di luar 0..311 | hanya skin bawaan yang bisa diwarisi |
| id baru yang dipakai dua kali | pendaftaran kedua kalah tanpa pesan |
| file yang disebut tapi tidak ada di `models/` | unduhannya 404 untuk semua pemain |
| nama dengan `../` atau garis miring di depan | jalurnya keluar dari folder models |
| baris tanpa `;` di akhir | parser server melewatinya |

`--manifest` menulis inventaris yang dibutuhkan cache: satu entri per model dengan `sha256` dan
ukuran untuk kedua file, dan `total_bytes` dihitung per file unik, bukan per entri, karena beberapa
skin biasanya memakai satu tekstur yang sama.

`bin/self-test.sh artconfig` membuktikan kasus benar dan semua penolakan di atas dengan fixture, jadi
tool ini tidak bisa diam-diam mulai menerima file yang rusak.

## Yang belum ada, dan kenapa

Client belum bisa menggambar model ini. Tidak ada bagian di engine yang dipakai client ini yang tahu
tentang `artconfig.txt`, id model kustom, atau handshake unduhan 0.3.DL, dan client SA-MP yang tahu
bukan milik kita untuk disalin.

Artinya ada tiga pekerjaan nyata, berurutan:

1. Manifest dan tata letak folder cache. `bin/artconfig.py --manifest` adalah separuh pertamanya dan
   sudah jalan sekarang.
2. Transport: client meminta file model ke server dan memverifikasinya dengan manifest. Ini butuh
   satu `artconfig.txt` asli beserta file `.dff`/`.txd`-nya untuk diuji, dari server yang kamu jalankan
   atau yang kamu punya izinnya.
3. Sisi engine: mendaftarkan model hasil unduhan ke game supaya server bisa memasangnya ke pemain atau
   ke dunia. Ini pekerjaan di lapisan native dan tidak bisa diuji tanpa nomor 1 dan 2.

Sampai itu selesai, client yang masuk ke server 0.3.DL dengan model kustom akan menampilkan model
bawaan: id dari server tidak menunjuk ke apa pun dan object atau skin aslinya tetap terlihat. Tidak
ada yang crash, dan tidak ada unduhan yang terjadi.

## Kalau kamu menjalankan server 0.3.DL

`bin/artconfig.py --check` bisa jadi pemeriksaan sebelum deploy, di CI atau manual. Lebih murah
daripada mengetahui teksturnya hilang dari screenshot pemain.
