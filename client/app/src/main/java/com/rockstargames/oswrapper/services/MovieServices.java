package com.rockstargames.oswrapper.services;

import com.rockstargames.oswrapper.GameView;

public final class MovieServices {
    private final GameView view;

    public MovieServices(GameView view) {
        this.view = view;
    }
    public boolean isMoviePlaying() {
        // هەمیشە دەڵێین ڤیدیۆ لێنادات بۆ ئەوەی یارییەکە نەوەستێت و چاوەڕێ نەکات
        return false;
    }
    public void playMovie(String filename, boolean skippable) {
        // هیچ مەکە - ڤیدیۆ لێ مەدە
    }
    public void setMovieText(String text) {
        // هیچ مەکە
    }
    public void setMovieTextScale(int scale) {
        // هیچ مەکە
    }
    public void stopMovie() {
        // هیچ مەکە
    }
}