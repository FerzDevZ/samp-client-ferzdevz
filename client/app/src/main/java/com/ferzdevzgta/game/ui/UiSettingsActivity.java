package com.ferzdevzgta.game.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.ferzdevzgta.game.R;
import com.ferzdevzgta.game.settings.IniFile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Edits the keys the native client owns in {@code SAMP/settings.ini}: chat box,
 * font size, frame limit and the GUI toggles that already exist in
 * {@code cpp/samp/settings.cpp}. Engine-drawn touch buttons are not here: the game
 * engine has its own adjustable-HUD editor (CAdjustableHUD), so adding a second
 * one would fight it.
 */
public class UiSettingsActivity extends AppCompatActivity {

    private static final String GTA = "/storage/emulated/0/GTA";
    private static final String INI = GTA + "/SAMP/settings.ini";

    private static final String[][] NUMERIC = {
            {"gui", "ChatPosX", "325"},
            {"gui", "ChatPosY", "25"},
            {"gui", "ChatSizeX", "1150"},
            {"gui", "ChatSizeY", "220"},
            {"gui", "ChatMaxMessages", "6"},
            {"gui", "FontSize", "30"},
            {"gui", "FontOutline", "2"},
            {"gui", "FPSLimit", "60"},
    };

    // Only keys settings.cpp actually reads; a key the engine ignores is a lie.
    private static final String[][] FLAGS = {
            {"gui", "firstperson", "true"},
            {"gui", "fps", "false"},
            {"gui", "radarrect", "false"},
            {"gui", "skybox", "false"},
            {"gui", "hparmourtext", "false"},
            {"gui", "androidkeyboard", "false"},
            {"gui", "Dialog", "true"},
    };

    private final Map<String, EditText> numericFields = new LinkedHashMap<>();
    private final Map<String, CheckBox> flagFields = new LinkedHashMap<>();
    private IniFile ini;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.screen(this);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24));

        root.addView(Ui.title(this, getString(R.string.settings_title)));
        Ui.gap(this, root, 6);
        root.addView(Ui.body(this, getString(R.string.settings_intro)));
        Ui.gap(this, root, 14);

        File f = new File(INI);
        if (!f.isFile()) {
            LinearLayout warn = Ui.card(this);
            TextView t = Ui.body(this, getString(R.string.settings_no_ini, INI));
            t.setTextColor(Ui.ERR);
            warn.addView(t);
            root.addView(warn);
            Ui.gap(this, root, 12);
            setContentView(scroll);
            return;
        }

        try {
            ini = IniFile.read(f);
        } catch (IOException ex) {
            LinearLayout warn = Ui.card(this);
            TextView t = Ui.body(this, ex.getMessage() == null ? "read error" : ex.getMessage());
            t.setTextColor(Ui.ERR);
            warn.addView(t);
            root.addView(warn);
            setContentView(scroll);
            return;
        }

        LinearLayout numbers = Ui.card(this);
        numbers.addView(Ui.fieldLabel(this, getString(R.string.settings_section_layout)));
        Ui.gap(this, numbers, 8);
        for (String[] row : NUMERIC) {
            addNumeric(numbers, row[0], row[1], row[2]);
        }
        root.addView(numbers);
        Ui.gap(this, root, 12);

        LinearLayout flags = Ui.card(this);
        flags.addView(Ui.fieldLabel(this, getString(R.string.settings_section_toggles)));
        Ui.gap(this, flags, 8);
        for (String[] row : FLAGS) {
            addFlag(flags, row[0], row[1], row[2]);
        }
        root.addView(flags);
        Ui.gap(this, root, 12);

        Button save = Ui.button(this, getString(R.string.settings_save), true);
        root.addView(save);
        Ui.gap(this, root, 8);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button reset = Ui.button(this, getString(R.string.settings_reset), false);
        Button share = Ui.button(this, getString(R.string.data_action_share_log), false);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.setMargins(0, 0, Ui.dp(this, 8), 0);
        reset.setLayoutParams(half);
        share.setLayoutParams(half);
        row.addView(reset);
        row.addView(share);
        root.addView(row);

        Ui.gap(this, root, 12);
        TextView note = Ui.body(this, getString(R.string.settings_note_restart));
        root.addView(note);

        scroll.addView(root);
        setContentView(scroll);

        save.setOnClickListener(v -> save());
        reset.setOnClickListener(v -> reset());
        share.setOnClickListener(v -> shareLog());
    }

    private void addNumeric(LinearLayout parent, String section, String key, String fallback) {
        Ui.gap(this, parent, 6);
        parent.addView(Ui.fieldLabel(this, key));
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText(ini.get(section, key, fallback));
        field.setTextColor(Ui.TEXT);
        field.setTextSize(14f);
        field.setBackgroundColor(Ui.BG);
        field.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        parent.addView(field, Ui.fill());
        numericFields.put(section + "." + key, field);
    }

    private void addFlag(LinearLayout parent, String section, String key, String fallback) {
        CheckBox box = new CheckBox(this);
        box.setText(key);
        box.setTextColor(Ui.TEXT);
        box.setTextSize(14f);
        box.setChecked(Boolean.parseBoolean(ini.get(section, key, fallback)));
        parent.addView(box);
        flagFields.put(section + "." + key, box);
    }

    private void save() {
        int written = 0;
        for (Map.Entry<String, EditText> e : numericFields.entrySet()) {
            String[] parts = e.getKey().split("\\.");
            String raw = e.getValue().getText().toString().trim();
            if (!isNumber(raw)) {
                Toast.makeText(this, getString(R.string.settings_invalid, parts[1]), Toast.LENGTH_LONG).show();
                e.getValue().requestFocus();
                return;
            }
            ini.set(parts[0], parts[1], raw);
            written++;
        }
        for (Map.Entry<String, CheckBox> e : flagFields.entrySet()) {
            String[] parts = e.getKey().split("\\.");
            ini.set(parts[0], parts[1], e.getValue().isChecked() ? "true" : "false");
            written++;
        }
        try {
            ini.write(new File(INI));
            Toast.makeText(this, getString(R.string.settings_saved, written), Toast.LENGTH_LONG).show();
        } catch (IOException ex) {
            Toast.makeText(this, ex.getMessage() == null ? "write failed" : ex.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void reset() {
        for (String[] row : NUMERIC) {
            EditText field = numericFields.get(row[0] + "." + row[1]);
            if (field != null) {
                field.setText(row[2]);
            }
        }
        for (String[] row : FLAGS) {
            CheckBox box = flagFields.get(row[0] + "." + row[1]);
            if (box != null) {
                box.setChecked(Boolean.parseBoolean(row[2]));
            }
        }
        Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_SHORT).show();
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty() || s.length() > 12) {
            return false;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void shareLog() {
        File log = new File(GTA, "logcat.txt");
        if (!log.isFile()) {
            Toast.makeText(this, R.string.data_no_log, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", log);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_TEXT, String.format(Locale.ROOT, "android %d | %s %s",
                    android.os.Build.VERSION.SDK_INT,
                    android.os.Build.MANUFACTURER, android.os.Build.MODEL));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.data_action_share_log)));
        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage() == null ? "share failed" : ex.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
