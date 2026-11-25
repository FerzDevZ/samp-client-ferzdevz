package com.ferzdevzgta.game.ui;

import com.ferzdevzgta.game.Check;

import java.util.Locale;

public final class LanguageTest {

    private LanguageTest() {
    }

    private static void eq(String what, Object expected, Object actual) {
        Check.eq(what, expected, actual);
    }

    public static void run() {
        // Explicit choices survive, including the legacy Indonesian code and a region tag.
        eq("in stays in", "in", Language.normalize("in"));
        eq("legacy id folds to in", "in", Language.normalize("id"));
        eq("region is dropped", "in", Language.normalize("id-ID"));
        eq("case and separator are folded", "in", Language.normalize("IN_br"));
        eq("en stays en", "en", Language.normalize("en"));
        eq("en region is dropped", "en", Language.normalize("en-GB"));

        // Nothing stored means "follow the device", which is a null, not a default.
        eq("null means follow system", null, Language.normalize(null));
        eq("empty means follow system", null, Language.normalize(""));
        eq("blank means follow system", null, Language.normalize("   "));
        eq("system keyword means follow system", null, Language.normalize("system"));
        eq("auto keyword means follow system", null, Language.normalize("auto"));

        // An unsupported language must land on English, never on a blank screen.
        eq("unsupported tag falls back to en", "en", Language.normalize("fr"));
        eq("unsupported region falls back to en", "en", Language.normalize("ru-RU"));

        eq("indonesian device", "in", Language.forLocale(new Locale("in", "ID")));
        eq("legacy indonesian device", "in", Language.forLocale(new Locale("id")));
        eq("english device", "en", Language.forLocale(Locale.US));
        eq("other device means en", "en", Language.forLocale(Locale.JAPAN));
        eq("null locale means en", "en", Language.forLocale(null));

        // Precedence: a stored choice beats the device.
        eq("stored en beats device in", "en", Language.resolve("en", new Locale("in", "ID")));
        eq("stored in beats device en", "in", Language.resolve("in", Locale.US));
        eq("no choice follows device en", "en", Language.resolve("", Locale.US));
        eq("no choice follows device in", "in", Language.resolve(null, new Locale("in")));

        eq("indonesian is first in the picker", 0, Language.indexOf("in"));
        eq("english is second in the picker", 1, Language.indexOf("en"));
        eq("unknown lands on the english slot", 1, Language.indexOf("de"));
        eq("exactly two languages are offered", 2, Language.TAGS.length);
    }
}
