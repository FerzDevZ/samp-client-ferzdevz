#include "main.h"
#include "gui/gui.h"
#include "gui/hudeditor.h"

#include "vendor/imgui/imgui.h"
#include "vendor/imgui/imgui_internal.h"
#include "vendor/inih/cpp/INIReader.h"
#include "vendor/SimpleIni/SimpleIni.h"

extern UI* pUI;

namespace {

constexpr const char* kIni = "/storage/emulated/0/GTA/SAMP/settings.ini";
constexpr const char* kSection = "hud";

// The palette the launcher uses, so the editor looks like the app that opened it.
constexpr ImU32 kAccent = IM_COL32(239, 125, 43, 230);
constexpr ImU32 kIdle = IM_COL32(243, 238, 230, 150);
constexpr ImU32 kWash = IM_COL32(11, 17, 27, 170);

struct Target {
    const char* key;    // written as [hud] key = "x,y"
    const char* label;  // shown above the handle
    Widget* widget;
    ImVec2 fallback;    // the position UI::initialize gives the widget
};

bool g_open = false;
int g_dragged = -1;
// Restored on close so the editor does not leave the chat box or the button panel hidden just
// because they were off when it opened.
bool g_savedVisible[3] = {false, false, false};

// Built once pUI exists. Chat and the button panel start hidden until a server sends a message or
// the player opens the panel, so the editor has to force them visible while it is open: dragging a
// rectangle you cannot see is not an editor.
Target g_targets[3];
int g_targetCount = 0;

void buildTargets()
{
    if (!pUI || g_targetCount > 0) return;

    g_targets[0] = {"chat", "Chat", pUI->chat(), UISettings::chatPos()};
    g_targets[1] = {"buttons", "Panel tombol", pUI->buttonpanel(), UISettings::buttonPanelPos()};
    g_targets[2] = {"voice", "Tombol voice", pUI->voicebutton(), UISettings::buttonVoicePos()};
    g_targetCount = 3;
}

Widget* visibleWidget(const Target& target)
{
    if (!target.widget) return nullptr;
    return target.widget;
}

void clampToScreen(ImVec2* pos, const ImVec2& size)
{
    ImGuiIO& io = ImGui::GetIO();
    float maxX = io.DisplaySize.x - size.x;
    float maxY = io.DisplaySize.y - size.y;
    if (maxX < 0.0f) maxX = 0.0f;
    if (maxY < 0.0f) maxY = 0.0f;
    if (pos->x < 0.0f) pos->x = 0.0f;
    if (pos->y < 0.0f) pos->y = 0.0f;
    if (pos->x > maxX) pos->x = maxX;
    if (pos->y > maxY) pos->y = maxY;
}

} // namespace

const char* HudEditor::SettingsPath()
{
    return kIni;
}

bool HudEditor::ParsePair(const std::string& text, float* x, float* y)
{
    if (!x || !y) return false;
    size_t comma = text.find(',');
    if (comma == std::string::npos) return false;
    try {
        float parsedX = std::stof(text.substr(0, comma));
        float parsedY = std::stof(text.substr(comma + 1));
        if (!(parsedX == parsedX) || !(parsedY == parsedY)) return false; // NaN
        *x = parsedX;
        *y = parsedY;
        return true;
    } catch (...) {
        return false;
    }
}

std::string HudEditor::FormatPair(float x, float y)
{
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "%.1f,%.1f", x, y);
    return std::string(buffer);
}

void HudEditor::Load()
{
    buildTargets();
    if (g_targetCount == 0) return;

    INIReader reader(kIni);
    if (reader.ParseError() < 0) {
        Log("HudEditor: no readable settings.ini, keeping built-in positions");
        return;
    }
    for (int i = 0; i < g_targetCount; i++) {
        Target& target = g_targets[i];
        if (!visibleWidget(target)) continue;
        std::string raw = reader.Get(kSection, target.key, "");
        float x = 0.0f;
        float y = 0.0f;
        if (ParsePair(raw, &x, &y)) {
            target.widget->setPosition(ImVec2(x, y));
        }
    }
}

bool HudEditor::Save()
{
    buildTargets();
    if (g_targetCount == 0) return false;

    CSimpleIniA ini;
    ini.SetUnicode(false);
    ini.LoadFile(kIni);

    for (int i = 0; i < g_targetCount; i++) {
        Target& target = g_targets[i];
        if (!visibleWidget(target)) continue;
        ImVec2 pos = target.widget->position();
        ini.SetValue(kSection, target.key, FormatPair(pos.x, pos.y).c_str());
    }
    SI_Error rc = ini.SaveFile(kIni);
    if (rc < 0) {
        Log("HudEditor: cannot write %s", kIni);
        return false;
    }
    Log("HudEditor: positions saved");
    return true;
}

void HudEditor::Reset()
{
    buildTargets();
    for (int i = 0; i < g_targetCount; i++) {
        Target& target = g_targets[i];
        if (visibleWidget(target)) {
            target.widget->setPosition(target.fallback);
        }
    }
}

void HudEditor::Toggle()
{
    buildTargets();
    g_open = !g_open;
    g_dragged = -1;
    // The widgets are normally hidden until the game needs them. Forcing them on while the editor
    // is open is the only way to place something that is not on screen yet.
    for (int i = 0; i < g_targetCount; i++) {
        Widget* widget = g_targets[i].widget;
        if (!widget) continue;
        if (g_open) {
            g_savedVisible[i] = widget->visible();
            widget->setVisible(true);
        } else {
            widget->setVisible(g_savedVisible[i]);
        }
    }
    if (!g_open) Save();
}

bool HudEditor::Visible()
{
    return g_open;
}

void HudEditor::Close()
{
    if (g_open) Toggle();
}

void HudEditor::Draw()
{
    if (!g_open) return;
    buildTargets();
    if (g_targetCount == 0) return;

    ImGuiIO& io = ImGui::GetIO();
    ImDrawList* list = ImGui::GetForegroundDrawList();

    list->AddRectFilled(ImVec2(0, 0), io.DisplaySize, kWash);

    ImGui::SetNextWindowPos(ImVec2(0, 0));
    ImGui::SetNextWindowSize(io.DisplaySize);
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(0, 0));
    ImGui::Begin("##hud_editor", nullptr,
                 ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
                 ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_NoBackground |
                 ImGuiWindowFlags_NoBringToFrontOnFocus);

    for (int i = 0; i < g_targetCount; i++) {
        Target& target = g_targets[i];
        Widget* widget = visibleWidget(target);
        if (!widget) continue;

        ImVec2 pos = widget->position();
        ImVec2 size = widget->size();
        if (size.x < 40.0f) size.x = 40.0f;
        if (size.y < 28.0f) size.y = 28.0f;

        ImGui::PushID(i);
        ImGui::SetCursorScreenPos(pos);
        ImGui::InvisibleButton("handle", size);
        bool active = ImGui::IsItemActive();
        if (active) {
            ImVec2 next = ImVec2(pos.x + io.MouseDelta.x, pos.y + io.MouseDelta.y);
            clampToScreen(&next, size);
            widget->setPosition(next);
            g_dragged = i;
            pos = next;
        }

        bool hot = ImGui::IsItemHovered();
        list->AddRectFilled(pos, ImVec2(pos.x + size.x, pos.y + size.y),
                           IM_COL32(31, 44, 69, 210), 8.0f);
        list->AddRect(pos, ImVec2(pos.x + size.x, pos.y + size.y),
                      (active || hot) ? kAccent : kIdle, 8.0f, 0, 2.0f);
        list->AddText(ImVec2(pos.x + 8.0f, pos.y + 4.0f), kAccent, target.label);
        ImGui::PopID();
    }

    ImGui::End();
    ImGui::PopStyleVar();

    // Controls sit in the top strip so a thumb already on the screen can reach them without
    // covering the widget being dragged.
    ImGui::SetNextWindowPos(ImVec2(12.0f, 12.0f));
    ImGui::SetNextWindowBgAlpha(0.92f);
    ImGui::Begin("##hud_editor_bar", nullptr,
                 ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize |
                 ImGuiWindowFlags_AlwaysAutoResize | ImGuiWindowFlags_NoMove);
    if (ImGui::Button("Simpan", ImVec2(120.0f, 44.0f))) {
        Save();
        Close();
    }
    ImGui::SameLine();
    if (ImGui::Button("Reset", ImVec2(110.0f, 44.0f))) {
        Reset();
    }
    ImGui::SameLine();
    if (ImGui::Button("Tutup", ImVec2(110.0f, 44.0f))) {
        Close();
    }
    ImGui::End();
}
