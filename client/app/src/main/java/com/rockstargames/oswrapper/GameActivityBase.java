// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.widget.Toast;
import android.hardware.input.InputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.FileProvider;

import com.ferzdevzgta.game.R;
import com.ferzdevzgta.game.log.SessionGuard;
import com.ferzdevzgta.game.media.ShotFile;
import com.ferzdevzgta.game.servers.GameConfig;
import com.rockstargames.oswrapper.input.GamepadInput;
import com.nvidia.devtech.NvAPKFileHelper;
import com.nvidia.devtech.NvUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * The activity the engine runs inside: it owns the GL surface, forwards lifecycle and
 * network changes to {@link GameThread}, and exposes the services the engine calls back.
 *
 * Subclasses extend this and do the game-specific work in {@link #onActivitySetup()} and
 * {@link #onActivityReady(boolean)}.
 */
public abstract class GameActivityBase extends AppCompatActivity {

    public static final String TAG = "GameActivityBase";

    /**
     * Engine data root. This is not the SAMP storage directory: the SAMP client reads
     * settings and logs from /storage/emulated/0/GTA/ (see client/app/src/main/cpp/samp),
     * while libGame.so looks for its own data under its VICE convention. They are two
     * different paths on purpose, so do not merge them without a device test.
     */
    private static final String ENGINE_DATA_ROOT = "VICE";

    /** Window flags that hide the status and navigation bars and keep them hidden. */
    private static final int SYSTEM_UI_FLAGS =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private static final int EGL_CONTEXT_CLIENT_VERSION = 3;
    private static final int NETWORK_NONE = 0;
    private static final int NETWORK_CELLULAR = 1;
    private static final int NETWORK_WIFI = 2;

    private static GameActivityBase instance;

    public Handler handler;
    public GamePlatformServices services;
    public GameView view;

    protected boolean supportPauseResume = true;
    protected boolean ResumeEventDone = false;
    protected ConnectivityManager connectivityManager;
    protected WifiManager wifiManager;

    private boolean ranInit;
    private int lastNetworkType;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private PermissionCallback requestPermissionCallback;
    private InputManager inputManager;
    private InputManager.InputDeviceListener padListener;

    /** Replaces the Kotlin function type the original code passed around. */
    public interface PermissionCallback {
        void onResult(boolean granted);
    }

    public static GameActivityBase getInstance() {
        return instance;
    }

    // --- accessors ----------------------------------------------------------

    protected ConnectivityManager getConnectivityManager() {
        if (connectivityManager == null) {
            throw new IllegalStateException("connectivityManager not initialized");
        }
        return connectivityManager;
    }

    protected WifiManager getWifiManager() {
        if (wifiManager == null) {
            throw new IllegalStateException("wifiManager not initialized");
        }
        return wifiManager;
    }

    public GamePlatformServices getServices() {
        if (services == null) {
            throw new IllegalStateException("services not initialized");
        }
        return services;
    }

    public GameView getView() {
        if (view == null) {
            throw new IllegalStateException("view not initialized");
        }
        return view;
    }

    public void setServices(GamePlatformServices services) {
        if (services == null) {
            throw new NullPointerException("services is null");
        }
        this.services = services;
    }

    public void setView(GameView view) {
        if (view == null) {
            throw new NullPointerException("view is null");
        }
        this.view = view;
    }

    protected void setConnectivityManager(ConnectivityManager manager) {
        if (manager == null) {
            throw new NullPointerException("connectivityManager is null");
        }
        this.connectivityManager = manager;
    }

    protected void setWifiManager(WifiManager manager) {
        if (manager == null) {
            throw new NullPointerException("wifiManager is null");
        }
        this.wifiManager = manager;
    }

    protected int getLastNetworkType() {
        return lastNetworkType;
    }

    public boolean getSupportPauseResume() {
        return supportPauseResume;
    }

    // --- network ------------------------------------------------------------

    protected boolean isNetworkAvailable() {
        NetworkInfo info = getConnectivityManager().getActiveNetworkInfo();
        return info != null && info.isAvailable() && info.isConnected();
    }

    protected boolean isWifiAvailable() {
        return getWifiManager().isWifiEnabled();
    }

    protected int getCurrentNetworkType() {
        if (!isNetworkAvailable()) {
            return NETWORK_NONE;
        }
        return isWifiAvailable() ? NETWORK_WIFI : NETWORK_CELLULAR;
    }

    private void checkNetworkType() {
        int current = getCurrentNetworkType();
        if (current == lastNetworkType) {
            return;
        }
        lastNetworkType = current;
        GameThread.INSTANCE.onNetworkChanged(current);
    }

    // --- storage ------------------------------------------------------------

    /**
     * The engine's data directory, created on demand. Returns "" when the path cannot be
     * created, which the caller has to treat as "no external data", not as a crash.
     */
    public String GetGameBaseDirectory() {
        File gameDir = new File(new File("/storage/emulated/0/"), ENGINE_DATA_ROOT);
        if (!gameDir.exists() && !gameDir.mkdirs()) {
            Log.w(TAG, "cannot create " + gameDir + ", the engine will run without external data");
            return "";
        }
        return gameDir.getAbsolutePath() + "/";
    }

    // --- overridable hooks --------------------------------------------------

    protected void onActivitySetup() {}

    protected void onActivityReady(boolean firstInit) {}

    // --- lifecycle ----------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        instance = this;
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        handler = new Handler();

        // Two Android storage roots are handed to the engine: the app-private directory the
        // engine may write into, and the shared root above it.
        File external = getExternalFilesDir(null);
        if (external == null) {
            throw new IllegalStateException("getExternalFilesDir returned null");
        }
        String storagePath = external.getAbsolutePath();
        int androidIndex = storagePath.indexOf("/Android");
        if (androidIndex < 0) {
            throw new IllegalStateException("cannot find /Android in " + storagePath);
        }
        String storageBase = storagePath.substring(0, androidIndex);
        Log.i(TAG, "storage: app=" + storagePath + " shared=" + storageBase);

        NvUtil util = NvUtil.getInstance();
        util.setActivity(this);
        util.setAppLocalValue("STORAGE_ROOT", storagePath);
        util.setAppLocalValue("STORAGE_ROOT_BASE", storageBase);
        NvAPKFileHelper.getInstance().setContext(this);

        try {
            GameThread.INSTANCE.onInitialSetup(this);
        } catch (UnsatisfiedLinkError missingEngine) {
            // Expected in a framework-only APK: the classes are here, libGame.so is not.
            Log.w(TAG, "engine library not loaded, skipping initial setup: " + missingEngine.getMessage());
        }

        onActivitySetup();

        boolean initialized = GameNative.implIsInitialized();
        Log.i(TAG, "engine initialized = " + initialized);

        ranInit = initialized;

        setWifiManager(getSystemService(WifiManager.class));
        setConnectivityManager(getSystemService(ConnectivityManager.class));
        getConnectivityManager().registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.i(TAG, "network available: " + network);
                checkNetworkType();
            }

            @Override
            public void onLost(Network network) {
                Log.i(TAG, "network lost: " + network);
                checkNetworkType();
            }

            @Override
            public void onUnavailable() {
                Log.i(TAG, "network unavailable");
                checkNetworkType();
            }
        });

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    Log.i(TAG, "permission result: " + granted);
                    PermissionCallback callback = requestPermissionCallback;
                    requestPermissionCallback = null;
                    if (callback != null) {
                        callback.onResult(granted);
                    }
                });

        hideSystemUI();
        setContentView(R.layout.game);

        registerPads();

        view = findViewById(R.id.viewGame);
        wireShotButton();
        beginSessionMarker();
        getWindow().setSustainedPerformanceMode(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        SurfaceHolder holder = view.getHolder();
        holder.setKeepScreenOn(true);
        holder.addCallback(new SurfaceHolder.Callback2() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                Log.i("GL", "surface created");
                GameThread thread = GameThread.INSTANCE;
                thread.cachedSurfaceHolder = surfaceHolder;
                thread.viewIsActive = true;
                if (thread.fixedWidth > 0) {
                    surfaceHolder.setFixedSize(thread.fixedWidth, thread.fixedHeight);
                }
                thread.InitEGLAndGLES2(EGL_CONTEXT_CLIENT_VERSION);
            }

            @Override
            public void surfaceChanged(
                    SurfaceHolder surfaceHolder, int format, int width, int height) {
                GameThread thread = GameThread.INSTANCE;
                thread.surfaceWidth = width;
                thread.surfaceHeight = height;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                Log.i("GL", "surface destroyed");
                GameThread thread = GameThread.INSTANCE;
                thread.viewIsActive = false;
                thread.destroyEGLSurface();
                // The surface is about to be freed; holding the holder would use a dead one.
                thread.cachedSurfaceHolder = null;
            }

            @Override
            public void surfaceRedrawNeeded(SurfaceHolder surfaceHolder) {
                // Required by Callback2, nothing to do.
            }
        });

        setServices(new GamePlatformServices(this, getView()));
        GameThread.INSTANCE.start(getServices());

        getView().setup(this);
        GameThread.INSTANCE.onActivityCreated(this, getView(), !initialized);
        onActivityReady(!initialized);
    }


    /**
     * One tap copies the surface into {@code GTA/Screenshots}.
     *
     * PixelCopy reads the composited surface, so what lands in the file is what the player saw,
     * including the engine's own HUD and any TextDraw the server drew. It needs no native change,
     * which keeps the JNI surface exactly as libGame.so expects it.
     */

    /** Leaves the marker the diagnostics screen reads when a session dies without closing. */
    private void beginSessionMarker() {
        try {
            SessionGuard.begin(new File(GameConfig.SAMP_DIR), System.currentTimeMillis(),
                    android.os.Process.myPid());
        } catch (Exception ignored) {
            // A read-only data folder must not stop the game from starting.
            Log.w(TAG, "session marker skipped");
        }
    }

    private void wireShotButton() {
        View button = findViewById(R.id.viewShot);
        if (button == null) {
            return;
        }
        button.setOnClickListener(v -> captureShot());
    }

    private void captureShot() {
        if (!(view instanceof SurfaceView)) {
            return;
        }
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            toast(getString(R.string.shot_failed));
            return;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            PixelCopy.request((SurfaceView) view, bitmap,
                    result -> {
                        if (result != PixelCopy.SUCCESS) {
                            toast(getString(R.string.shot_failed));
                            return;
                        }
                        saveShot(bitmap);
                    }, new Handler(Looper.getMainLooper()));
        } catch (Throwable refused) {
            // A capture must never take the game down, whatever the driver returns.
            Log.w(TAG, "screenshot skipped: " + refused.getMessage());
            toast(getString(R.string.shot_failed));
        }
    }

    private void saveShot(Bitmap bitmap) {
        File folder = ShotFile.directory(new File(GameConfig.GTA_ROOT));
        File file = new File(folder, ShotFile.name(System.currentTimeMillis()));
        try (OutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception failed) {
            Log.w(TAG, "screenshot write failed: " + failed.getMessage());
            toast(getString(R.string.shot_failed));
            return;
        }
        toast(getString(R.string.shot_saved, file.getAbsolutePath()));
        shareShot(file);
    }

    private void shareShot(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.shot_share)));
        } catch (Exception noPicker) {
            // The file already exists, so a missing share target is not worth an error dialog.
            Log.w(TAG, "screenshot share skipped: " + noPicker.getMessage());
        }
    }

    private void toast(String text) {
        try {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            Log.i(TAG, text);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        SessionGuard.clear(new File(GameConfig.SAMP_DIR));
        unregisterPads();
        super.onDestroy();
        GameThread.INSTANCE.onActivityDestroyed();
        GameThread.INSTANCE.stop();
        instance = null;
    }

    @Override
    public void onAttachedToWindow() {
        Log.i(TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
        GameThread.INSTANCE.start(getServices());
    }

    @Override
    public void onDetachedFromWindow() {
        Log.i(TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
        GameThread.INSTANCE.stop();
    }

    @Override
    public void onLowMemory() {
        Log.i(TAG, "onLowMemory");
        super.onLowMemory();
        GameThread.INSTANCE.onLowMemory();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
        getView().onPause();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
        hideSystemUI();
        getView().onResume();
        // Pads survive a background trip; the engine's own table does not. Hand it the list
        // again, otherwise the controller works only until the first pause.
        callPad("resume", GamepadInput::onResume);
    }

    // --- gamepad ------------------------------------------------------------

    /**
     * Forwards pad traffic to the engine. Every call is guarded, because the framework-only APK
     * has the classes but not libGame.so, and an input event must not kill the app.
     */
    private static void callPad(String what, Runnable action) {
        try {
            action.run();
        } catch (UnsatisfiedLinkError missingEngine) {
            Log.w(TAG, "gamepad " + what + " without engine: " + missingEngine.getMessage());
        }
    }

    private void registerPads() {
        inputManager = getSystemService(InputManager.class);
        if (inputManager == null) {
            Log.w(TAG, "no InputManager, pads stay silent");
            return;
        }
        padListener = new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                InputDevice device = InputDevice.getDevice(deviceId);
                Log.i(TAG, "input device added: " + deviceId + " " + (device == null ? "?" : device.getName()));
                if (GamepadInput.isPad(device)) {
                    callPad("connect", () -> GamepadInput.onConnected(deviceId));
                }
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                Log.i(TAG, "input device removed: " + deviceId);
                callPad("disconnect", () -> GamepadInput.onDisconnected(deviceId));
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
                // Layouts change when a pad is remapped, and the engine re-reads them on connect.
                InputDevice device = InputDevice.getDevice(deviceId);
                if (GamepadInput.isPad(device)) {
                    callPad("change", () -> {
                        GamepadInput.onDisconnected(deviceId);
                        GamepadInput.onConnected(deviceId);
                    });
                }
            }
        };
        inputManager.registerInputDeviceListener(padListener, handler);
        callPad("initial resume", GamepadInput::onResume);
    }

    private void unregisterPads() {
        if (inputManager != null && padListener != null) {
            inputManager.unregisterInputDeviceListener(padListener);
            padListener = null;
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        try {
            if (GamepadInput.onMotion(event)) {
                return true;
            }
        } catch (UnsatisfiedLinkError missingEngine) {
            Log.w(TAG, "pad motion without engine: " + missingEngine.getMessage());
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            if (GamepadInput.onKey(event, event.getAction() == KeyEvent.ACTION_DOWN)) {
                return true;
            }
        } catch (UnsatisfiedLinkError missingEngine) {
            Log.w(TAG, "pad key without engine: " + missingEngine.getMessage());
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.i(TAG, "onWindowFocusChanged: " + hasFocus);
        super.onWindowFocusChanged(hasFocus);
        hideSystemUI();
    }

    // --- helpers ------------------------------------------------------------

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(SYSTEM_UI_FLAGS);
    }

    /** True when the engine reported itself initialized during onCreate. */
    protected boolean hasEngineInitialized() {
        return ranInit;
    }

    public void requestPermission(String permission, PermissionCallback callback) {
        if (permission == null) {
            throw new NullPointerException("permission is null");
        }
        if (callback == null) {
            throw new NullPointerException("callback is null");
        }
        if (requestPermissionLauncher == null) {
            throw new IllegalStateException("requestPermissionLauncher not initialized");
        }
        Log.i(TAG, "requestPermission: " + permission);
        requestPermissionCallback = callback;
        requestPermissionLauncher.launch(permission);
    }
}
