# Changelog

Indonesian version: [CHANGELOG.id.md](CHANGELOG.id.md).

## 0.2.0

Pads work, settings survive a phone change, and the client can explain itself offline.

### Gamepad

- `implOnGamepadConnected`, `implOnGamepadDisconnected` and `implOnGamepadResume` were exported by
  `libGame.so` from the first build and had no Java caller, so a controller was a dead accessory.
  The signatures were read from the binary (`onConnect(int)`, `onDisconnect(int)`,
  `onResume(std::vector<int>)`) before anything was declared, and `bin/jni-pins.py` now reports
  **0 unclaimed exports**.
- A pad added while the game is already running is reported through `InputManager`, and the id list
  is handed back on every resume, because the engine rebuilds its table and a controller otherwise
  stops working after the first pause.
- Sticks have a 0.15 deadzone rescaled to full travel. Triggers are read from `AXIS_LTRIGGER` /
  `AXIS_RTRIGGER` and fall back to `AXIS_Z` / `AXIS_RZ` on pads without analog triggers.
- Every pad call is guarded against `UnsatisfiedLinkError`, so the framework-only APK keeps working.

### Profile backup

- Settings has **Export** and **Restore**: one zip containing `settings.ini` and `servers.txt`.
- The archive is written and read by `settings/ProfileBackup.java`, which accepts only its own three
  entry names, caps every entry at 512 KiB and refuses an archive without `settings.ini`. A
  traversal-shaped entry is ignored instead of extracted, and a test proves nothing lands outside
  the destination.

### Offline guide

- New **Guide** screen rendered by the system WebView from `assets/help/help.html`. It adds nothing
  to the APK because Android ships the engine, and it is the only place formatted help text fits:
  the app screens are Java, the HUD is ImGui, and TextDraw belongs to the server.
- The page gets three bridge methods: current language, version, and open diagnostics or Discord.
  File access from file URLs, universal access and DOM storage are all off, and any link that is not
  the asset opens in the browser.

### Changed

- `versionName` 0.2.0, `versionCode` 2.
- Settings screen gained a Profile backup card and a Guide entry.

## 0.1.0

First tagged build.

- Native client for Android arm64 (`libsamp.so`, 3583408 bytes) published as a release asset,
  separate from the APK.
- Rebrand to `com.ferzdevzgta.game`, telemetry removed, client source in the repository.
- `bin/android-build.sh`, `bin/self-test.sh`, cache builder for the `full` and `lite` variants.
- Clean-room rewrite of `com.rockstargames.oswrapper`, 20 of 21 files, names pinned by
  `tests/pins/oswrapper-pins.txt`.
- Server picker that writes `settings.ini` for the player, on-device APK patcher for Termux.
