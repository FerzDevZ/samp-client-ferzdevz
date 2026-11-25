package com.ferzdevzgta.game.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.ferzdevzgta.game.R;
import com.ferzdevzgta.game.SAMP;
import com.ferzdevzgta.game.data.CacheManifest;
import com.ferzdevzgta.game.diag.Report;
import com.ferzdevzgta.game.log.CrashHandler;
import com.ferzdevzgta.game.log.SessionGuard;
import com.ferzdevzgta.game.servers.GameConfig;
import com.ferzdevzgta.game.servers.ServerEndpoint;
import com.ferzdevzgta.game.ui.dialog.DialogManager;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Answers "why did it not start" on the phone that failed.
 *
 * <p>Every value here is read from the device, not from a build script: the two native libraries,
 * the five JNI entry points the client calls, the data tree, the manifest and the connection
 * settings. The report can be copied or shared, which is the difference between a bug report that
 * can be acted on and one that cannot.
 */
public class DiagnosticsActivity extends AppCompatActivity {

    private static final String GTA_ROOT = "/storage/emulated/0/GTA";
    private static final int WALK_LIMIT = 20000;

    private final List<String> report = new ArrayList<>();

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
        root.addView(Ui.title(this, getString(R.string.diag_title)));
        Ui.gap(this, root, 6);
        root.addView(Ui.subtitle(this, getString(R.string.diag_intro)));

        int passed = 0;
        int total = 0;

        Ui.gap(this, root, 18);
        LinearLayout card = Ui.card(this);

        total++;
        String version = versionText();
        passed += addCheck(card, R.string.diag_check_app, version != null, orMissing(version));

        total++;
        String nativeLibs = nativeLibraryText();
        boolean nativeOk = nativeLibs.contains("2/2");
        passed += addCheck(card, R.string.diag_check_native, nativeOk, nativeLibs);

        total++;
        int entryPoints = jniEntryPoints();
        passed += addCheck(card, R.string.diag_check_jni, entryPoints == 5, entryPoints + "/5");

        total++;
        long[] dataTree = countTree(new File(GTA_ROOT, "data"));
        boolean dataOk = dataTree[0] > 0;
        passed += addCheck(card, R.string.diag_check_data, dataOk,
                dataTree[0] + " files, " + Report.bytes(dataTree[1]));

        total++;
        String manifest = manifestText();
        passed += addCheck(card, R.string.diag_check_manifest, !manifest.isEmpty(),
                manifest.isEmpty() ? getString(R.string.diag_missing) : manifest);

        total++;
        ServerEndpoint target = GameConfig.readServer();
        boolean targetOk = target != null && !"127.0.0.1".equals(target.host());
        passed += addCheck(card, R.string.diag_check_settings, targetOk,
                targetOk ? target.address() : getString(R.string.diag_none));

        File log = new File(GTA_ROOT, "logcat.txt");
        total++;
        passed += addCheck(card, R.string.diag_check_log, log.isFile(),
                log.isFile() ? Report.bytes(log.length()) : getString(R.string.diag_missing));

        // A native crash never reaches the Java handler, so the marker file is the only evidence
        // that the previous game session did not close on purpose.
        long unfinished = SessionGuard.unfinishedSince(new File(GameConfig.SAMP_DIR),
                android.os.Process.myPid());
        total++;
        boolean sessionOk = unfinished < 0L;
        passed += addCheck(card, R.string.diag_check_session, sessionOk,
                sessionOk ? getString(R.string.diag_session_clean)
                        : getString(R.string.diag_session_unfinished, timeText(unfinished)));

        String lastCrash = CrashHandler.lastCrash(log);
        total++;
        passed += addCheck(card, R.string.diag_check_crash, lastCrash.isEmpty(),
                lastCrash.isEmpty() ? getString(R.string.diag_none) : firstLine(lastCrash, 2));

        root.addView(card, Ui.fill());

        Ui.gap(this, root, 14);
        root.addView(Ui.pill(this, Report.summary(passed, total),
                passed == total ? Ui.OK : Ui.WARN));

        Ui.gap(this, root, 18);
        LinearLayout actions = Ui.row(this);
        Button copy = Ui.button(this, getString(R.string.diag_copy), Ui.PRIMARY,
                R.drawable.ic_log);
        copy.setOnClickListener(v -> copyReport());
        actions.addView(copy, Ui.weight(this, 1f, 8f));
        Button share = Ui.button(this, getString(R.string.diag_share), Ui.SECONDARY,
                R.drawable.ic_log);
        share.setOnClickListener(v -> shareLog(log));
        actions.addView(share, Ui.weight(this, 1f, 0f));
        root.addView(actions, Ui.fill());

        // The report is copied before Discord opens: the player lands in the chat with the
        // evidence already on the clipboard, which is the difference between a usable report and
        // "it crashes".
        Ui.gap(this, root, 10);
        Button bug = Ui.button(this, getString(R.string.report_discord), Ui.SECONDARY,
                R.drawable.ic_report);
        bug.setOnClickListener(v -> reportToDiscord());
        root.addView(bug, Ui.fill());
        root.addView(Ui.label(this, getString(R.string.report_discord_note)), Ui.fill(8, this));

        if (!lastCrash.isEmpty()) {
            Ui.gap(this, root, 18);
            root.addView(Ui.section(this, getString(R.string.diag_crash_title)));
            Ui.gap(this, root, 8);
            LinearLayout crashCard = Ui.card(this);
            TextView crashText = Ui.mono(this, lastCrash);
            crashText.setTextColor(Ui.ERR);
            crashText.setTextIsSelectable(true);
            crashCard.addView(crashText, Ui.fill());
            root.addView(crashCard, Ui.fill());
        }

        Ui.gap(this, root, 18);
        root.addView(Ui.section(this, getString(R.string.diag_log_title)));
        Ui.gap(this, root, 8);
        TextView tail = Ui.mono(this, tailText(log));
        tail.setTextIsSelectable(true);
        LinearLayout logCard = Ui.card(this);
        logCard.addView(tail, Ui.fill());
        root.addView(logCard, Ui.fill());

        return Ui.scroll(this, root);
    }

    private int addCheck(LinearLayout card, int labelRes, boolean ok, String value) {
        String label = getString(labelRes);
        LinearLayout row = Ui.statusRow(this, label, value, ok ? Ui.OK : Ui.WARN);
        card.addView(row, Ui.fill());
        report.add(Report.block(label, ok, value));
        return ok ? 1 : 0;
    }

    private String orMissing(String value) {
        return value == null ? getString(R.string.diag_missing) : value;
    }

    /** versionName and versionCode, which the phone actually installed. */
    private String versionText() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + code + ")";
        } catch (Exception e) {
            Log.w("Diagnostics", "version: " + e.getMessage());
            return null;
        }
    }

    /**
     * The two libraries the client loads, read from the directory Android actually extracted them
     * into. A missing arm64 build shows up here and nowhere else.
     */
    private String nativeLibraryText() {
        File dir = new File(getApplicationInfo().nativeLibraryDir);
        int found = 0;
        StringBuilder names = new StringBuilder();
        for (String name : new String[]{"libGame.so", "libsamp.so"}) {
            File candidate = new File(dir, name);
            if (candidate.isFile()) {
                found++;
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(name).append(" ").append(Report.bytes(candidate.length()));
            }
        }
        return found + "/2 " + names;
    }

    /**
     * Counts the native methods declared by the three classes that own them. A declaration
     * without a symbol is not a proof of anything, so the number is reported as declared, and the
     * real proof is the game starting.
     */
    private int jniEntryPoints() {
        int count = 0;
        for (Class<?> owner : new Class<?>[]{SAMP.class, DialogManager.class, AttachEdit.class}) {
            for (Method method : owner.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isNative(method.getModifiers())) {
                    count++;
                }
            }
        }
        return count;
    }

    /** File count and total size under a directory, bounded so a wrong path cannot hang. */
    private long[] countTree(File dir) {
        long[] result = new long[]{0, 0};
        if (dir == null || !dir.isDirectory()) {
            return result;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return result;
        }
        for (File child : children) {
            if (result[0] >= WALK_LIMIT) {
                break;
            }
            if (child.isDirectory()) {
                long[] inner = countTree(child);
                result[0] += inner[0];
                result[1] += inner[1];
            } else {
                result[0]++;
                result[1] += child.length();
            }
        }
        return result;
    }

    /** "lite, 1234 entries" when either manifest is present. */
    private String manifestText() {
        for (String variant : new String[]{"lite", "full"}) {
            File file = new File(GTA_ROOT, "manifest-" + variant + ".json");
            if (!file.isFile()) {
                continue;
            }
            try {
                CacheManifest manifest = CacheManifest.parse(
                        new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                return variant + ", " + manifest.entries().size() + " entries, "
                        + Report.bytes(manifest.totalBytes());
            } catch (Exception e) {
                return variant + ", " + getString(R.string.diag_unreadable);
            }
        }
        return "";
    }

    /** The first {@code count} lines of a block, for the one-line status row. */
    private String firstLine(String block, int count) {
        String[] lines = block.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(count, lines.length); i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(lines[i].trim());
        }
        return out.toString();
    }

    private String tailText(File log) {
        if (!log.isFile()) {
            return getString(R.string.diag_none);
        }
        try {
            byte[] all = Files.readAllBytes(log.toPath());
            String text = new String(all, StandardCharsets.UTF_8);
            return Report.tail(text, 20);
        } catch (Exception e) {
            return getString(R.string.diag_unreadable);
        }
    }


    /** "11 Feb 14:22", the phone's own zone, because a start time is read by a person. */
    private String timeText(long epochMillis) {
        return new java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(epochMillis));
    }

    private void reportToDiscord() {
        copyReport();
        if (!Ui.openUrl(this, Community.INVITE)) {
            showNote(getString(R.string.report_discord_missing));
        }
    }

    private void showNote(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }

    private void copyReport() {
        try {
            StringBuilder text = new StringBuilder();
            text.append(getString(R.string.diag_title)).append('\n');
            for (String line : report) {
                text.append(line).append('\n');
            }
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("report", text.toString()));
            }
            Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("Diagnostics", "copy: " + e.getMessage());
        }
    }

    private void shareLog(File log) {
        try {
            if (log == null || !log.isFile()) {
                Toast.makeText(this, R.string.diag_no_log, Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", log);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.diag_share)));
        } catch (Exception e) {
            Log.e("Diagnostics", "share: " + e.getMessage());
        }
    }
}
