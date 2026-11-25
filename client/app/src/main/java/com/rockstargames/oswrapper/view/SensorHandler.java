// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.view;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.rockstargames.oswrapper.GameActivityBase;
import com.rockstargames.oswrapper.GameThread;
import com.rockstargames.oswrapper.GameView;

/**
 * Feeds the accelerometer to the engine with the axes remapped for the display rotation.
 *
 * The decompiled version tracked an "active" flag it never assigned, so the resume path
 * registered the listener on every call while the pause path never unregistered it: the
 * sensor kept streaming into the engine after the game was backgrounded. Registration now
 * sets the flag and unregistration clears it, which is what the flag was for.
 */
public final class SensorHandler implements SensorEventListener, GameViewHandler {

    private static final String TAG = "GameView.SensorHandler";

    private final GameView view;
    private final SensorManager sensorManager;
    private final WindowManager windowManager;
    private final Sensor accelerometer;

    private int displayRotation;
    private boolean sensorsRegistered;

    public SensorHandler(Context context, GameView view) {
        if (context == null) {
            throw new NullPointerException("context is null");
        }
        if (view == null) {
            throw new NullPointerException("view is null");
        }
        this.view = view;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.accelerometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void enableSensors() {
        if (accelerometer == null || sensorManager == null) {
            return;
        }
        if (sensorsRegistered) {
            return;
        }
        sensorsRegistered = sensorManager.registerListener(
                this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        Log.i(TAG, "accelerometer registered = " + sensorsRegistered);
        updateRotation();
    }

    public void disableSensors() {
        if (!sensorsRegistered || sensorManager == null) {
            return;
        }
        sensorManager.unregisterListener(this);
        sensorsRegistered = false;
        Log.i(TAG, "accelerometer unregistered");
    }

    public void updateRotation() {
        int rotation;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Display display = view.getDisplay();
            rotation = display != null ? display.getRotation() : 0;
        } else {
            rotation = windowManager.getDefaultDisplay().getRotation();
        }
        displayRotation = rotation;
    }

    // --- SensorEventListener ------------------------------------------------

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // The engine only reads the accelerometer values.
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null
                || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        float rawX = event.values[0];
        float rawY = event.values[1];
        float rawZ = event.values[2];

        float x;
        float y;
        switch (displayRotation) {
            case 0:
            case 2:
                // Portrait and reverse portrait: the device axes sit 90 degrees from the screen.
                x = rawY;
                y = -rawX;
                break;
            case 1:
            case 3:
            default:
                x = rawX;
                y = rawY;
                break;
        }
        float z = rawZ;

        // Mirrored so that tilting left and tilting right do not produce the same quadrant.
        if (x < 0) {
            y = -y;
        }

        GameThread.INSTANCE.onAccelerometerChanged(x, y, z);
    }

    // --- GameViewHandler ----------------------------------------------------

    @Override
    public void setup(GameActivityBase activity) {
        if (activity == null) {
            throw new NullPointerException("activity is null");
        }
        enableSensors();
    }

    @Override
    public void onPause() {
        disableSensors();
    }

    @Override
    public void onResume() {
        enableSensors();
    }

    public boolean getSensorsActive() {
        return sensorsRegistered;
    }

    public GameView getView() {
        return view;
    }
}
