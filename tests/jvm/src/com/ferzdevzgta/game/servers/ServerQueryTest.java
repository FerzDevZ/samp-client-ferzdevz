package com.ferzdevzgta.game.servers;

import com.ferzdevzgta.game.Check;

/** The query request layout and the info answer offsets, checked without a socket. */
public final class ServerQueryTest {

    private ServerQueryTest() {}

    public static void run() {
        byte[] packet = info("FerzDevZ Roleplay", "Freeroam", "English", 34, 100, false);

        Check.eq("request is twelve bytes", 12, ServerQuery.request(new byte[]{1, 2, 3, 4}, 7777)
                .length);
        byte[] request = ServerQuery.request(new byte[]{1, 2, 3, 4}, 7777);
        Check.eq("request magic", "SAMP",
                new String(request, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1));
        Check.eq("request address byte 0", (byte) 1, request[4]);
        Check.eq("request address byte 3", (byte) 4, request[7]);
        Check.eq("request port low byte", (byte) (7777 & 0xFF), request[8]);
        Check.eq("request port high byte", (byte) ((7777 >> 8) & 0xFF), request[9]);
        Check.eq("request opcode", (byte) 'i', request[10]);
        Check.eq("request payload", (byte) 'i', request[11]);

        Check.throwsOn("wrong address length", () -> ServerQuery.request(new byte[]{1, 2}, 7777));
        Check.throwsOn("port zero", () -> ServerQuery.request(new byte[]{1, 2, 3, 4}, 0));
        Check.throwsOn("port too high", () -> ServerQuery.request(new byte[]{1, 2, 3, 4}, 70000));

        ServerQuery.Info parsed = ServerQuery.parse(packet);
        Check.ok("a full answer parses", parsed != null);
        Check.eq("hostname", "FerzDevZ Roleplay", parsed.hostname);
        Check.eq("gamemode", "Freeroam", parsed.gamemode);
        Check.eq("language", "English", parsed.language);
        Check.eq("players", 34, parsed.players);
        Check.eq("max players", 100, parsed.maxPlayers);
        Check.eq("not locked", false, parsed.locked);

        ServerQuery.Info locked = ServerQuery.parse(
                info("Server", "TDM", "Indonesian", 0, 50, true));
        Check.ok("a locked server still parses", locked != null);
        Check.eq("lock flag is read", true, locked.locked);
        Check.eq("zero players is legitimate", 0, locked.players);

        Check.eq("wrong magic is rejected", null, ServerQuery.parse(
                mutate(packet, 0, (byte) 'X')));
        Check.eq("wrong opcode is rejected", null, ServerQuery.parse(
                mutate(packet, 10, (byte) 'c')));
        Check.eq("truncated header is rejected", null,
                ServerQuery.parse(java.util.Arrays.copyOf(packet, 8)));
        Check.eq("truncated payload is rejected", null,
                ServerQuery.parse(java.util.Arrays.copyOf(packet, packet.length - 2)));
        Check.eq("empty packet is rejected", null, ServerQuery.parse(new byte[0]));
        Check.eq("null packet is rejected", null, ServerQuery.parse(null));
        Check.eq("a length past the buffer is rejected", null,
                ServerQuery.parse(mutate(packet, 16, (byte) 0x7F)));

        ServerQuery.Info blank = ServerQuery.parse(info("  Padded  ", " DM ", "EN", 1, 2, false));
        Check.eq("values are trimmed", "Padded", blank.hostname);
        Check.eq("gamemode is trimmed", "DM", blank.gamemode);
        Check.eq("a single player server parses", 1, blank.players);
    }

    /** Builds a valid info answer: eleven byte header, then the payload. */
    private static byte[] info(String name, String mode, String language, int players,
                               int maxPlayers, boolean locked) {
        byte[] hostname = name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] gamemode = mode.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] lang = language.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int size = 11 + 1 + 2 + 2 + (2 + hostname.length) + (2 + gamemode.length)
                + (2 + lang.length);
        byte[] out = new byte[size];
        out[0] = 'S';
        out[1] = 'A';
        out[2] = 'M';
        out[3] = 'P';
        out[10] = 'i';
        int at = 11;
        out[at] = (byte) (locked ? 1 : 0);
        out[at + 1] = (byte) (players & 0xFF);
        out[at + 2] = (byte) ((players >> 8) & 0xFF);
        out[at + 3] = (byte) (maxPlayers & 0xFF);
        out[at + 4] = (byte) ((maxPlayers >> 8) & 0xFF);
        at += 5;
        at = putString(out, at, hostname);
        at = putString(out, at, gamemode);
        putString(out, at, lang);
        return out;
    }

    private static int putString(byte[] out, int at, byte[] value) {
        out[at] = (byte) (value.length & 0xFF);
        out[at + 1] = (byte) ((value.length >> 8) & 0xFF);
        System.arraycopy(value, 0, out, at + 2, value.length);
        return at + 2 + value.length;
    }

    private static byte[] mutate(byte[] source, int index, byte value) {
        byte[] copy = source.clone();
        copy[index] = value;
        return copy;
    }
}
