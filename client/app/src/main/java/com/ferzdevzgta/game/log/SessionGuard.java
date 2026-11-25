package com.ferzdevzgta.game.log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * A marker file that is written when the game starts and removed when it closes, so the next
 * launch can say whether the previous session ended on purpose.
 *
 * The Java crash handler cannot see a {@code libGame.so} segfault: the process is gone before any
 * of our code runs. This marker is the only honest signal that survives that, and the wording in
 * the diagnostics screen says exactly that. A process killed by the system looks the same as a
 * crash from here, so the text never claims it was a crash.
 */
public final class SessionGuard {

    public static final String FILE_NAME = "session.open";

    private SessionGuard() {
    }

    /** Writes the marker. Called when the game screen opens. */
    public static void begin(File dir, long started, int pid) throws IOException {
        if (dir == null) {
            throw new IllegalArgumentException("dir is null");
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir.getAbsolutePath());
        }
        String body = "started=" + started + "\npid=" + pid + "\n";
        Files.write(new File(dir, FILE_NAME).toPath(), body.getBytes(StandardCharsets.UTF_8));
    }

    /** Removes the marker. Called when the game screen closes on purpose. */
    public static void clear(File dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.deleteIfExists(new File(dir, FILE_NAME).toPath());
        } catch (IOException ignored) {
            // The next begin() overwrites it anyway.
        }
    }

    /**
     * Start time of a session that never cleared its marker, or -1 when there is nothing to
     * report. A marker written by the running process means the game is open right now, not that
     * it crashed, so it is ignored.
     */
    public static long unfinishedSince(File dir, int currentPid) {
        if (dir == null) {
            return -1L;
        }
        File marker = new File(dir, FILE_NAME);
        if (!marker.isFile()) {
            return -1L;
        }
        try {
            List<String> lines = Files.readAllLines(marker.toPath(), StandardCharsets.UTF_8);
            long started = -1L;
            int pid = -1;
            for (String line : lines) {
                String text = line.trim();
                if (text.startsWith("started=")) {
                    started = parseLong(text.substring("started=".length()), -1L);
                } else if (text.startsWith("pid=")) {
                    pid = (int) parseLong(text.substring("pid=".length()), -1);
                }
            }
            if (started <= 0 || pid == currentPid) {
                return -1L;
            }
            return started;
        } catch (IOException | RuntimeException unreadable) {
            return -1L;
        }
    }

    private static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }
}
