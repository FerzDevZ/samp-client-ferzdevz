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
import com.ferzdevzgta.game.ui.DataSetupActivity;
import com.ferzdevzgta.game.ui.ServerBrowserActivity;
import com.ferzdevzgta.game.ui.Ui;
import com.ferzdevzgta.game.ui.UiSettingsActivity;

import java.io.File;

/**
 * Launcher screen: shows what the client would do right now (data present, server chosen,
 * nickname) instead of an empty screen with one button, and offers the language switch on
 * first run.
 */
public class MainActivity extends AppCompatActivity {
    private Handler handler = new Handler(Looper.getMainLooper());
    private Button startButton;
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
            checkAndRequestStoragePermission();
            Log.d("MainActivity", "onCreate completed successfully");
        } catch (Exception e) {
            Log.e("MainActivity", "Critical error in onCreate: " + e.getMessage(), e);
            finish();
        }
    }

    private View buildUi() {
        LinearLayout root = Ui.column(this);

        root.addView(Ui.eyebrow(this, getString(R.string.home_eyebrow)));
        Ui.gap(this, root, 8);
        root.addView(Ui.title(this, getString(R.string.client_name)));
        Ui.gap(this, root, 4);
        root.addView(Ui.subtitle(this, getString(R.string.samp_ver)));

        // What the next Start does, in three lines, instead of an empty middle.
        Ui.gap(this, root, 24);
        LinearLayout statusCard = Ui.card(this);
        statusCard.addView(Ui.section(this, getString(R.string.home_status_title)));
        LinearLayout dataRow = Ui.statusRow(this, getString(R.string.home_status_data), "", 0);
        dataValue = (TextView) dataRow.getChildAt(1);
        statusCard.addView(dataRow, Ui.fill());
        LinearLayout serverRow = Ui.statusRow(this, getString(R.string.home_status_server), "", 0);
        serverValue = (TextView) serverRow.getChildAt(1);
        statusCard.addView(serverRow, Ui.fill());
        LinearLayout nickRow = Ui.statusRow(this, getString(R.string.home_status_nick), "", 0);
        nickValue = (TextView) nickRow.getChildAt(1);
        statusCard.addView(nickRow, Ui.fill());
        root.addView(statusCard, Ui.fill());

        // Actions
        Ui.gap(this, root, 20);
        startButton = Ui.button(this, getString(R.string.home_start), Ui.PRIMARY);
        startButton.setOnClickListener(v -> onStartPressed());
        root.addView(startButton, Ui.fill());
        Ui.gap(this, root, 6);
        root.addView(Ui.label(this, getString(R.string.home_start_hint)));

        Ui.gap(this, root, 16);
        LinearLayout rowA = Ui.row(this);
        Button dataButton = Ui.button(this, getString(R.string.home_action_data), Ui.SECONDARY);
        Button serverButton = Ui.button(this, getString(R.string.home_action_server), Ui.SECONDARY);
        dataButton.setOnClickListener(v -> startActivity(new Intent(this, DataSetupActivity.class)));
        serverButton.setOnClickListener(v -> startActivity(new Intent(this, ServerBrowserActivity.class)));
        rowA.addView(dataButton, Ui.weight(this, 1f, 8f));
        rowA.addView(serverButton, Ui.weight(this, 1f, 0f));
        root.addView(rowA, Ui.fill());

        Ui.gap(this, root, 8);
        Button displayButton = Ui.button(this, getString(R.string.home_action_settings), Ui.SECONDARY);
        displayButton.setOnClickListener(v -> startActivity(new Intent(this, UiSettingsActivity.class)));
        root.addView(displayButton, Ui.fill());

        // Language
        Ui.gap(this, root, 24);
        LinearLayout languageCard = Ui.card(this);
        languageCard.addView(Ui.section(this, getString(R.string.language_title)));
        languageCard.addView(Ui.languageSwitch(this), Ui.fill());
        Ui.gap(this, languageCard, 10);
        languageCard.addView(Ui.label(this, getString(R.string.language_note)));
        root.addView(languageCard, Ui.fill());

        // Credits
        Ui.gap(this, root, 24);
        TextView bug = Ui.label(this, getString(R.string.home_bug_info));
        root.addView(bug);
        Ui.gap(this, root, 4);
        TextView credits = Ui.label(this, getString(R.string.home_authors));
        credits.setTextColor(Ui.ACCENT);
        credits.setOnClickListener(v -> openTelegram());
        root.addView(credits);

        return Ui.scroll(this, root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    /** Reflects the three things Start depends on, so the launcher never lies. */
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

        startButton.setText(dataPresent ? getString(R.string.home_start)
                : getString(R.string.home_action_data));
    }

    private void openTelegram() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/kuzia15")));
        } catch (Exception e) {
            Log.e("MainActivity", "Error opening tg: " + e.getMessage());
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
