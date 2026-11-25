package com.rockstargames.oswrapper.input;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.ferzdevzgta.game.input.GamepadMap;
import com.rockstargames.oswrapper.GameNative;

/**
 * Forwards pad events to the engine. Three of these callbacks existed in libGame.so from the
 * start and had no Java caller, so a controller was a dead accessory: the pad connected, Android
 * reported it, and the game never heard about it.
 *
 * COORDINATES: six axes in the order the engine's signature expects. Triggers are read from
 * AXIS_LTRIGGER/AXIS_RTRIGGER when present and from AXIS_Z/AXIS_RZ otherwise, because a pad
 * without analog triggers still reports braking on Z.
 */
public final class GamepadInput {

    private static int lastController = -1;

    private GamepadInput() {
    }

    /** Devices that report JOYSTICK; a keyboard also raises key events and must not count. */
    public static boolean isPad(InputDevice device) {
        return device != null && GamepadMap.isGamepad(device.getSources());
    }

    public static boolean onMotion(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (!isPad(device)) {
            return false;
        }
        if (event.getSource() != InputDevice.SOURCE_JOYSTICK) {
            return false;
        }
        int id = device.getId();
        lastController = id;
        float triggerL = event.getAxisValue(MotionEvent.AXIS_LTRIGGER, 0);
        float triggerR = event.getAxisValue(MotionEvent.AXIS_RTRIGGER, 0);
        if (triggerL == 0f && triggerR == 0f) {
            triggerL = -event.getAxisValue(MotionEvent.AXIS_Z, 0);
            triggerR = -event.getAxisValue(MotionEvent.AXIS_RZ, 0);
        }
        float[] axes = GamepadMap.axes(
                event.getAxisValue(MotionEvent.AXIS_X, 0),
                event.getAxisValue(MotionEvent.AXIS_Y, 0),
                event.getAxisValue(MotionEvent.AXIS_Z, 0),
                event.getAxisValue(MotionEvent.AXIS_RZ, 0),
                triggerL,
                triggerR);
        GameNative.implOnGamepadAxesChanged(id, axes[0], axes[1], axes[2], axes[3], axes[4], axes[5]);
        return true;
    }

    /** Returns true when the event belonged to a pad and was passed to the engine. */
    public static boolean onKey(KeyEvent event, boolean down) {
        if (!isPad(event.getDevice())) {
            return false;
        }
        if (event.getRepeatCount() > 0) {
            return true;
        }
        int id = event.getDevice().getId();
        if (down) {
            GameNative.implOnGamepadButtonDown(id, event.getKeyCode());
        } else {
            GameNative.implOnGamepadButtonUp(id, event.getKeyCode());
        }
        return true;
    }

    public static void onConnected(int controllerId) {
        lastController = controllerId;
        GameNative.implOnGamepadConnected(controllerId);
    }

    public static void onDisconnected(int controllerId) {
        GameNative.implOnGamepadDisconnected(controllerId);
        if (lastController == controllerId) {
            lastController = -1;
        }
    }

    /**
     * The engine rebuilds its pad table from the ids it is given, so a resume without this list
     * left a controller connected but unlisted after the app came back from the background.
     */
    public static void onResume() {
        int[] ids = InputDevice.getDeviceIds();
        int[] pads = new int[ids.length];
        int count = 0;
        for (int id : ids) {
            if (isPad(InputDevice.getDevice(id))) {
                pads[count++] = id;
            }
        }
        int[] trimmed = new int[count];
        System.arraycopy(pads, 0, trimmed, 0, count);
        GameNative.implOnGamepadResume(trimmed);
    }

    /** True when a pad is plugged in right now; the diagnostics screen shows this. */
    public static boolean anyConnected() {
        for (int id : InputDevice.getDeviceIds()) {
            if (isPad(InputDevice.getDevice(id))) {
                return true;
            }
        }
        return false;
    }

    public static String describeConnected() {
        StringBuilder sb = new StringBuilder();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (!isPad(device)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(device.getName()).append(" #").append(id);
        }
        return sb.toString();
    }
}
