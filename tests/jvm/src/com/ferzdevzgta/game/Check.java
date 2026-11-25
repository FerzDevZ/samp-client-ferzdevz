package com.ferzdevzgta.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tiny assertion helper: no JUnit, no dependencies, no build file. */
public final class Check {

    public interface Body {
        void run() throws Exception;
    }

    public static int passed;
    public static int failed;
    private static final List<String> failures = new ArrayList<>();

    private Check() {
    }

    public static void ok(String what, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            failures.add(what);
        }
    }

    public static void eq(String what, Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            passed++;
        } else {
            failed++;
            failures.add(what + ": expected <" + expected + "> got <" + actual + ">");
        }
    }

    public static void throwsOn(String what, Body body) {
        try {
            body.run();
            failed++;
            failures.add(what + ": nothing was thrown");
        } catch (Exception expected) {
            passed++;
        }
    }

    public static int report() {
        for (String f : failures) {
            System.out.println("  NOT OK  " + f);
        }
        System.out.println("  checks: " + passed + " passed, " + failed + " failed");
        return failed == 0 ? 0 : 1;
    }
}
