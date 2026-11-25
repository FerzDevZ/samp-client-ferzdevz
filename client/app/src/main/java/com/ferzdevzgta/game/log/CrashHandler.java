package com.ferzdevzgta.game.log;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Records a Java crash where a player can find it.
 *
 * <p>The native engine already writes {@code GTA/logcat.txt}; a Java exception used to leave
 * nothing behind, so a report of "black screen" carried no evidence. This appends a marked block
 * with the stack trace to the same file, rotates through {@link LogRotation} so the block never
 * fills the disk, and chains to the previously installed handler so the system dialog still
 * appears.
 *
 * <p>No Android imports: {@code bin/self-test.sh} writes and reads a real file on the JVM.
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    /** Prefixed to every block so the newest record can be found without parsing the whole file. */
    public static final String MARKER = "[app crash]";

    /** Closes a block. A block that never got this far was cut short, and the reader says so. */
    public static final String END = "[/app crash]";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final File log;
    private final String version;
    private final Thread.UncaughtExceptionHandler previous;

    private CrashHandler(File log, String version, Thread.UncaughtExceptionHandler previous) {
        this.log = log;
        this.version = version;
        this.previous = previous;
    }

    /**
     * Installs the handler for every thread. A failure to install is swallowed: the app must not
     * crash while installing the code that records crashes.
     */
    public static void install(File log, String version) {
        try {
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            if (previous instanceof CrashHandler) {
                return;
            }
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(log, version, previous));
        } catch (Exception ignored) {
            // nothing sensible to do here, and throwing would lose the original crash
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        try {
            String block = format(error, System.currentTimeMillis(), version,
                    thread == null ? null : thread.getName());
            append(log, block, LogRotation.DEFAULT_MAX_BYTES);
        } catch (Exception ignored) {
            // the original crash matters more than this record
        }
        if (previous != null) {
            previous.uncaughtException(thread, error);
        }
    }

    /** One readable block. UTC timestamps, so a report from any timezone lines up with a build. */
    public static String format(Throwable error, long epochMillis, String version, String thread) {
        StringBuilder out = new StringBuilder();
        out.append(MARKER).append('\n');
        out.append("time   : ")
                .append(STAMP.format(Instant.ofEpochMilli(epochMillis)))
                .append(" UTC\n");
        out.append("version: ").append(version == null ? "?" : version).append('\n');
        out.append("thread : ").append(thread == null ? "?" : thread).append('\n');
        if (error == null) {
            out.append("error  : unknown\n");
        } else {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            out.append(trace.toString().replace("\r", ""));
        }
        out.append(END).append('\n');
        return out.toString();
    }

    /**
     * Appends the block, rotating first when the file is already at the size limit.
     *
     * @return true when the block reached the file
     */
    public static boolean append(File log, String block, long maxBytes) {
        if (log == null || block == null || block.isEmpty()) {
            return false;
        }
        try {
            File parent = log.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return false;
            }
            LogRotation.rotate(log, maxBytes, LogRotation.DEFAULT_KEEP);
            Files.write(log.toPath(), block.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The newest crash block, or an empty string when the log holds none. Returns what is there
     * even when the block has no closing line, because a truncated record is still a clue.
     */
    public static String lastCrash(File log) {
        String content = read(log);
        if (content.isEmpty()) {
            return "";
        }
        int start = content.lastIndexOf(MARKER);
        if (start < 0) {
            return "";
        }
        String block = content.substring(start);
        int end = block.indexOf(END);
        return end < 0 ? block.trim() : block.substring(0, end + END.length()).trim();
    }

    /** The log as text, empty when it is missing or unreadable. */
    public static String read(File log) {
        if (log == null || !log.isFile()) {
            return "";
        }
        try {
            byte[] all = Files.readAllBytes(log.toPath());
            return new String(all, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
