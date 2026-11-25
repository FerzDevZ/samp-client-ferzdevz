package com.ferzdevzgta.game.servers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's server list, stored as plain text at {@code /storage/emulated/0/GTA/SAMP/servers.txt}.
 *
 * One server per line: {@code Name|host:port}. Blank lines and lines starting with {@code #} are
 * ignored, so a list can be annotated by hand or edited on a PC without the app. A line that does
 * not parse is skipped instead of failing the whole file: one typo must not hide every server.
 */
public final class ServerList {

    public static final String FILE_NAME = "servers.txt";

    private ServerList() {}

    /** Parses the whole file body, dropping duplicates and unparseable lines. */
    public static List<ServerEndpoint> parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        // Identity is the address, not the display name: the same server listed twice under two
        // names is still one connection target.
        Map<String, ServerEndpoint> unique = new LinkedHashMap<>();
        for (String line : text.split("\\R", -1)) {
            ServerEndpoint endpoint = ServerEndpoint.tryParse(line);
            if (endpoint != null && !unique.containsKey(endpoint.address())) {
                unique.put(endpoint.address(), endpoint);
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** Writes the list back, comments and all, keeping whatever the player typed. */
    public static String serialize(List<ServerEndpoint> servers) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Server list for the SA-MP client. One per line: Name|host:port\n");
        sb.append("# Lines starting with # and blank lines are ignored.\n");
        for (ServerEndpoint server : servers) {
            sb.append(server.format()).append('\n');
        }
        return sb.toString();
    }

    /** Adds or replaces a server, keeping the order stable so the list does not jump. */
    public static List<ServerEndpoint> addOrReplace(List<ServerEndpoint> servers, ServerEndpoint server) {
        List<ServerEndpoint> out = new ArrayList<>(servers == null ? Collections.emptyList() : servers);
        for (int i = 0; i < out.size(); i++) {
            ServerEndpoint existing = out.get(i);
            if (existing.host().equals(server.host()) && existing.port() == server.port()) {
                out.set(i, server);
                return out;
            }
        }
        out.add(server);
        return out;
    }
}
