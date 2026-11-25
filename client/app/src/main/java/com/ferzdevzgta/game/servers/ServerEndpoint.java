package com.ferzdevzgta.game.servers;

import java.util.Locale;

/**
 * One server the player can join: a display name and a host:port pair.
 *
 * Parsing and validation live here rather than in the activity so the rules can be tested on
 * a JVM. The native client takes whatever string ends up in {@code settings.ini}, so anything
 * accepted here is passed straight to the socket layer.
 */
public final class ServerEndpoint {

    public static final int DEFAULT_PORT = 7777;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_HOST_LENGTH = 253;
    public static final int MAX_NICKNAME_LENGTH = 24;

    private final String name;
    private final String host;
    private final int port;

    public ServerEndpoint(String name, String host, int port) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            cleanName = host;
        }
        if (cleanName.length() > MAX_NAME_LENGTH) {
            cleanName = cleanName.substring(0, MAX_NAME_LENGTH);
        }
        this.name = cleanName;
        this.host = requireHost(host);
        this.port = requirePort(port);
    }

    /** Parses one line of a server list: {@code Name|host:port}, or a bare {@code host:port}. */
    public static ServerEndpoint parse(String line) {
        if (line == null) {
            throw new IllegalArgumentException("line is null");
        }
        String text = line.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("line is empty");
        }
        if (text.startsWith("#")) {
            throw new IllegalArgumentException("comment, not a server: " + text);
        }
        String name = "";
        String address = text;
        int bar = text.indexOf('|');
        if (bar >= 0) {
            name = text.substring(0, bar).trim();
            address = text.substring(bar + 1).trim();
        }
        if (address.startsWith("samp://")) {
            address = address.substring("samp://".length());
        }
        return new ServerEndpoint(name, hostOf(address), portOf(address));
    }

    /** Tries to parse, returning null instead of throwing, for list-building code. */
    public static ServerEndpoint tryParse(String line) {
        try {
            return parse(line);
        } catch (IllegalArgumentException notAServer) {
            return null;
        }
    }

    private static String hostOf(String address) {
        int colon = address.lastIndexOf(':');
        if (colon < 0) {
            return address;
        }
        return address.substring(0, colon);
    }

    private static int portOf(String address) {
        int colon = address.lastIndexOf(':');
        if (colon < 0) {
            return DEFAULT_PORT;
        }
        String digits = address.substring(colon + 1).trim();
        if (digits.isEmpty()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("port is not a number: " + digits);
        }
    }

    public static String requireHost(String host) {
        if (host == null) {
            throw new IllegalArgumentException("host is null");
        }
        String clean = host.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("host is empty");
        }
        if (clean.length() > MAX_HOST_LENGTH) {
            throw new IllegalArgumentException("host is longer than " + MAX_HOST_LENGTH);
        }
        if (clean.indexOf(' ') >= 0 || clean.indexOf('/') >= 0 || clean.indexOf('|') >= 0) {
            throw new IllegalArgumentException("host contains a forbidden character: " + host);
        }
        if (isIpv4Literal(clean) && !ipv4IsValid(clean)) {
            throw new IllegalArgumentException("host is not a valid IPv4 address: " + host);
        }
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_';
            if (!allowed) {
                throw new IllegalArgumentException("host contains '" + c + "': " + host);
            }
        }
        return clean;
    }

    public static int requirePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        return port;
    }

    /** A player name the native client can hold: at most 24 bytes, no spaces. */
    public static String requireNickname(String nickname) {
        if (nickname == null) {
            throw new IllegalArgumentException("nickname is null");
        }
        String clean = nickname.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("nickname is empty");
        }
        if (clean.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("nickname is longer than " + MAX_NICKNAME_LENGTH);
        }
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || "_[]()$=.-@".indexOf(c) >= 0;
            if (!allowed) {
                throw new IllegalArgumentException("nickname contains '" + c + "'");
            }
        }
        return clean;
    }

    private static boolean isIpv4Literal(String host) {
        int dots = 0;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') {
                dots++;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return dots == 3;
    }

    private static boolean ipv4IsValid(String literal) {
        String[] parts = literal.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException notANumber) {
                return false;
            }
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    public String name() {
        return name;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String address() {
        return host + ":" + port;
    }

    /** The line this endpoint is stored as. Round-trips through {@link #parse(String)}. */
    public String format() {
        return name + "|" + address();
    }

    @Override
    public String toString() {
        return name + "|" + address();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ServerEndpoint)) {
            return false;
        }
        ServerEndpoint that = (ServerEndpoint) other;
        return port == that.port && host.equals(that.host) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return (host.hashCode() * 31 + port) * 31 + name.hashCode();
    }
}
