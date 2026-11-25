package com.ferzdevzgta.game.settings;

import com.ferzdevzgta.game.Check;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The native side loads this file with INIReader and calls std::terminate() when
 * it cannot parse, so an edit must never lose a key it did not touch.
 */
public final class IniFileTest {

    private static final String SAMPLE = ""
            + "; SA-MP client settings\n"
            + "[client]\n"
            + "name = Player1\n"
            + "host = 127.0.0.1\n"
            + "port = 7777\n"
            + "\n"
            + "[gui]\n"
            + "ChatPosX = 325.0\n"
            + "ChatPosY = 25.0\n"
            + "FPSLimit = 60\n"
            + "\n"
            + "[debug]\n"
            + "debug = false\n";

    public static void run() throws Exception {
        File dir = Files.createTempDirectory("ini").toFile();
        File ini = new File(dir, "settings.ini");
        Files.write(ini.toPath(), SAMPLE.getBytes(StandardCharsets.UTF_8));

        IniFile f = IniFile.read(ini);
        Check.eq("read existing value", "325.0", f.get("gui", "ChatPosX", "?"));
        Check.eq("read missing key falls back", "?", f.get("gui", "NoSuchKey", "?"));
        Check.eq("read missing section falls back", "?", f.get("nope", "x", "?"));

        // change an existing key
        f.set("gui", "ChatPosX", "400.0");
        // add a key to an existing section
        f.set("gui", "FirstPerson", "true");
        // add a whole section
        f.set("download", "url", "https://example.invalid/cache");

        File out = new File(dir, "settings.out.ini");
        f.write(out);

        String written = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        Check.ok("untouched key survives", written.contains("name = Player1"));
        Check.ok("comment survives", written.contains("; SA-MP client settings"));
        Check.ok("blank line survives", written.contains("[client]"));
        Check.ok("edited key replaced in place", written.contains("ChatPosX = 400.0"));
        Check.ok("old value gone", !written.contains("ChatPosX = 325.0"));
        Check.ok("new key added to existing section", written.contains("FirstPerson = true"));
        Check.ok("new section appended", written.contains("[download]") && written.contains("url = https://example.invalid/cache"));
        Check.ok("file ends with a newline", written.endsWith("\n"));

        // the new key landed inside [gui], not at the end of the file
        int guiAt = written.indexOf("[gui]");
        int debugAt = written.indexOf("[debug]");
        int firstPersonAt = written.indexOf("FirstPerson = true");
        Check.ok("FirstPerson is inside [gui]",
                guiAt >= 0 && debugAt > guiAt && firstPersonAt > guiAt && firstPersonAt < debugAt);

        // round trip
        IniFile again = IniFile.read(out);
        Check.eq("round trip ChatPosX", "400.0", again.get("gui", "ChatPosX", "?"));
        Check.eq("round trip FirstPerson", "true", again.get("gui", "FirstPerson", "?"));
        Check.eq("round trip new section", "https://example.invalid/cache", again.get("download", "url", "?"));
        Check.eq("round trip untouched", "Player1", again.get("client", "name", "?"));

        // writing twice must not duplicate the key
        again.set("gui", "ChatPosX", "450.0");
        File out2 = new File(dir, "settings.out2.ini");
        again.write(out2);
        String second = new String(Files.readAllBytes(out2.toPath()), StandardCharsets.UTF_8);
        Check.eq("no duplicate key after a second write", 1, count(second, "ChatPosX"));

        // CRLF files stay CRLF: the engine does not care, a diff does
        File crlf = new File(dir, "crlf.ini");
        Files.write(crlf.toPath(), "[gui]\r\nChatPosX = 1.0\r\n".getBytes(StandardCharsets.UTF_8));
        IniFile c = IniFile.read(crlf);
        c.set("gui", "ChatPosX", "2.0");
        File crlfOut = new File(dir, "crlf.out.ini");
        c.write(crlfOut);
        Check.ok("CRLF preserved", new String(Files.readAllBytes(crlfOut.toPath()), StandardCharsets.UTF_8).contains("\r\n"));

        // empty file can still be written and read back
        IniFile e = IniFile.empty();
        e.set("gui", "FPSLimit", "90");
        File emptyOut = new File(dir, "empty.ini");
        e.write(emptyOut);
        Check.eq("empty file gets a section", "90", IniFile.read(emptyOut).get("gui", "FPSLimit", "?"));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int at = 0;
        while ((at = haystack.indexOf(needle, at)) >= 0) {
            n++;
            at += needle.length();
        }
        return n;
    }
}
