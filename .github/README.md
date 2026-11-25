# .github/

One workflow, `workflows/ci.yml`, triggered on push to `main`, on tags `v*`, on
pull requests and manually.

## What it does

1. JDK 17 (temurin), Android SDK, NDK `26.2.11394342`, cmake `3.22.1`.
2. Fails if a proprietary binary is tracked in git: `libGame.so`, or any
   `vendor/bass/libs/*.so`.
3. Runs `bin/android-build.sh native`, compiles `libsamp.so` with no Rockstar
   file present.
4. Asserts the output is `ELF 64-bit LSB shared object, ARM aarch64` and that it
   contains SA-MP symbols.
5. Uploads `libsamp.so` as a build artifact named after the commit.

## What it deliberately does not do

It never assembles or publishes an APK. An installable APK contains Rockstar's
`libGame.so` (8.5 MB) and would be redistribution. The check in step 2 exists so
that this cannot happen by accident either.

## Adding a release for your own use

If you decide to publish the APK yourself, that is your account and your call.
Nothing here automates it. The supported path for other people is: they clone,
run `bin/fetch-blobs.sh`, `bin/android-build.sh`, and supply their own asset tree
(`docs/DATA.md`).

---

# .github/ (Bahasa Indonesia)

Satu workflow, `workflows/ci.yml`, jalan pada push ke `main`, tag `v*`, pull
request, dan manual.

## Yang dikerjakan

1. JDK 17 (temurin), Android SDK, NDK `26.2.11394342`, cmake `3.22.1`.
2. Gagal kalau ada binary proprietary yang ter-track di git: `libGame.so` atau
   `vendor/bass/libs/*.so`.
3. Menjalankan `bin/android-build.sh native`, mengompilasi `libsamp.so` tanpa file
   Rockstar.
4. Memastikan hasilnya `ELF 64-bit LSB shared object, ARM aarch64` dan mengandung
   simbol SA-MP.
5. Mengunggah `libsamp.so` sebagai artifact build bernama commit-nya.

## Yang sengaja tidak dikerjakan

CI tidak pernah merakit atau menerbitkan APK. APK yang bisa dipasang berisi
`libGame.so` milik Rockstar (8,5 MB), dan itu berarti redistribusi. Pemeriksaan di
langkah 2 ada supaya hal itu juga tidak terjadi tanpa sengaja.

## Kalau kamu ingin menerbitkan rilis sendiri

Kalau kamu memutuskan menerbitkan APK-nya, itu akunmu dan keputusanmu. Tidak ada
yang mengotomatiskan itu di sini. Jalur yang didukung untuk orang lain: clone,
`bin/fetch-blobs.sh`, `bin/android-build.sh`, lalu sediakan tree aset sendiri
(`docs/id/DATA.md`).
