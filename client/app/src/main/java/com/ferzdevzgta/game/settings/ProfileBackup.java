package com.ferzdevzgta.game.settings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * One file that carries the settings a player would otherwise type again after changing phones:
 * {@code SAMP/settings.ini} and {@code SAMP/servers.txt}, plus a small text summary.
 *
 * The archive is a plain zip with fixed entry names, written without Android imports so
 * {@code tests/jvm} covers the round trip, the refusal of a foreign zip and the size cap. Entry
 * names are constants, never taken from the archive, so a crafted zip cannot write outside the
 * destination.
 */
public final class ProfileBackup {

    public static final String SETTINGS_ENTRY = "settings.ini";
    public static final String SERVERS_ENTRY = "servers.txt";
    public static final String INFO_ENTRY = "profile.txt";

    /**
     * A settings.ini is a few kilobytes. The cap means a corrupt or hostile archive cannot be used
     * to fill the shared storage through this screen.
     */
    public static final long MAX_ENTRY_BYTES = 512 * 1024L;

    public static final class Summary {
        public boolean settings = false;
        public boolean servers = false;
        public int serversCount = 0;
        public String info = "";
    }

    private ProfileBackup() {
    }

    /** Packs whatever exists. A missing servers.txt is not an error, a missing settings.ini is. */
    public static void write(File settings, File servers, File out, String info) throws IOException {
        if (settings == null || !settings.isFile()) {
            throw new IOException("settings.ini not found, run the game once");
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create " + parent);
        }
        try (OutputStream raw = new FileOutputStream(out);
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
            copy(settings, zip, SETTINGS_ENTRY);
            if (servers != null && servers.isFile()) {
                copy(servers, zip, SERVERS_ENTRY);
            }
            zip.putNextEntry(new ZipEntry(INFO_ENTRY));
            zip.write((info == null ? "" : info).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static void copy(File source, ZipOutputStream zip, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = new BufferedInputStream(new FileInputStream(source))) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                zip.write(buf, 0, n);
            }
        }
        zip.closeEntry();
    }

    /**
     * Restores into the given files. Returns what was found instead of throwing on a partial
     * archive, so the caller can say "settings restored, no server list in this file".
     */
    public static Summary read(File archive, File settingsTarget, File serversTarget) throws IOException {
        Summary summary = new Summary();
        List<String> seen = new ArrayList<>();
        try (InputStream raw = new FileInputStream(archive);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (SETTINGS_ENTRY.equals(name)) {
                    writeCapped(zip, settingsTarget, SETTINGS_ENTRY);
                    summary.settings = true;
                    seen.add(name);
                } else if (SERVERS_ENTRY.equals(name)) {
                    writeCapped(zip, serversTarget, SERVERS_ENTRY);
                    summary.servers = true;
                    summary.serversCount = countLines(serversTarget);
                    seen.add(name);
                } else if (INFO_ENTRY.equals(name)) {
                    summary.info = readCapped(zip, INFO_ENTRY);
                    seen.add(name);
                }
                // Everything else is ignored on purpose: the names this method writes are the
                // only ones it accepts, so a path like ../../settings.ini is never created.
            }
        }
        if (!summary.settings) {
            throw new IOException("no settings.ini in the archive");
        }
        return summary;
    }

    private static void writeCapped(InputStream in, File out, String entry) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create " + parent);
        }
        long total = 0;
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_ENTRY_BYTES) {
                    throw new IOException(entry + " is larger than " + MAX_ENTRY_BYTES + " bytes");
                }
                os.write(buf, 0, n);
            }
        }
    }

    private static String readCapped(InputStream in, String entry) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[2 * 1024];
        long total = 0;
        int n;
        int limit = (int) Math.min(MAX_ENTRY_BYTES, 8 * 1024);
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_ENTRY_BYTES) {
                throw new IOException(entry + " is larger than " + MAX_ENTRY_BYTES + " bytes");
            }
            if (sb.length() < limit) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }
        return sb.toString().trim();
    }

    static int countLines(File file) {
        int lines = 0;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                lines++;
            }
        } catch (IOException ignored) {
            return 0;
        }
        return lines;
    }

    /** Suggested file name: one per day, so exporting twice does not overwrite silently. */
    public static String fileName(String version, long epochMillis) {
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US);
        return "samp-profile-" + version + "-" + fmt.format(new java.util.Date(epochMillis)) + ".zip";
    }
}
