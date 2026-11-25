package com.ferzdevzgta.game.data;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared fixture: a real HTTP server over a temp directory, with Range support. */
final class Fixture {

    static final class Server implements AutoCloseable {
        final HttpServer http;
        final File root;
        final AtomicInteger requests = new AtomicInteger();
        final AtomicInteger ranged = new AtomicInteger();
        volatile boolean corrupt = false;

        Server(File root) throws IOException {
            this.root = root;
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/", this::handle);
            http.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + http.getAddress().getPort() + "/";
        }

        private void handle(HttpExchange ex) throws IOException {
            requests.incrementAndGet();
            String path = ex.getRequestURI().getPath().substring(1);
            File f = new File(root, path);
            if (!f.isFile()) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            byte[] body = Files.readAllBytes(f.toPath());
            if (corrupt) {
                body[0] ^= 0xFF;
            }
            String range = ex.getRequestHeaders().getFirst("Range");
            long from = 0;
            if (range != null && range.startsWith("bytes=")) {
                ranged.incrementAndGet();
                from = Long.parseLong(range.substring(6, range.indexOf('-')));
            }
            long length = body.length - from;
            if (from > 0) {
                ex.getResponseHeaders().add("Content-Range", "bytes " + from + "-" + (body.length - 1) + "/" + body.length);
                ex.sendResponseHeaders(206, length);
            } else {
                ex.sendResponseHeaders(200, length);
            }
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body, (int) from, (int) length);
            }
        }

        public void close() {
            http.stop(0);
        }
    }

    static File tempDir(String prefix) throws IOException {
        File d = Files.createTempDirectory(prefix).toFile();
        d.deleteOnExit();
        return d;
    }

    static void write(File root, String path, byte[] data) throws IOException {
        File f = new File(root, path);
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        Files.write(f.toPath(), data);
    }

    static byte[] bytes(int size, int seed) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) ((i * 31 + seed) & 0xFF);
        }
        return b;
    }

    /** A manifest covering every file currently under {@code root}. */
    static String manifestFor(File root, String[] paths) {
        StringBuilder files = new StringBuilder();
        long total = 0;
        for (String p : paths) {
            File f = new File(root, p);
            if (files.length() > 0) {
                files.append(",");
            }
            total += f.length();
            files.append("  {\"path\": \"")
                    .append(p)
                    .append("\", \"size\": ")
                    .append(f.length())
                    .append(", \"sha256\": \"")
                    .append(CacheManifest.sha256(f))
                    .append("\"}\n");
        }
        return "{\n \"variant\": \"full\",\n \"files\": [\n" + files + " ],\n \"total_bytes\": " + total + "\n}\n";
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
