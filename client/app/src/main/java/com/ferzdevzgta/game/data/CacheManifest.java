package com.ferzdevzgta.game.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A cache manifest as produced by {@code bin/build-cache.sh}: a list of files
 * with their size and SHA-256, so a downloaded tree can be proven complete
 * before the engine is allowed to read it.
 *
 * Deliberately free of Android imports: it is compiled and tested on the JVM by
 * {@code tests/jvm}, where a wrong hash check is cheap to catch.
 */
public final class CacheManifest {

    public static final class Entry {
        public final String path;
        public final long size;
        public final String sha256;

        Entry(String path, long size, String sha256) {
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
        }

        public String toString() {
            return path + " (" + size + " B, " + sha256.substring(0, 8) + "…)";
        }
    }

    public static final class WrongFormatException extends IllegalArgumentException {
        public WrongFormatException(String message) {
            super(message);
        }
    }

    private final String variant;
    private final List<Entry> entries;
    private final long totalBytes;

    private CacheManifest(String variant, List<Entry> entries, long totalBytes) {
        this.variant = variant;
        this.entries = Collections.unmodifiableList(entries);
        this.totalBytes = totalBytes;
    }

    public String variant() {
        return variant;
    }

    public List<Entry> entries() {
        return entries;
    }

    public long totalBytes() {
        return totalBytes;
    }

    // --- parsing -------------------------------------------------------------

    public static CacheManifest parse(String json) {
        Object root = new Json(json).parseDocument();
        if (!(root instanceof Map)) {
            throw new WrongFormatException("manifest root is not an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) root;

        Object filesNode = doc.get("files");
        if (!(filesNode instanceof List)) {
            throw new WrongFormatException("manifest has no \"files\" array");
        }

        List<Entry> out = new ArrayList<>();
        long total = 0;
        for (Object item : (List<?>) filesNode) {
            if (!(item instanceof Map)) {
                throw new WrongFormatException("a \"files\" element is not an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> f = (Map<String, Object>) item;
            String path = str(f, "path");
            long size = num(f, "size");
            String sha = str(f, "sha256").toLowerCase(java.util.Locale.ROOT);
            if (sha.length() != 64) {
                throw new WrongFormatException("bad sha256 for " + path + ": " + sha);
            }
            if (path.startsWith("/") || path.contains("..")) {
                throw new WrongFormatException("refusing unsafe path: " + path);
            }
            out.add(new Entry(path, size, sha));
            total += size;
        }
        if (out.isEmpty()) {
            throw new WrongFormatException("manifest lists no files");
        }
        Object v = doc.get("variant");
        return new CacheManifest(v instanceof String ? (String) v : "unknown", out, total);
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof String)) {
            throw new WrongFormatException("missing string field: " + key);
        }
        return (String) v;
    }

    private static long num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof Double)) {
            throw new WrongFormatException("missing numeric field: " + key);
        }
        return (long) ((Double) v).doubleValue();
    }

    // --- verification --------------------------------------------------------

    /** Paths present but not listed, missing, the wrong size, or the wrong hash. */
    public List<String> verify(File root) {
        List<String> problems = new ArrayList<>();
        for (Entry e : entries) {
            File f = new File(root, e.path);
            if (!f.isFile()) {
                problems.add("missing " + e.path);
            } else if (f.length() != e.size) {
                problems.add("size " + e.path + " (" + f.length() + " != " + e.size + ")");
            } else if (!e.sha256.equals(sha256(f))) {
                problems.add("sha256 " + e.path);
            }
        }
        return problems;
    }

    public boolean isComplete(File root) {
        return verify(root).isEmpty();
    }

    public static String sha256(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return hex(md.digest());
        } catch (IOException e) {
            return "";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from this runtime", e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Minimal JSON reader: objects, arrays, strings, numbers, true/false/null.
     * The manifest is machine-written and flat, so a full JSON library would be
     * 300 KB of APK for one file shape.
     */
    private static final class Json {
        private final String s;
        private int i;

        Json(String s) {
            this.s = s;
        }

        Object parseDocument() {
            skip();
            Object v = value();
            skip();
            if (i != s.length()) {
                throw new WrongFormatException("trailing data at offset " + i);
            }
            return v;
        }

        private Object value() {
            skip();
            if (i >= s.length()) {
                throw new WrongFormatException("unexpected end of manifest");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return number();
            }
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new HashMap<>();
            i++; // {
            skip();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                skip();
                String k = string();
                skip();
                if (peek() != ':') {
                    throw new WrongFormatException("expected ':' at offset " + i);
                }
                i++;
                m.put(k, value());
                skip();
                char c = peek();
                i++;
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw new WrongFormatException("expected ',' or '}' at offset " + (i - 1));
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skip();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(value());
                skip();
                char c = peek();
                i++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new WrongFormatException("expected ',' or ']' at offset " + (i - 1));
                }
            }
        }

        private String string() {
            if (peek() != '"') {
                throw new WrongFormatException("expected a string at offset " + i);
            }
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw new WrongFormatException("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:
                        throw new WrongFormatException("bad escape \\" + e);
                }
            }
        }

        private Double number() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) {
                    i++;
                } else {
                    break;
                }
            }
            if (start == i) {
                throw new WrongFormatException("unexpected character '" + s.charAt(i) + "' at offset " + i);
            }
            return Double.valueOf(s.substring(start, i));
        }

        private void expect(String word) {
            if (!s.startsWith(word, i)) {
                throw new WrongFormatException("expected " + word + " at offset " + i);
            }
            i += word.length();
        }

        private char peek() {
            if (i >= s.length()) {
                throw new WrongFormatException("unexpected end of manifest");
            }
            return s.charAt(i);
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
