package com.ferzdevzgta.game.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pulls the asset tree described by a {@link CacheManifest} from a plain HTTP
 * host. Replaces Play Asset Delivery, which only works for an app installed from
 * the Play Store and therefore does nothing for a sideloaded APK.
 *
 * Properties that matter and are tested in {@code tests/jvm}:
 *   - a file already on disk with the right size and hash is never downloaded;
 *   - an interrupted transfer resumes with a Range request instead of restarting;
 *   - a file whose hash is wrong after download is deleted and retried, never kept;
 *   - retries back off exponentially and give up after {@link #MAX_ATTEMPTS}.
 *
 * No Android imports: the same code runs under a plain JVM test.
 */
public final class CacheDownloader {

    public static final int MAX_ATTEMPTS = 3;

    public interface Progress {
        void onFileStart(String path, int index, int count);

        void onFileProgress(String path, long done, long size);

        void onFileDone(String path, boolean downloaded);

        void onStatus(String message);
    }

    /** Injected so tests do not actually wait. */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public static final class Result {
        public int downloaded;
        public int skipped;
        public long bytes;
        public final List<String> failures = new ArrayList<>();

        public boolean ok() {
            return failures.isEmpty();
        }

        public String summary() {
            return downloaded + " downloaded, " + skipped + " already valid, "
                    + (bytes / (1024 * 1024)) + " MB transferred"
                    + (failures.isEmpty() ? "" : ", " + failures.size() + " failed");
        }
    }

    private final String baseUrl;
    private final CacheManifest manifest;
    private final File root;
    private final Sleeper sleeper;

    public CacheDownloader(String baseUrl, CacheManifest manifest, File root, Sleeper sleeper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.manifest = manifest;
        this.root = root;
        this.sleeper = sleeper;
    }

    public Result sync(Progress progress) throws InterruptedException {
        Result result = new Result();
        List<CacheManifest.Entry> entries = manifest.entries();
        for (int idx = 0; idx < entries.size(); idx++) {
            CacheManifest.Entry e = entries.get(idx);
            if (Thread.interrupted()) {
                throw new InterruptedException("cancelled");
            }
            File target = new File(root, e.path);
            if (isValid(target, e)) {
                result.skipped++;
                progress.onFileDone(e.path, false);
                continue;
            }
            progress.onFileStart(e.path, idx + 1, entries.size());
            try {
                long moved = fetchWithRetries(e, target, progress);
                result.downloaded++;
                result.bytes += moved;
                progress.onFileDone(e.path, true);
            } catch (IOException ex) {
                result.failures.add(e.path + ": " + ex.getMessage());
                progress.onStatus("gagal: " + e.path + ", " + ex.getMessage());
            }
        }
        return result;
    }

    static boolean isValid(File f, CacheManifest.Entry e) {
        return f.isFile() && f.length() == e.size && e.sha256.equals(CacheManifest.sha256(f));
    }

    private long fetchWithRetries(CacheManifest.Entry e, File target, Progress progress)
            throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                long moved = fetchOnce(e, target, progress);
                if (!e.sha256.equals(CacheManifest.sha256(target))) {
                    // A corrupted transfer must never reach the engine.
                    delete(target);
                    throw new IOException("hash mismatch (attempt " + attempt + ")");
                }
                return moved;
            } catch (IOException ex) {
                last = ex;
                if (attempt < MAX_ATTEMPTS) {
                    long wait = backoffMillis(attempt);
                    progress.onStatus("retry " + attempt + "/" + (MAX_ATTEMPTS - 1)
                            + " in " + wait + " ms: " + e.path);
                    sleeper.sleep(wait);
                }
            }
        }
        throw new IOException(last == null ? "unknown failure" : last.getMessage(), last);
    }

    /** 500, 1000, 2000… plus up to 250 ms of jitter so retries do not sync up. */
    static long backoffMillis(int attempt) {
        long base = 500L << (attempt - 1);
        return base + ThreadLocalRandom.current().nextLong(250);
    }

    private long fetchOnce(CacheManifest.Entry e, File target, Progress progress) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create " + parent);
        }
        File part = new File(target.getPath() + ".part");
        long have = part.isFile() ? part.length() : 0;
        if (have > e.size) {
            delete(part);
            have = 0;
        }

        URL url = new URL(baseUrl + "/" + encodePath(e.path));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept-Encoding", "identity");
        if (have > 0) {
            c.setRequestProperty("Range", "bytes=" + have + "-");
        }
        long moved = 0;
        try {
            int code = c.getResponseCode();
            boolean resuming = code == HttpURLConnection.HTTP_PARTIAL;
            if (code != HttpURLConnection.HTTP_OK && !resuming) {
                throw new IOException("HTTP " + code);
            }
            if (resuming && have > 0 && have < e.size) {
                // keep what is on disk and append
            } else {
                have = 0;
            }
            long done = have;
            try (InputStream in = c.getInputStream();
                 FileOutputStream out = new FileOutputStream(part, have > 0)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                long lastTick = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    moved += n;
                    if (done - lastTick >= 512 * 1024) {
                        lastTick = done;
                        progress.onFileProgress(e.path, done, e.size);
                    }
                }
                out.flush();
                out.getFD().sync();
            }
            if (part.length() != e.size) {
                throw new IOException("size " + part.length() + " != " + e.size);
            }
            if (!part.renameTo(target)) {
                throw new IOException("cannot move into place: " + target);
            }
            return moved;
        } finally {
            c.disconnect();
        }
    }

    static String encodePath(String path) {
        StringBuilder sb = new StringBuilder(path.length() + 8);
        for (byte b : path.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '/' || c == '.' || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    private static void delete(File f) {
        if (f.isFile() && !f.delete()) {
            try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
                raf.setLength(0);
            } catch (IOException ignored) {
                // the hash check will fail again and the file is never used
            }
        }
    }
}
