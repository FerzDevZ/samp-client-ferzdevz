package com.ferzdevzgta.game.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
    private TextView status;
    private TextView detail;
    private TextView variantLabel;
    private ProgressBar progress;
    private Button fullButton;
    private Button liteButton;
    private Button syncButton;
    private Button playButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.screen(this);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24));

        root.addView(Ui.title(this, getString(R.string.data_title)));
        Ui.gap(this, root, 6);
        root.addView(Ui.body(this, getString(R.string.data_intro)));
        Ui.gap(this, root, 14);

        LinearLayout picker = Ui.card(this);
        variantLabel = Ui.fieldLabel(this, "");
        picker.addView(variantLabel);
        Ui.gap(this, picker, 8);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        fullButton = Ui.button(this, getString(R.string.data_variant_full), false);
        liteButton = Ui.button(this, getString(R.string.data_variant_lite), false);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.setMargins(0, 0, Ui.dp(this, 8), 0);
        fullButton.setLayoutParams(half);
        liteButton.setLayoutParams(half);
        row.addView(fullButton);
        row.addView(liteButton);
        picker.addView(row);
        root.addView(picker);
        Ui.gap(this, root, 12);

        LinearLayout progressCard = Ui.card(this);
        status = Ui.fieldLabel(this, getString(R.string.data_status_idle));
        progressCard.addView(status);
        Ui.gap(this, progressCard, 8);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        progressCard.addView(progress);
        root.addView(progressCard);
        Ui.gap(this, root, 12);

        syncButton = Ui.button(this, getString(R.string.data_action_sync), true);
        root.addView(syncButton);
        Ui.gap(this, root, 8);

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        Button verifyButton = Ui.button(this, getString(R.string.data_action_verify), false);
        Button settingsButton = Ui.button(this, getString(R.string.data_action_settings), false);
        verifyButton.setLayoutParams(half);
        settingsButton.setLayoutParams(half);
        secondary.addView(verifyButton);
        secondary.addView(settingsButton);
        root.addView(secondary);
        Ui.gap(this, root, 8);

        LinearLayout third = new LinearLayout(this);
        third.setOrientation(LinearLayout.HORIZONTAL);
        Button logButton = Ui.button(this, getString(R.string.data_action_share_log), false);
        Button serversButton = Ui.button(this, getString(R.string.servers_title), false);
        logButton.setLayoutParams(half);
        serversButton.setLayoutParams(half);
        third.addView(logButton);
        third.addView(serversButton);
        root.addView(third);
        Ui.gap(this, root, 8);

        playButton = Ui.button(this, getString(R.string.data_action_play), true);
        root.addView(playButton);
        Ui.gap(this, root, 14);

        detail = Ui.mono(this, "");
        root.addView(detail);

        scroll.addView(root);
        setContentView(scroll);

        fullButton.setOnClickListener(v -> chooseVariant("full"));
        liteButton.setOnClickListener(v -> chooseVariant("lite"));
        syncButton.setOnClickListener(v -> sync());
        verifyButton.setOnClickListener(v -> verifyOnly());
        settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, UiSettingsActivity.class)));
        logButton.setOnClickListener(v -> shareLog());
        serversButton.setOnClickListener(v ->
                startActivity(new Intent(this, ServerBrowserActivity.class)));
        playButton.setOnClickListener(v -> {
            try {
                GameConfig.ensureSettings();
            } catch (IOException failure) {
                setStatus(getString(R.string.servers_write_failed, failure.getMessage()), Ui.ERR, 0);
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
        });

        chooseVariant("full");
        verifyOnly();
    }

    private void chooseVariant(String v) {
        variant = v;
        variantLabel.setText(getString(R.string.data_variant_chosen,
                v.equals("full") ? getString(R.string.data_variant_full) : getString(R.string.data_variant_lite)));
        paintToggle();
        verifyOnly();
    }

    private void paintToggle() {
        boolean full = "full".equals(variant);
        fullButton.setTextColor(full ? 0xFF0B0E12 : Ui.TEXT);
        liteButton.setTextColor(full ? Ui.TEXT : 0xFF0B0E12);
        android.graphics.drawable.GradientDrawable a = new android.graphics.drawable.GradientDrawable();
        a.setCornerRadius(Ui.dp(this, 10));
        a.setColor(full ? Ui.ACCENT : Ui.CARD);
        a.setStroke(Ui.dp(this, 1), full ? Ui.ACCENT : Ui.STROKE);
        fullButton.setBackground(a);
        android.graphics.drawable.GradientDrawable b = new android.graphics.drawable.GradientDrawable();
        b.setCornerRadius(Ui.dp(this, 10));
        b.setColor(full ? Ui.CARD : Ui.ACCENT);
        b.setStroke(Ui.dp(this, 1), full ? Ui.STROKE : Ui.ACCENT);
        liteButton.setBackground(b);
        fullButton.setEnabled(!busy.get());
        liteButton.setEnabled(!busy.get());
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
        setStatus(getString(R.string.data_status_checking), Ui.MUTED, 0);
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
                ui.post(() -> {
                    setStatus(text, problems.isEmpty() ? Ui.OK : Ui.ERR, problems.isEmpty() ? 1000 : 0);
                    setDetail(sb.length() == 0 ? "" : sb.toString());
                    syncButton.setEnabled(true);
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    setStatus(getString(R.string.data_status_no_manifest), Ui.ERR, 0);
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
        fullButton.setEnabled(false);
        liteButton.setEnabled(false);
        setDetail("");
        final String base = endpoint();
        setStatus(getString(R.string.data_status_prepare), Ui.MUTED, 0);

        worker.execute(() -> {
            try {
                CacheManifest m = readManifest();
                File root = new File(GTA);
                java.util.List<String> missing = m.verify(root);
                if (missing.isEmpty()) {
                    ui.post(() -> {
                        setStatus(getString(R.string.data_status_nothing_to_do, m.entries().size()), Ui.OK, 1000);
                        done();
                    });
                    return;
                }
                if (base == null) {
                    ui.post(() -> {
                        setStatus(getString(R.string.data_error_no_endpoint), Ui.ERR, 0);
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
                        setStatus(getString(R.string.data_status_done, result.summary()), Ui.OK, 1000);
                    } else {
                        setStatus(getString(R.string.data_status_partial, result.summary(), after.size()), Ui.ERR, 0);
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
                    setStatus(getString(R.string.data_status_cancelled), Ui.MUTED, 0);
                    done();
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    setStatus(getString(R.string.data_status_failed), Ui.ERR, 0);
                    setDetail(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                    done();
                });
            }
        });
    }

    private void done() {
        busy.set(false);
        syncButton.setEnabled(true);
        fullButton.setEnabled(true);
        liteButton.setEnabled(true);
        paintToggle();
    }

    private final class ProgressUi implements CacheDownloader.Progress {
        public void onFileStart(String path, int index, int count) {
            ui.post(() -> {
                setStatus(getString(R.string.data_status_file, index, count), Ui.TEXT, 0);
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
                setStatus(getString(R.string.data_status_bytes, megabytes(done), megabytes(size)), Ui.TEXT, permille);
            });
        }

        public void onFileDone(String path, boolean downloaded) {
            // the next onFileStart moves the label on
        }

        public void onStatus(String message) {
            ui.post(() -> setDetail(message));
        }
    }

    private void setStatus(String text, int color, int permille) {
        status.setText(text);
        status.setTextColor(color);
        if (permille >= 0) {
            progress.setProgress(permille);
        }
    }

    private void setDetail(String text) {
        detail.setText(text == null ? "" : text);
        detail.setTextColor(Ui.MUTED);
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
