package com.ferzdevzgta.game.input;

import com.ferzdevzgta.game.Check;

/** Pins the pad rules: deadzone, trigger normalisation, axis order and source detection. */
public final class GamepadMapTest {

    public static void run() {
        // A resting stick must read as zero, otherwise the camera drifts on its own.
        Check.eq("rest is zero", 0f, GamepadMap.deadzone(0f));
        Check.eq("small drift is zero", 0f, GamepadMap.deadzone(0.10f));
        Check.eq("deadzone edge is zero", 0f, GamepadMap.deadzone(GamepadMap.DEADZONE));
        Check.ok("half push moves", GamepadMap.deadzone(0.575f) > 0.49f);
        Check.eq("full push is one", 1f, GamepadMap.deadzone(1f));
        Check.eq("full left is minus one", -1f, GamepadMap.deadzone(-1f));
        Check.eq("beyond full is clamped", 1f, GamepadMap.deadzone(3.7f));
        Check.eq("NaN does not leak", 0f, GamepadMap.deadzone(Float.NaN));

        // Pads report triggers as 0..1 or -1..1; both ends have to become 0..1.
        Check.eq("trigger at rest", 0f, GamepadMap.trigger(0f));
        Check.eq("negative rest becomes zero", 0f, GamepadMap.trigger(-1f));
        Check.eq("half trigger", 0.25f, GamepadMap.trigger(-0.5f));
        Check.eq("full trigger", 1f, GamepadMap.trigger(1f));
        Check.eq("trigger clamped above", 1f, GamepadMap.trigger(4f));

        float[] axes = GamepadMap.axes(0.9f, -0.9f, 0f, 0f, -1f, 1f);
        Check.eq("six axes", GamepadMap.AXES, axes.length);
        Check.ok("x forward", axes[0] > 0.8f);
        Check.ok("y reversed", axes[1] < -0.8f);
        Check.eq("z at rest", 0f, axes[2]);
        Check.eq("trigger left down", 0f, axes[4]);
        Check.eq("trigger right up", 1f, axes[5]);
        Check.eq("axes summary", "0.88 -0.88 0.00 0.00 0.00 1.00", GamepadMap.describeAxes(axes));
        Check.eq("short array is reported", "no axes", GamepadMap.describeAxes(new float[]{0f}));

        // Keyboards also raise key events; only a JOYSTICK source may be treated as a pad.
        Check.ok("joystick source", GamepadMap.isGamepad(GamepadMap.SOURCE_JOYSTICK));
        Check.ok("joystick with extra bits", GamepadMap.isGamepad(GamepadMap.SOURCE_JOYSTICK | 0x1));
        Check.ok("keyboard is not a pad", !GamepadMap.isGamepad(0x00000101));
        Check.ok("touchscreen is not a pad", !GamepadMap.isGamepad(0x00001002));
        Check.ok("zero is not a pad", !GamepadMap.isGamepad(0));
    }
}
