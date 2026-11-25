package com.ferzdevzgta.game.log;

import com.ferzdevzgta.game.Check;

import java.io.File;
import java.nio.file.Files;

/** Log rotation: a phone that plays for a month must not fill its disk. */
public final class LogRotationTest {

    public static void run() throws Exception {
        File dir = Files.createTempDirectory("log").toFile();
        File log = new File(dir, "logcat.txt");

        // below the limit: untouched
        write(log, 1000, 'a');
        Check.ok("small log not rotated", !LogRotation.rotate(log, 2048, 2));
        Check.ok("small log still there", log.isFile());
        Check.eq("small log size unchanged", 1000L, log.length());

        // over the limit: becomes .1
        write(log, 4096, 'b');
        Check.ok("large log rotated", LogRotation.rotate(log, 2048, 2));
        Check.ok("rotated file kept as .1", new File(dir, "logcat.txt.1").isFile());
        Check.ok("original recreated empty (not moved)", !log.isFile() || log.length() == 0);

        // second rotation pushes .1 to .2
        write(log, 4096, 'c');
        LogRotation.rotate(log, 2048, 2);
        Check.ok(".1 holds the newest rotation", firstByte(new File(dir, "logcat.txt.1")) == 'c');
        Check.ok(".2 holds the previous one", firstByte(new File(dir, "logcat.txt.2")) == 'b');

        // third rotation drops the oldest instead of growing forever
        write(log, 4096, 'd');
        LogRotation.rotate(log, 2048, 2);
        Check.ok(".2 is now the c log, b is gone", firstByte(new File(dir, "logcat.txt.2")) == 'c');
        Check.eq("only keep backups exist, live log is not recreated", 2, count(dir));

        // keep = 0 means delete
        write(log, 4096, 'e');
        LogRotation.rotate(log, 2048, 0);
        Check.ok("keep=0 deletes the log", !log.isFile());
    }

    private static void write(File f, int size, char fill) throws Exception {
        byte[] b = new byte[size];
        java.util.Arrays.fill(b, (byte) fill);
        Files.write(f.toPath(), b);
    }

    private static char firstByte(File f) throws Exception {
        return (char) Files.readAllBytes(f.toPath())[0];
    }

    private static int count(File dir) {
        String[] names = dir.list();
        return names == null ? 0 : names.length;
    }
}
