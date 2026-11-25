package com.ferzdevzgta.game.diag;

import java.util.Locale;

/**
 * Formatting rules for the on-device diagnostic screen.
 *
 * <p>No Android imports, so the parts that decide what a report looks like are covered by
 * {@code bin/self-test.sh} on the JVM. The screen itself only gathers the values.
 */
public final class Report {

    private Report() {
    }

    /** Returns at most {@code lines} trailing lines, keeping the newest evidence. */
    public static String tail(String content, int lines) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (lines <= 0) {
            return "";
        }
        String[] all = content.split("\n", -1);
        int end = all.length;
        while (end > 0 && all[end - 1].trim().isEmpty()) {
            end--;
        }
        int start = Math.max(0, end - lines);
        StringBuilder out = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(all[i].replace("\r", "").replace("\t", "  "));
        }
        return out.toString();
    }

    /** One line per check, in the same order the screen shows them. */
    public static String block(String label, boolean ok, String value) {
        String mark = ok ? "[ok]  " : "[fail]";
        String detail = value == null ? "" : value;
        return mark + " " + label + (detail.isEmpty() ? "" : ": " + detail);
    }

    /** "1.4 MB" style sizes; three significant digits is enough to spot a wrong install. */
    public static String bytes(long value) {
        if (value < 0) {
            return "?";
        }
        if (value < 1024) {
            return value + " B";
        }
        double amount = value;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (amount >= 1024 && unit < units.length - 1) {
            amount /= 1024;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", amount, units[unit]);
    }

    /** "4 of 5 checks pass". A report with no checks is reported as failing, not as passing. */
    public static String summary(int passed, int total) {
        if (total <= 0) {
            return "no checks ran";
        }
        return passed + " of " + total + " checks pass";
    }
}
