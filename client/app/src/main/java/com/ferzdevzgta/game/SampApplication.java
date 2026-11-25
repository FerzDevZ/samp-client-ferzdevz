package com.ferzdevzgta.game;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.util.Log;

import com.ferzdevzgta.game.log.CrashHandler;
import com.ferzdevzgta.game.ui.LanguageStore;

import java.io.File;

/**
 * Applies the stored screen language before the first activity is created, so a restart does
 * not flash the other locale for one frame, and installs the crash recorder that writes into the
 * same file the native client logs to.
 */
public class SampApplication extends Application {

    /** Where the native client already writes, so a report never needs two files. */
    private static final String LOG_PATH = "/storage/emulated/0/GTA/logcat.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            LanguageStore.apply(this);
        } catch (RuntimeException failure) {
            // A language that cannot be applied must not stop the client from starting.
            Log.w("SampApplication", "language not applied: " + failure.getMessage());
        }
        try {
            CrashHandler.install(new File(LOG_PATH), version());
        } catch (RuntimeException failure) {
            Log.w("SampApplication", "crash handler not installed: " + failure.getMessage());
        }
    }

    /** versionName and versionCode, which is what a report has to quote to be reproducible. */
    private String version() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + code + ")";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
