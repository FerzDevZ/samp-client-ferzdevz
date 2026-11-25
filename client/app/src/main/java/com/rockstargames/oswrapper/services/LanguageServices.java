// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.services;

import com.rockstargames.oswrapper.GameActivityBase;

import java.util.Locale;

/**
 * Tells the engine which language to use, as the number its own tables are indexed by.
 *
 * The engine only ships text for the seven languages below. Anything else falls back to
 * English instead of failing, because an unsupported locale must not stop the game.
 */
public class LanguageServices {

    private static final int LANGUAGE_ENGLISH = 0;
    private static final int LANGUAGE_FRENCH = 1;
    private static final int LANGUAGE_GERMAN = 2;
    private static final int LANGUAGE_ITALIAN = 3;
    private static final int LANGUAGE_SPANISH = 4;
    private static final int LANGUAGE_RUSSIAN = 5;
    private static final int LANGUAGE_JAPANESE = 6;

    private final GameActivityBase activity;

    public LanguageServices(GameActivityBase activity) {
        if (activity == null) {
            throw new IllegalArgumentException("activity is null");
        }
        this.activity = activity;
    }

    public int getDeviceLocale() {
        String language = Locale.getDefault().getLanguage();
        if (language == null) {
            return LANGUAGE_ENGLISH;
        }
        switch (language) {
            case "fr":
                return LANGUAGE_FRENCH;
            case "de":
                return LANGUAGE_GERMAN;
            case "it":
                return LANGUAGE_ITALIAN;
            case "es":
                return LANGUAGE_SPANISH;
            case "ru":
                return LANGUAGE_RUSSIAN;
            case "ja":
                return LANGUAGE_JAPANESE;
            case "en":
            default:
                return LANGUAGE_ENGLISH;
        }
    }
}
