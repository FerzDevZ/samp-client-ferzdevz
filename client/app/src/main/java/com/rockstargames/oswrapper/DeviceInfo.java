// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Vibrator;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Device facts that the engine reads once, at start-up.
 *
 * <p>The class name, the ten public fields and their types are fixed by
 * {@code libGame.so}, which reaches them through JNI field lookup. Renaming a field
 * breaks the engine silently, so the field block below is treated as a binary
 * interface and not as normal code.
 */
public class DeviceInfo {

    public static final String TAG = "DeviceInfo";

    private static final String CPU_MAX_FREQ = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq";

    public final int cpuFrequency;
    public final String hardware;
    public final boolean hasTouchScreen;
    public final boolean hasVibrator;
    public final boolean isPhone;
    public final boolean isTvDevice;
    public final String manufacturer;
    public final String model;
    public final int osVersion;
    public final String product;

    public DeviceInfo(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is null");
        }

        Configuration config = context.getResources().getConfiguration();

        this.cpuFrequency = readCpuMaxFrequency();
        this.osVersion = Build.VERSION.SDK_INT;

        this.model = orEmpty(Build.MODEL);
        this.product = orEmpty(Build.PRODUCT);
        this.manufacturer = orEmpty(Build.MANUFACTURER);
        this.hardware = orEmpty(Build.HARDWARE);

        this.isTvDevice = isTelevision(context);
        this.hasVibrator = hasVibrator(context);
        this.isPhone = (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                < Configuration.SCREENLAYOUT_SIZE_LARGE;
        this.hasTouchScreen = config.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH;
    }

    /**
     * Highest frequency the first core reports, in kHz, or 0 when the kernel does not
     * expose it. Several Android builds hide the file behind SELinux, so a zero here is
     * normal and must not stop start-up.
     */
    private static int readCpuMaxFrequency() {
        File file = new File(CPU_MAX_FREQ);
        if (!file.canRead()) {
            return 0;
        }
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
            return raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (IOException | NumberFormatException e) {
            Log.w(TAG, "cannot read " + CPU_MAX_FREQ + ": " + e.getMessage());
            return 0;
        }
    }

    private static boolean isTelevision(Context context) {
        UiModeManager manager = context.getSystemService(UiModeManager.class);
        return manager != null
                && manager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private static boolean hasVibrator(Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        return vibrator != null && vibrator.hasVibrator();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
