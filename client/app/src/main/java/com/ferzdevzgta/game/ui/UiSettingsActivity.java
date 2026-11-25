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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.ferzdevzgta.game.BuildConfig;
import com.ferzdevzgta.game.R;
import com.ferzdevzgta.game.servers.GameConfig;
import com.ferzdevzgta.game.settings.IniFile;
import com.ferzdevzgta.game.settings.ProfileBackup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Edits the keys the native client owns in {@code SAMP/settings.ini}: chat box, font size,
 * frame limit and the GUI toggles that already exist in {@code cpp/samp/settings.cpp}, plus the
 * screen language, which is stored by the app rather than by the engine.
 *
 * Engine-drawn touch buttons are not here: the game engine has its own adjustable-HUD editor
 * (CAdjustableHUD), so adding a second one would fight it.
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

    private static final int[] NUMERIC_LABELS = {
            R.string.settings_field_chat_x,
            R.string.settings_field_chat_y,
            R.string.settings_field_chat_w,
            R.string.settings_field_chat_h,
            R.string.settings_field_chat_messages,
            R.string.settings_field_font_size,
            R.string.settings_field_font_outline,
            R.string.settings_field_fps_limit,
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

    private static final int[] FLAG_LABELS = {
            R.string.settings_flag_firstperson,
            R.string.settings_flag_fps,
            R.string.settings_flag_radar,
            R.string.settings_flag_skybox,
            R.string.settings_flag_hparmour,
            R.string.settings_flag_keyboard,
            R.string.settings_flag_dialog,
    };

    private final Map<String, EditText> numericFields = new LinkedHashMap<>();
    private final Map<String, CheckBox> flagFields = new LinkedHashMap<>();
    private IniFile ini;

    // Export goes through the system file picker so the archive lands wherever the player keeps
    // backups, not deep inside the app's cache where nobody would find it.
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            this::exportTo);

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::importFrom);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.paintWindow(this);
        setContentView(buildUi());
        Ui.applyInsets(this, findViewById(android.R.id.content));
    }

    private ScrollView buildUi() {
        LinearLayout root = Ui.column(this);

        root.addView(Ui.eyebrow(this, getString(R.string.app_name)));
        Ui.gap(this, root, 6);
        root.addView(Ui.title(this, getString(R.string.settings_title)));
        Ui.gap(this, root, 8);
        root.addView(Ui.body(this, getString(R.string.settings_intro)));

        // Language first: it is the only setting that changes this screen itself.
        Ui.gap(this, root, 20);
        LinearLayout languageCard = Ui.card(this);
        languageCard.addView(Ui.section(this, getString(R.string.settings_section_language)));
        languageCard.addView(Ui.languageSwitch(this), Ui.fill());
        Ui.gap(this, languageCard, 10);
        languageCard.addView(Ui.label(this, getString(R.string.language_note)));
        root.addView(languageCard, Ui.fill());

        File file = new File(INI);
        if (!file.isFile()) {
            Ui.gap(this, root, 12);
            root.addView(warning(getString(R.string.settings_no_ini, INI)), Ui.fill());
            return Ui.scroll(this, root);
        }
        try {
            ini = IniFile.read(file);
        } catch (IOException ex) {
            Ui.gap(this, root, 12);
            root.addView(warning(ex.getMessage() == null ? "read error" : ex.getMessage()),
                    Ui.fill());
            return Ui.scroll(this, root);
        }

        Ui.gap(this, root, 12);
        LinearLayout numbers = Ui.card(this);
        numbers.addView(Ui.section(this, getString(R.string.settings_section_layout)));
        for (int i = 0; i < NUMERIC.length; i++) {
            addNumeric(numbers, NUMERIC[i][0], NUMERIC[i][1], NUMERIC[i][2], NUMERIC_LABELS[i]);
        }
        root.addView(numbers, Ui.fill());

        Ui.gap(this, root, 12);
        LinearLayout flags = Ui.card(this);
        flags.addView(Ui.section(this, getString(R.string.settings_section_toggles)));
        for (int i = 0; i < FLAGS.length; i++) {
            addFlag(flags, FLAGS[i][0], FLAGS[i][1], FLAGS[i][2], FLAG_LABELS[i]);
        }
        root.addView(flags, Ui.fill());

        Ui.gap(this, root, 20);
        Button save = Ui.button(this, getString(R.string.settings_save), Ui.PRIMARY, R.drawable.ic_check);
        save.setOnClickListener(v -> save());
        root.addView(save, Ui.fill());

        Ui.gap(this, root, 8);
        LinearLayout row = Ui.row(this);
        Button reset = Ui.button(this, getString(R.string.settings_reset), Ui.SECONDARY);
        Button share = Ui.button(this, getString(R.string.data_action_share_log), Ui.SECONDARY, R.drawable.ic_log);
        reset.setOnClickListener(v -> reset());
        share.setOnClickListener(v -> shareLog());
        row.addView(reset, Ui.weight(this, 1f, 8f));
        row.addView(share, Ui.weight(this, 1f, 0f));
        root.addView(row, Ui.fill());

        Ui.gap(this, root, 14);
        LinearLayout backup = Ui.card(this);
        backup.addView(Ui.section(this, getString(R.string.profile_section)));
        Ui.gap(this, backup, 8);
        LinearLayout backupRow = Ui.row(this);
        Button export = Ui.button(this, getString(R.string.profile_export), Ui.SECONDARY);
        Button restore = Ui.button(this, getString(R.string.profile_import), Ui.GHOST);
        export.setOnClickListener(v -> exportLauncher.launch(
                ProfileBackup.fileName(BuildConfig.VERSION_NAME, System.currentTimeMillis())));
        restore.setOnClickListener(v -> importLauncher.launch(new String[]{"application/zip"}));
        backupRow.addView(export, Ui.weight(this, 1f, 8f));
        backupRow.addView(restore, Ui.weight(this, 1f, 0f));
        backup.addView(backupRow, Ui.fill());
        backup.addView(Ui.label(this, getString(R.string.profile_note)), Ui.fill(8, this));
        root.addView(backup, Ui.fill());

        Ui.gap(this, root, 14);
        Button diagnostics = Ui.button(this, getString(R.string.diag_open), Ui.GHOST,
                R.drawable.ic_warn);
        diagnostics.setOnClickListener(v -> startActivity(
                new android.content.Intent(this, DiagnosticsActivity.class)));
        root.addView(diagnostics, Ui.fill());

        Ui.gap(this, root, 18);
        root.addView(Ui.section(this, getString(R.string.settings_section_support)));
        Ui.gap(this, root, 8);
        Button guide = Ui.button(this, getString(R.string.guide_open), Ui.GHOST,
                R.drawable.ic_report);
        guide.setOnClickListener(v -> startActivity(
                new android.content.Intent(this, WebPanelActivity.class)));
        root.addView(guide, Ui.fill());
        Ui.gap(this, root, 8);
        Button discord = Ui.button(this, getString(R.string.report_discord), Ui.SECONDARY,
                R.drawable.ic_report);
        discord.setOnClickListener(v -> {
            if (!Ui.openUrl(this, Community.INVITE)) {
                android.widget.Toast.makeText(this, getString(R.string.report_discord_missing),
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
        root.addView(discord, Ui.fill());
        root.addView(Ui.label(this, getString(R.string.report_discord_note)), Ui.fill(8, this));

        Ui.gap(this, root, 14);
        root.addView(Ui.label(this, getString(R.string.settings_note_restart)));

        return Ui.scroll(this, root);
    }

    private void exportTo(android.net.Uri target) {
        if (target == null) {
            return;
        }
        File temp = new File(getCacheDir(), "profile.zip");
        try {
            ProfileBackup.write(GameConfig.settingsFile(), GameConfig.serversFile(), temp,
                    getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
            try (InputStream in = new java.io.FileInputStream(temp);
                 OutputStream out = getContentResolver().openOutputStream(target)) {
                if (out == null) {
                    throw new IOException("picker returned no stream");
                }
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            toast(getString(R.string.profile_export_ok));
        } catch (IOException | SecurityException ex) {
            toast(getString(R.string.profile_export_fail, String.valueOf(ex.getMessage())));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void importFrom(android.net.Uri source) {
        if (source == null) {
            return;
        }
        File temp = new File(getCacheDir(), "profile-import.zip");
        try {
            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(temp)) {
                if (in == null) {
                    throw new IOException("picker returned no stream");
                }
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            ProfileBackup.Summary summary = ProfileBackup.read(temp,
                    GameConfig.settingsFile(), GameConfig.serversFile());
            toast(summary.servers
                    ? getString(R.string.profile_import_ok, summary.serversCount)
                    : getString(R.string.profile_import_partial));
            // Rebuild the form from the file that was just written, not from the old copy.
            recreate();
        } catch (IOException | SecurityException ex) {
            toast(getString(R.string.profile_import_fail, String.valueOf(ex.getMessage())));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }

    private LinearLayout warning(String message) {
        LinearLayout card = Ui.card(this);
        TextView text = Ui.body(this, message);
        text.setTextColor(Ui.ERR);
        card.addView(text);
        return card;
    }

    private void addNumeric(LinearLayout parent, String section, String key, String fallback,
                            int labelRes) {
        Ui.gap(this, parent, 12);
        LinearLayout labelRow = Ui.row(this);
        labelRow.addView(Ui.label(this, getString(labelRes)),
                Ui.weight(this, 1f, 8f));
        labelRow.addView(Ui.mono(this, key));
        parent.addView(labelRow, Ui.fill());

        EditText field = Ui.input(this, fallback,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText(ini.get(section, key, fallback));
        parent.addView(field, Ui.fill(6, this));
        numericFields.put(section + "." + key, field);
    }

    private void addFlag(LinearLayout parent, String section, String key, String fallback,
                         int labelRes) {
        CheckBox box = Ui.toggle(this, getString(labelRes));
        box.setChecked(Boolean.parseBoolean(ini.get(section, key, fallback)));
        parent.addView(box, Ui.fill());
        flagFields.put(section + "." + key, box);
    }

    private void save() {
        int written = 0;
        for (Map.Entry<String, EditText> e : numericFields.entrySet()) {
            String[] parts = e.getKey().split("\\.");
            String raw = e.getValue().getText().toString().trim();
            if (!isNumber(raw)) {
                Toast.makeText(this, getString(R.string.settings_invalid, parts[1]),
                        Toast.LENGTH_LONG).show();
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
