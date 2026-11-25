package com.ferzdevzgta.game.diag;

import com.ferzdevzgta.game.Check;

/** Log tailing, size formatting and the report lines, all without a device. */
public final class ReportTest {

    private ReportTest() {}

    public static void run() {
        String log = "a\nb\nc\nd\n";
        Check.eq("tail keeps the newest lines", "b\nc\nd", Report.tail(log, 3));
        Check.eq("tail keeps everything when short", "a\nb\nc\nd", Report.tail(log, 10));
        Check.eq("tail of zero is empty", "", Report.tail(log, 0));
        Check.eq("tail of empty is empty", "", Report.tail("", 3));
        Check.eq("tail of null is empty", "", Report.tail(null, 3));
        Check.eq("carriage returns are dropped", "x", Report.tail("x\r\n", 1));
        Check.eq("trailing blank lines are ignored", "y", Report.tail("y\n\n\n", 1));
        Check.eq("tabs become spaces", "  x", Report.tail("\tx", 1));
        Check.eq("a single line log", "only", Report.tail("only", 5));

        Check.eq("bytes stays bytes", "512 B", Report.bytes(512));
        Check.eq("kilobytes", "1.0 KB", Report.bytes(1024));
        Check.eq("megabytes", "1.5 MB", Report.bytes(1024 * 1024 + 512 * 1024));
        Check.eq("gigabytes", "2.0 GB", Report.bytes(2L * 1024 * 1024 * 1024));
        Check.eq("unknown size", "?", Report.bytes(-1));

        Check.eq("passing line", "[ok]   data: 512 B", Report.block("data", true, "512 B"));
        Check.eq("failing line", "[fail] data: missing", Report.block("data", false, "missing"));
        Check.eq("line without a value", "[ok]   data", Report.block("data", true, ""));
        Check.eq("line with a null value", "[fail] data", Report.block("data", false, null));

        Check.eq("summary", "4 of 5 checks pass", Report.summary(4, 5));
        Check.eq("nothing ran is not a pass", "no checks ran", Report.summary(0, 0));
    }
}
