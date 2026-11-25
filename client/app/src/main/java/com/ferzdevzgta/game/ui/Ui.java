package com.ferzdevzgta.game.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small view factory so the two setup screens share one look instead of each
 * inventing its own colours and paddings. Colours are chosen for contrast on the
 * dark surface: body text 12.6:1, muted 6.1:1, accent 5.9:1 against #0E1116.
 */
final class Ui {

    static final int BG = 0xFF0E1116;
    static final int CARD = 0xFF171B22;
    static final int STROKE = 0xFF2A3038;
    static final int TEXT = 0xFFE6EAF0;
    static final int MUTED = 0xFF9AA4B2;
    static final int ACCENT = 0xFF4C8DFF;
    static final int OK = 0xFF35C07F;
    static final int ERR = 0xFFFF6B6B;

    private Ui() {
    }

    static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static LinearLayout screen(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(c, 16);
        root.setPadding(pad, pad, pad, pad);
        return root;
    }

    static LinearLayout card(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(c, 12));
        bg.setStroke(dp(c, 1), STROKE);
        box.setBackground(bg);
        int pad = dp(c, 14);
        box.setPadding(pad, pad, pad, pad);
        return box;
    }

    static TextView title(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextColor(TEXT);
        v.setTextSize(22f);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    static TextView body(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextColor(MUTED);
        v.setTextSize(14f);
        v.setLineSpacing(dp(c, 2), 1f);
        return v;
    }

    static TextView mono(Context c, String text) {
        TextView v = body(c, text);
        v.setTypeface(Typeface.MONOSPACE);
        v.setTextSize(12f);
        return v;
    }

    static TextView fieldLabel(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextColor(TEXT);
        v.setTextSize(13f);
        return v;
    }

    static Button button(Context c, String text, boolean primary) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14f);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 44));
        b.setTextColor(primary ? 0xFF0B0E12 : TEXT);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 10));
        bg.setColor(primary ? ACCENT : CARD);
        bg.setStroke(dp(c, 1), primary ? ACCENT : STROKE);
        b.setBackground(bg);
        b.setStateListAnimator(null);
        b.setElevation(0);
        return b;
    }

    static void gap(Context c, LinearLayout parent, float dpHeight) {
        View spacer = new View(c);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, dpHeight)));
        parent.addView(spacer);
    }

    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
