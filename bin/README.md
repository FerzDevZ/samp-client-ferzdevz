# bin/

Four scripts. Each one is a gate: it exits non-zero when the thing it claims is
true turns out not to be.

| Script | Purpose | Needs `libGame.so` |
|---|---|---|
| `android-build.sh` | build `libsamp.so` and/or the APK, then inspect the artifact | yes, except in `native` mode |
| `fetch-blobs.sh` | copy the two binary sets that cannot be tracked in git | — |
| `build-cache.sh` | build and verify the `/storage/emulated/0/GTA/` asset tree | — |

## android-build.sh

```bash
bin/android-build.sh            # debug APK
bin/android-build.sh release    # release APK (unsigned without a keystore)
bin/android-build.sh clean      # wipe build dirs, then debug APK
bin/android-build.sh native     # libsamp.so only — no APK, no libGame.so
```

Preflight: JDK, NDK 26.2.11394342, cmake 3.22.1. Then it writes
`client/local.properties`, sets the `gradlew` exec bit, repairs a missing trailing
newline in `client/gradle.properties`, builds, and asserts the result:

- APK modes: `lib/arm64-v8a/libsamp.so` **and** `lib/arm64-v8a/libGame.so` inside the APK;
- `native`: `libsamp.so` exists under `app/build/intermediates/cxx`.

Measured here: `clean` 2m08s–2m50s, `native` 10s, both 0 warnings.

## fetch-blobs.sh

```bash
bin/fetch-blobs.sh            # no-op if the files are already there
bin/fetch-blobs.sh --force    # refetch
```

Clones the upstream `GTA-2.11` branch into a temp dir and copies out:
`jniLibs/arm64-v8a/libGame.so` and `samp/vendor/bass/libs/<abi>/*.so`.

Both are ignored by git on purpose — Rockstar's binary and a licence that forbids
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
| `fetch-blobs.sh` | menyalin dua set binary yang tidak boleh masuk git | — |
| `build-cache.sh` | membangun dan memverifikasi tree aset `/storage/emulated/0/GTA/` | — |

## android-build.sh

Empat mode seperti tabel di atas. Preflight: JDK, NDK 26.2.11394342, cmake 3.22.1.
Lalu menulis `client/local.properties`, memasang exec bit `gradlew`, memperbaiki
newline yang hilang di `client/gradle.properties`, build, dan memastikan hasilnya:

- mode APK: `lib/arm64-v8a/libsamp.so` **dan** `lib/arm64-v8a/libGame.so` ada di dalam APK;
- `native`: `libsamp.so` ada di `app/build/intermediates/cxx`.

Terukur di sini: `clean` 2m08s–2m50s, `native` 10s, keduanya 0 warning.

## fetch-blobs.sh

Idempoten. Clone branch `GTA-2.11` upstream ke direktori sementara lalu menyalin
`libGame.so` dan `libbass*.so`. Keduanya sengaja di-ignore git — binary Rockstar
dan lisensi yang melarang redistribusi. Lihat `docs/LEGAL.md`.

## build-cache.sh

Membangun `cache/full/`, `cache/lite/` (tanpa `audio/` dan `movies/`),
`manifest-full.json`, `manifest-lite.json`, dan `PROVENANCE.txt`.

`--verify` menghitung ulang ukuran dan SHA-256 tiap file lalu menyebut yang tidak
cocok. Sudah diuji dengan fixture APK+OBB sintetis: full 2,7 MB / lite 852 KB,
verify PASS, lalu satu byte diubah di `lite/data/handling.cfg` menghasilkan
`CORRUPT data/handling.cfg` dan exit non-zero.
