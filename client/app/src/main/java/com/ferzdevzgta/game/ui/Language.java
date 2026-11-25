package com.ferzdevzgta.game.ui;

import java.util.Locale;

/**
 * The two languages the app screens ship in, and the rules that map a stored tag or a device
 * locale onto one of them. No Android imports, so the rules run under plain {@code javac} in
 * bin/self-test.sh, where a wrong default actually fails a build.
 */
public final class Language {

    public static final String INDONESIAN = "in";
    public static final String ENGLISH = "en";

    /** Order shown in the picker. */
    public static final String[] TAGS = {INDONESIAN, ENGLISH};

    private Language() {
    }

    /**
     * Maps whatever was stored (or handed in by the system) to a supported tag.
     * Returns null for an empty value, which means "follow the device locale".
     * Regions and legacy codes are folded: {@code id-ID} and {@code in} both mean Indonesian.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String tag = raw.trim().toLowerCase(Locale.ROOT);
        if (tag.isEmpty() || "system".equals(tag) || "auto".equals(tag)) {
            return null;
        }
        int cut = tag.indexOf('-');
        if (cut >= 0) {
            tag = tag.substring(0, cut);
        }
        cut = tag.indexOf('_');
        if (cut >= 0) {
            tag = tag.substring(0, cut);
        }
        if ("in".equals(tag) || "id".equals(tag)) {
            return INDONESIAN;
        }
        if ("en".equals(tag)) {
            return ENGLISH;
        }
        return ENGLISH;
    }

    /** Indonesian device locale means Indonesian UI, everything else means English. */
    public static String forLocale(Locale locale) {
        if (locale == null) {
            return ENGLISH;
        }
        String code = locale.getLanguage();
        if ("in".equals(code) || "id".equals(code)) {
            return INDONESIAN;
        }
        return ENGLISH;
    }

    /** The tag the app should use now: explicit choice first, device locale second. */
    public static String resolve(String stored, Locale device) {
        String explicit = normalize(stored);
        return explicit != null ? explicit : forLocale(device);
    }

    public static int indexOf(String tag) {
        String norm = normalize(tag);
        for (int i = 0; i < TAGS.length; i++) {
            if (TAGS[i].equals(norm)) {
                return i;
            }
        }
        return 1;
    }
}
