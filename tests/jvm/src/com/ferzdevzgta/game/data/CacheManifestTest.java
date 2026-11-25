package com.ferzdevzgta.game.data;

import com.ferzdevzgta.game.Check;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Manifest parsing + verification. */
public final class CacheManifestTest {

    public static void run() throws Exception {
        File dir = Fixture.tempDir("man");
        Fixture.write(dir, "data/handling.cfg", Fixture.bytes(4096, 1));
        Fixture.write(dir, "models/gta3.img", Fixture.bytes(9000, 2));
        String[] paths = {"data/handling.cfg", "models/gta3.img"};
        String json = Fixture.manifestFor(dir, paths);

        CacheManifest m = CacheManifest.parse(json);
        Check.eq("manifest variant", "full", m.variant());
        Check.eq("manifest entries", 2, m.entries().size());
        Check.eq("manifest total bytes", 13096L, m.totalBytes());
        Check.eq("manifest clean tree has no problems", 0, m.verify(dir).size());
        Check.ok("manifest isComplete", m.isComplete(dir));

        // one flipped byte must be named, not merely counted
        File victim = new File(dir, "data/handling.cfg");
        byte[] data = Files.readAllBytes(victim.toPath());
        data[100] ^= 0xFF;
        Files.write(victim.toPath(), data);
        List<String> problems = m.verify(dir);
        Check.eq("tampered file reported once", 1, problems.size());
        Check.ok("tampered file named in the problem", problems.get(0).contains("data/handling.cfg"));

        // truncation is a size problem, cheaper to detect than a bad hash
        Files.write(new File(dir, "models/gta3.img").toPath(), Fixture.bytes(10, 3));
        Check.eq("short file reported", 2, m.verify(dir).size());

        Check.throwsOn("missing file array rejected", () -> CacheManifest.parse("{\"variant\":\"full\"}"));
        Check.throwsOn("empty file array rejected", () -> CacheManifest.parse("{\"files\":[]}"));
        Check.throwsOn("short sha rejected",
                () -> CacheManifest.parse("{\"files\":[{\"path\":\"a\",\"size\":1,\"sha256\":\"abc\"}]}"));
        Check.throwsOn("path traversal rejected", () -> CacheManifest.parse(
                "{\"files\":[{\"path\":\"../../etc/passwd\",\"size\":1,\"sha256\":\""
                        + "0".repeat(64) + "\"}]}"));
        Check.throwsOn("absolute path rejected", () -> CacheManifest.parse(
                "{\"files\":[{\"path\":\"/data/local/tmp/x\",\"size\":1,\"sha256\":\""
                        + "0".repeat(64) + "\"}]}"));
        Check.throwsOn("truncated json rejected", () -> CacheManifest.parse("{\"files\":[{\"path\":\"a\""));

        // the real thing: a manifest produced by bin/build-cache.sh
        File real = new File("tests/fixtures/manifest-sample.json");
        if (real.isFile()) {
            CacheManifest r = CacheManifest.parse(new String(Files.readAllBytes(real.toPath())));
            Check.ok("build-cache.sh manifest parses (" + r.entries().size() + " files)", !r.entries().isEmpty());
        }
    }
}
