// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.services;

import android.util.Log;

import com.rockstargames.oswrapper.GameView;

/** Menu music: the engine asks for a playlist by name and then controls it. */
public final class PlaylistServices {

    private static final String TAG = "PlaylistServices";

    private final GameView view;

    public PlaylistServices(GameView view) {
        if (view == null) {
            throw new IllegalArgumentException("view is null");
        }
        this.view = view;
    }

    public int playlistCount() {
        return view.getPlaylistHandler().getCount();
    }

    public boolean playlistIsPlaying() {
        return view.getPlaylistHandler().isPlaying();
    }

    public void playlistOpen(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        Log.i(TAG, "playlistOpen: " + name);
        view.getPlaylistHandler().open(name);
    }

    public void playlistPause() {
        view.getPlaylistHandler().pause();
    }

    public void playlistPlay() {
        view.getPlaylistHandler().play();
    }

    public void playlistSetVolume(float volume) {
        view.getPlaylistHandler().setVolume(volume);
    }

    public void playlistStop() {
        view.getPlaylistHandler().stop();
    }
}
