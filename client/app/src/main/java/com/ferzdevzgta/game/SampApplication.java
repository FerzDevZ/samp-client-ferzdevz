package com.ferzdevzgta.game;

import android.app.Application;
import android.util.Log;

import com.ferzdevzgta.game.ui.LanguageStore;

/**
 * Applies the stored screen language before the first activity is created, so a restart does
 * not flash the other locale for one frame. Everything else keeps the default Application
 * behaviour.
 */
public class SampApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            LanguageStore.apply(this);
        } catch (RuntimeException failure) {
            // A language that cannot be applied must not stop the client from starting.
            Log.w("SampApplication", "language not applied: " + failure.getMessage());
        }
    }
}
