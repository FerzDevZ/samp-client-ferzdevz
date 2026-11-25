# The wrapper surface (`com.rockstargames.oswrapper`)

**Bahasa Indonesia: [id/OSWRAPPER.md](id/OSWRAPPER.md)**

The client does not ship Rockstar's game library. It ships a framework APK and a
patcher that adds the user's own `libGame.so` (see [PATCHER.md](PATCHER.md)). What the
framework still carries from the original package is a set of Java classes under
`com.rockstargames.oswrapper` and `com.nvidia.devtech`. This page records what is
pinned by the binary, what is free to rewrite, and how far the rewrite has got.

## Why the names cannot change

`libGame.so` resolves 38 native entry points by name:

```
$ nm -D --defined-only libGame.so | grep -c Java_
38
$ nm -D --defined-only libGame.so | grep Java_ | head -2
Java_com_rockstargames_oswrapper_GameNative_implIsInitialized
Java_com_rockstargames_oswrapper_GameNative_implOnDrawFrame
```

Name-based JNI lookup means the Java class, package and method name are part of the
binary interface. Three more classes are looked up by path:

```
$ strings libGame.so | grep -oE 'com/(rockstargames|nvidia)/[A-Za-z0-9/$_]+' | sort -u
com/nvidia/devtech/NvUtil
com/rockstargames/oswrapper/DeviceInfo
com/rockstargames/oswrapper/GamePlatformServices
```

Method names travel the same way. `libGame.so` carries the strings it looks up, so the
pinned part of a class can be listed from the binary instead of guessed:

```
$ for n in getInstance getParameter getAppLocalValue setAppLocalValue hasAppLocalValue hasTouchScreen
> do printf '%-20s %s\n' "$n" "$(strings -n 4 libGame.so | grep -cx "$n")"; done
getInstance          1
getParameter         1
getAppLocalValue     1
setAppLocalValue     1
hasAppLocalValue     1
hasTouchScreen       1
```

So there are two layers:

| Layer | Example | Changeable |
|---|---|---|
| Binary interface: package, class name, method name, argument types, public field names | `GameNative.implOnDrawFrame(float)`, `DeviceInfo.osVersion` | no |
| Everything inside | loop structure, helper methods, allocation, logging, error handling | yes |

A rewrite keeps the first row byte-identical and replaces the second.

## What the rewrite is and is not

It replaces Java that was decompiled from a shipped dex with code written against the
interface the binary and the manifest require. That removes Rockstar-derived source
from this repository and from the framework APK.

It is not a legal clean room: the same session has read the decompiled files, so the
usual "spec author has not seen the original" separation does not hold. Treat the
legal question as open and get your own advice before relying on this for publishing.
What is measurable today: the framework APK no longer contains decompiled bytecode of
these classes, only a reimplementation of their interface.

## Current state

`bin/oswrapper-audit.sh` divides the tree by a first-line marker and prints line
counts. Last measured:

```
rewritten: 4 files, 523 lines
  ok    com/nvidia/devtech/NvUtil.java
  ok    com/rockstargames/oswrapper/DeviceInfo.java
  ok    com/rockstargames/oswrapper/GameNative.java
  ok    com/rockstargames/oswrapper/GamePlatformServices.java
derived:   25 files, 4440 lines
```

### Two native declarations that could never resolve

Declaring a `native` method does not fail until the method is called, so the third slice
started with a comparison instead of a rewrite: `bin/jni-pins.py` reads every `native`
declaration under the wrapper and every `Java_...` symbol `libGame.so` exports, then fails
on a declaration with no symbol. It found two:

| Declaration | Called from | Reality |
|---|---|---|
| `GameNative.initializeSAMP()` | only a commented-out line in `GameActivityBase` | no symbol anywhere; the real entry point is `SAMP.initializeSAMP` in libsamp.so |
| `GameNative.implOnGamepadCountChanged(int)` | `GameThread.onGamepadCountChanged` | no symbol; libGame.so exports `implOnGamepadConnected`, `implOnGamepadDisconnected` and `implOnGamepadResume` instead |

Both are gone. The gamepad call site keeps its log line and explains why it no longer
calls anything: connecting a controller already reaches the engine through
`implOnGamepadAxesChanged` and `implOnGamepadButtonDown`. The three unclaimed exports are
reported as dead code, not as failures, because nothing in Java references them.

The check runs in `bin/self-test.sh` with a negative case, and it is the reason this
section exists: both bugs were invisible to the compiler and would have waited for a phone
to hit that path.

Two things happened in the second slice, and one of them is a deletion rather than a
rewrite. `GamePlatformServices` was a façade over six `I*Services` interfaces whose only
job was to mirror a C++ vtable; `strings -n 6 libGame.so | grep -cx ILanguageServices`
returns 0 for all six, so nothing outside our own source ever mentioned them. The
interfaces are gone, the six services keep their bodies and their names, and the engine
still finds all 34 callback names because those are pinned.

The pin list is now a gate instead of a paragraph: `tests/pins/oswrapper-pins.txt` holds
every name measured in `libGame.so`, and `bin/wrapper-pins.sh` runs `javap` over the
compiled classes and fails when one is missing. `bin/self-test.sh` runs it, including a
negative case with an invented name, because a check that cannot fail is not a check.

Run it yourself for the live number; the table below is the rewrite order, not a
promise of dates.

| Order | Files | Why this order |
|---|---|---|
| 1 | `DeviceInfo`, `NvUtil` | tiny, no dependencies, and the engine reads them first | 
| 2 | `GamePlatformServices` | done: the third class `libGame.so` names, 34 pinned callback names |
| 3 | `GameNative` | done: 35 declarations that resolve, two dead ones removed |
| 4 | `GameActivityBase`, `GameView`, `view/*` | next: the activity and input surface our own code extends |
| 5 | `GameThread` (1221 lines) | the frame loop; last because everything above must be stable first |
| 6 | `DownloaderActivityBase`, `DownloaderConfig`, `DownloaderItem` | Play Asset Delivery shell, replaced by our own downloader, candidate for deletion rather than rewrite |
| 7 | `services/*`, `AssetUtils`, `NvAPKFile*` | thin adapters, low risk |

## Adding a rewrite

1. Read the current file and the binary evidence that constrains it.
2. Rewrite the body; keep pinned names, argument lists and public field names.
3. Put this line first in the file, so the audit sees it:

   ```java
   // Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
   ```

4. `bin/oswrapper-audit.sh` must show the file under `rewritten`.
5. `cd client && ./gradlew :app:assembleDebug` must stay green.
6. `bin/wrapper-pins.sh` must pass; it reads the compiled classes, so it catches a
   rename that the compiler happily accepts.
7. Say in the commit what changed in behaviour, if anything. A rewrite that keeps
   behaviour has no behavioural note; a rewrite that fixes a null crash does.

## What stays unverified

Nothing here runs without a device that has the game data and a patched APK. Compile,
audit and symbol checks are all that can be proven on a build machine. A rewrite is
"done" when `bin/oswrapper-audit.sh --strict` exits 0 *and* a patched build starts a
game on a real phone.
