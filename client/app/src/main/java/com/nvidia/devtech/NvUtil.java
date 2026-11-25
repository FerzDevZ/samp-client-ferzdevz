// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.nvidia.devtech;

import android.app.Activity;

import java.util.HashMap;
import java.util.Map;

/**
 * Small string store the engine reads and writes through JNI.
 *
 * <p>{@code libGame.so} looks this class up by name and calls {@code getInstance()},
 * so the singleton accessor and the six public methods are a binary interface. The
 * map behind them is ours.
 */
public class NvUtil {

    private static final NvUtil INSTANCE = new NvUtil();

    private final Map<String, String> appLocalValues = new HashMap<>();
    private Activity activity;

    private NvUtil() {
    }

    public static NvUtil getInstance() {
        return INSTANCE;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public String getAppLocalValue(String key) {
        return appLocalValues.get(key);
    }

    public boolean hasAppLocalValue(String key) {
        return appLocalValues.containsKey(key);
    }

    public void setAppLocalValue(String key, String value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            appLocalValues.remove(key);
        } else {
            appLocalValues.put(key, value);
        }
    }

    /**
     * Reads an intent extra from the activity that was handed to {@link #setActivity}.
     *
     * <p>Returns null when no activity is set. The value is a launch parameter, so a
     * missing one is a normal case, not a crash.
     */
    public String getParameter(String name) {
        Activity current = activity;
        if (current == null || current.getIntent() == null) {
            return null;
        }
        return current.getIntent().getStringExtra(name);
    }
}
