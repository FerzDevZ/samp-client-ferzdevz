// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.services;

import com.rockstargames.oswrapper.GameView;

/** Forwards the engine's splash screen requests to the view that owns the splash surfaces. */
public final class SplashScreenServices {

    private final GameView view;

    public SplashScreenServices(GameView view) {
        if (view == null) {
            throw new IllegalArgumentException("view is null");
        }
        this.view = view;
    }

    public GameView getView() {
        return view;
    }

    public void hideSplashScreen() {
        view.getSplashScreenHandler().hide();
    }

    public boolean isSplashScreenVisible() {
        return view.getSplashScreenHandler().getVisible();
    }

    public void setSplashImage(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("filename is null");
        }
        view.getSplashScreenHandler().setImage(filename);
    }

    public void setSplashText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        view.getSplashScreenHandler().setText(text);
    }

    public void showSplashScreen() {
        view.getSplashScreenHandler().show();
    }
}
