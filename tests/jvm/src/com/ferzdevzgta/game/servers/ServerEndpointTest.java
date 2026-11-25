package com.ferzdevzgta.game.servers;

import com.ferzdevzgta.game.Check;

/** Parsing rules for one server line. Every rejection here is a string the engine cannot use. */
public final class ServerEndpointTest {

    private ServerEndpointTest() {}

    public static void run() {
        ServerEndpoint full = ServerEndpoint.parse("Los Santos Roleplay|samp.psgaming.net:7777");
        Check.eq("name is kept", "Los Santos Roleplay", full.name());
        Check.eq("host is lowercased", "samp.psgaming.net", full.host());
        Check.eq("port is kept", 7777, full.port());
        Check.eq("address", "samp.psgaming.net:7777", full.address());

        ServerEndpoint bare = ServerEndpoint.parse("1.2.3.4:7777");
        Check.eq("bare line falls back to host as name", "1.2.3.4", bare.name());
        Check.eq("bare line keeps its port", 7777, bare.port());

        ServerEndpoint noPort = ServerEndpoint.parse("example.org");
        Check.eq("missing port uses the default", ServerEndpoint.DEFAULT_PORT, noPort.port());

        ServerEndpoint scheme = ServerEndpoint.parse("My Server|samp://127.0.0.1:1234");
        Check.eq("samp scheme is stripped", "127.0.0.1", scheme.host());
        Check.eq("scheme line keeps its name", "My Server", scheme.name());

        ServerEndpoint padded = ServerEndpoint.parse("  Padded  |  10.0.0.1:9999  ");
        Check.eq("whitespace is trimmed", "Padded", padded.name());
        Check.eq("host is trimmed", "10.0.0.1", padded.host());

        Check.ok("round trips through format", ServerEndpoint.parse(padded.format()).equals(padded));

        Check.eq("comment is not a server", null, ServerEndpoint.tryParse("# hello"));
        Check.eq("blank line is not a server", null, ServerEndpoint.tryParse("   "));
        Check.eq("null is not a server", null, ServerEndpoint.tryParse(null));
        Check.eq("port out of range is rejected", null, ServerEndpoint.tryParse("8.8.8.8:70000"));
        Check.eq("non numeric port is rejected", null, ServerEndpoint.tryParse("8.8.8.8:abc"));
        Check.eq("bad octet is rejected", null, ServerEndpoint.tryParse("256.1.1.1:7777"));
        Check.eq("space in host is rejected", null, ServerEndpoint.tryParse("bad host:7777"));
        Check.eq("slash in host is rejected", null, ServerEndpoint.tryParse("bad/host:7777"));

        Check.throwsOn("port zero", () -> ServerEndpoint.requirePort(0));
        Check.throwsOn("port above 65535", () -> ServerEndpoint.requirePort(65536));
        Check.throwsOn("empty host", () -> ServerEndpoint.requireHost("  "));
        Check.throwsOn("null host", () -> ServerEndpoint.requireHost(null));
        Check.throwsOn("host with unicode", () -> ServerEndpoint.requireHost("exämple.org"));

        Check.eq("nickname keeps digits and dots", "Player_1[x]", ServerEndpoint.requireNickname(" Player_1[x] "));
        Check.throwsOn("nickname with a space", () -> ServerEndpoint.requireNickname("bad name"));
        Check.throwsOn("empty nickname", () -> ServerEndpoint.requireNickname("   "));
        Check.throwsOn("nickname longer than 24", () -> ServerEndpoint.requireNickname("a234567890123456789012345"));
        Check.throwsOn("nickname with a semicolon", () -> ServerEndpoint.requireNickname("name;drop"));

        ServerEndpoint longName = new ServerEndpoint(
                "a name that goes on and on and on and on and on and on and on and on", "1.1.1.1", 7777);
        Check.eq("long display name is clipped", ServerEndpoint.MAX_NAME_LENGTH, longName.name().length());
    }
}
