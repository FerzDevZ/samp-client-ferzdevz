# Device verification

`bin/self-test.sh` proves logic and formatting. It cannot prove that a screen looks right on a
phone, that the storage permission flow survives a real ROM, or that `libGame.so` starts with the
data tree a player actually has. This file is the checklist for that half.

Run it once on the first real device, keep the filled table in the commit that fixes whatever it
finds, and re-run it after any change to the launcher, the diagnostics screen or the patcher.

## 0. Before the first launch

| Check | Expected |
|---|---|
| APK installed over the previous build | install succeeds, no `INSTALL_FAILED_UPDATE_INCOMPATIBLE` |
| The sha256 of the file you installed | equals the one in `samp-download/index.html` |
| `Settings → Apps → SA-MP FerzDevZ → Storage` | permission granted, or the app asks once |
| Android version | 9 or newer; the edge-to-edge layout matters from 15 up |

## 1. First frame

| Step | Expected | If it fails |
|---|---|---|
| Tap the icon | brand background, no white flash, then the launcher | the splash window background is not applied: check `Theme.SampLauncher` in the manifest |
| Header | the title sits **below** the clock and the battery, not under them | `Ui.applyInsets` is missing or called before `setContentView` |
| Bottom bar | `Play` or `Set up first`, plus `Data` and `Server`, reachable by thumb | the bar is inside the scroll view |
| Status card | three rows, each with a coloured dot, and one `Ready` or `Not ready` pill | `refreshStatus()` did not run |

On a clean install the three rows should say: data `missing`, server `none`, nickname `none`, and
the primary button should read `Set up first`.

## 2. Language

| Step | Expected |
|---|---|
| `Display → Bahasa Indonesia` | every label on that screen changes immediately |
| Close from the recents list, open again | the app starts in Indonesian, no English frame first |
| Open `Diagnostics` while in Indonesian | the diagnostic labels are Indonesian too |
| Switch back to English | no mixed-language screen anywhere |

The engine keeps its own language. This switch is for the app screens only, which the note under
the selector says.

## 3. Diagnostics, before the data exists

Open `Display → Diagnostics`. This is the screen that turns a bug report into evidence.

| Row | Expected on a clean install | Expected after setup |
|---|---|---|
| App version | `0.2.0 (2)` | same |
| Native libraries | `2/2 libGame.so …, libsamp.so …` | same |
| JNI entry points | `5/5` | same |
| Game data | **fails**, `0 files, 0 B` | passes, a real file count and size |
| Data manifest | `missing` until you copy one | `lite, N entries, M B` |
| Connection | `none` | the saved `host:port` |
| Log file | `missing` before the first run | a size |
| Last crash | `none` | `none`, unless something actually crashed |

Tap `Copy report` and paste it into a note. That text is what makes a report actionable.

## 4. Game data

| Step | Expected |
|---|---|
| Put the cache contents in `/sdcard/GTA/` (`cp -a …/lite/. /sdcard/GTA/`) | `Data` row turns green after a re-check |
| `Data → Verify` | every file matches the manifest, no hash mismatch |
| Fill `[download] url` in `SAMP/settings.ini`, then `Data → Sync` | progress moves, the summary reports what was fetched |
| Kill the app mid-sync, reopen | the sync resumes from a byte offset, not from zero |
| Remove the URL and press Sync | the app refuses with a readable message, it does not crash |

## 5. Servers

| Step | Expected |
|---|---|
| `Server → Add`, type a host and port | the row appears, marked as the current target |
| Wait up to two seconds | live text appears: latency, players of slots, `offline` when the address is dead |
| `Refresh` | every row is re-probed, no row is left on the old value |
| Add a second server and pick it | `SAMP/settings.ini` changes, the launcher row follows |
| Enter a nickname | the nickname row stops saying `none` |

A dead address must cost one timeout per row and never freeze the list.

## 6. The game itself

| Step | Expected |
|---|---|
| `Play` | the engine starts, the splash and then the menu appear |
| Join a server | the server list answers with a name and a player count |
| Disconnect, go back | the launcher is intact, settings kept |
| Rotate the phone on the launcher | no clipped text, no overlapping rows |
| Font size `Largest` in system settings | no truncated button label |

## 7. When something fails

Capture these three, in this order:

1. `Diagnostics → Copy report` (the whole text, not a screenshot).
2. `Diagnostics → Share log`, or `GTA/logcat.txt` directly.
3. The exact step from this file that failed, and what the screen showed instead.

The crash block in `logcat.txt` looks like this, and the `version:` line is what makes it
reproducible:

```
[app crash]
time   : 2026-11-25 06:20:11 UTC
version: 0.2.0 (2)
thread : main
java.lang.IllegalStateException: boom
    at …
[/app crash]
```

## 8. Result

| Date | Device, Android | Version | Steps passed | First failure | Fixed in |
|---|---|---|---|---|---|
| | | | | | |

## 9. What this file cannot cover

- **Split APK installs.** `bin/patch-apk.sh --gta-split-dir` has only been exercised on synthetic
  fixtures. A real Play Store split install is the next thing to try after step 6 passes.
- **Load.** Hundreds of players, long sessions and timer drift need a server and time, not a
  checklist.
- **Other devices.** Android 15 and 16 change insets and back behaviour. One passing phone is one
  passing phone.

## 0.2.0 additions

| Step | Pass looks like |
|---|---|
| Plug a pad into a running game | the pad works within a second, no restart, no `[pad]` failure line in `GTA/logcat.txt` |
| Press Home, come back | the pad still works. A pad that dies after the first pause means `implOnGamepadResume` was skipped |
| Open Diagnostics with a pad attached | the pad row lists its name and id, so the app is not guessing |
| Settings, Export, save the zip | the file picker appears and the zip exists after saving |
| Fresh install, Settings, Restore, pick that zip | settings come back and the server list shows the old entries |
| Settings, Open guide | the page loads offline, language follows the app, and the two buttons reach Diagnostics and Discord |
