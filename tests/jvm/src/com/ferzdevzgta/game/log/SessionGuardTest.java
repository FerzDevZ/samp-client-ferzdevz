package com.ferzdevzgta.game.log;

import com.ferzdevzgta.game.Check;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The marker that survives a native crash and tells the next launch about it. */
public final class SessionGuardTest {

    public static void run() throws Exception {
        Path tmp = Files.createTempDirectory("session");
        try {
            File dir = tmp.toFile();
            Check.eq("nothing to report on a clean install", -1L,
                    SessionGuard.unfinishedSince(dir, 100));

            SessionGuard.begin(dir, 1770822153000L, 4242);
            Check.ok("marker exists after begin", new File(dir, SessionGuard.FILE_NAME).isFile());
            Check.eq("marker reports the start", 1770822153000L,
                    SessionGuard.unfinishedSince(dir, 100));

            SessionGuard.clear(dir);
            Check.ok("marker gone after clear", !new File(dir, SessionGuard.FILE_NAME).exists());
            Check.eq("clean close reports nothing", -1L,
                    SessionGuard.unfinishedSince(dir, 100));

            // The game is open in this very process: that is a live session, not a crash.
            SessionGuard.begin(dir, 1770822153000L, 777);
            Check.eq("same pid is treated as running", -1L, SessionGuard.unfinishedSince(dir, 777));
            Check.eq("another pid sees the unfinished session", 1770822153000L,
                    SessionGuard.unfinishedSince(dir, 778));

            Files.write(new File(dir, SessionGuard.FILE_NAME).toPath(),
                    "garbage\n".getBytes(StandardCharsets.UTF_8));
            Check.eq("unreadable marker is ignored", -1L, SessionGuard.unfinishedSince(dir, 100));

            new File(dir, SessionGuard.FILE_NAME).delete();
            Files.write(new File(dir, SessionGuard.FILE_NAME).toPath(),
                    "started=0\npid=1\n".getBytes(StandardCharsets.UTF_8));
            Check.eq("zero start is ignored", -1L, SessionGuard.unfinishedSince(dir, 100));

            Check.eq("null dir is safe", -1L, SessionGuard.unfinishedSince(null, 100));
            SessionGuard.clear(null);

            File missing = new File(dir, "nested/deep");
            SessionGuard.begin(missing, 1L, 2);
            Check.ok("begin creates its directory", missing.isDirectory());
        } finally {
            deleteTree(tmp);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        Files.walk(root)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // temp dir
                    }
                });
    }
}
