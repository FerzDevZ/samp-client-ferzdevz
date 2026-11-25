# Architecture

Read this before changing a package name, a JNI symbol, or a storage path.

## Layout

```
client/                               the client, tracked source (fork of kuzia15/SAMP-Mobile GTA-2.11)
  app/src/main/java/
    com/ferzdevzgta/game/             our Java entry (SAMP, GTASA, ui/*) — 7 files
    com/ferzdevzgta/launcher/         launcher activity — 3 files
    com/rockstargames/oswrapper/      game wrapper — 32 files, PACKAGE NAME IS PINNED
    com/rockstargames/gtasa/          activity/downloader shell — 5 files
    com/nvidia/devtech/               NVIDIA glue — 3 files, PACKAGE NAME IS PINNED
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
| `/storage/emulated/0/GTA/SAMP/fonts/` | font used by the native UI | `samp/main.cpp:295` |
| `/storage/emulated/0/GTA/logcat.txt` | client log | `samp/main.cpp:397` |

The asset tree itself is not shipped — see [DATA.md](DATA.md).
