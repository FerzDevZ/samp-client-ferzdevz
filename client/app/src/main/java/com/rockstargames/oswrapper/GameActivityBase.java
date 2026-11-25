// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.ferzdevzgta.game.R;
import com.nvidia.devtech.NvAPKFileHelper;
import com.nvidia.devtech.NvUtil;

import java.io.File;

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

        view = findViewById(R.id.viewGame);
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

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
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
