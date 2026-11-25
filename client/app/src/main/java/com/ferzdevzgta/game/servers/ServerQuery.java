package com.ferzdevzgta.game.servers;

import java.nio.charset.StandardCharsets;

/**
 * SA-MP server query, the "info" answer only.
 *
 * <p>The request is the twelve byte header the server expects: the literal {@code SAMP}, the four
 * address bytes and the port, followed by {@code i}. The answer repeats the header with {@code i}
 * as the opcode and then carries the lock flag, the player counts and three length prefixed
 * strings.
 *
 * <p>Kept free of Android imports so {@code bin/self-test.sh} can parse a recorded answer on the
 * JVM instead of needing a phone to prove the offsets are right.
 */
public final class ServerQuery {

    /** Query timeout. A server that has not answered in this window counts as offline. */
    public static final int DEFAULT_TIMEOUT_MS = 1200;

    private static final int HEADER_LENGTH = 11;

    private ServerQuery() {
    }

    /** One decoded answer. Immutable; every field is the value the server reported. */
    public static final class Info {
        public final String hostname;
        public final String gamemode;
        public final String language;
        public final int players;
        public final int maxPlayers;
        public final boolean locked;

        Info(String hostname, String gamemode, String language, int players, int maxPlayers,
             boolean locked) {
            this.hostname = hostname;
            this.gamemode = gamemode;
            this.language = language;
            this.players = players;
            this.maxPlayers = maxPlayers;
            this.locked = locked;
        }

        @Override
        public String toString() {
            return hostname + " (" + gamemode + ") " + players + "/" + maxPlayers;
        }
    }

    /** Builds the request for an already resolved address. */
    public static byte[] request(byte[] address, int port) {
        if (address == null || address.length != 4) {
            throw new IllegalArgumentException("address must be 4 bytes");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range");
        }
        byte[] out = new byte[HEADER_LENGTH + 1];
        out[0] = 'S';
        out[1] = 'A';
        out[2] = 'M';
        out[3] = 'P';
        System.arraycopy(address, 0, out, 4, 4);
        out[8] = (byte) (port & 0xFF);
        out[9] = (byte) ((port >> 8) & 0xFF);
        out[10] = 'i';
        out[11] = 'i';
        return out;
    }

    /**
     * Decodes an info answer. Returns null when the packet is not an info answer or is truncated,
     * which is what a UDP port that belongs to something else looks like.
     */
    public static Info parse(byte[] packet) {
        if (packet == null || packet.length < HEADER_LENGTH + 5) {
            return null;
        }
        if (packet[0] != 'S' || packet[1] != 'A' || packet[2] != 'M' || packet[3] != 'P') {
            return null;
        }
        if (packet[10] != 'i') {
            return null;
        }
        int at = HEADER_LENGTH;
        boolean locked = packet[at] != 0;
        int players = readShort(packet, at + 1);
        int maxPlayers = readShort(packet, at + 3);
        if (players < 0 || maxPlayers < 0) {
            return null;
        }
        Cursor cursor = new Cursor(packet, at + 5);
        String hostname = cursor.readString();
        String gamemode = cursor.readString();
        String language = cursor.readString();
        if (hostname == null || gamemode == null || language == null) {
            return null;
        }
        return new Info(hostname.trim(), gamemode.trim(), language.trim(), players, maxPlayers,
                locked);
    }

    private static int readShort(byte[] data, int at) {
        return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8);
    }

    /** Reads length prefixed strings; a length that runs past the buffer ends the parse. */
    private static final class Cursor {
        private final byte[] data;
        private int at;

        Cursor(byte[] data, int at) {
            this.data = data;
            this.at = at;
        }

        String readString() {
            if (at + 2 > data.length) {
                return null;
            }
            int length = readShort(data, at);
            at += 2;
            if (length < 0 || at + length > data.length) {
                return null;
            }
            String value = new String(data, at, length, StandardCharsets.ISO_8859_1);
            at += length;
            return value;
        }
    }
}
