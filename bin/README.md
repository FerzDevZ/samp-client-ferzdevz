# bin/

Four scripts. Each one is a gate: it exits non-zero when the thing it claims is
true turns out not to be.

| Script | Purpose | Needs `libGame.so` |
|---|---|---|
| `android-build.sh` | build `libsamp.so` and/or the APK, then inspect the artifact | yes, except in `native` mode |
| `patch-apk.sh` | inject a game-owned `libGame.so` into the framework APK, realign, re-sign | it is the input |
| `fetch-blobs.sh` | copy the two binary sets that cannot be tracked in git | - |
| `build-cache.sh` | build and verify the `/storage/emulated/0/GTA/` asset tree | - |
| `self-test.sh` | prove the JVM tests, the cache round trip and the patcher refusals still pass | - |
| `patch-termux.sh` | same job on a phone: `zip -0`, `apksigner --lib-page-alignment`, no SDK binaries | it is the input |
| `serve-apk.sh` | stages the newest APK and serves it over HTTP, printing the Tailscale and LAN URLs plus the sha256 | the built APK |
| `apk-verify.py` | read the zip structures and prove the library is stored, aligned, intact; no SDK needed | - |
| `wrapper-pins.sh` | `javap` the compiled wrapper and fail when a name `libGame.so` looks up is gone | `tests/pins/oswrapper-pins.txt` |
| `jni-pins.py` | match `native` declarations against the symbols a library exports, both directions | `libGame.so` |
| `oswrapper-audit.sh` | count rewritten vs still derived wrapper files, `--strict` fails while any remain | - |

## android-build.sh

```bash
bin/android-build.sh            # debug APK
bin/android-build.sh release    # release APK (unsigned without a keystore)
bin/android-build.sh clean      # wipe build dirs, then debug APK
bin/android-build.sh native     # libsamp.so only, no APK, no libGame.so
bin/android-build.sh framework  # APK minus libGame.so, re-signed -> dist/samp-client-framework.apk
```

`framework` reads the APK gradle just built, deletes `lib/arm64-v8a/libGame.so`,
aligns, signs with `~/.samp-client/patcher.keystore`, and asserts the game library
is really gone. 28 MB instead of 36 MB.

Preflight: JDK, NDK 26.2.11394342, cmake 3.22.1. Then it writes
`client/local.properties`, sets the `gradlew` exec bit, repairs a missing trailing
newline in `client/gradle.properties`, builds, and asserts the result:

- APK modes: `lib/arm64-v8a/libsamp.so` **and** `lib/arm64-v8a/libGame.so` inside the APK;
- `native`: `libsamp.so` exists under `app/build/intermediates/cxx`.

Measured here: `clean` 2m08s–2m50s, `native` 10s, `framework` 11s on an incremental
build, all 0 warnings.

## patch-apk.sh

```bash
bin/patch-apk.sh --gta-apk GTA.apk --out dist/samp-client-0.1.0.apk
bin/patch-apk.sh --gta-split-dir /path/to/split --out dist/samp-client-0.1.0.apk
bin/patch-apk.sh --gta-apk GTA.apk --framework dist/samp-client-framework.apk --out client.apk
```

Refuses unless the game APK declares `com.rockstargames.gtasa` and carries an arm64
`libGame.so`, and unless the framework carries `libsamp.so` and no `libGame.so`.
Injects with `zip -0` (stored, a deflated `.so` cannot be mmap'd), `zipalign -p -f 4`,
signs, then verifies: signature, both libraries present and `Stored`, alignment still
valid after signing, package unchanged.

Tested: PASS on a fixture game APK (injected `libGame.so` byte-identical to the
source), and three refusals, framework that still holds the game library, APK
without it, APK with the wrong package.

## fetch-blobs.sh

```bash
bin/fetch-blobs.sh            # no-op if the files are already there
bin/fetch-blobs.sh --force    # refetch
```

Clones the upstream `GTA-2.11` branch into a temp dir and copies out:
`jniLibs/arm64-v8a/libGame.so` and `samp/vendor/bass/libs/<abi>/*.so`.

Both are ignored by git on purpose, Rockstar's binary and a licence that forbids
redistribution. See `docs/LEGAL.md`.

## build-cache.sh

```bash
bin/build-cache.sh --apk GTA.apk --obb main.8.<id>.obb --out cache
bin/build-cache.sh --dir /path/to/extracted/tree --out cache
bin/build-cache.sh --out cache --verify
```

Writes `cache/full/`, `cache/lite/` (`audio/` and `movies/` removed),
`manifest-full.json`, `manifest-lite.json`, `PROVENANCE.txt`.

`--verify` recomputes size and SHA-256 per file and names each mismatch. Tested
against a synthetic APK+OBB fixture: full 2.7 MB / lite 852 KB, verify PASS, then a
1-byte edit to `lite/data/handling.cfg` produced `CORRUPT data/handling.cfg` and a
non-zero exit.

---

# bin/ (Bahasa Indonesia)

Empat script. Semuanya gate: keluar dengan status non-zero kalau klaimnya ternyata
tidak benar.

| Script | Fungsi | Butuh `libGame.so` |
|---|---|---|
| `android-build.sh` | build `libsamp.so` dan/atau APK, lalu periksa artifact-nya | ya, kecuali mode `native` |
| `patch-apk.sh` | menyuntik `libGame.so` milik pengguna ke APK framework, realign, tanda tangan ulang | itu inputnya |
| `self-test.sh` | membuktikan tes JVM, round trip cache, dan penolakan patcher masih lolos | - |
| `patch-termux.sh` | kerja yang sama di HP: `zip -0`, `apksigner --lib-page-alignment`, tanpa binary SDK | itu inputnya |
| `serve-apk.sh` | menyalin APK terbaru lalu menyajikannya lewat HTTP, mencetak URL Tailscale dan LAN beserta sha256 | APK hasil build |
| `apk-verify.py` | membaca struktur zip dan membuktikan library stored, aligned, utuh; tanpa SDK | - |
| `wrapper-pins.sh` | `javap` wrapper hasil kompilasi, gagal kalau ada nama yang dicari `libGame.so` hilang | `tests/pins/oswrapper-pins.txt` |
| `jni-pins.py` | mencocokkan deklarasi `native` dengan simbol yang diekspor library, dua arah | `libGame.so` |
| `oswrapper-audit.sh` | menghitung file wrapper yang sudah ditulis ulang vs masih turunan, `--strict` gagal selama masih ada | - |
| `fetch-blobs.sh` | menyalin dua set binary yang tidak boleh masuk git | - |
| `build-cache.sh` | membangun dan memverifikasi tree aset `/storage/emulated/0/GTA/` | - |

## android-build.sh

Empat mode seperti tabel di atas. Preflight: JDK, NDK 26.2.11394342, cmake 3.22.1.
Lalu menulis `client/local.properties`, memasang exec bit `gradlew`, memperbaiki
newline yang hilang di `client/gradle.properties`, build, dan memastikan hasilnya:

- mode APK: `lib/arm64-v8a/libsamp.so` **dan** `lib/arm64-v8a/libGame.so` ada di dalam APK;
- `native`: `libsamp.so` ada di `app/build/intermediates/cxx`.

Mode `framework` membaca APK hasil gradle, menghapus `lib/arm64-v8a/libGame.so`,
menyejajarkan, menandatangani dengan `~/.samp-client/patcher.keystore`, lalu
memastikan library game benar-benar hilang. 28 MB, bukan 36 MB.

Terukur di sini: `clean` 2m08s–2m50s, `native` 10s, `framework` 11s pada build
inkremental, semuanya 0 warning.

## patch-apk.sh

Menolak kalau APK game tidak mendeklarasikan `com.rockstargames.gtasa` atau tidak
membawa `libGame.so` arm64, dan menolak kalau framework tidak punya `libsamp.so`
atau masih punya `libGame.so`. Menyuntik dengan `zip -0` (stored, `.so` yang
dipadatkan tidak bisa di-mmap), `zipalign -p -f 4`, tanda tangan, lalu verifikasi:
signature, kedua library ada dan `Stored`, alignment masih valid setelah
ditandatangani, package tidak berubah.

Sudah diuji: PASS pada APK game fixture (`libGame.so` hasil suntik identik
byte-per-byte dengan sumber), dan tiga penolakan, framework yang masih membawa
library game, APK tanpa library itu, APK dengan package salah.

## fetch-blobs.sh

Idempoten. Clone branch `GTA-2.11` upstream ke direktori sementara lalu menyalin
`libGame.so` dan `libbass*.so`. Keduanya sengaja di-ignore git, binary Rockstar
dan lisensi yang melarang redistribusi. Lihat `docs/LEGAL.md`.

## build-cache.sh

Membangun `cache/full/`, `cache/lite/` (tanpa `audio/` dan `movies/`),
`manifest-full.json`, `manifest-lite.json`, dan `PROVENANCE.txt`.

`--verify` menghitung ulang ukuran dan SHA-256 tiap file lalu menyebut yang tidak
cocok. Sudah diuji dengan fixture APK+OBB sintetis: full 2,7 MB / lite 852 KB,
verify PASS, lalu satu byte diubah di `lite/data/handling.cfg` menghasilkan
`CORRUPT data/handling.cfg` dan exit non-zero.
