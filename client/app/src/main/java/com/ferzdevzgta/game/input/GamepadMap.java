package com.ferzdevzgta.game.input;

/**
 * Gamepad shape rules, with no Android imports so {@code tests/jvm} can pin them.
 *
 * The engine's callbacks were confirmed by disassembling libGame.so before anything was declared:
 *
 *   implOnGamepadConnected(int)            -> GamepadProvider::onConnect(int)
 *   implOnGamepadDisconnected(int)         -> GamepadProvider::onDisconnect(int)
 *   implOnGamepadResume(int[])             -> GamepadProvider::onResume(std::vector<int>)
 *   implOnGamepadAxesChanged(int, 6 floats)
 *   implOnGamepadButtonDown/Up(int, int)   -> onButtonChanged(int, int, bool)
 *
 * The button id is the platform button code, which on Android is the {@code KeyEvent} code: this
 * is the Android build of the engine, so OS_GamepadButton takes the value the OS already assigned.
 */
public final class GamepadMap {

    /** Android's InputDevice.SOURCE_JOYSTICK, repeated here to keep this file Android-free. */
    public static final int SOURCE_JOYSTICK = 0x01000010;

    /** Sticks rest around 0.08 on a worn pad; below this the engine would see drift as movement. */
    public static final float DEADZONE = 0.15f;

    /** Six axes, in the order the engine's callback expects. */
    public static final int AXES = 6;

    private GamepadMap() {
    }

    public static boolean isGamepad(int sources) {
        return (sources & SOURCE_JOYSTICK) == SOURCE_JOYSTICK;
    }

    /** Rescales past the deadzone so the remaining travel still reaches 1.0 at the edge. */
    public static float deadzone(float raw) {
        if (Float.isNaN(raw)) {
            return 0f;
        }
        float clamped = Math.max(-1f, Math.min(1f, raw));
        float magnitude = Math.abs(clamped);
        if (magnitude <= DEADZONE) {
            return 0f;
        }
        return Math.signum(clamped) * (magnitude - DEADZONE) / (1f - DEADZONE);
    }

    /** Triggers rest at either 0 or -1 depending on the pad, so both ends map to 0. */
    public static float trigger(float raw) {
        if (Float.isNaN(raw)) {
            return 0f;
        }
        float value = raw < 0f ? (raw + 1f) / 2f : raw;
        return Math.max(0f, Math.min(1f, value));
    }

    public static float[] axes(float x, float y, float z, float rz, float triggerL, float triggerR) {
        return new float[]{
                deadzone(x), deadzone(y), deadzone(z), deadzone(rz),
                trigger(triggerL), trigger(triggerR)
        };
    }

    /** Number of axes a pad reports, for the diagnostics screen. */
    public static String describeAxes(float[] axes) {
        if (axes == null || axes.length < AXES) {
            return "no axes";
        }
        StringBuilder sb = new StringBuilder(48);
        for (int i = 0; i < AXES; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(java.util.Locale.US, "%.2f", axes[i]));
        }
        return sb.toString();
    }
}
