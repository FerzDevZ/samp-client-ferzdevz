// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.view;

import android.Manifest;
import android.content.ContentUris;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import com.rockstargames.oswrapper.GameActivityBase;
import com.rockstargames.oswrapper.GameThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background music for the game menu: it builds a playlist from a Google Play Music playlist
 * with the same name and from a music folder on shared storage, then plays it on repeat.
 *
 * Both sources are optional. A device with neither simply reports an empty playlist, which is
 * what the engine expects instead of an error.
 */
public final class PlaylistHandler implements GameViewHandler,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener {

    private static final String TAG = "GameView.PlaylistHandler";

    private static final String GOOGLE_PLAYLISTS =
            "content://com.google.android.music.MusicContent/playlists";
    private static final String COLUMN_PLAYLIST_NAME = "playlist_name";
    private static final String COLUMN_SOURCE_ID = "SourceId";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_DATA = "_data";

    /** Shared storage folder the game reads music from. Lower case, matching is case folded. */
    private static final String MUSIC_ROOT = "/storage/emulated/0/music/";

    private GameActivityBase activity;
    private final Object lock = new Object();
    private final List<Uri> playlist = new ArrayList<>();
    private final AtomicBoolean isAvailable = new AtomicBoolean(false);

    private MediaPlayer player;
    private int currentIndex;
    private int currentAt;
    private int currentLength;
    private boolean isPaused;
    private boolean wasPlaying;
    private float volume = 1.0f;

    // --- open ---------------------------------------------------------------

    public void open(final String name) {
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        // Android 13 split storage permission by media type; audio has its own.
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        Log.i(TAG, "open: " + name + ", requesting " + permission);
        getActivity().requestPermission(permission, granted -> doOpen(name, granted));
    }

    private void doOpen(String name, boolean granted) {
        Log.i(TAG, "doOpen: " + name + " granted=" + granted);
        getActivity().runOnUiThread(() -> {
            synchronized (lock) {
                currentLength = 0;
                currentIndex = 0;
                currentAt = 0;
                isPaused = false;
                if (player != null) {
                    player.release();
                    player = null;
                }
                playlist.clear();

                if (granted) {
                    readGoogleMusicPlaylist(name);
                    readStorageFolder(name);
                }

                Log.i(TAG, "open: " + playlist.size() + " track(s)");
                isAvailable.set(!playlist.isEmpty());
            }
            GameThread.INSTANCE.onPlaylistOpenComplete(isAvailable.get(), getCount());
        });
    }

    /** Playlists exported by the Google Play Music app, if it is installed. */
    private void readGoogleMusicPlaylist(String name) {
        try {
            Cursor playlists = getActivity().getContentResolver().query(
                    Uri.parse(GOOGLE_PLAYLISTS),
                    new String[] {COLUMN_ID},
                    COLUMN_PLAYLIST_NAME + " = ?",
                    new String[] {name},
                    null);
            if (playlists == null) {
                return;
            }

            long playlistId;
            try {
                if (playlists.getCount() != 1) {
                    return;
                }
                playlists.moveToFirst();
                playlistId = playlists.getLong(playlists.getColumnIndexOrThrow(COLUMN_ID));
            } finally {
                playlists.close();
            }

            Cursor members = getActivity().getContentResolver().query(
                    Uri.parse(GOOGLE_PLAYLISTS + "/" + playlistId + "/members"),
                    new String[] {COLUMN_SOURCE_ID},
                    null, null, null);
            if (members == null) {
                return;
            }
            try {
                while (members.moveToNext()) {
                    add(members.getLong(members.getColumnIndexOrThrow(COLUMN_SOURCE_ID)));
                }
            } finally {
                members.close();
            }
        } catch (Exception failure) {
            // The provider is not part of the platform; absence is normal.
            Log.w(TAG, "Google Play Music playlist unavailable", failure);
        }
    }

    /** Every audio file under /storage/emulated/0/music/&lt;name&gt;/. */
    private void readStorageFolder(String name) {
        try {
            Cursor cursor = getActivity().getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[] {COLUMN_ID, COLUMN_DATA},
                    null, null, null);
            if (cursor == null) {
                return;
            }
            try {
                String prefix = (MUSIC_ROOT + name + "/").toLowerCase(java.util.Locale.ROOT);
                while (cursor.moveToNext()) {
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATA));
                    if (path == null) {
                        continue;
                    }
                    if (path.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                        add(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                    }
                }
            } finally {
                cursor.close();
            }
        } catch (Exception failure) {
            Log.w(TAG, "music folder unavailable", failure);
        }
    }

    private void add(long id) {
        playlist.add(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id));
    }

    // --- playback -----------------------------------------------------------

    public void play() {
        if (!isAvailable.get()) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            synchronized (lock) {
                if (currentIndex >= playlist.size()) {
                    Log.w(TAG, "play: playlist is empty");
                    isAvailable.set(false);
                    return;
                }
                try {
                    isPaused = false;
                    if (player == null) {
                        player = new MediaPlayer();
                        player.setAudioAttributes(new AudioAttributes.Builder()
                                .setLegacyStreamType(android.media.AudioManager.STREAM_MUSIC)
                                .build());
                    }
                    Uri uri = playlist.get(currentIndex);
                    player.reset();
                    player.setOnCompletionListener(this);
                    player.setOnPreparedListener(this);
                    player.setDataSource(getActivity(), uri);
                    player.setVolume(volume, volume);
                    player.prepareAsync();
                } catch (IOException unreadable) {
                    // A track that vanished from storage should not stop the playlist.
                    Log.w(TAG, "cannot read " + playlist.get(currentIndex), unreadable);
                    playlist.remove(currentIndex);
                    if (currentIndex >= playlist.size()) {
                        currentIndex = 0;
                    }
                    if (player != null) {
                        player.reset();
                    }
                    isAvailable.set(!playlist.isEmpty());
                    play();
                } catch (Exception failure) {
                    Log.w(TAG, "cannot start playback", failure);
                }
            }
        });
    }

    public void pause() {
        if (!isAvailable.get()) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            synchronized (lock) {
                if (player == null) {
                    return;
                }
                try {
                    player.pause();
                    currentAt = player.getCurrentPosition();
                    isPaused = true;
                } catch (Exception failure) {
                    Log.w(TAG, "pause failed", failure);
                }
            }
        });
    }

    public void stop() {
        if (!isAvailable.get()) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            synchronized (lock) {
                if (player == null) {
                    return;
                }
                try {
                    player.stop();
                    currentAt = 0;
                } catch (Exception failure) {
                    Log.w(TAG, "stop failed", failure);
                }
            }
        });
    }

    public void setVolume(final float volume) {
        Log.i(TAG, "setVolume: " + volume);
        getActivity().runOnUiThread(() -> {
            this.volume = volume;
            if (player != null) {
                player.setVolume(volume, volume);
            }
        });
    }

    // --- MediaPlayer callbacks ----------------------------------------------

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp == null) {
            return;
        }
        mp.seekTo(currentAt);
        mp.start();
        currentAt = 0;
        currentLength = mp.getDuration();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        synchronized (lock) {
            // currentAt is zero here, so a finished track always advances the index.
            currentIndex++;
            if (currentIndex >= playlist.size()) {
                currentIndex = 0;
            }
            if (mp != null) {
                mp.reset();
            }
        }
        if (!isPaused) {
            play();
        }
    }

    // --- GameViewHandler ----------------------------------------------------

    @Override
    public void setup(GameActivityBase activity) {
        if (activity == null) {
            throw new NullPointerException("activity is null");
        }
        this.activity = activity;
    }

    @Override
    public void onPause() {
        wasPlaying = isPlaying();
        pause();
    }

    @Override
    public void onResume() {
        if (wasPlaying) {
            wasPlaying = false;
            play();
        }
    }

    // --- accessors ----------------------------------------------------------

    public int getCount() {
        synchronized (lock) {
            return playlist.size();
        }
    }

    public boolean isPlaying() {
        synchronized (lock) {
            return player != null && player.isPlaying();
        }
    }

    private GameActivityBase getActivity() {
        if (activity == null) {
            throw new IllegalStateException("activity not set, call setup() first");
        }
        return activity;
    }
}
