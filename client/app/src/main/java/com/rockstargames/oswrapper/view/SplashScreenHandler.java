// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.view;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.ferzdevzgta.game.R;
import com.rockstargames.oswrapper.GameActivityBase;

import java.io.InputStream;

/**
 * The loading screen the engine shows before the first frame: an image from the APK assets
 * and a line of text, both replaced from native callbacks. Every mutation posts to the UI
 * thread because the engine calls these from its own thread.
 */
public final class SplashScreenHandler implements GameViewHandler {

    private static final String TAG = "GameView.SplashScreenHandler";

    /** Text is sized against this reference height, then divided by display density. */
    private static final float REFERENCE_HEIGHT = 1024.0f;
    private static final float BASE_TEXT_SIZE = 24.0f;

    private GameActivityBase activity;
    private ImageView image;
    private TextView text;
    private View view;

    @Override
    public void setup(GameActivityBase activity) {
        if (activity == null) {
            throw new NullPointerException("activity is null");
        }
        this.activity = activity;

        view = activity.findViewById(R.id.viewSplash);
        if (view == null) {
            throw new IllegalStateException("viewSplash not found in layout");
        }
        image = view.findViewById(R.id.splashImage);
        if (image == null) {
            throw new IllegalStateException("splashImage not found in layout");
        }
        text = view.findViewById(R.id.splashText);
        if (text == null) {
            throw new IllegalStateException("splashText not found in layout");
        }
    }

    // --- visibility ---------------------------------------------------------

    public void show() {
        Log.i(TAG, "show");
        getActivity().runOnUiThread(() -> {
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
        });
    }

    public void hide() {
        Log.i(TAG, "hide");
        getActivity().runOnUiThread(() -> {
            if (image != null) {
                image.setImageDrawable(null);
            }
            if (text != null) {
                text.setText("");
            }
            if (view != null) {
                view.setVisibility(View.GONE);
            }
        });
    }

    public boolean getVisible() {
        return view != null && view.getVisibility() != View.GONE;
    }

    // --- content ------------------------------------------------------------

    public void setImage(String filename) {
        if (filename == null) {
            throw new NullPointerException("filename is null");
        }
        Log.i(TAG, "setImage: " + filename);
        getActivity().runOnUiThread(() -> {
            try (InputStream stream = getActivity().getAssets().open(filename)) {
                if (image != null) {
                    image.setImageDrawable(Drawable.createFromStream(stream, null));
                }
                if (view != null) {
                    view.setVisibility(View.VISIBLE);
                }
            } catch (Exception failure) {
                Log.w(TAG, "setImage failed for " + filename, failure);
            }
        });
    }

    public void setText(String value) {
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        String trimmed = value.trim();
        Log.i(TAG, "setText: " + trimmed);
        getActivity().runOnUiThread(() -> {
            if (view == null || text == null) {
                return;
            }
            view.setVisibility(View.VISIBLE);
            float scaled = BASE_TEXT_SIZE * (view.getMeasuredHeight() / REFERENCE_HEIGHT);
            float adjusted = scaled / view.getResources().getDisplayMetrics().density;
            text.setText(trimmed);
            text.setTextSize(adjusted);
        });
    }

    // --- GameViewHandler ----------------------------------------------------

    @Override
    public void onPause() {
        // Nothing to release: the splash screen holds no listeners.
    }

    @Override
    public void onResume() {
        // Nothing to restore.
    }

    private GameActivityBase getActivity() {
        if (activity == null) {
            throw new IllegalStateException("activity not set, call setup() first");
        }
        return activity;
    }
}
