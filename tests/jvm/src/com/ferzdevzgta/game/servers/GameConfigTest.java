package com.ferzdevzgta.game.servers;

import com.ferzdevzgta.game.Check;
import com.ferzdevzgta.game.settings.IniFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings file the native client parses before its first frame.
 *
 * The key parity check reads {@code cpp/samp/settings.cpp} itself, so adding a key to the engine
 * without adding it here fails this test instead of shipping a file the engine silently defaults.
 */
public final class GameConfigTest {

    private static final Pattern READER_CALL = Pattern.compile(
            "reader\\.(?:Get|GetInteger|GetReal|GetBoolean|GetString)\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"");

    private GameConfigTest() {}

    public static void run() throws Exception {
        Path dir = Files.createTempDirectory("gameconfig");
        try {
            File ini = dir.resolve("SAMP/settings.ini").toFile();

            Check.ok("missing file is created", !ini.isFile());
            GameConfig.ensureSettings(ini);
            Check.ok("ensureSettings writes the file", ini.isFile() && ini.length() > 0);

            IniFile read = IniFile.read(ini);
            Check.eq("placeholder host", "127.0.0.1", read.get("client", "host", ""));
            Check.eq("placeholder port", "7777", read.get("client", "port", ""));
            Check.eq("placeholder name", "Player", read.get("client", "name", ""));
            Check.eq("version default", GameConfig.DEFAULT_VERSION, read.get("client", "version", ""));

            long firstWrite = ini.length();
            GameConfig.ensureSettings(ini);
            Check.eq("ensureSettings never shrinks an existing file", firstWrite, ini.length());

            ServerEndpoint server = new ServerEndpoint("Test server", "samp.example.org", 7777);
            GameConfig.save(ini, "Ferz_Dev", server, null);

            IniFile saved = IniFile.read(ini);
            Check.eq("nickname is saved", "Ferz_Dev", saved.get("client", "name", ""));
            Check.eq("host is saved", "samp.example.org", saved.get("client", "host", ""));
            Check.eq("port is saved", "7777", saved.get("client", "port", ""));
            Check.eq("nickname reads back", "Ferz_Dev", GameConfig.readNickname(ini));
            Check.eq("server reads back", "samp.example.org", GameConfig.readServer(ini).host());
            Check.eq("server port reads back", 7777, GameConfig.readServer(ini).port());

            GameConfig.save(ini, "Ferz_Dev", server, "secret");
            Check.eq("password is saved when given", "secret", IniFile.read(ini).get("client", "password", ""));

            IniFile edited = IniFile.read(ini);
            edited.set("gui", "ChatSizeX", "900.0");
            edited.write(ini);
            GameConfig.save(ini, "Other_Name", server, null);
            Check.eq("a hand-edited key survives save", "900.0",
                    IniFile.read(ini).get("gui", "ChatSizeX", ""));

            Check.throwsOn("empty nickname is refused", () -> {
                try {
                    GameConfig.save(ini, "  ", server, null);
                } catch (IOException wrapping) {
                    throw new IllegalArgumentException(wrapping.getMessage());
                }
            });

            File missing = dir.resolve("nope/settings.ini").toFile();
            Check.eq("readNickname on a missing file", null, GameConfig.readNickname(missing));
            Check.eq("readServer on a missing file", null, GameConfig.readServer(missing));

            Files.write(ini.toPath(), "not an ini at all\n".getBytes(StandardCharsets.UTF_8));
            Check.eq("garbage nickname reads as null", null, GameConfig.readNickname(ini));
            Check.eq("garbage server reads as null", null, GameConfig.readServer(ini));

            checkKeyParity(dir.resolve("parity.ini").toFile());
        } finally {
            deleteTree(dir);
        }
    }

    /** Every key {@code settings.cpp} reads must exist in the file this class writes. */
    private static void checkKeyParity(File target) throws IOException {
        File source = new File("client/app/src/main/cpp/samp/settings.cpp");
        if (!source.isFile()) {
            Check.ok("settings.cpp found for the key parity check", false);
            return;
        }
        String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
        Set<String> wanted = new LinkedHashSet<>();
        Matcher m = READER_CALL.matcher(text);
        while (m.find()) {
            wanted.add(m.group(1) + "." + m.group(2));
        }
        Check.ok("settings.cpp reads at least 30 keys", wanted.size() >= 30);

        GameConfig.save(target, "Parity", new ServerEndpoint("", "1.2.3.4", 7777), null);
        IniFile written = IniFile.read(target);

        Set<String> missing = new LinkedHashSet<>();
        for (String pair : wanted) {
            int dot = pair.indexOf('.');
            String section = pair.substring(0, dot);
            String key = pair.substring(dot + 1);
            if (written.get(section, key, null) == null) {
                missing.add(pair);
            }
        }
        Check.eq("every key settings.cpp reads is written: " + missing, 0, missing.size());
    }

    private static void deleteTree(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // temp dir, nothing to recover
                        }
                    });
        } catch (IOException ignored) {
            // temp dir, nothing to recover
        }
    }
}
