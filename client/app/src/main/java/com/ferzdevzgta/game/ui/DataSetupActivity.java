package com.ferzdevzgta.game.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.ferzdevzgta.game.R;
import com.ferzdevzgta.game.data.CacheDownloader;
import com.ferzdevzgta.game.data.CacheManifest;
import com.ferzdevzgta.game.servers.GameConfig;
import com.ferzdevzgta.game.servers.ServerEndpoint;
import com.ferzdevzgta.game.settings.IniFile;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Replaces Play Asset Delivery. It works for a sideloaded APK, which the old
 * DownloaderActivity (Play Core) never did.
 *
 * Flow: read manifest-<variant>.json next to the game data -> verify the tree on
 * disk -> download only what is missing or wrong -> verify again. A player who
 * has no data at all and one who has a half-copied directory go through the same
 * screen.
 */
public class DataSetupActivity extends AppCompatActivity {

    private static final String GTA = "/storage/emulated/0/GTA";
    private static final String TAG = "DataSetup";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private String variant = "full";
    private TextView statusText;
    private TextView detail;
    private LinearLayout pillSlot;
    private ProgressBar progress;
    private Button syncButton;
    private LinearLayout variantSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.paintWindow(this);
        setContentView(buildUi());
        chooseVariant("full");
        verifyOnly();
    }

    private ScrollView buildUi() {
        LinearLayout root = Ui.column(this);

        root.addView(Ui.eyebrow(this, getString(R.string.app_name)));
        Ui.gap(this, root, 6);
        root.addView(Ui.title(this, getString(R.string.data_title)));
        Ui.gap(this, root, 8);
        root.addView(Ui.body(this, getString(R.string.data_intro)));

        // Variant
        Ui.gap(this, root, 20);
        LinearLayout variantCard = Ui.card(this);
        variantCard.addView(Ui.section(this, getString(R.string.data_section_variant)));
        variantSwitch = Ui.segmented(this,
                new String[]{getString(R.string.data_variant_full), getString(R.string.data_variant_lite)},
                0,
                index -> chooseVariant(index == 0 ? "full" : "lite"));
        variantCard.addView(variantSwitch, Ui.fill());
        root.addView(variantCard, Ui.fill(0, this));

        // Status
        Ui.gap(this, root, 12);
        LinearLayout statusCard = Ui.card(this);
        LinearLayout statusHeader = Ui.row(this);
        TextView statusLabel = Ui.section(this, getString(R.string.data_section_status));
        statusLabel.setPadding(0, 0, 0, 0);
        statusHeader.addView(statusLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        pillSlot = new LinearLayout(this);
        pillSlot.setOrientation(LinearLayout.HORIZONTAL);
        statusHeader.addView(pillSlot);
        statusCard.addView(statusHeader);
        Ui.gap(this, statusCard, 8);
        statusText = Ui.body(this, getString(R.string.data_status_idle));
        statusCard.addView(statusText);
        progress = Ui.progress(this);
        statusCard.addView(progress);
        detail = Ui.mono(this, "");
        statusCard.addView(detail);
        root.addView(statusCard, Ui.fill());

        // Primary action
        Ui.gap(this, root, 16);
        syncButton = Ui.button(this, getString(R.string.data_action_sync), Ui.PRIMARY);
        root.addView(syncButton, Ui.fill());

        // Secondary actions, two per row so nothing is narrower than a thumb
        Ui.gap(this, root, 8);
        LinearLayout rowA = Ui.row(this);
        Button verifyButton = Ui.button(this, getString(R.string.data_action_verify), Ui.SECONDARY);
        Button logButton = Ui.button(this, getString(R.string.data_action_share_log), Ui.SECONDARY);
        rowA.addView(verifyButton, Ui.weight(this, 1f, 8f));
        rowA.addView(logButton, Ui.weight(this, 1f, 0f));
        root.addView(rowA, Ui.fill());

        Ui.gap(this, root, 8);
        LinearLayout rowB = Ui.row(this);
        Button serversButton = Ui.button(this, getString(R.string.home_action_server), Ui.SECONDARY);
        Button settingsButton = Ui.button(this, getString(R.string.home_action_settings), Ui.SECONDARY);
        rowB.addView(serversButton, Ui.weight(this, 1f, 8f));
        rowB.addView(settingsButton, Ui.weight(this, 1f, 0f));
        root.addView(rowB, Ui.fill());

        // Play
        Ui.gap(this, root, 20);
        Button playButton = Ui.button(this, getString(R.string.data_action_play), Ui.PRIMARY);
        root.addView(playButton, Ui.fill());

        // Language, reachable on the first run before any data exists
        Ui.gap(this, root, 20);
        LinearLayout languageCard = Ui.card(this);
        languageCard.addView(Ui.section(this, getString(R.string.settings_section_language)));
        languageCard.addView(Ui.languageSwitch(this), Ui.fill());
        Ui.gap(this, languageCard, 8);
        languageCard.addView(Ui.label(this, getString(R.string.language_note)));
        root.addView(languageCard, Ui.fill());

        verifyButton.setOnClickListener(v -> verifyOnly());
        logButton.setOnClickListener(v -> shareLog());
        serversButton.setOnClickListener(v ->
                startActivity(new Intent(this, ServerBrowserActivity.class)));
        settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, UiSettingsActivity.class)));
        syncButton.setOnClickListener(v -> sync());
        playButton.setOnClickListener(v -> play());

        return Ui.scroll(this, root);
    }

    private void play() {
        try {
            GameConfig.ensureSettings();
        } catch (IOException failure) {
            setStatus(getString(R.string.servers_write_failed, failure.getMessage()), Ui.ERR, 0, 0);
            return;
        }
        ServerEndpoint target = GameConfig.readServer();
        if (target == null || "127.0.0.1".equals(target.host())) {
            // No server chosen yet: pick one instead of booting into a dead connection.
            startActivity(new Intent(this, ServerBrowserActivity.class));
            return;
        }
        startActivity(new Intent(this, com.ferzdevzgta.game.SAMP.class));
        finish();
    }

    private void chooseVariant(String v) {
        variant = v;
        verifyOnly();
    }

    private File manifestFile() {
        return new File(GTA, "manifest-" + variant + ".json");
    }

    private String endpoint() {
        try {
            File ini = new File(GTA, "SAMP/settings.ini");
            if (ini.isFile()) {
                String url = IniFile.read(ini).get("download", "url", "");
                if (url != null && url.startsWith("http")) {
                    return url;
                }
            }
        } catch (IOException ignored) {
            // fall through to the message below
        }
        return null;
    }

    private CacheManifest readManifest() throws IOException {
        File f = manifestFile();
        if (!f.isFile()) {
            throw new IOException(getString(R.string.data_error_no_manifest, f.getPath()));
        }
        byte[] raw = java.nio.file.Files.readAllBytes(f.toPath());
        return CacheManifest.parse(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
    }

    private void verifyOnly() {
        if (busy.get()) {
            return;
        }
        setStatus(getString(R.string.data_status_checking), Ui.MUTED, Ui.MUTED, 0);
        worker.execute(() -> {
            try {
                CacheManifest m = readManifest();
                java.util.List<String> problems = m.verify(new File(GTA));
                int total = m.entries().size();
                String text = problems.isEmpty()
                        ? getString(R.string.data_status_ok, total, megabytes(m.totalBytes()))
                        : getString(R.string.data_status_broken, problems.size(), total);
                StringBuilder sb = new StringBuilder();
                int shown = 0;
                for (String p : problems) {
                    if (shown++ == 40) {
                        sb.append("… ").append(problems.size() - 40).append(" more\n");
                        break;
                    }
                    sb.append(p).append('\n');
                }
                final boolean clean = problems.isEmpty();
                ui.post(() -> {
                    setStatus(text, clean ? Ui.OK : Ui.ERR, clean ? Ui.OK : Ui.ERR,
                            clean ? 1000 : 0);
                    setDetail(sb.length() == 0 ? "" : sb.toString());
                    syncButton.setEnabled(true);
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    setStatus(getString(R.string.data_status_no_manifest), Ui.ERR, Ui.ERR, 0);
                    setDetail(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                    syncButton.setEnabled(true);
                });
            }
        });
    }

    private void sync() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        syncButton.setEnabled(false);
        setDetail("");
        final String base = endpoint();
        setStatus(getString(R.string.data_status_prepare), Ui.MUTED, Ui.MUTED, 0);

        worker.execute(() -> {
            try {
                CacheManifest m = readManifest();
                File root = new File(GTA);
                java.util.List<String> missing = m.verify(root);
                if (missing.isEmpty()) {
                    ui.post(() -> {
                        setStatus(getString(R.string.data_status_nothing_to_do, m.entries().size()),
                                Ui.OK, Ui.OK, 1000);
                        done();
                    });
                    return;
                }
                if (base == null) {
                    ui.post(() -> {
                        setStatus(getString(R.string.data_error_no_endpoint), Ui.ERR, Ui.ERR, 0);
                        setDetail(getString(R.string.data_error_endpoint_hint));
                        done();
                    });
                    return;
                }
                CacheDownloader downloader = new CacheDownloader(base, m, root, Thread::sleep);
                CacheDownloader.Result result = downloader.sync(new ProgressUi());
                java.util.List<String> after = m.verify(root);
                ui.post(() -> {
                    if (after.isEmpty() && result.ok()) {
                        setStatus(getString(R.string.data_status_done, result.summary()),
                                Ui.OK, Ui.OK, 1000);
                    } else {
                        setStatus(getString(R.string.data_status_partial, result.summary(), after.size()),
                                Ui.ERR, Ui.ERR, 0);
                        StringBuilder sb = new StringBuilder();
                        for (String f : result.failures) {
                            sb.append(f).append('\n');
                        }
                        for (String f : after) {
                            sb.append(f).append('\n');
                        }
                        setDetail(sb.toString());
                    }
                    done();
                });
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                ui.post(() -> {
                    setStatus(getString(R.string.data_status_cancelled), Ui.MUTED, Ui.MUTED, 0);
                    done();
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    setStatus(getString(R.string.data_status_failed), Ui.ERR, Ui.ERR, 0);
                    setDetail(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                    done();
                });
            }
        });
    }

    private void done() {
        busy.set(false);
        syncButton.setEnabled(true);
        if (variantSwitch != null) {
            variantSwitch.setAlpha(1f);
        }
    }

    private final class ProgressUi implements CacheDownloader.Progress {
        public void onFileStart(String path, int index, int count) {
            ui.post(() -> {
                setStatus(getString(R.string.data_status_file, index, count), Ui.TEXT, Ui.ACCENT, -1);
                setDetail(path);
            });
        }

        public void onFileProgress(String path, long done, long size) {
            if (size <= 0) {
                return;
            }
            int permille = (int) Math.min(1000, (done * 1000) / size);
            ui.post(() -> {
                progress.setProgress(permille);
                setStatus(getString(R.string.data_status_bytes, megabytes(done), megabytes(size)),
                        Ui.TEXT, Ui.ACCENT, permille);
            });
        }

        public void onFileDone(String path, boolean downloaded) {
            // the next onFileStart moves the label on
        }

        public void onStatus(String message) {
            ui.post(() -> setDetail(message));
        }
    }

    /**
     * One status line plus one badge. The badge word comes from the tone, so the text can stay
     * a full sentence with numbers in it.
     */
    private void setStatus(String text, int textColour, int tone, int permille) {
        if (statusText != null) {
            statusText.setText(text);
            statusText.setTextColor(textColour);
        }
        if (pillSlot != null) {
            pillSlot.removeAllViews();
            pillSlot.addView(Ui.pill(this, toneWord(tone), tone));
        }
        if (progress != null && permille >= 0) {
            progress.setProgress(permille);
        }
    }

    private String toneWord(int tone) {
        if (tone == Ui.OK) {
            return getString(R.string.data_state_ok);
        }
        if (tone == Ui.ERR) {
            return getString(R.string.data_state_error);
        }
        if (tone == Ui.MUTED) {
            return getString(R.string.data_state_idle);
        }
        return getString(R.string.data_state_work);
    }

    private void setDetail(String text) {
        if (detail != null) {
            detail.setText(text == null ? "" : text);
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
            send.putExtra(Intent.EXTRA_SUBJECT, "SA-MP FerzDevZ log");
            send.putExtra(Intent.EXTRA_TEXT, header());
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.data_action_share_log)));
        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage() == null ? "share failed" : ex.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String header() {
        return String.format(Locale.ROOT,
                "app %s | android %d | %s %s | abi %s",
                appVersion(), android.os.Build.VERSION.SDK_INT,
                android.os.Build.MANUFACTURER, android.os.Build.MODEL,
                android.os.Build.SUPPORTED_ABIS.length > 0 ? android.os.Build.SUPPORTED_ABIS[0] : "?");
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
        ui.removeCallbacksAndMessages(null);
    }
}
