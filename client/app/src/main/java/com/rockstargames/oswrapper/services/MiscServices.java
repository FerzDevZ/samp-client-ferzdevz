// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.services;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.rockstargames.oswrapper.GameActivityBase;

/** Small things the engine asks the app for: its version, opening a link, quitting. */
public final class MiscServices {

    private static final String TAG = "MiscServices";
    private static final String UNKNOWN_VERSION = "unknown";

    private final GameActivityBase activity;

    public MiscServices(GameActivityBase activity) {
        if (activity == null) {
            throw new IllegalArgumentException("activity is null");
        }
        this.activity = activity;
    }

    public GameActivityBase getActivity() {
        return activity;
    }

    public String getAppVersion() {
        String versionName;
        try {
            PackageManager manager = activity.getPackageManager();
            versionName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? manager.getPackageInfo(
                            activity.getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName
                    : manager.getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException missing) {
            Log.w(TAG, "cannot read own package info: " + missing.getMessage());
            versionName = null;
        }
        return versionName == null ? UNKNOWN_VERSION : versionName;
    }

    public void openLink(final String url) {
        if (url == null) {
            return;
        }
        Log.i(TAG, "openLink: " + url);
        final Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception invalid) {
            Log.e(TAG, "openLink: not a usable URI: " + url, invalid);
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception noHandler) {
                // No browser installed is a normal device state, not a crash.
                Log.w(TAG, "openLink: nothing can handle " + uri, noHandler);
            }
        });
    }

    public void quit() {
        Log.i(TAG, "quit");
        activity.finishAffinity();
    }
}
