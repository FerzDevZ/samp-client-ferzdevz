package com.ferzdevzgta.launcher.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ferzdevzgta.game.R;
import com.ferzdevzgta.game.SAMP;
import com.ferzdevzgta.game.log.LogRotation;
import com.ferzdevzgta.game.servers.GameConfig;
import com.ferzdevzgta.game.servers.ServerEndpoint;
import com.ferzdevzgta.game.ui.Community;
import com.ferzdevzgta.game.ui.DataSetupActivity;
import com.ferzdevzgta.game.ui.ServerBrowserActivity;
import com.ferzdevzgta.game.ui.Ui;
import com.ferzdevzgta.game.ui.UiSettingsActivity;

import java.io.File;

/**
 * Launcher screen. Answers one question before the player commits: is this install ready to
 * play, and if not, which of the three requirements is missing. The action bar is pinned to the
 * bottom so the thumb reaches it on a phone and short screens do not leave dead space.
 */
public class MainActivity extends AppCompatActivity {
    private Handler handler = new Handler(Looper.getMainLooper());
    private Button startButton;
    private TextView summary;
    private LinearLayout summarySlot;
    private TextView dataValue;
    private TextView serverValue;
    private TextView nickValue;
    private boolean storagePermissionGranted = false;

    private final ActivityResultLauncher<String[]> requestStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Boolean granted : permissions.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    storagePermissionGranted = true;
                    Log.d("MainActivity", "All storage permissions granted");
                    startGameIfReady();
                } else {
                    storagePermissionGranted = false;
                    Log.e("MainActivity", "Storage permissions denied");
                    Toast.makeText(this, R.string.perm_storage_required, Toast.LENGTH_LONG).show();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        requestManageStoragePermission();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        storagePermissionGranted = true;
                        Log.d("MainActivity", "Manage storage permission granted");
                        startGameIfReady();
                    } else {
                        storagePermissionGranted = false;
                        Log.e("MainActivity", "Manage storage permission denied");
                        Toast.makeText(this, R.string.perm_all_files_required, Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("MainActivity", "onCreate started");

        Ui.paintWindow(this);

        // The native Log() appends forever; keep months of play from filling the disk.
        // A rotation failure must never stop the launch.
        try {
            LogRotation.rotate(new File("/storage/emulated/0/GTA/logcat.txt"));
        } catch (Exception e) {
            Log.w("MainActivity", "log rotation skipped: " + e.getMessage());
        }

        try {
            setContentView(buildUi());
            Ui.applyInsets(this, findViewById(android.R.id.content));
            checkAndRequestStoragePermission();
            Log.d("MainActivity", "onCreate completed successfully");
        } catch (Exception e) {
            Log.e("MainActivity", "Critical error in onCreate: " + e.getMessage(), e);
            finish();
        }
    }

    private View buildUi() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Ui.BG);

        // ---------------------------------------------------------------- content
        LinearLayout content = Ui.column(this);

        content.addView(Ui.eyebrow(this, getString(R.string.home_eyebrow)));
        Ui.gap(this, content, 8);
        content.addView(Ui.title(this, getString(R.string.app_name)));
        Ui.gap(this, content, 4);
        content.addView(Ui.subtitle(this, getString(R.string.samp_ver)));

        Ui.gap(this, content, 22);
        LinearLayout statusCard = Ui.card(this);
        LinearLayout statusHead = Ui.row(this);
        statusHead.addView(Ui.section(this, getString(R.string.home_status_title)),
                Ui.weight(this, 1f, 8f));
        summarySlot = new LinearLayout(this);
        summarySlot.setOrientation(LinearLayout.HORIZONTAL);
        statusHead.addView(summarySlot);
        statusCard.addView(statusHead, Ui.fill());
        Ui.gap(this, statusCard, 4);

        // Each requirement is also the shortcut to the screen that fixes it, so the launcher
        // never asks the player to guess which button below belongs to which warning above.
        LinearLayout dataRow = Ui.statusRow(this, getString(R.string.home_status_data), "-", Ui.WARN);
        dataValue = Ui.statusValue(dataRow);
        makeShortcut(dataRow, DataSetupActivity.class);
        statusCard.addView(dataRow, Ui.fill());

        LinearLayout serverRow = Ui.statusRow(this, getString(R.string.home_status_server), "-", Ui.WARN);
        serverValue = Ui.statusValue(serverRow);
        makeShortcut(serverRow, ServerBrowserActivity.class);
        statusCard.addView(serverRow, Ui.fill());

        LinearLayout nickRow = Ui.statusRow(this, getString(R.string.home_status_nick), "-", Ui.WARN);
        nickValue = Ui.statusValue(nickRow);
        makeShortcut(nickRow, ServerBrowserActivity.class);
        statusCard.addView(nickRow, Ui.fill());
        content.addView(statusCard, Ui.fill());
        Ui.fadeIn(statusCard, 0);

        Ui.gap(this, content, 12);
        LinearLayout languageCard = Ui.card(this);
        languageCard.addView(Ui.section(this, getString(R.string.language_title)));
        Ui.gap(this, languageCard, 10);
        languageCard.addView(Ui.languageSwitch(this), Ui.fill());
        Ui.gap(this, languageCard, 10);
        languageCard.addView(Ui.label(this, getString(R.string.language_note)));
        content.addView(languageCard, Ui.fill());
        Ui.fadeIn(languageCard, 1);

        Ui.gap(this, content, 20);
        content.addView(linkNote(getString(R.string.home_bug_info), getString(R.string.home_bug_open)));

        ScrollView scroll = Ui.scroll(this, content);
        shell.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ------------------------------------------------------------ action bar
        shell.addView(actionBar(), Ui.fill());
        return shell;
    }

    private View actionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 20);
        bar.setPadding(pad, Ui.dp(this, 12), pad, Ui.dp(this, 16));

        View line = Ui.divider(this);
        bar.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        Ui.gap(this, bar, 12);
        startButton = Ui.button(this, getString(R.string.home_start), Ui.PRIMARY, R.drawable.ic_play);
        startButton.setOnClickListener(v -> onStartPressed());
        bar.addView(startButton, Ui.fill());

        Ui.gap(this, bar, 8);
        LinearLayout row = Ui.row(this);
        Button dataButton = Ui.button(this, getString(R.string.home_action_data), Ui.SECONDARY, R.drawable.ic_data);
        Button serverButton = Ui.button(this, getString(R.string.home_action_server), Ui.SECONDARY, R.drawable.ic_server);
        dataButton.setOnClickListener(v -> startActivity(new Intent(this, DataSetupActivity.class)));
        serverButton.setOnClickListener(v -> startActivity(new Intent(this, ServerBrowserActivity.class)));
        row.addView(dataButton, Ui.weight(this, 1f, 8f));
        row.addView(serverButton, Ui.weight(this, 1f, 0f));
        bar.addView(row, Ui.fill());

        Ui.gap(this, bar, 8);
        Button displayButton = Ui.button(this, getString(R.string.home_action_settings), Ui.GHOST, R.drawable.ic_settings);
        displayButton.setOnClickListener(v -> startActivity(new Intent(this, UiSettingsActivity.class)));
        bar.addView(displayButton, Ui.fill());
        return bar;
    }

    /** Turns a status row into the shortcut for the screen that changes it. */
    private void makeShortcut(LinearLayout row, Class<?> screen) {
        Ui.pressable(row);
        row.setFocusable(true);
        row.setOnClickListener(v -> startActivity(new Intent(this, screen)));
    }

    /**
     * A quiet note that is also the link. The sentence promising bug reports are welcome and the
     * control that opens the room are the same element, so nobody has to hunt for the channel.
     */
    private TextView linkNote(String note, String action) {
        TextView view = Ui.label(this, note + "  " + action);
        view.setTextColor(Ui.FAINT);
        view.setPaintFlags(view.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        Ui.pressable(view);
        view.setOnClickListener(v -> {
            if (!Ui.openUrl(this, Community.INVITE)) {
                Toast.makeText(this, getString(R.string.report_discord_missing), Toast.LENGTH_LONG)
                        .show();
            }
        });
        return view;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    /** Reflects the three things Play depends on, so the launcher never lies about its state. */
    private void refreshStatus() {
        if (dataValue == null) {
            return;
        }
        boolean dataPresent = gameDataPresent();
        dataValue.setText(getString(dataPresent
                ? R.string.home_status_data_ok
                : R.string.home_status_data_missing));
        dataValue.setTextColor(dataPresent ? Ui.OK : Ui.WARN);

        ServerEndpoint server = GameConfig.readServer();
        boolean serverChosen = server != null && !"127.0.0.1".equals(server.host());
        serverValue.setText(serverChosen ? server.address() : getString(R.string.home_status_server_none));
        serverValue.setTextColor(serverChosen ? Ui.TEXT : Ui.WARN);

        String nick = GameConfig.readNickname();
        boolean nickSet = nick != null && !nick.trim().isEmpty();
        nickValue.setText(nickSet ? nick : getString(R.string.home_status_nick_none));
        nickValue.setTextColor(nickSet ? Ui.TEXT : Ui.WARN);

        boolean ready = dataPresent && serverChosen;
        startButton.setText(ready ? getString(R.string.home_start)
                : getString(R.string.home_start_setup));
        // The icon follows the label. A play triangle on a button that says "set up first" promises
        // a game that will not start.
        Ui.setButtonIcon(startButton, ready ? R.drawable.ic_play : R.drawable.ic_settings);
        if (summarySlot != null) {
            summarySlot.removeAllViews();
            summarySlot.addView(Ui.pill(this,
                    getString(ready ? R.string.home_summary_ready : R.string.home_summary_not_ready),
                    ready ? Ui.OK : Ui.WARN));
        }
    }

    private void onStartPressed() {
        try {
            if (!storagePermissionGranted) {
                checkAndRequestStoragePermission();
                return;
            }
            if (!gameDataPresent()) {
                startActivity(new Intent(this, DataSetupActivity.class));
                return;
            }
            startGame();
        } catch (Exception e) {
            Log.e("MainActivity", "Error in start button click: " + e.getMessage());
        }
    }

    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                storagePermissionGranted = true;
                Log.d("MainActivity", "Already have manage storage permission");
            } else {
                requestManageStoragePermission();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{
                        android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.READ_MEDIA_AUDIO
                };
            } else {
                permissions = new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
            }

            boolean allGranted = true;
            for (String perm : permissions) {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                storagePermissionGranted = true;
                Log.d("MainActivity", "Already have storage permissions");
            } else {
                requestStoragePermissionLauncher.launch(permissions);
            }
        } else {
            storagePermissionGranted = true;
        }
    }

    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageStorageLauncher.launch(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                manageStorageLauncher.launch(intent);
            }
        }
    }

    private void startGameIfReady() {
        if (storagePermissionGranted) {
            if (!gameDataPresent()) {
                startActivity(new Intent(MainActivity.this, DataSetupActivity.class));
                return;
            }
            startGame();
        } else {
            Toast.makeText(this, R.string.perm_storage_denied, Toast.LENGTH_LONG).show();
        }
    }

    /** True when the game data tree exists: a fresh sideload has the APK but no assets. */
    private boolean gameDataPresent() {
        return new File("/storage/emulated/0/GTA/data").isDirectory();
    }

    /**
     * Boots the client, after making sure a connection target exists. The native side parses
     * settings.ini before its first frame and stops when the file is missing, so the file is
     * created here and the browser opens when no server has been chosen yet.
     */
    private void startGame() {
        try {
            GameConfig.ensureSettings();
            ServerEndpoint target = GameConfig.readServer();
            if (target == null || "127.0.0.1".equals(target.host())) {
                Log.d("MainActivity", "No server chosen, opening the browser");
                startActivity(new Intent(MainActivity.this, ServerBrowserActivity.class));
                return;
            }
            Log.d("MainActivity", "Starting game, target " + target.address());
            Intent gameIntent = new Intent(MainActivity.this, SAMP.class);
            startActivity(gameIntent);
        } catch (Exception e) {
            Log.e("MainActivity", "Error starting game: " + e.getMessage());
            Toast.makeText(this,
                    getString(R.string.servers_write_failed, String.valueOf(e.getMessage())),
                    Toast.LENGTH_LONG).show();
        }
    }

    public static void hideKeyboard(Activity activity) {
        try {
            InputMethodManager inputManager = (InputMethodManager) activity
                    .getSystemService(Context.INPUT_METHOD_SERVICE);

            View currentFocusedView = activity.getCurrentFocus();
            if (currentFocusedView != null) {
                inputManager.hideSoftInputFromWindow(currentFocusedView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error hiding keyboard: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            handler.removeCallbacksAndMessages(null);
        } catch (Exception e) {
            Log.e("MainActivity", "Error in onDestroy: " + e.getMessage());
        }
    }
}
