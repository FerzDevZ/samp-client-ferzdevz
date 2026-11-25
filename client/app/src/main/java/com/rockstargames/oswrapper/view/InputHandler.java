// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.view;

import android.graphics.PointF;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rockstargames.oswrapper.GameActivityBase;
import com.rockstargames.oswrapper.GameThread;

/**
 * Translates Android input into the engine's events: multi touch, gamepad buttons, gamepad
 * axes and the back button.
 *
 * The decompiled version counted controllers by building a Kotlin list of every device and
 * dropping nulls; a plain loop over the ids gives the same answer without the allocation,
 * which matters because the count is polled while input is streaming.
 */
public class InputHandler implements GameViewHandler {

    public static InputHandler INSTANCE = new InputHandler();

    private static final String TAG = "GameView.InputHandler";

    /** Sources that make a device a controller rather than a keyboard or a touch screen. */
    private static final int CONTROLLER_SOURCE_MASK =
            InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK | InputDevice.SOURCE_CLASS_JOYSTICK;

    /** Any device that reports the joystick class; used to tell a pad from the back key. */
    private static final int JOYSTICK_CLASS_MASK = InputDevice.SOURCE_CLASS_JOYSTICK;

    /** Dpad axes report a hat switch, not a button, so they are tracked as pressed state. */
    private static final float HAT_DEADZONE = 0.2f;

    /*
     * Gamepad button ids the engine expects. These are the engine's numbering, not Android's:
     * Android key codes are mapped onto them in toGamepadButton.
     */
    private static final int BUTTON_A = 0;
    private static final int BUTTON_B = 1;
    private static final int BUTTON_X = 2;
    private static final int BUTTON_Y = 3;
    private static final int BUTTON_START = 4;
    private static final int BUTTON_BACK = 5;
    private static final int BUTTON_L1 = 6;
    private static final int BUTTON_R1 = 7;
    private static final int BUTTON_DPAD_UP = 8;
    private static final int BUTTON_DPAD_DOWN = 9;
    private static final int BUTTON_DPAD_LEFT = 10;
    private static final int BUTTON_DPAD_RIGHT = 11;
    private static final int BUTTON_L3 = 12;
    private static final int BUTTON_R3 = 13;
    private static final int BUTTON_PLATFORM_BACK = 14;
    private static final int BUTTON_PLATFORM_MENU = 15;

    private static final int NO_BUTTON = -1;
    private static final int CONTROLLER_ID = 0;

    private final SparseArray<PointF> touches = new SparseArray<>();

    private boolean dpadUpPressed;
    private boolean dpadDownPressed;
    private boolean dpadLeftPressed;
    private boolean dpadRightPressed;

    private InputHandler() {}

    /** Number of connected controllers. Polled, so it allocates nothing per call. */
    public int getControllerCount() {
        int count = 0;
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && (device.getSources() & CONTROLLER_SOURCE_MASK) != 0) {
                count++;
            }
        }
        return count;
    }

    // --- key mapping --------------------------------------------------------

    /** Android key code to engine button id, or {@link #NO_BUTTON}. */
    private static int toGamepadButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return BUTTON_PLATFORM_BACK;
            case KeyEvent.KEYCODE_MENU:
                return BUTTON_PLATFORM_MENU;
            case KeyEvent.KEYCODE_BUTTON_MODE:
                return BUTTON_START;
            case KeyEvent.KEYCODE_BUTTON_A:
                return BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_B:
                return BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_X:
                return BUTTON_X;
            case KeyEvent.KEYCODE_BUTTON_Y:
                return BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_L1:
                return BUTTON_L1;
            case KeyEvent.KEYCODE_BUTTON_R1:
                return BUTTON_R1;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                return BUTTON_L3;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return BUTTON_R3;
            case KeyEvent.KEYCODE_BUTTON_START:
                return BUTTON_START;
            default:
                return NO_BUTTON;
        }
    }

    private static boolean fromJoystick(KeyEvent event) {
        return event != null && (event.getSource() & JOYSTICK_CLASS_MASK) != 0;
    }

    // --- key events ---------------------------------------------------------

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Back is handled on release so a held key cannot fire twice; consume it now.
        if (keyCode == KeyEvent.KEYCODE_BACK && !fromJoystick(event)) {
            return true;
        }
        int button = toGamepadButton(keyCode);
        if (button == NO_BUTTON) {
            return false;
        }
        GameThread.INSTANCE.onGamepadButtonDown(CONTROLLER_ID, button);
        return true;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !fromJoystick(event)) {
            Log.i(TAG, "onBackPressed");
            GameThread.INSTANCE.onBackButtonPressed();
            return true;
        }
        int button = toGamepadButton(keyCode);
        if (button == NO_BUTTON) {
            return false;
        }
        GameThread.INSTANCE.onGamepadButtonUp(CONTROLLER_ID, button);
        return true;
    }

    // --- gamepad motion -----------------------------------------------------

    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event == null || (event.getSource() & JOYSTICK_CLASS_MASK) == 0) {
            return false;
        }

        float stickX = event.getAxisValue(MotionEvent.AXIS_X);
        float stickY = event.getAxisValue(MotionEvent.AXIS_Y);
        float z = event.getAxisValue(MotionEvent.AXIS_Z);
        float rz = event.getAxisValue(MotionEvent.AXIS_RZ);
        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);

        if (hatX > HAT_DEADZONE) {
            GameThread.INSTANCE.onGamepadButtonDown(CONTROLLER_ID, BUTTON_DPAD_RIGHT);
            dpadRightPressed = true;
        } else if (hatX < -HAT_DEADZONE) {
            GameThread.INSTANCE.onGamepadButtonDown(CONTROLLER_ID, BUTTON_DPAD_LEFT);
            dpadLeftPressed = true;
        } else {
            if (dpadLeftPressed) {
                GameThread.INSTANCE.onGamepadButtonUp(CONTROLLER_ID, BUTTON_DPAD_LEFT);
            }
            if (dpadRightPressed) {
                GameThread.INSTANCE.onGamepadButtonUp(CONTROLLER_ID, BUTTON_DPAD_RIGHT);
            }
            dpadLeftPressed = false;
            dpadRightPressed = false;
        }

        if (hatY < -HAT_DEADZONE) {
            GameThread.INSTANCE.onGamepadButtonDown(CONTROLLER_ID, BUTTON_DPAD_UP);
            dpadUpPressed = true;
        } else if (hatY > HAT_DEADZONE) {
            GameThread.INSTANCE.onGamepadButtonDown(CONTROLLER_ID, BUTTON_DPAD_DOWN);
            dpadDownPressed = true;
        } else {
            if (dpadUpPressed) {
                GameThread.INSTANCE.onGamepadButtonUp(CONTROLLER_ID, BUTTON_DPAD_UP);
            }
            if (dpadDownPressed) {
                GameThread.INSTANCE.onGamepadButtonUp(CONTROLLER_ID, BUTTON_DPAD_DOWN);
            }
            dpadUpPressed = false;
            dpadDownPressed = false;
        }

        // The engine wants the strongest right-hand trigger, whatever axis the pad reports it on.
        float rightTrigger = max3(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_THROTTLE),
                event.getAxisValue(MotionEvent.AXIS_GAS));
        float leftTrigger = Math.max(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE));

        GameThread.INSTANCE.onGamepadAxesChanged(
                CONTROLLER_ID, stickX, stickY, z, rz, leftTrigger, rightTrigger);
        return true;
    }

    // --- touch events -------------------------------------------------------

    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event is null");
        }
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = event.getX(actionIndex);
                float y = event.getY(actionIndex);
                touches.put(pointerId, new PointF(x, y));
                GameThread.INSTANCE.onTouchStart(pointerId, x, y);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_OUTSIDE: {
                PointF last = touches.get(pointerId);
                if (last != null) {
                    GameThread.INSTANCE.onTouchEnd(pointerId, last.x, last.y);
                }
                touches.remove(pointerId);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int index = 0; index < event.getPointerCount(); index++) {
                    float x = event.getX(index);
                    float y = event.getY(index);
                    int id = event.getPointerId(index);
                    touches.put(id, new PointF(x, y));
                    GameThread.INSTANCE.onTouchMove(id, x, y);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                // Every pointer is gone at once; release the ones we were tracking.
                for (int index = 0; index < touches.size(); index++) {
                    int id = touches.keyAt(index);
                    PointF last = touches.get(id);
                    if (last != null) {
                        GameThread.INSTANCE.onTouchEnd(id, last.x, last.y);
                    }
                }
                touches.clear();
                return true;
            }
            default:
                return false;
        }
    }

    // --- GameViewHandler ----------------------------------------------------

    @Override
    public void setup(GameActivityBase activity) {
        // Input arrives through the view's own callbacks, there is nothing to register.
    }

    @Override
    public void onPause() {
        // Held touches must not survive a pause: the engine would never see the release.
        releaseTouches();
    }

    @Override
    public void onResume() {
        // Nothing to restore.
    }

    private void releaseTouches() {
        for (int index = 0; index < touches.size(); index++) {
            int id = touches.keyAt(index);
            PointF last = touches.get(id);
            if (last != null) {
                GameThread.INSTANCE.onTouchEnd(id, last.x, last.y);
            }
        }
        touches.clear();
    }

    private static float max3(float a, float b, float c) {
        return Math.max(a, Math.max(b, c));
    }
}
