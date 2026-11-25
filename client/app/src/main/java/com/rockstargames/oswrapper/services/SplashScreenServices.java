package com.rockstargames.oswrapper.services;

import com.rockstargames.oswrapper.GameView;
import kotlin.jvm.internal.Intrinsics;

public final class SplashScreenServices {
    private final GameView view;

    public SplashScreenServices(GameView view) {
        Intrinsics.checkNotNullParameter(view, "view");
        this.view = view;
    }

    public final GameView getView() {
        return this.view;
    }
    public void hideSplashScreen() {
        this.view.getSplashScreenHandler().hide();
    }
    public boolean isSplashScreenVisible() {
        return this.view.getSplashScreenHandler().getVisible();
    }
    public void setSplashImage(String filename) {
        Intrinsics.checkNotNullParameter(filename, "filename");
        this.view.getSplashScreenHandler().setImage(filename);
    }
    public void setSplashText(String text) {
        Intrinsics.checkNotNullParameter(text, "text");
        this.view.getSplashScreenHandler().setText(text);
    }
    public void showSplashScreen() {
        this.view.getSplashScreenHandler().show();
    }
}
