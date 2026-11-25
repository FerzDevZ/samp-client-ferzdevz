package com.ferzdevzgta.game.settings;

import com.ferzdevzgta.game.Check;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Round trip, safety cap and refusal cases for the profile archive. */
public final class ProfileBackupTest {

    public static void run() throws Exception {
        File dir = Files.createTempDirectory("profile").toFile();
        try {
            File settings = new File(dir, "settings.ini");
            File servers = new File(dir, "servers.txt");
            write(settings, "[display]\nfontsize = 3\n[server]\nhostname = 1.2.3.4\nport = 7777\n");
            write(servers, "1.2.3.4:7777\n5.6.7.8:7778\n");

            File archive = new File(dir, "backup.zip");
            ProfileBackup.write(settings, servers, archive, "SA-MP FerzDevZ 0.2.0");
            Check.ok("archive written", archive.isFile() && archive.length() > 0);

            File restIni = new File(dir, "restored.ini");
            File restSrv = new File(dir, "restored.txt");
            ProfileBackup.Summary got = ProfileBackup.read(archive, restIni, restSrv);
            Check.ok("settings restored", got.settings);
            Check.ok("servers restored", got.servers);
            Check.eq("server count", 2, got.serversCount);
            Check.eq("version string survives", "SA-MP FerzDevZ 0.2.0", got.info);
            Check.ok("ini bytes match", read(restIni).equals(read(settings)));
            Check.ok("server bytes match", read(restSrv).equals(read(servers)));

            // A zip without settings.ini must be refused instead of half-applied.
            File foreign = new File(dir, "foreign.zip");
            zipOf(foreign, new String[][]{{"other.txt", "nope"}});
            boolean refused = false;
            try {
                ProfileBackup.read(foreign, restIni, restSrv);
            } catch (IOException expected) {
                refused = true;
            }
            Check.ok("archive without settings refused", refused);

            // An entry over the cap must be refused, not written.
            File huge = new File(dir, "huge.zip");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < (int) ProfileBackup.MAX_ENTRY_BYTES + 32; i++) {
                sb.append('x');
            }
            zipOf(huge, new String[][]{{ProfileBackup.SETTINGS_ENTRY, sb.toString()}});
            refused = false;
            try {
                ProfileBackup.read(huge, restIni, restSrv);
            } catch (IOException expected) {
                refused = true;
            }
            Check.ok("oversized entry refused", refused);

            // A traversal-shaped entry is not a known name, so it is skipped, never extracted.
            File sneaky = new File(dir, "sneaky.zip");
            zipOf(sneaky, new String[][]{
                    {"../../settings.ini", "[display]\nfontsize = 9\n"},
                    {ProfileBackup.SETTINGS_ENTRY, "[display]\nfontsize = 2\n"}});
            File target = new File(dir, "safe.ini");
            ProfileBackup.read(sneaky, target, restSrv);
            Check.ok("traversal entry ignored", read(target).contains("fontsize = 2"));
            Check.ok("nothing written above the destination",
                    !new File(dir.getParentFile(), "settings.ini").exists());

            boolean missing = false;
            try {
                ProfileBackup.write(new File(dir, "nope.ini"), null, new File(dir, "x.zip"), "x");
            } catch (IOException expected) {
                missing = true;
            }
            Check.ok("missing settings refused", missing);

            // A missing server list is normal on a fresh install, so it must still export.
            File onlyIni = new File(dir, "only-ini.zip");
            ProfileBackup.write(settings, new File(dir, "absent.txt"), onlyIni, "x");
            ProfileBackup.Summary partial = ProfileBackup.read(onlyIni, restIni, restSrv);
            Check.ok("export without server list", partial.settings && !partial.servers);

            String name = ProfileBackup.fileName("0.2.0", 1_700_000_000_000L);
            Check.ok("file name carries version", name.startsWith("samp-profile-0.2.0-"));
            Check.ok("file name ends in zip", name.endsWith(".zip"));
        } finally {
            deleteTree(dir);
        }
    }

    private static void write(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void zipOf(File file, String[][] entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (String[] entry : entries) {
                zip.putNextEntry(new ZipEntry(entry[0]));
                zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
