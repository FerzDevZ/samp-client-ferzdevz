// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.rockstargames.oswrapper.view.GameViewHandler;
import com.rockstargames.oswrapper.view.InputHandler;
import com.rockstargames.oswrapper.view.MovieHandler;
import com.rockstargames.oswrapper.view.PlaylistHandler;
import com.rockstargames.oswrapper.view.SensorHandler;
import com.rockstargames.oswrapper.view.SplashScreenHandler;

import java.util.Arrays;
import java.util.List;

/**
 * The GL surface plus the handlers that feed it: input, sensors, splash screen, movies and
 * playlists. Lifecycle for every handler is driven from here, in list order, so adding a
 * handler means adding it in one place.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback2 {

    private static final String TAG = "GameView";

    /** Pixel format for a GL surface. SurfaceHolder.setFormat takes the framework constant. */
    private static final int FORMAT_OPAQUE_GL = 3;

    private final List<GameViewHandler> handlers;
    private final InputHandler inputHandler;
    private final MovieHandler movieHandler;
    private final PlaylistHandler playlistHandler;
    private final SensorHandler sensorHandler;
    private final SplashScreenHandler splashScreenHandler;

    private GameActivityBase activity;
    private boolean hasFocus = true;

    public GameView(Context context, AttributeSet attributes) {
        super(context, attributes);
        getHolder().addCallback(this);

        this.inputHandler = InputHandler.INSTANCE;
        this.movieHandler = new MovieHandler();
        this.playlistHandler = new PlaylistHandler();
        this.sensorHandler = new SensorHandler(context, this);
        this.splashScreenHandler = new SplashScreenHandler();

        this.handlers = Arrays.asList(
                inputHandler, movieHandler, playlistHandler, sensorHandler, splashScreenHandler);
    }

    // --- accessors ----------------------------------------------------------

    public boolean getHasFocus() {
        return hasFocus;
    }

    public InputHandler getInputHandler() {
        return inputHandler;
    }

    public MovieHandler getMovieHandler() {
        return movieHandler;
    }

    public PlaylistHandler getPlaylistHandler() {
        return playlistHandler;
    }

    public SensorHandler getSensorHandler() {
        return sensorHandler;
    }

    public SplashScreenHandler getSplashScreenHandler() {
        return splashScreenHandler;
    }

    // --- setup --------------------------------------------------------------

    /** Called once the activity has a services object; every handler gets the activity. */
    public void setup(GameActivityBase activity) {
        if (activity == null) {
            throw new IllegalArgumentException("activity is null");
        }
        this.activity = activity;
        getHolder().setFormat(FORMAT_OPAQUE_GL);
        getHolder().addCallback(this);
        setKeepScreenOn(true);
        setFocusableInTouchMode(true);
        requestFocus();
        for (GameViewHandler handler : handlers) {
            handler.setup(activity);
        }
    }

    // --- lifecycle ----------------------------------------------------------

    public void onPause() {
        Log.i(TAG, "onPause");
        GameThread.INSTANCE.onPause();
        for (GameViewHandler handler : handlers) {
            handler.onPause();
        }
    }

    public void onResume() {
        Log.i(TAG, "onResume");
        GameThread.INSTANCE.onResume();
        for (GameViewHandler handler : handlers) {
            handler.onResume();
        }
    }

    private void setHasFocus(boolean focused) {
        Log.i(TAG, "hasFocus = " + focused);
        this.hasFocus = focused;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        Log.i(TAG, "onWindowFocusChanged: " + hasWindowFocus);
        setHasFocus(hasWindowFocus);
        if (hasFocus) {
            onResume();
        } else {
            onPause();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged: " + newConfig);
        sensorHandler.updateRotation();
    }

    // --- input --------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event is null");
        }
        return inputHandler.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return inputHandler.onGenericMotionEvent(event) || super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return inputHandler.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return inputHandler.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    // --- surface ------------------------------------------------------------

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        requireHolder(holder);
        Log.i(TAG, "surfaceCreated: " + holder.getSurface());
        GameThread.INSTANCE.onSurfaceCreated();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        requireHolder(holder);
        Surface surface = holder.getSurface();
        if (surface == null) {
            throw new IllegalStateException("surface is null in surfaceChanged");
        }
        Log.i(TAG, "surfaceChanged: " + width + "x" + height);
        GameThread.INSTANCE.onSurfaceChanged(surface, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        requireHolder(holder);
        Log.i(TAG, "surfaceDestroyed");
        GameThread.INSTANCE.onSurfaceDestroyed();
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        requireHolder(holder);
    }

    @Override
    public void surfaceRedrawNeededAsync(SurfaceHolder holder, Runnable drawingFinished) {
        requireHolder(holder);
        if (drawingFinished == null) {
            throw new IllegalArgumentException("drawingFinished is null");
        }
        GameThread.INSTANCE.onSurfaceRedrawNeededAsync(drawingFinished);
    }

    private static void requireHolder(SurfaceHolder holder) {
        if (holder == null) {
            throw new IllegalArgumentException("holder is null");
        }
    }
}
