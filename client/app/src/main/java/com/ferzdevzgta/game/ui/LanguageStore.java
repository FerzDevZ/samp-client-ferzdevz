package com.ferzdevzgta.game.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * Persists the screen language and hands it to AppCompat.
 *
 * <p>Stored value is a language tag, or an empty string meaning "follow the device". The
 * stored value is what survives a reinstall-free upgrade; the applied value is what AppCompat
 * re-creates activities with, so a switch takes effect immediately without a restart.
 */
public final class LanguageStore {

    private static final String PREFS = "ui_language";
    private static final String KEY = "tag";

    private LanguageStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Empty string when the player never chose, which means the device locale wins. */
    public static String stored(Context context) {
        String raw = prefs(context).getString(KEY, "");
        return raw == null ? "" : raw;
    }

    public static String current(Context context) {
        return Language.resolve(stored(context), Locale.getDefault());
    }

    /** Saves and applies. AppCompat recreates the visible activity, so call it from the UI. */
    public static void choose(Context context, String tag) {
        String norm = Language.normalize(tag);
        prefs(context).edit().putString(KEY, norm == null ? "" : norm).apply();
        apply(context);
    }

    /**
     * Applies the stored choice to the whole app. Called from Application.onCreate so the very
     * first activity is created with the right resources, and again after a switch.
     */
    public static void apply(Context context) {
        String stored = stored(context);
        String tag = Language.resolve(stored, Locale.getDefault());
        LocaleListCompat desired = LocaleListCompat.forLanguageTags(tag);
        if (!desired.equals(AppCompatDelegate.getApplicationLocales())) {
            AppCompatDelegate.setApplicationLocales(desired);
        }
    }
}
