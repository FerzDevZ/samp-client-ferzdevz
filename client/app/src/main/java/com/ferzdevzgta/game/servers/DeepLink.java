package com.ferzdevzgta.game.servers;

import java.util.Locale;

/**
 * {@code samp://host:port} links, so a server address posted on a forum or a Discord room can be
 * tapped instead of retyped.
 *
 * Pure by design: the activity hands in the raw URL string and gets a {@link ServerEndpoint} or
 * null, which lets {@code tests/jvm} pin every refusal case without an emulator.
 *
 * Only the shape this client can actually join is accepted. A link with a path, a fragment or an
 * unknown scheme is refused rather than half-understood, because a silent wrong port is worse than
 * a message saying the link is not a server.
 */
public final class DeepLink {

    public static final String SCHEME = "samp";

    private DeepLink() {
    }

    /** Returns the endpoint in the link, or null when the link is not a joinable server address. */
    public static ServerEndpoint parse(String url) {
        if (url == null) {
            return null;
        }
        String text = url.trim();
        int scheme = text.indexOf("://");
        if (scheme <= 0) {
            return null;
        }
        String prefix = text.substring(0, scheme).toLowerCase(Locale.ROOT);
        if (!SCHEME.equals(prefix) && !"samp-client".equals(prefix)) {
            return null;
        }
        String rest = text.substring(scheme + 3);
        // A fragment or query carries nothing this client can use, so it is cut, not parsed.
        int cut = indexOfAny(rest, '#', '?');
        if (cut >= 0) {
            rest = rest.substring(0, cut);
        }
        while (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        if (rest.isEmpty() || rest.indexOf('/') >= 0) {
            return null;
        }
        // Reuse the list parser: a link and a line in servers.txt must agree on what a valid
        // host and port are, or the same address could be accepted in one place and refused in
        // the other.
        return ServerEndpoint.tryParse(rest);
    }

    /** Builds the link a player can share, always with an explicit port. */
    public static String toUri(ServerEndpoint server) {
        if (server == null) {
            return null;
        }
        return SCHEME + "://" + server.host() + ":" + server.port();
    }

    private static int indexOfAny(String text, char first, char second) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == first || c == second) {
                return i;
            }
        }
        return -1;
    }
}
