package com.ferzdevzgta.game.log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Keeps the native log from growing without limit on a phone that has been
 * playing for weeks. The client writes {@code /storage/emulated/0/GTA/logcat.txt}
 * and never truncates it; a 400 MB log is not a bug report, it is a filled disk.
 *
 * Rotation happens before the game starts, so the file the engine has open is
 * never moved underneath it.
 */
public final class LogRotation {

    public static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024;
    public static final int DEFAULT_KEEP = 2;

    private LogRotation() {
    }

    /**
     * @return true when the log was large enough to rotate
     */
    public static boolean rotate(File log, long maxBytes, int keep) throws IOException {
        if (log == null || !log.isFile() || log.length() < maxBytes) {
            return false;
        }
        if (keep <= 0) {
            Files.deleteIfExists(log.toPath());
            return true;
        }
        // oldest first: log.2 goes away, log.1 becomes log.2, log becomes log.1
        for (int i = keep; i >= 2; i--) {
            File older = new File(log.getPath() + "." + i);
            File newer = new File(log.getPath() + "." + (i - 1));
            Files.deleteIfExists(older.toPath());
            if (newer.isFile()) {
                Files.move(newer.toPath(), older.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(log.toPath(), new File(log.getPath() + ".1").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    public static boolean rotate(File log) throws IOException {
        return rotate(log, DEFAULT_MAX_BYTES, DEFAULT_KEEP);
    }
}
