// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.nvidia.devtech;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Opens files that live inside the APK's assets for the engine, which asks by name without a
 * directory component.
 *
 * The index is built once, from `assetfile.txt` when the build provides it and otherwise from
 * a single recursive scan of the asset tree. Lookups are lower case because the engine asks in
 * whatever case the data file uses, and asset names on device are case sensitive.
 */
public class NvAPKFileHelper {

    private static final String TAG = "NvAPKFileHelper";

    /** Optional pre-built index: first line is the entry count, then one path per line. */
    private static final String INDEX_FILE = "assetfile.txt";

    /** The engine falls back to this extension when a plain name is not found. */
    private static final String FALLBACK_EXTENSION = ".mp3";

    private static final int READ_BUFFER_SIZE = 4096;

    private static final NvAPKFileHelper INSTANCE = new NvAPKFileHelper();

    private final Map<String, String> assetPaths = new HashMap<>();

    private Context context;
    private int assetCount;
    private boolean indexBuilt;

    private NvAPKFileHelper() {}

    public static NvAPKFileHelper getInstance() {
        return INSTANCE;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    // --- lookup -------------------------------------------------------------

    private String resolve(String requested) {
        if (requested == null) {
            return null;
        }
        String key = requested.toLowerCase(Locale.ROOT);
        String exact = assetPaths.get(key);
        return exact != null ? exact : assetPaths.get(key + FALLBACK_EXTENSION);
    }

    private void addAssetFile(String path) {
        assetPaths.put(path.toLowerCase(Locale.ROOT), path);
        assetCount++;
    }

    /** Builds the index once, from the pre-built file or from a scan. */
    private void buildIndex() {
        if (context == null) {
            Log.w(TAG, "no context, asset lookup is disabled");
            return;
        }
        if (readIndexFile()) {
            return;
        }
        assetPaths.clear();
        assetCount = 0;
        getDirectoryListing(context.getAssets(), "");
    }

    private boolean readIndexFile() {
        try (InputStream stream = context.getAssets().open(INDEX_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String header = reader.readLine();
            if (header == null) {
                return false;
            }
            int count = Integer.parseInt(header.trim());
            if (count <= 0) {
                return false;
            }
            assetPaths.clear();
            assetCount = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                String path = line.trim();
                if (!path.isEmpty()) {
                    addAssetFile(path);
                }
            }
            return true;
        } catch (IOException | NumberFormatException unusable) {
            // No usable index in this build: fall back to the scan.
            Log.i(TAG, "no usable " + INDEX_FILE + ", scanning assets instead");
            return false;
        }
    }

    /** Walks the asset tree once. Entries without a dot are treated as directories. */
    void getDirectoryListing(AssetManager assetManager, String path) {
        try {
            String[] entries = assetManager.list(path);
            if (entries == null) {
                return;
            }
            if (entries.length == 0) {
                if (!path.isEmpty()) {
                    addAssetFile(path);
                }
                return;
            }
            for (String entry : entries) {
                String child = path.isEmpty() ? entry : path + "/" + entry;
                if (entry.indexOf('.') == -1) {
                    getDirectoryListing(assetManager, child);
                } else {
                    addAssetFile(child);
                }
            }
        } catch (Exception failure) {
            Log.w(TAG, "cannot list assets at '" + path + "'", failure);
        }
    }

    // --- streams ------------------------------------------------------------

    public NvAPKFile openFileAndroid(String name) {
        if (!indexBuilt) {
            buildIndex();
            indexBuilt = true;
        }
        String path = resolve(name);
        if (path == null) {
            return null;
        }

        NvAPKFile file = new NvAPKFile();
        file.bufferSize = 0;
        file.position = 0;
        file.length = 0;
        try {
            InputStream stream = context.getAssets().open(path);
            file.is = stream;
            file.length = stream.available();
            // Mark at the start so seekFileAndroid can return to position 0.
            stream.mark(file.length);
            file.bufferSize = READ_BUFFER_SIZE;
            file.data = new byte[READ_BUFFER_SIZE];
            return file;
        } catch (IOException missing) {
            Log.w(TAG, "cannot open asset '" + path + "': " + missing.getMessage());
            return null;
        }
    }

    public void readFileAndroid(NvAPKFile file, int count) {
        if (file == null || file.is == null || count <= 0) {
            return;
        }
        if (count > file.bufferSize) {
            file.data = new byte[count];
            file.bufferSize = count;
        }
        try {
            int read = file.is.read(file.data, 0, count);
            if (read > 0) {
                file.position += read;
            }
        } catch (IOException failure) {
            Log.w(TAG, "read failed at " + file.position, failure);
        }
    }

    /** Seeks to an absolute offset by skipping, because an asset stream is not seekable. */
    public long seekFileAndroid(NvAPKFile file, int targetPosition) {
        if (file == null || file.is == null) {
            return 0;
        }
        try {
            file.is.reset();
            long remaining = targetPosition;
            while (remaining > 0) {
                long skipped = file.is.skip(remaining);
                if (skipped <= 0) {
                    break;
                }
                remaining -= skipped;
            }
            file.position = (int) (targetPosition - remaining);
            return file.position;
        } catch (IOException failure) {
            Log.w(TAG, "seek to " + targetPosition + " failed", failure);
            return file.position;
        }
    }

    public void closeFileAndroid(NvAPKFile file) {
        if (file == null) {
            return;
        }
        try {
            if (file.is != null) {
                file.is.close();
            }
        } catch (IOException failure) {
            Log.w(TAG, "close failed", failure);
        }
        file.data = new byte[0];
        file.is = null;
    }

    /** Number of indexed assets, useful when the engine asks why a name was not found. */
    int getAssetCount() {
        return assetCount;
    }
}
