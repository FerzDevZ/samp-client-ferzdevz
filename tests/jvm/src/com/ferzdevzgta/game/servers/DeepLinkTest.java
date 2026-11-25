package com.ferzdevzgta.game.servers;

import com.ferzdevzgta.game.Check;

/** Deep link shape: what is joinable, what is refused, and the round trip back to a link. */
public final class DeepLinkTest {

    public static void run() {
        ServerEndpoint a = DeepLink.parse("samp://127.0.0.1:7777");
        Check.ok("ip and port accepted", a != null);
        Check.eq("host kept", "127.0.0.1", a.host());
        Check.eq("port kept", 7777, a.port());

        ServerEndpoint b = DeepLink.parse("samp://samp.example.com:7778");
        Check.ok("hostname accepted", b != null);
        Check.eq("hostname kept", "samp.example.com", b.host());
        Check.eq("alternate port kept", 7778, b.port());

        ServerEndpoint bare = DeepLink.parse("samp://samp.example.com");
        Check.ok("port optional", bare != null);
        Check.eq("default port", ServerEndpoint.DEFAULT_PORT, bare.port());

        ServerEndpoint upper = DeepLink.parse("SAMP://127.0.0.1:7777");
        Check.ok("scheme is case insensitive", upper != null);

        ServerEndpoint trailing = DeepLink.parse("samp://127.0.0.1:7777/");
        Check.ok("trailing slash tolerated", trailing != null);
        Check.eq("trailing slash does not change port", 7777, trailing.port());

        ServerEndpoint noisy = DeepLink.parse("samp://127.0.0.1:7777?nickname=x#top");
        Check.ok("query and fragment cut", noisy != null);
        Check.eq("query does not break port", 7777, noisy.port());

        Check.ok("http refused", DeepLink.parse("http://127.0.0.1:7777") == null);
        Check.ok("no scheme refused", DeepLink.parse("127.0.0.1:7777") == null);
        Check.ok("empty refused", DeepLink.parse("samp://") == null);
        Check.ok("null refused", DeepLink.parse(null) == null);
        Check.ok("path refused", DeepLink.parse("samp://host:7777/join") == null);
        Check.ok("zero port refused", DeepLink.parse("samp://host:0") == null);
        Check.ok("big port refused", DeepLink.parse("samp://host:70000") == null);
        Check.ok("text port refused", DeepLink.parse("samp://host:abc") == null);
        Check.ok("empty host refused", DeepLink.parse("samp://:7777") == null);

        ServerEndpoint server = new ServerEndpoint("Test", "127.0.0.1", 7777);
        Check.eq("link built", "samp://127.0.0.1:7777", DeepLink.toUri(server));
        Check.ok("link round trip", DeepLink.parse(DeepLink.toUri(server)) != null);
        Check.ok("no link for null", DeepLink.toUri(null) == null);
    }
}
