package com.ferzdevzgta.game.media;

import com.ferzdevzgta.game.Check;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Screenshot naming and the folder it lands in. */
public final class ShotFileTest {

    public static void run() throws Exception {
        Check.eq("folder name", "Screenshots", ShotFile.DIR_NAME);

        // 2026-02-11 14:22:33 UTC, but the formatter uses the phone's zone, so the assertion
        // checks the shape and the minute-level consistency instead of a fixed local string.
        String name = ShotFile.name(1770822153000L);
        Check.ok("starts with the prefix", name.startsWith("samp-"));
        Check.ok("png extension", name.endsWith(".png"));
        Check.ok("length is stable", name.length() == "samp-20260211-142233.png".length());
        Check.ok("only safe characters", name.matches("samp-[0-9]{8}-[0-9]{6}\\.png"));

        String later = ShotFile.name(1770822153000L + 1000L);
        Check.ok("seconds differ", !name.equals(later));
        Check.eq("same time same name", name, ShotFile.name(1770822153000L));

        Path tmp = Files.createTempDirectory("shots");
        try {
            File root = tmp.toFile();
            File dir = ShotFile.directory(root);
            Check.ok("directory created", dir.isDirectory());
            Check.eq("directory under the gta root", "Screenshots", dir.getName());

            File file = ShotFile.file(root, 1770822153000L);
            Check.ok("file lives in the folder", file.getParentFile().equals(dir));
            Check.ok("file does not exist yet", !file.exists());
            Check.ok("nothing wrote the file", !Files.exists(file.toPath()));

            // Two shots in the same second must still be one name, so the second write would
            // overwrite rather than leaving a second mystery file behind.
            Check.eq("file name matches", ShotFile.name(1770822153000L), file.getName());
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
                        // a temp dir that will be cleaned by the OS is not worth failing a test
                    }
                });
    }
}
