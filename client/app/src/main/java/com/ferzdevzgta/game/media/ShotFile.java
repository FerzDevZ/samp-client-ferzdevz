package com.ferzdevzgta.game.media;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Where a screenshot goes and what it is called.
 *
 * Pure so the naming rule can be pinned by a JVM test: the file lands in
 * {@code GTA/Screenshots}, carries the moment it was taken, and never contains a character the
 * filesystem refuses. Sharing apps sort by name, so the timestamp is the leading element.
 */
public final class ShotFile {

    public static final String DIR_NAME = "Screenshots";
    public static final String PREFIX = "samp-";

    private ShotFile() {
    }

    /** {@code <gtaRoot>/Screenshots}, created if missing. */
    public static File directory(File gtaRoot) {
        File dir = new File(gtaRoot, DIR_NAME);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** {@code samp-20260211-142233.png}, in the phone's local time so it matches the gallery. */
    public static String name(long epochMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        return PREFIX + format.format(new Date(epochMillis)) + ".png";
    }

    public static File file(File gtaRoot, long epochMillis) {
        return new File(directory(gtaRoot), name(epochMillis));
    }
}
