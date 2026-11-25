# Architecture

Read this before changing a package name, a JNI symbol, or a storage path.

## Layout

```
client/                               the client, tracked source (fork of kuzia15/SAMP-Mobile GTA-2.11)
  app/src/main/java/
    com/ferzdevzgta/game/             our Java entry (SAMP, GTASA, ui/*), 7 files
    com/ferzdevzgta/launcher/         launcher activity, 3 files
    com/rockstargames/oswrapper/      game wrapper, 32 files, PACKAGE NAME IS PINNED
    com/rockstargames/gtasa/          activity/downloader shell, 5 files
    com/nvidia/devtech/               NVIDIA glue, 3 files, PACKAGE NAME IS PINNED
  app/src/main/cpp/
    samp/                             SA-MP netcode, game hooks, chat, GUI  -> libsamp.so
    opus/ bass/ raknet/ imgui/ ...    vendored third-party
  app/src/main/jniLibs/arm64-v8a/     prebuilt .so, mostly git-ignored (see LEGAL.md)
bin/                                  build, blob-fetch and cache gates
docs/                                 this directory
```

Single ABI: `arm64-v8a`. `minSdk 28`, `targetSdk 36`, `compileSdk 36`, NDK 26.2.11394342.

## Startup path

```
MainActivity (com.ferzdevzgta.launcher.activity)
  └─ System.loadLibrary("multi")
SAMP          (com.ferzdevzgta.game)      System.loadLibrary("Game"), then "samp"
  └─ native initializeSAMP()               libsamp.so patches libGame.so, then hands control over
GameActivity  (com.rockstargames.gtasa)
  └─ GameNative (com.rockstargames.oswrapper) -> libGame.so renders the frame
       libsamp.so hooks the game loop, adds SA-MP net layer, chat and GUI on top
```

`libsamp.so` never links against `libGame.so` at build time. It resolves the game
library at runtime (`CUtil::FindLib`) and installs hooks into it. That is why
`bin/android-build.sh native` compiles the whole client with no Rockstar binary
present, and why the APK still needs one to run.

## JNI contract

Five symbols in `libsamp.so` are resolved by Java's symbol-naming rule, so the
Java package and the C symbol must change together or the app throws
`UnsatisfiedLinkError` on first call.

| Java method | Exported symbol |
|---|---|
| `com.ferzdevzgta.game.SAMP.initializeSAMP()` | `Java_com_ferzdevzgta_game_SAMP_initializeSAMP` |
| `...game.ui.dialog.DialogManager.sendDialogResponse(int,int,int,byte[])` | `Java_com_ferzdevzgta_game_ui_dialog_DialogManager_sendDialogResponse` |
| `...game.ui.AttachEdit.Exit()` | `Java_com_ferzdevzgta_game_ui_AttachEdit_Exit` |
| `...game.ui.AttachEdit.AttachClick(int,boolean)` | `Java_com_ferzdevzgta_game_ui_AttachEdit_AttachClick` |
| `...game.ui.AttachEdit.Save()` | `Java_com_ferzdevzgta_game_ui_AttachEdit_Save` |

Check after any rename:

```bash
SO=$(find client/app/build/intermediates/cxx -name libsamp.so -path '*arm64-v8a*' | head -1)
nm -D --defined-only "$SO" | grep Java_
```

## Namespaces that must not be renamed

`libGame.so` is a prebuilt binary. It contains hardcoded references, so three
Java names are frozen:

```bash
nm -D --defined-only client/app/src/main/jniLibs/arm64-v8a/libGame.so | grep -c Java_
# 38   all Java_com_rockstargames_oswrapper_GameNative_*

strings -a client/app/src/main/jniLibs/arm64-v8a/libGame.so | grep -E '^com/'
# com/nvidia/devtech/NvUtil
# com/rockstargames/oswrapper/DeviceInfo
# com/rockstargames/oswrapper/GamePlatformServices
```

| Frozen | Reason |
|---|---|
| `com.rockstargames.oswrapper` | 38 JNI symbols live in the binary |
| `com.nvidia.devtech` | `NvUtil` is looked up by path inside the binary |

`com.ferzdevzgta.*` is ours: it owns the SAMP symbols and the launcher, so it was
renamed from `com.gta.*`. `com.rockstargames.gtasa` has no symbol pinned by the
binary, but it sits directly on top of the frozen wrapper and is left as is.

## Storage paths

Hardcoded, not configurable from the build:

| Path | Contents | Defined in |
|---|---|---|
| `/storage/emulated/0/GTA/` | game assets | `samp/main.cpp:36`, `samp/game/hooks.cpp:1385` |
| `/storage/emulated/0/GTA/SAMP/settings.ini` | client settings | `samp/settings.cpp:15` |
| `/storage/emulated/0/GTA/SAMP/servers.txt` | server list for the browser | `servers/GameConfig.java` |
| `/storage/emulated/0/GTA/SAMP/fonts/` | font used by the native UI | `samp/main.cpp:295` |
| `/storage/emulated/0/GTA/logcat.txt` | client log | `samp/main.cpp:397` |

The asset tree itself is not shipped, see [DATA.md](DATA.md).

## Choosing a server

The native client reads `[client] host`, `port`, `name` and `password` from
`SAMP/settings.ini` once, on the first frame, and never again. Everything that picks a
connection therefore writes that file before the game starts:

| Piece | File | Job |
|---|---|---|
| Browser screen | `game/ui/ServerBrowserActivity.java` | lists servers, adds one, saves the nickname, starts the game |
| List file | `game/servers/ServerList.java` | `SAMP/servers.txt`, one server per line: a display name, then `host:port`, separated by a vertical bar |
| Address rules | `game/servers/ServerEndpoint.java` | host and port validation, nickname limits |
| File writer | `game/servers/GameConfig.java` | creates `settings.ini` on a fresh install, writes `[client]` |

The player never edits the ini by hand. Two guards keep a bad file from ending the run:

1. `settings.cpp` logs a warning instead of calling `std::terminate()` when the file is
   missing or unreadable, and every key falls back to its built-in default.
2. `main.cpp` skips the connection when `[client] host` is empty and says so in the log,
   instead of pointing RakNet at nothing.

The key set written by `GameConfig` is checked against `settings.cpp` by
`GameConfigTest`, so adding a key to the engine without adding it to the template fails
`bin/self-test.sh`.

## Screens and language

Five screens sit in front of the engine. Each one is built in Java against `ui/Ui.java`
instead of a layout file, so the palette, spacing and touch targets cannot drift apart
between screens:

| Screen | Class | Job |
|---|---|---|
| Launcher | `launcher/activity/MainActivity.java` | shows data, server and nickname state, opens the game or the setup screen |
| Data | `game/ui/DataSetupActivity.java` | variant pick, verify, resumable download, share log |
| Servers | `game/ui/ServerBrowserActivity.java` | server list, add, remove, connect |
| Settings | `game/ui/UiSettingsActivity.java` | the `settings.ini` keys the engine reads |
| Language | part of launcher, data and settings | Indonesian or English |

The language is stored by the app, not by the engine:

- `game/ui/Language.java` holds the rules and has no Android imports, so
  `tests/jvm/src/com/ferzdevzgta/game/ui/LanguageTest.java` runs it under plain `javac`.
  `id`, `id-ID`, `in` and `IN_br` all fold to `in`; an unsupported language falls back to
  English; an empty value means "follow the device".
- `game/ui/LanguageStore.java` persists the choice under `ui_language` and calls
  `AppCompatDelegate.setApplicationLocales`, which recreates the screens.
- `game/SampApplication.java` applies the stored choice in `onCreate`, before the first
  activity, so the launcher never flashes the wrong language.

`res/values/strings.xml` is English, `res/values-in/strings.xml` is Indonesian, and
`bin/self-test.sh strings` fails when a key exists in one file and not the other, or when
the two translations use different format placeholders.

The engine keeps its own language (`gui.Language` in `settings.ini`, mapped by
`services/LanguageServices.java`) and this selector does not touch it: the game UI and the
launcher UI are translated separately, and `language_note` says so on screen.

Colours live in one place, `ui/Ui.java`, and `bin/ui-contrast.py` reads that file and
computes WCAG 2.2 AA ratios for the seventeen pairs the screens actually use. A darker hint
colour fails that check instead of becoming unreadable on a phone.

The app targets SDK 36, and Android 15 draws every such app edge to edge: the window extends
under the clock and the gesture bar whether the theme asks for it or not. Each screen calls
`Ui.applyInsets` on its content view, which adds the system bar and cutout insets to the
existing padding. Without it the first line of a screen sits on top of the status bar, which
is exactly the kind of overlap that has no compile error.

The launcher pins its actions in a bottom bar instead of ending the scroll with them, so the
primary button stays inside thumb reach and a short screen does not end in empty space. Its
status card shows one dot per requirement, and the primary button changes label, Play or Set
up first, instead of repeating the same three words on three different controls.

### Icons, splash and diagnostics

Controls are text first, with a vector glyph from `res/drawable/ic_*.xml` where one helps. The
glyph is tinted with the label colour by `Ui.icon`, so a secondary button never comes out with a
white icon on a light surface. The launcher icon is an adaptive icon whose foreground is a bitmap
per density, produced from the source drawing by `bin/make-icons.py`, and `Theme.SampLauncher`
sets `splash_window` as the window background, which removes the white flash between the tap and
the first frame.

The server screen asks every listed address for its info answer through `servers/ServerQuery`
(pure, JVM tested) and `net/ServerQueryClient` (UDP, one thread per probe, 1200 ms timeout, the
result posted to the main thread). A dead address costs one timeout in the background, not a
frozen list.

`ui/DiagnosticsActivity` reads the phone, not the build: app version, both native libraries with
their sizes, the five declared JNI entry points, the data tree, the manifest, the stored target
and the tail of `GTA/logcat.txt`, with copy and share. Its formatting rules live in `diag/Report`
so `bin/self-test.sh` covers them without a device.

There is no separate first-run wizard on purpose: the launcher already lists the three
requirements, and each row is the shortcut to the screen that fixes it. A wizard would repeat the
same three actions one tap at a time.

### The game loading screen

`ui/LoadingScreen` is inflated from `res/layout/loadingscreen.xml` over the game surface and stays
until the engine reports that the first frame is up. It keeps three promises:

- It never invents a percentage. The engine reports no byte count, so the bar is a marquee and the
  status line names the phase (`Preparing the engine`, `Reading game data`, `Connecting to the
  world`). A bar that reaches 100 percent before the world is playable is a claim the player can
  see through.
- No timer dismisses it. Only `hide()` ends the screen, which the client calls when loading is
  done. The previous implementation advanced a fake countdown and hid the screen after 4.5
  seconds, which could reveal a still-black world.
- A long wait explains itself. After twenty seconds the tip line is replaced with a sentence that
  points at `GTA/logcat.txt`, and the bar keeps moving, so a slow load never looks frozen.

One line of real tips rotates every seven seconds, off when the system animation scale is zero.
The block is padded for a display cutout, because the game runs landscape and a camera hole would
otherwise cover the identity block.

### Touch feedback and empty states

`Ui.pressable` adds a haptic tick on press and a two percent press scale, both disabled when the
animation scale is zero. Every button created through `Ui.button` gets it, as do the launcher
status rows, so a control that looks like a button also feels like one.

`Ui.emptyState` renders an icon, a title, one sentence and the single action that fills the list.
An empty server list is the first screen a new player meets, so it asks a question and offers the
button instead of printing a sentence in the middle of the card.

`res/values/samp_colors.xml` holds the colours that XML needs before Java runs. It is not a second
palette: `bin/ui-contrast.py` fails when any entry disagrees with the constants in `Ui.java`.

### Palette and icon come from the same drawing

The app icon is hand drawn: navy field, sunset disc, palms, black outlines. The design tokens in
`ui/Ui.java` follow it, so the app and the icon read as one product instead of a poster with an
unrelated interface attached:

| Token | Value | Where it comes from |
|---|---|---|
| `BG` | `#0B111B` | the navy field, darkened so text can reach 16:1 |
| `SURFACE` | `#151F32` | the navy field itself |
| `SURFACE_2` | `#1F2C45` | the lighter navy around the sun |
| `ACCENT` | `#EF7D2B` | the sunset orange |
| `WARN` | `#F2C14E` | the yellow of the lettering |
| `TEXT` | `#F3EEE6` | the warm off white of the sky |

`WARN` stays distinct from `ACCENT` by hue and lightness, so a status dot is never mistaken for a
button, and `bin/ui-contrast.py` keeps the pairs above AA. `res/drawable-*/ic_brand.png` is the same
drawing cut to a rounded square at 96 dp, so the home screen icon, the splash window and the loading
screen show one mark at every size.

The icon is shipped as a bitmap, not a traced vector, because it is a pen illustration: tracing it
would drop the line work and the lettering. `bin/make-icons.py --source <picture>` finds the square
inside a screenshot, applies a rounded mask, and writes `mipmap-*/ic_launcher_foreground.png` at the
adaptive icon's 108 dp viewport with the art at 78 percent of it, which is inside the mask's safe
zone. The numbers live in one place in that script instead of in five hand-cropped files.

`bin/icon-check.py` guards the wiring, because the drawing can be perfect and invisible anyway. The
manifest once declared `android:icon="@drawable/ic_launcher_background"`, the icon's own background
layer: the build stayed green, every PNG was present, and every phone showed an empty navy square.
The check compares the manifest reference, both adaptive XML files, the five densities, the absence
of stale legacy `ic_launcher.webp` files, and the splash and loading references, and `bin/self-test.sh
icons` re-creates that exact mistake in a scratch tree to prove the check rejects it.

### Reporting a bug

`ui/Community` holds the Discord invite in one constant, with no Android imports, so
`CommunityTest` can pin its shape in the JVM suite: https, host `discord.gg`, alphanumeric code,
no query string. The invite is reachable from three places, because a support channel nobody can
find is decoration:

- the launcher note at the bottom, which is underlined and tappable
- Display, in a Support section
- Diagnostics, where the button copies the report to the clipboard before opening Discord, so the
  player arrives with the evidence already pasted-ready

## Input: gamepad

Three of the pad callbacks existed in `libGame.so` before anything in Java declared them, so a
controller connected, Android reported it, and the game never heard about it. They were declared
only after the signatures were read from the binary:

```
implOnGamepadConnected(int)      -> GamepadProvider::onConnect(int)
implOnGamepadDisconnected(int)   -> GamepadProvider::onDisconnect(int)
implOnGamepadResume(int[])       -> GamepadProvider::onResume(std::vector<int>)
```

`bin/jni-pins.py` now reports 0 unclaimed exports, which is the proof that the three were not
guessed. The wiring has three parts:

- `com.rockstargames.oswrapper.input.GamepadInput` registers an `InputManager.InputDeviceListener`,
  so a pad added while the game is already running is reported immediately, not only on the next
  startup.
- The same class hands the full id list back on every resume. The engine rebuilds its pad table from
  that list, so skipping it left a controller connected but unusable after the first pause.
- `com.ferzdevzgta.game.input.GamepadMap` holds the numbers and no Android imports: 0.15 deadzone
  rescaled to full travel, triggers accepted as 0..1 or -1..1, six axes in the order the engine's
  signature expects, and a `SOURCE_JOYSTICK` test so a keyboard key is not mistaken for a pad.
  `GamepadMapTest` pins all of it in the JVM suite.

Every call is wrapped against `UnsatisfiedLinkError`. The framework-only APK has the Java classes
and no engine, and a pad event must not be able to kill it.

## Settings that leave the phone

`settings/ProfileBackup` writes one zip with `settings.ini` and `servers.txt`, which is what makes
moving to a new phone a short job instead of an evening of retyping. The archive is a plain zip with
three fixed entry names, and reading it accepts those names only:

- an archive without `settings.ini` is refused rather than half-applied
- every entry is capped at 512 KiB, so a corrupt or hostile file cannot fill shared storage
- an entry named `../../settings.ini` is ignored, because the destination path comes from the code
  and never from the archive

`ProfileBackupTest` covers the round trip, both refusals and the traversal case against a scratch
directory. Export and Restore go through the system file picker, so the zip lands wherever the player
keeps backups.

## The guide screen and why it is a WebView

The client has three UI layers and none of them can hold formatted help text: the app screens are
Java views, the in-game HUD is ImGui, and TextDraw belongs to the server. Android ships a WebView
with every device, so `ui/WebPanelActivity` renders `assets/help/help.html` and adds nothing to the
APK.

It is deliberately narrow in authority: JavaScript is on for the asset, file access from file URLs
is off, universal access is off, DOM storage is off, and any link that is not the asset is handed to
the browser. The page may call exactly three methods: current language, version, and open diagnostics
or Discord. `openDiagnostics` is used instead of a second copy-to-clipboard path because the report
is already built in one place, and duplicating it would let the two versions drift.

None of this replaces server TextDraw. That is RPC data the engine must draw, and translating it to
HTML would be a different project with unreliable results across servers.

## Links that open the client

`samp://host:port` is registered on the launcher activity, so an address posted in a chat room is
tapped instead of retyped. The parser is `servers/DeepLink.java` and it is pure: the intent gives a
string, the parser returns a `ServerEndpoint` or null, and the JVM suite pins every refusal case.

The link reuses `ServerEndpoint` instead of writing a second set of rules, because a link and a line
in `servers.txt` must agree on what is joinable. A path, a fragment or an unknown scheme is refused
outright: a silently wrong port is worse than a message saying the link is not a server.

`GameConfig.select` then moves only `client.host` and `client.port`. The nickname and password
already in the file stay, because a link arrives after the player typed a name, and a link should
not be able to rename them. The server is also appended to `servers.txt` so it shows up in the
browser later, and long pressing a row copies the same link back out.

## Voice: a key plus a permission

The engine already has voice: `cpp/samp/voice/Plugin.cpp` is compiled in, and
`gui/samp_widgets/voicebutton.h` draws the button only when `gui/VoiceChatEnable` is true. What was
missing was the Android side of the same promise. The switch now exists in Settings, and turning it
on asks for `RECORD_AUDIO` first, then stores the key only if the grant landed.

`BASS_RecordGetDeviceInfo` returns nothing while the microphone is refused, so a key written without
the grant produces a voice button that records silence. The launcher checks the permission again
before starting the game, which covers the case of a key written by hand in `settings.ini`.

## Screenshots

The game screen has a 44dp camera button. `PixelCopy` reads the composited surface into a bitmap and
the file lands in `GTA/Screenshots/samp-<date>-<time>.png`, then the share sheet opens.

PixelCopy was chosen over a native `glReadPixels` for two reasons: it reads what the player actually
sees, HUD and server TextDraw included, and it adds no JNI entry point, so the frozen surface
`libGame.so` expects stays exactly as it is. `media/ShotFile.java` holds the naming rule and is
covered by the JVM suite.

## The session marker

A segfault inside `libGame.so` kills the process before any Java code can write a log, so
`log/CrashHandler.java` cannot see it. `log/SessionGuard.java` writes `SAMP/session.open` when the
game screen opens and deletes it in `onDestroy`.

Diagnostics reads the marker on the next launch. A marker from the running process is ignored,
because that means the game is open now. A session the system killed looks the same as a crash from
here, so the row says the session did not close on purpose and never claims to know why.

## The frame counter is a switch, not a feature

`gui/fps` is read by `cpp/samp/settings.cpp` and drawn by `CDebugInfo`, and the Settings screen
already toggles it. Nothing in Java should draw a second counter: two numbers would disagree and
neither could be trusted.

## The HUD editor

The engine ships an adjustable-HUD class for GTA touch buttons, but every body in it is a commented
out hook and nothing calls it. The SA-MP widgets this client draws itself are the chat box, the
button panel and the voice button, and none of them could be moved: `gui/ChatPosX`,
`gui/VoiceChatPosX` and `gui/SpawnScreenPosX` are parsed by `settings.cpp` and then never read, so
writing them changed nothing.

`gui/hudeditor.cpp` is the editor for those three. A `HUD` button in the button panel opens it, the
widgets turn visible while it is open, and a drag writes the new position straight into the widget.
`Simpan` writes `[hud]` keys and closes, `Reset` puts the built-in positions back.

Three rules make it safe to ship:

1. It writes its own `[hud]` section. It never touches `[gui]`, so a bug in the editor cannot change
   chat size, font or the FPS switch. `bin/hud-check.py` fails the build if the editor names the gui
   section.
2. `HudEditor::Load()` runs at the end of `UI::initialize()`, before the first frame, so the game
   opens where the player left it. `HudEditor::Draw()` runs inside `UI::drawList()`, inside the ImGui
   frame, because the handles are ImGui items rather than rectangles on a draw list.
3. While the editor is open, `UI::OnTouchEvent` gives the touch to ImGui only. Handing the same
   touch to the chat box would open the keyboard behind the editor.

Positions are stored as plain `x,y` in the same coordinate space the widgets already use, so the
editor needs no 640x480-to-screen conversion. `ParsePair` and `FormatPair` share the format, and a
drag is clamped to the display so a widget cannot be parked off screen.
