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
white icon on a light surface. The launcher icon is an adaptive vector, and `Theme.SampLauncher`
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
