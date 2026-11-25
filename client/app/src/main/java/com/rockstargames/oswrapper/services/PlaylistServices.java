package com.rockstargames.oswrapper.services;

import android.util.Log;
import com.rockstargames.oswrapper.GameView;

import kotlin.jvm.internal.Intrinsics;

public final class PlaylistServices {
    private static final String TAG = "PlaylistServices";
    private final GameView view;

    public PlaylistServices(GameView view) {
        Intrinsics.checkNotNullParameter(view, "view");
        this.view = view;
    }
    public int playlistCount() {
        return this.view.getPlaylistHandler().getCount();
    }
    public boolean playlistIsPlaying() {
        return this.view.getPlaylistHandler().isPlaying();
    }
    public void playlistOpen(String name) {
        Intrinsics.checkNotNullParameter(name, "name");
        Log.i(TAG, "[!!] playlistOpen: " + name);
        this.view.getPlaylistHandler().open(name);
    }
    public void playlistPause() {
        Log.i(TAG, "[!!] playlistPause");
        this.view.getPlaylistHandler().pause();
    }
    public void playlistPlay() {
        Log.i(TAG, "[!!] playlistPlay");
        this.view.getPlaylistHandler().play();
    }
    public void playlistSetVolume(float volume) {
        Log.i(TAG, "[!!] playlistSetVolume: " + volume);
        this.view.getPlaylistHandler().setVolume(volume);
    }
    public void playlistStop() {
        Log.i(TAG, "[!!] playlistStop");
        this.view.getPlaylistHandler().stop();
    }
}
