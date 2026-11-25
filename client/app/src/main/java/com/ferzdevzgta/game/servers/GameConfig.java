package com.ferzdevzgta.game.servers;

import com.ferzdevzgta.game.settings.IniFile;

import java.io.File;
import java.io.IOException;

/**
 * Writes the connection keys the native client reads at boot into
 * {@code /storage/emulated/0/GTA/SAMP/settings.ini}.
 *
 * The engine parses that file before the first frame and refuses to continue without it, so a
 * fresh install needs it to exist before the game is launched. Every other key here is a default
 * copied from {@code cpp/samp/settings.cpp}; the self-test re-reads that file and fails if a key
 * is missing from this template.
 */
public final class GameConfig {

    public static final String GTA_ROOT = "/storage/emulated/0/GTA";
    public static final String SAMP_DIR = GTA_ROOT + "/SAMP";
    public static final String SETTINGS_FILE = SAMP_DIR + "/settings.ini";
    public static final String SERVERS_FILE = SAMP_DIR + "/" + ServerList.FILE_NAME;

    public static final String DEFAULT_VERSION = "0.3.7";

    private GameConfig() {}

    public static File settingsFile() {
        return new File(SETTINGS_FILE);
    }

    public static File serversFile() {
        return new File(SERVERS_FILE);
    }

    /** Creates the settings file with defaults if it is missing, and returns it either way. */
    public static File ensureSettings() throws IOException {
        return ensureSettings(settingsFile());
    }

    public static File ensureSettings(File file) throws IOException {
        if (file.isFile() && file.length() > 0) {
            return file;
        }
        IniFile ini = IniFile.empty();
        applyDefaults(ini);
        ini.write(file);
        return file;
    }

    /**
     * Points the client at one server and one nickname, leaving every other key untouched.
     * Missing keys are filled from the template so the file is always complete.
     */
    public static void save(String nickname, ServerEndpoint server, String password) throws IOException {
        save(settingsFile(), nickname, server, password);
    }

    public static void save(File file, String nickname, ServerEndpoint server, String password)
            throws IOException {
        String name = ServerEndpoint.requireNickname(nickname);
        if (server == null) {
            throw new IllegalArgumentException("server is null");
        }
        IniFile ini = file.isFile() && file.length() > 0 ? IniFile.read(file) : IniFile.empty();
        applyDefaults(ini);
        ini.set("client", "name", name);
        ini.set("client", "host", server.host());
        ini.set("client", "port", Integer.toString(server.port()));
        if (password != null && !password.trim().isEmpty()) {
            ini.set("client", "password", password.trim());
        }
        ini.write(file);
    }

    /**
     * Points the client at a server without touching the nickname or the password.
     *
     * A link opened from a browser or Discord arrives after the player has already typed a name,
     * and rewriting that name from a URL would be a surprise. Only host and port move.
     */
    public static void select(ServerEndpoint server) throws IOException {
        select(settingsFile(), server);
    }

    public static void select(File file, ServerEndpoint server) throws IOException {
        if (server == null) {
            throw new IllegalArgumentException("server is null");
        }
        IniFile ini = file.isFile() && file.length() > 0 ? IniFile.read(file) : IniFile.empty();
        applyDefaults(ini);
        ini.set("client", "host", server.host());
        ini.set("client", "port", Integer.toString(server.port()));
        ini.write(file);
    }

    /** Reads the nickname currently saved, or null when the file has no usable one. */
    public static String readNickname() {
        return readNickname(settingsFile());
    }

    public static String readNickname(File file) {
        try {
            if (!file.isFile()) {
                return null;
            }
            String name = IniFile.read(file).get("client", "name", "");
            return name == null || name.trim().isEmpty() ? null : name.trim();
        } catch (IOException unreadable) {
            return null;
        }
    }

    /** Reads the host and port currently saved, or null when they are missing or invalid. */
    public static ServerEndpoint readServer() {
        return readServer(settingsFile());
    }

    public static ServerEndpoint readServer(File file) {
        try {
            if (!file.isFile()) {
                return null;
            }
            IniFile ini = IniFile.read(file);
            String host = ini.get("client", "host", "");
            if (host == null || host.trim().isEmpty()) {
                return null;
            }
            int port;
            try {
                port = Integer.parseInt(ini.get("client", "port", Integer.toString(ServerEndpoint.DEFAULT_PORT)).trim());
            } catch (NumberFormatException notANumber) {
                port = ServerEndpoint.DEFAULT_PORT;
            }
            return new ServerEndpoint("", host, port);
        } catch (IOException | IllegalArgumentException unusable) {
            return null;
        }
    }

    /** The full default key set, matching the fallbacks in {@code settings.cpp}. */
    static void applyDefaults(IniFile ini) {
        // Placeholders, replaced by the server browser before the first connection. The native
        // client reads these with the same fallbacks, so an untouched file still boots.
        ini.set("client", "name", orDefault(ini, "client", "name", "Player"));
        ini.set("client", "host", orDefault(ini, "client", "host", "127.0.0.1"));
        ini.set("client", "port", orDefault(ini, "client", "port", "7777"));
        if (isEmpty(ini.get("client", "version", ""))) {
            ini.set("client", "version", DEFAULT_VERSION);
        }
        if (ini.get("client", "password", null) == null) {
            ini.set("client", "password", "");
        }
        if (isEmpty(ini.get("client", "autoaim", ""))) {
            ini.set("client", "autoaim", "false");
        }

        ini.set("debug", "debug", orDefault(ini, "debug", "debug", "false"));
        ini.set("debug", "online", orDefault(ini, "debug", "online", "true"));

        // Chat, dialog and HUD geometry. A player who edited one value keeps it.
        ini.set("gui", "Font", orDefault(ini, "gui", "Font", "arial.ttf"));
        ini.set("gui", "FontSize", orDefault(ini, "gui", "FontSize", "30.0"));
        ini.set("gui", "FontOutline", orDefault(ini, "gui", "FontOutline", "2"));
        ini.set("gui", "ChatPosX", orDefault(ini, "gui", "ChatPosX", "325.0"));
        ini.set("gui", "ChatPosY", orDefault(ini, "gui", "ChatPosY", "25.0"));
        ini.set("gui", "ChatSizeX", orDefault(ini, "gui", "ChatSizeX", "1150.0"));
        ini.set("gui", "ChatSizeY", orDefault(ini, "gui", "ChatSizeY", "220.0"));
        ini.set("gui", "ChatMaxMessages", orDefault(ini, "gui", "ChatMaxMessages", "6"));
        ini.set("gui", "SpawnScreenPosX", orDefault(ini, "gui", "SpawnScreenPosX", "660.0"));
        ini.set("gui", "SpawnScreenPosY", orDefault(ini, "gui", "SpawnScreenPosY", "950.0"));
        ini.set("gui", "SpawnScreenSizeX", orDefault(ini, "gui", "SpawnScreenSizeX", "600.0"));
        ini.set("gui", "SpawnScreenSizeY", orDefault(ini, "gui", "SpawnScreenSizeY", "100.0"));
        ini.set("gui", "HealthBarWidth", orDefault(ini, "gui", "HealthBarWidth", "100.0"));
        ini.set("gui", "HealthBarHeight", orDefault(ini, "gui", "HealthBarHeight", "10.0"));
        ini.set("gui", "ScoreBoardSizeX", orDefault(ini, "gui", "ScoreBoardSizeX", "846.0"));
        ini.set("gui", "ScoreBoardSizeY", orDefault(ini, "gui", "ScoreBoardSizeY", "614.0"));
        ini.set("gui", "PassengerUseTexture", orDefault(ini, "gui", "PassengerUseTexture", "true"));
        ini.set("gui", "PassengerTextureSize", orDefault(ini, "gui", "PassengerTextureSize", "30.0"));
        ini.set("gui", "PassengerTexturePosX", orDefault(ini, "gui", "PassengerTexturePosX", "120.0"));
        ini.set("gui", "PassengerTexturePosY", orDefault(ini, "gui", "PassengerTexturePosY", "430.0"));
        ini.set("gui", "Dialog", orDefault(ini, "gui", "Dialog", "true"));
        ini.set("gui", "VoiceChatEnable", orDefault(ini, "gui", "VoiceChatEnable", "false"));
        ini.set("gui", "VoiceChatKey", orDefault(ini, "gui", "VoiceChatKey", "66"));
        ini.set("gui", "VoiceChatSize", orDefault(ini, "gui", "VoiceChatSize", "30.0"));
        ini.set("gui", "VoiceChatPosX", orDefault(ini, "gui", "VoiceChatPosX", "1520.0"));
        ini.set("gui", "VoiceChatPosY", orDefault(ini, "gui", "VoiceChatPosY", "480.0"));
        ini.set("gui", "androidkeyboard", orDefault(ini, "gui", "androidkeyboard", "false"));
        ini.set("gui", "firstperson", orDefault(ini, "gui", "firstperson", "true"));
        ini.set("gui", "cutout", orDefault(ini, "gui", "cutout", "false"));
        ini.set("gui", "fps", orDefault(ini, "gui", "fps", "false"));
        ini.set("gui", "FPSLimit", orDefault(ini, "gui", "FPSLimit", "60"));
        ini.set("gui", "hparmourtext", orDefault(ini, "gui", "hparmourtext", "false"));
        ini.set("gui", "outfitguns", orDefault(ini, "gui", "outfitguns", "false"));
        ini.set("gui", "pcmoney", orDefault(ini, "gui", "pcmoney", "false"));
        ini.set("gui", "radarrect", orDefault(ini, "gui", "radarrect", "false"));
        ini.set("gui", "skybox", orDefault(ini, "gui", "skybox", "false"));
        ini.set("gui", "snow", orDefault(ini, "gui", "snow", "false"));
    }

    private static String orDefault(IniFile ini, String section, String key, String fallback) {
        String value = ini.get(section, key, null);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
