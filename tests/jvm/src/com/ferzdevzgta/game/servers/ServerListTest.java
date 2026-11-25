package com.ferzdevzgta.game.servers;

import com.ferzdevzgta.game.Check;

import java.util.ArrayList;
import java.util.List;

/** The server list file: comments, blank lines, duplicates and one bad line. */
public final class ServerListTest {

    private ServerListTest() {}

    public static void run() {
        String body = "# my servers\n"
                + "\n"
                + "Alpha|1.2.3.4:7777\n"
                + "not a server line at all|\n"
                + "Bravo|5.6.7.8\n"
                + "Alpha again|1.2.3.4:7777\n"
                + "   \n";

        List<ServerEndpoint> parsed = ServerList.parse(body);
        Check.eq("two unique servers survive a file with junk", 2, parsed.size());
        Check.eq("first server keeps its order", "Alpha", parsed.get(0).name());
        Check.eq("second server keeps its order", "Bravo", parsed.get(1).name());
        Check.eq("missing port falls back to 7777", 7777, parsed.get(1).port());

        Check.eq("empty text is an empty list", 0, ServerList.parse("").size());
        Check.eq("null text is an empty list", 0, ServerList.parse(null).size());
        Check.eq("only comments is an empty list", 0, ServerList.parse("# a\n# b\n").size());

        String written = ServerList.serialize(parsed);
        Check.ok("serialized list names the format", written.contains("Name|host:port"));
        Check.ok("serialized list keeps the first server", written.contains("Alpha|1.2.3.4:7777"));
        Check.eq("serialized list reads back the same size", 2, ServerList.parse(written).size());

        List<ServerEndpoint> list = new ArrayList<>(parsed);
        list = ServerList.addOrReplace(list, new ServerEndpoint("Renamed", "1.2.3.4", 7777));
        Check.eq("replace does not grow the list", 2, list.size());
        Check.eq("replace keeps the position", "Renamed", list.get(0).name());

        list = ServerList.addOrReplace(list, new ServerEndpoint("Charlie", "9.9.9.9", 7777));
        Check.eq("a new server is appended", 3, list.size());
        Check.eq("the appended server is last", "Charlie", list.get(2).name());
        Check.eq("addOrReplace tolerates a null list", 1, ServerList.addOrReplace(null, parsed.get(0)).size());
    }
}
