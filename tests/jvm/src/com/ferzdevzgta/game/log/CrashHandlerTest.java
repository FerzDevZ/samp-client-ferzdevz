package com.ferzdevzgta.game.log;

import com.ferzdevzgta.game.Check;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The crash block: written, rotated, and read back off a real file. */
public final class CrashHandlerTest {

    private CrashHandlerTest() {}

    public static void run() {
        try {
            File dir = Files.createTempDirectory("crash").toFile();
            File log = new File(dir, "logcat.txt");

            RuntimeException boom = new IllegalStateException("boom");
            String block = CrashHandler.format(boom, 1_700_000_000_000L, "0.1.0 (1)", "main");

            Check.ok("block starts with the marker", block.startsWith(CrashHandler.MARKER));
            Check.ok("block ends with the closing marker", block.trim().endsWith(CrashHandler.END));
            Check.ok("block carries the exception class", block.contains("java.lang.IllegalStateException: boom"));
            Check.ok("block carries the version", block.contains("version: 0.1.0 (1)"));
            Check.ok("block carries the thread", block.contains("thread : main"));
            Check.ok("block carries a UTC stamp", block.contains("UTC"));
            Check.ok("block has a frame", block.contains("\tat "));

            Check.ok("a null error still formats", CrashHandler.format(null, 0L, null, null)
                    .contains("error  : unknown"));
            Check.ok("null thread is tolerated", CrashHandler.format(boom, 0L, "v", null)
                    .contains("thread : ?"));

            Check.eq("nothing to write is refused", false, CrashHandler.append(null, block, 1024));
            Check.eq("an empty block is refused", false, CrashHandler.append(log, "", 1024));

            Check.eq("the block reaches the file", true,
                    CrashHandler.append(log, block, LogRotation.DEFAULT_MAX_BYTES));
            String once = CrashHandler.read(log);
            Check.ok("file holds the block", once.contains("boom"));
            Check.eq("one marker in the file", 1, count(once, CrashHandler.MARKER));

            Check.eq("a second crash appends", true,
                    CrashHandler.append(log, block, LogRotation.DEFAULT_MAX_BYTES));
            Check.eq("two markers now", 2, count(CrashHandler.read(log), CrashHandler.MARKER));

            String newest = CrashHandler.lastCrash(log);
            Check.ok("last crash is a full block", newest.startsWith(CrashHandler.MARKER)
                    && newest.endsWith(CrashHandler.END));
            Check.eq("last crash holds one block only", 1, count(newest, CrashHandler.MARKER));

            File empty = new File(dir, "empty.txt");
            Files.write(empty.toPath(), "just engine output\n".getBytes(StandardCharsets.UTF_8));
            Check.eq("a log without a crash has none", "", CrashHandler.lastCrash(empty));
            Check.eq("a missing log has none", "", CrashHandler.lastCrash(new File(dir, "nope.txt")));
            Check.eq("a null log has none", "", CrashHandler.lastCrash(null));
            Check.eq("reading a missing log is empty", "", CrashHandler.read(new File(dir, "nope.txt")));

            File truncated = new File(dir, "truncated.txt");
            String cut = block.substring(0, block.indexOf(CrashHandler.END));
            Files.write(truncated.toPath(), cut.getBytes(StandardCharsets.UTF_8));
            String partial = CrashHandler.lastCrash(truncated);
            Check.ok("a cut block is still reported", partial.startsWith(CrashHandler.MARKER));
            Check.ok("a cut block has no closing marker", !partial.contains(CrashHandler.END));

            File big = new File(dir, "big.txt");
            byte[] filler = new byte[64];
            java.util.Arrays.fill(filler, (byte) 'x');
            for (int i = 0; i < 40; i++) {
                Files.write(big.toPath(), filler, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            }
            long before = big.length();
            Check.ok("a big log was rotated", CrashHandler.append(big, block, 1024));
            Check.ok("the rotated log is smaller", big.length() < before);
            Check.ok("the rotated file kept the block", CrashHandler.lastCrash(big)
                    .contains("boom"));
            Check.ok("a rotation backup exists", new File(dir, "big.txt.1").isFile());

            File nested = new File(dir, "missing/dir/logcat.txt");
            Check.eq("a missing parent directory is created", true,
                    CrashHandler.append(nested, block, LogRotation.DEFAULT_MAX_BYTES));
            Check.ok("the nested file exists", nested.isFile());
        } catch (Exception e) {
            Check.ok("crash handler test failed: " + e, false);
        }
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        int at = 0;
        while (true) {
            int hit = haystack.indexOf(needle, at);
            if (hit < 0) {
                return found;
            }
            found++;
            at = hit + needle.length();
        }
    }
}
