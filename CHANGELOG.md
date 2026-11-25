# Changelog

Indonesian version: [CHANGELOG.id.md](CHANGELOG.id.md).

## 0.4.0

The widgets you look at all game can be moved by hand, a 0.3.DL server's model list can be checked
before anyone joins, and the phone has one command to build instead of two to remember.

### HUD editor

- A `HUD` button in the in-game button panel opens an editor for the chat box, the button panel and
  the voice button. Drag one, tap `Simpan`, and it stays there: `gui/hudeditor.cpp` writes `[hud]` in
  `settings.ini` and `HudEditor::Load` applies it before the first frame.
- Positions are stored in the coordinate space the widgets already use, so no 640x480 conversion was
  invented for this.
- The editor writes its own section and never `[gui]`. A bug in it cannot change chat size, font or
  the FPS switch, and `bin/hud-check.py` fails the build if the editor ever names the gui section.
- One thing this fixed: `gui/ChatPosX`, `gui/VoiceChatPosX` and `gui/SpawnScreenPosX` were parsed by
  `settings.cpp` and then never read by anything, so writing them changed nothing. The new keys are
  the ones that work.

### 0.3.DL model lists

- `bin/artconfig.py` reads a server's `models/artconfig.txt` and refuses what a server accepts at
  startup and then loses a model over: skin ids outside 20000..30000, object ids outside
  -30000..-1000, a stock-skin base outside 0..311, a repeated id, a file missing from `models/`, a
  name with `../`, a line without its semicolon.
- `--manifest` writes the inventory a cache needs: sha256 and size for both files per entry, with the
  total counted per unique file because several skins usually share one texture.
- The client cannot draw these models yet. Nothing in the engine knows about custom model ids or the
  0.3.DL download handshake. [docs/DL.md](docs/DL.md) says exactly which three pieces are missing and
  in what order they have to be built.

### One command on the phone

- `bin/build-termux.sh` is the entry point in Termux: it reports which tools are present, can fetch
  the framework APK from a URL, refuses a framework that is older than the source tree it sits next
  to, then calls `bin/patch-termux.sh`.
- The staleness check is the part that matters. A patched APK built from an old framework looks fine
  and runs the old UI, and nothing during a patch would have noticed.
- Measured with a synthetic GTA split on this machine: exit 0, `PASS`, package
  `com.ferzdevzgta.game`, `libGame.so` stored at an offset that is a multiple of 4096.

### Gates

`bin/self-test.sh all` covers the two new checks: `hud` (entry point, load order, frame order, own
ini section, plus a negative case that writes `[gui]`) and `artconfig` (a good file plus seven
refusals). `CMakeLists.txt` now uses `CONFIGURE_DEPENDS`, so a new native file compiles without
deleting the CMake cache first.

## 0.3.0

Servers arrive by link, voice has a switch that can actually work, and a session that died leaves
evidence.

### Links

- `samp://host:port` opens the launcher with that server already selected. The parser lives in
  `servers/DeepLink.java` and reuses the same host and port rules as `servers.txt`, so a link and a
  line in the list cannot disagree about what is joinable.
- A link moves the host and port only. The nickname and password already saved stay untouched,
  because a link opened from a chat room arrives after the player typed a name.
- Long press a server in the browser to copy its link. That is how an address gets passed around
  without anyone retyping digits.

### Voice

- The engine reads `gui/VoiceChatEnable` and only draws its voice button when that key is true, so
  the switch is now in Settings next to the other real keys.
- Android refuses the microphone without a runtime grant, and the switch used to be a promise the
  app could not keep. Ticking it now asks for `RECORD_AUDIO` first, and the launcher checks again
  before starting the game. A refused grant leaves the key off instead of leaving a dead button.

### Screenshots

- A 44dp camera button in the game screen copies the surface with `PixelCopy` into
  `GTA/Screenshots/samp-<date>-<time>.png` and opens the share sheet. What lands in the file is
  what the player saw, including engine HUD and server TextDraw, and no JNI was added.

### Session marker

- A native crash in `libGame.so` never reaches the Java crash handler. The game now writes
  `SAMP/session.open` at start and deletes it on a normal close, so Diagnostics can say the last
  session did not close on purpose. A session killed by the system looks the same from here, so the
  text never calls it a crash.

### Notes

- The FPS counter was already there: `gui/fps` is read by `cpp/samp/settings.cpp` and drawn by
  `CDebugInfo`, so it is a toggle, not a feature to write.
- Gates: 351 JVM assertions, 25 suites, 17 contrast pairs, 38 JNI natives with 0 unclaimed exports.

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
