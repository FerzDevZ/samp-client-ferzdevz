#!/usr/bin/env python3
"""Gate for the in-game HUD editor.

Checks that the editor is wired, that it writes where the engine reads, and that it cannot
silently overwrite an engine key it does not own. Every check is a text match on files that
must exist, so a half-wired editor fails here instead of on a phone.

Exit 0 means: the editor has an entry point, is loaded before the first frame, drawn inside the
ImGui frame, saves to its own section, and the section name does not collide with `[gui]`.
"""

import os
import re
import sys
from pathlib import Path

# HUD_CHECK_ROOT exists so the self test can point the same checks at a deliberately broken copy
# and prove the gate fails. Without it the gate would only ever be tested on code that passes.
ROOT = Path(os.environ.get("HUD_CHECK_ROOT") or Path(__file__).resolve().parent.parent)
CPP = ROOT / "client/app/src/main/cpp/samp"
EDITOR = CPP / "gui/hudeditor.cpp"
EDITOR_H = CPP / "gui/hudeditor.h"
GUI = CPP / "gui/gui.cpp"
PANEL = CPP / "gui/samp_widgets/buttonpanel.cpp"
SETTINGS = CPP / "settings.cpp"

FAIL = []


def check(condition, message):
    if condition:
        print("ok   " + message)
    else:
        FAIL.append(message)
        print("FAIL " + message)


def text(path):
    return path.read_text(encoding="utf-8") if path.exists() else ""


editor = text(EDITOR)
gui = text(GUI)
panel = text(PANEL)
settings = text(SETTINGS)

check(bool(editor), "hudeditor.cpp exists")
check(bool(text(EDITOR_H)), "hudeditor.h exists")

# 1. the editor has to be reachable from the game, not only from code
check("HudEditor" in panel and "HUD" in panel, "button panel opens the editor")
check("HudEditor::Toggle" in panel, "panel calls Toggle, not a second code path")

# 2. positions must apply before the first frame
load_at = gui.find("HudEditor::Load()")
init_at = gui.find("bool UI::initialize()")
render_at = gui.find("void UI::render()")
check(load_at > init_at > -1 and load_at < render_at, "Load runs at the end of UI::initialize")

# 3. drawing must happen inside the ImGui frame (drawList), not after Render
draw_at = gui.find("HudEditor::Draw()")
drawlist_at = gui.find("void UI::drawList()")
touch_at = gui.find("bool UI::OnTouchEvent")
check(drawlist_at < draw_at < touch_at, "Draw is called from UI::drawList, inside the frame")

# 4. touches belong to the editor while it is open
check("HudEditor::Visible()" in gui[touch_at:], "OnTouchEvent defers to the editor")

# 5. one ini path, the same one the engine loads
paths = set(re.findall(r'"(/storage/[^"]*settings\.ini)"', editor + settings))
check(len(paths) == 1, "editor and engine read the same settings.ini (%d path)" % len(paths))

# 6. saving must go to the editor's own section, never to [gui]
section = re.search(r'kSection\s*=\s*"([a-z_]+)"', editor)
check(bool(section), "editor declares a section name")
if section:
    check(section.group(1) != "gui", "editor section is not [gui]")
    check('SetValue(kSection,' in editor, "Save writes through kSection")
    check('[gui]' not in editor and '"gui"' not in editor, "editor never names the gui section")

# 7. every widget the editor offers is a real getter on UI
getters = re.findall(r'pUI->(\w+)\(\)', editor)
check(len(getters) >= 3, "editor targets at least three widgets")
for name in getters:
    check(("%s()" % name) in text(CPP / "gui/gui.h"), "ui/gui.h exposes %s()" % name)

# 8. the parser and the writer share one format
check("ParsePair" in editor and "FormatPair" in editor, "one pair format for read and write")
check('"%.1f,%.1f"' in editor, "FormatPair uses the same separator ParsePair splits on")
check("std::stof" in editor, "ParsePair parses numbers instead of copying text")

# 9. a drag cannot push a widget off the screen
check("clampToScreen" in editor, "drags are clamped to the display")
check("DisplaySize" in editor, "clamping uses the real display size")

# 10. closing the editor restores what was visible before
check("g_savedVisible" in editor, "visibility is restored on close")

# 11. the feature is documented where a newcomer looks
for doc in ("docs/ARCHITECTURE.md", "docs/id/ARCHITECTURE.md"):
    body = text(ROOT / doc)
    check("hudeditor" in body.lower(), "%s names gui/hudeditor.cpp" % doc)

sys.exit(1 if FAIL else 0)
