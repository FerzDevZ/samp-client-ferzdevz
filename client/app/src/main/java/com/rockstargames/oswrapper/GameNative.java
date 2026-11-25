// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper;

import android.content.res.AssetManager;
import android.view.Surface;

import com.bytedance.shadowhook.ShadowHook;

/**
 * The engine's Java-side entry points into native code.
 *
 * Every method here is a declaration only: the bodies live in libGame.so, which exports one
 * `Java_com_rockstargames_oswrapper_GameNative_<name>` symbol per method. A declaration
 * whose symbol is missing throws UnsatisfiedLinkError the first time it is called, not at
 * load time, so `bin/jni-pins.py` compares this file against `nm -D libGame.so` instead of
 * waiting for a phone to reach that code path.
 */
public final class GameNative {

    /**
     * Kept because decompiled callers and the engine both expect the singleton to exist.
     * Nothing reads it; the class is all statics.
     */
    public static final GameNative INSTANCE = new GameNative();

    static {
        // ShadowHook has to be installed before libGame.so runs its own initialiser.
        ShadowHook.init(new ShadowHook.ConfigBuilder().setMode(ShadowHook.Mode.UNIQUE).build());
        System.loadLibrary("Game");
        System.loadLibrary("samp");
    }

    private GameNative() {
    }

    // --- lifecycle ----------------------------------------------------------

    public static native boolean implIsInitialized();

    public static native void implOnInitialSetup(
            DeviceInfo deviceInfo,
            AssetManager assets,
            String[] assetPackNames,
            String[] assetPackPaths);

    public static native void implOnActivityCreated(
            GamePlatformServices services, boolean firstInit);

    public static native void implOnActivityDestroyed();

    public static native void implOnPause();

    public static native void implOnResume();

    public static native void implOnLowMemory();

    public static native void implOnBackButtonPressed();

    // --- frame and surface --------------------------------------------------

    public static native void implOnDrawFrame(float dt);

    public static native void implOnSurfaceCreated();

    public static native void implOnSurfaceChanged(Surface surface, int width, int height);

    public static native void implOnSurfaceDestroyed();

    // --- input --------------------------------------------------------------

    public static native void implOnTouchStart(int pointerId, float x, float y);

    public static native void implOnTouchMove(int pointerId, float x, float y);

    public static native void implOnTouchEnd(int pointerId, float x, float y);

    public static native void implOnAccelerometerChanged(float x, float y, float z);

    /**
     * Gamepad callbacks. The first three were exported by libGame.so from the start with no Java
     * declaration, so a connected pad was invisible to the game. Signatures were read from the
     * binary: onConnect(int), onDisconnect(int), onResume(std::vector<int>).
     */
    public static native void implOnGamepadConnected(int controllerId);

    public static native void implOnGamepadDisconnected(int controllerId);

    public static native void implOnGamepadResume(int[] controllerIds);

    public static native void implOnGamepadAxesChanged(
            int controllerId,
            float x,
            float y,
            float z,
            float rz,
            float triggerL,
            float triggerR);

    public static native void implOnGamepadButtonDown(int controllerId, int buttonId);

    public static native void implOnGamepadButtonUp(int controllerId, int buttonId);

    // --- network ------------------------------------------------------------

    public static native void implOnNetworkChanged(int network);

    public static native void implOnHttpRequestResponse(
            int id,
            int statusCode,
            String statusLine,
            String[] headerNames,
            String[] headerValues);

    public static native void implOnHttpRequestData(int id, byte[] data);

    public static native void implOnHttpRequestError(int id, int statusCode);

    public static native void implOnHttpRequestFinished(int id);

    // --- playlists ----------------------------------------------------------

    public static native void implOnPlaylistOpenComplete(boolean available, int count);

    // --- Rockstar account ---------------------------------------------------
    // Declared because the engine calls them. This build has no account, so the Java side
    // reports "signed out" and never produces the completions.

    public static native void implOnRockstarSetup(String environment, String rockstarId);

    public static native void implOnRockstarInitialComplete();

    public static native void implOnRockstarSignInComplete();

    public static native void implOnRockstarSignOutComplete();

    public static native void implOnRockstarIdChanged(String id);

    public static native void implOnRockstarTicketChanged(String ticket);

    public static native void implOnRockstarStateChanged(boolean pauseGameplay);

    public static native void implOnRockstarGateComplete(int id, boolean pass);

    public static native void implOnRockstarCloudDisabledComplete();

    public static native void implOnRockstarAccountDeletionComplete();
}
