#pragma once

#include <string>

/**
 * Drag-to-place editor for the client's own on-screen widgets.
 *
 * The engine ships an adjustable-HUD class for the GTA touch buttons, but it is a hook stub with
 * every body commented out, and the SA-MP widgets drawn by this client (chat box, button panel,
 * voice button) never had a position key that anything applied. This editor covers those: it
 * drags them where the player wants and writes `[hud]` keys that are read at every start.
 *
 * It is drawn with the same ImGui layer the widgets use, so no new coordinates are invented and
 * the rectangle on screen is the rectangle the widget occupies.
 */
class HudEditor {
public:
    /** Applies `[hud]` from settings.ini to the widgets, falling back to their built-in spots. */
    static void Load();

    /** Called every frame; does nothing unless the editor is open. */
    static void Draw();

    static void Toggle();
    static bool Visible();
    static void Close();

    /** Writes the current positions back to settings.ini. */
    static bool Save();

    /** Puts every widget back to the position the code initializes it with. */
    static void Reset();

    /** Path of the file the editor writes, kept in one place for the tests and docs. */
    static const char* SettingsPath();

    /** Parses one `x,y` pair; exposed so the reader and the writer agree on the format. */
    static bool ParsePair(const std::string& text, float* x, float* y);

    static std::string FormatPair(float x, float y);
};
