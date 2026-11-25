package com.ferzdevzgta.game.settings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Line-preserving editor for {@code /storage/emulated/0/GTA/SAMP/settings.ini}.
 *
 * The native client reads that file with INIReader and dies when it is missing,
 * so anything that edits it must keep every key it does not understand. This
 * class only rewrites the lines it is asked to change and leaves the rest byte
 * for byte.
 */
public final class IniFile {

    private final List<String> lines = new ArrayList<>();
    private String newline = "\n";

    public static IniFile read(File f) throws IOException {
        IniFile ini = new IniFile();
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        if (text.contains("\r\n")) {
            ini.newline = "\r\n";
        }
        for (String line : text.split("\n", -1)) {
            ini.lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        // a trailing empty element is an artefact of the split, not a line
        if (!ini.lines.isEmpty() && ini.lines.get(ini.lines.size() - 1).isEmpty()) {
            ini.lines.remove(ini.lines.size() - 1);
        }
        return ini;
    }

    public static IniFile empty() {
        return new IniFile();
    }

    public String get(String section, String key, String fallback) {
        int at = indexOfKey(section, key);
        if (at < 0) {
            return fallback;
        }
        int eq = lines.get(at).indexOf('=');
        return eq < 0 ? fallback : lines.get(at).substring(eq + 1).trim();
    }

    public void set(String section, String key, String value) {
        int at = indexOfKey(section, key);
        String line = key + " = " + value;
        if (at >= 0) {
            lines.set(at, line);
            return;
        }
        int sec = indexOfSection(section);
        if (sec < 0) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
                lines.add("");
            }
            lines.add("[" + section + "]");
            lines.add(line);
            return;
        }
        int insertAt = sec + 1;
        for (int i = sec + 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("[")) {
                break;
            }
            if (!t.isEmpty()) {
                insertAt = i + 1;
            }
        }
        lines.add(insertAt, line);
    }

    public void write(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append(newline);
        }
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create " + parent);
        }
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Unmodifiable view, for tests and for the diff a caller may want to log. */
    public List<String> lines() {
        return java.util.Collections.unmodifiableList(lines);
    }

    private int indexOfSection(String section) {
        String want = "[" + section.toLowerCase(java.util.Locale.ROOT) + "]";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().toLowerCase(java.util.Locale.ROOT).equals(want)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfKey(String section, String key) {
        int sec = indexOfSection(section);
        if (sec < 0) {
            return -1;
        }
        for (int i = sec + 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("[")) {
                return -1;
            }
            int eq = t.indexOf('=');
            if (eq > 0 && t.substring(0, eq).trim().equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }
}
