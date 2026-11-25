package com.ferzdevzgta.game.ui;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.ferzdevzgta.game.R;

/**
 * The screen between tapping Play and the first playable frame.
 *
 * <p>Three rules it keeps, because a loading screen is a promise:
 *
 * <ol>
 *   <li>It never invents a percentage. The engine reports no byte count, so the bar is a marquee
 *       and the status line names the phase instead. A bar that reaches 100 percent before the
 *       game is playable is a lie the player can see.</li>
 *   <li>It is not dismissed by a timer. Only {@link #hide()} ends it, which the engine calls when
 *       the first frame is up. The old countdown hid the screen after 4.5 seconds whether or not
 *       the world had loaded.</li>
 *   <li>If the wait runs long, it says so and points at {@code GTA/logcat.txt} instead of
 *       spinning forever with no explanation.</li>
 * </ol>
 */
public final class LoadingScreen {

    private static final String TAG = "LoadingScreen";

    /** After this long the hint changes from a tip to an explanation. */
    private static final long SLOW_AFTER_MS = 20_000L;

    /** How long a tip stays on screen. Two lines are readable in this window. */
    private static final long TIP_INTERVAL_MS = 7_000L;

    private static final int[] TIPS = {R.string.loading_tip_1, R.string.loading_tip_2, R.string.loading_tip_3};

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ConstraintLayout root;
    private final TextView status;
    private final TextView hint;
    private final TextView version;

    private int tipIndex;
    private boolean visible;
    private boolean slow;

    public LoadingScreen(Activity activity) {
        this.activity = activity;
        this.root = (ConstraintLayout) activity.getLayoutInflater()
                .inflate(R.layout.loadingscreen, null);
        activity.addContentView(root, new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT));

        this.status = root.findViewById(R.id.loadingStatus);
        this.hint = root.findViewById(R.id.loadingHint);
        this.version = root.findViewById(R.id.loadingVersion);

        setVersionText();
        padForCutout();
        show();
    }

    /** versionName from the installed package, so the screen agrees with the diagnostics report. */
    private void setVersionText() {
        if (version == null) {
            return;
        }
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            version.setText(info.versionName + "  " + activity.getString(R.string.loading_version_unknown));
        } catch (Exception e) {
            Log.w(TAG, "version unavailable: " + e.getMessage());
        }
    }

    /**
     * Keeps the text out of a display cutout. The game runs landscape, where a camera hole sits on
     * one short edge, and the identity block would otherwise be partially hidden by it.
     */
    private void padForCutout() {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    view.setPadding(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                            cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
                }
            }
            return insets;
        });
    }

    // --- content ------------------------------------------------------------

    /** Names the phase in progress. Called by the client as it moves through startup. */
    public void setStatus(int stringRes) {
        if (status == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                status.setText(stringRes);
            } catch (Exception e) {
                Log.w(TAG, "status not set: " + e.getMessage());
            }
        });
    }

    // --- lifecycle ----------------------------------------------------------

    public void show() {
        if (root == null) {
            return;
        }
        visible = true;
        slow = false;
        root.setVisibility(View.VISIBLE);
        root.setAlpha(1f);
        if (hint != null && !Ui.motionEnabled(activity)) {
            // With animations off, a rotating line is motion for its own sake. The static
            // explanation is more useful than a changing tip.
            hint.setText(R.string.loading_tip_1);
        } else {
            rotateTip();
        }
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            slow = true;
            if (hint != null) {
                hint.setText(R.string.loading_slow);
            }
        }, SLOW_AFTER_MS);
    }

    /** Fades out, then removes itself. Idempotent: the engine may call it twice. */
    public void hide() {
        if (root == null || !visible) {
            return;
        }
        visible = false;
        handler.removeCallbacksAndMessages(null);
        if (!Ui.motionEnabled(activity)) {
            root.setVisibility(View.GONE);
            return;
        }
        root.animate()
                .alpha(0f)
                .setDuration(220L)
                .withEndAction(() -> {
                    root.setVisibility(View.GONE);
                    root.setAlpha(1f);
                })
                .start();
    }

    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        if (root != null) {
            root.setVisibility(View.GONE);
        }
    }

    private void rotateTip() {
        if (hint == null || slow) {
            return;
        }
        hint.setText(TIPS[tipIndex % TIPS.length]);
        tipIndex++;
        handler.postDelayed(this::rotateTip, TIP_INTERVAL_MS);
    }
}
