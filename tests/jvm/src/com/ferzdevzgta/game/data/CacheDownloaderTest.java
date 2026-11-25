package com.ferzdevzgta.game.data;

import com.ferzdevzgta.game.Check;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Download, resume, skip, retry, corruption. Against a real HTTP server. */
public final class CacheDownloaderTest {

    private static final class Recorder implements CacheDownloader.Progress {
        final List<String> events = new ArrayList<>();

        public void onFileStart(String path, int index, int count) {
            events.add("start " + path + " " + index + "/" + count);
        }

        public void onFileProgress(String path, long done, long size) {
            events.add("progress " + path + " " + done + "/" + size);
        }

        public void onFileDone(String path, boolean downloaded) {
            events.add("done " + path + (downloaded ? " downloaded" : " skipped"));
        }

        public void onStatus(String message) {
            events.add("status " + message);
        }
    }

    private static final CacheDownloader.Sleeper NO_SLEEP = millis -> { };

    public static void run() throws Exception {
        File serverRoot = Fixture.tempDir("srv");
        File local = Fixture.tempDir("dst");
        Fixture.write(serverRoot, "data/handling.cfg", Fixture.bytes(200_000, 7));
        Fixture.write(serverRoot, "audio/radio/x.ogg", Fixture.bytes(120_000, 9));
        String[] paths = {"data/handling.cfg", "audio/radio/x.ogg"};

        try (Fixture.Server srv = new Fixture.Server(serverRoot)) {
            CacheManifest m = CacheManifest.parse(Fixture.manifestFor(serverRoot, paths));

            // 1. cold run downloads both
            Recorder rec = new Recorder();
            CacheDownloader d = new CacheDownloader(srv.baseUrl(), m, local, NO_SLEEP);
            CacheDownloader.Result r = d.sync(rec);
            Check.ok("cold run succeeds: " + r.summary(), r.ok());
            Check.eq("cold run downloaded", 2, r.downloaded);
            Check.eq("cold run skipped nothing", 0, r.skipped);
            Check.eq("bytes transferred", 320_000L, r.bytes);
            Check.ok("tree verifies after download", m.isComplete(local));
            Check.eq("two HTTP requests for two files", 2, srv.requests.get());
            Check.eq("no .part files left behind", 0, partFiles(local));

            // 2. warm run touches nothing
            srv.requests.set(0);
            Recorder warm = new Recorder();
            CacheDownloader.Result r2 = new CacheDownloader(srv.baseUrl(), m, local, NO_SLEEP).sync(warm);
            Check.eq("warm run downloaded nothing", 0, r2.downloaded);
            Check.eq("warm run skipped both", 2, r2.skipped);
            Check.eq("warm run made no requests", 0, srv.requests.get());

            // 3. interrupted transfer resumes instead of restarting
            File half = new File(local, "data/handling.cfg");
            byte[] full = Files.readAllBytes(half.toPath());
            Files.write(new File(local, "data/handling.cfg.part").toPath(),
                    java.util.Arrays.copyOfRange(full, 0, 50_000));
            Files.delete(half.toPath());
            srv.requests.set(0);
            srv.ranged.set(0);
            CacheDownloader.Result r3 = new CacheDownloader(srv.baseUrl(), m, local, NO_SLEEP).sync(new Recorder());
            Check.eq("resume downloaded the one broken file", 1, r3.downloaded);
            Check.eq("resume used a Range request", 1, srv.ranged.get());
            Check.eq("resume transferred only the tail", 150_000L, r3.bytes);
            Check.ok("resumed file verifies", m.isComplete(local));

            // 4. a corrupted response is retried, then reported, never kept
            Files.delete(new File(local, "audio/radio/x.ogg").toPath());
            srv.corrupt = true;
            srv.requests.set(0);
            Recorder bad = new Recorder();
            CacheDownloader.Result r4 = new CacheDownloader(srv.baseUrl(), m, local, NO_SLEEP).sync(bad);
            Check.eq("3 attempts on a corrupt file", CacheDownloader.MAX_ATTEMPTS, srv.requests.get());
            Check.ok("corrupt file reported as failure", !r4.ok());
            Check.eq("one failure recorded", 1, r4.failures.size());
            Check.ok("failure names the file", r4.failures.get(0).contains("x.ogg"));
            Check.ok("no corrupt file left on disk", !new File(local, "audio/radio/x.ogg").isFile());
            Check.ok("retry was recorded", bad.events.stream().anyMatch(e -> e.startsWith("status retry")));
            srv.corrupt = false;

            // 5. 404 fails fast with the status code in the message
            File missing = Fixture.tempDir("missing");
            CacheManifest m404 = CacheManifest.parse(Fixture.manifestFor(serverRoot, new String[]{paths[0]}));
            Files.deleteIfExists(new File(missing, "data/handling.cfg").toPath());
            srv.requests.set(0);
            java.nio.file.Files.deleteIfExists(new File(serverRoot, "data/handling.cfg").toPath());
            CacheDownloader.Result r5 = new CacheDownloader(srv.baseUrl(), m404, missing, NO_SLEEP).sync(new Recorder());
            Check.ok("404 reported", !r5.ok());
            Check.ok("404 mentions the code", r5.failures.get(0).contains("404"));
        }

        // 6. backoff grows and stays jittered
        long b1 = CacheDownloader.backoffMillis(1);
        long b2 = CacheDownloader.backoffMillis(2);
        long b3 = CacheDownloader.backoffMillis(3);
        Check.ok("backoff grows: " + b1 + " < " + b2 + " < " + b3, b1 < b2 && b2 < b3);
        Check.ok("backoff has jitter", b3 >= 2000 && b3 < 2250);
        long min = Long.MAX_VALUE;
        for (int i = 0; i < 50; i++) {
            min = Math.min(min, CacheDownloader.backoffMillis(1));
        }
        Check.ok("jitter varies across calls", min < 750);

        // 7. path encoding keeps the separators but escapes the rest
        Check.eq("encode keeps slashes", "data/handling.cfg", CacheDownloader.encodePath("data/handling.cfg"));
        Check.eq("encode escapes space", "a%20b/c%23d", CacheDownloader.encodePath("a b/c#d"));
    }

    private static int partFiles(File root) {
        File[] all = root.listFiles();
        int n = 0;
        if (all == null) {
            return 0;
        }
        for (File f : all) {
            if (f.isDirectory()) {
                n += partFiles(f);
            } else if (f.getName().endsWith(".part")) {
                n++;
            }
        }
        return n;
    }
}
