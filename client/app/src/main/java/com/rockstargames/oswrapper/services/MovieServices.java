// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.services;

import com.rockstargames.oswrapper.GameView;

/**
 * Intro movies are disabled on purpose.
 *
 * The engine plays an intro and waits for it before the menu. On this client the boot must not
 * depend on a video that may be missing from the data build, so every query answers "not
 * playing" and every request is ignored. The handler behind the surfaces is still complete;
 * this class is the switch that keeps it silent.
 */
public final class MovieServices {

    private final GameView view;

    public MovieServices(GameView view) {
        if (view == null) {
            throw new IllegalArgumentException("view is null");
        }
        this.view = view;
    }

    public boolean isMoviePlaying() {
        return false;
    }

    public void playMovie(String filename, boolean skippable) {
        // Disabled: see the class comment.
    }

    public void setMovieText(String text) {
        // Disabled: no movie means no subtitle surface to draw on.
    }

    public void setMovieTextScale(int scale) {
        // Disabled: see the class comment.
    }

    public void stopMovie() {
        // Disabled: nothing was started, so nothing has to be released.
    }
}
