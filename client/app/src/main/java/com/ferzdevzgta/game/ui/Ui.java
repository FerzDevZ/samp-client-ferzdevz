package com.ferzdevzgta.game.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.ferzdevzgta.game.R;

/**
 * One design system for the setup screens: tokens, type scale, spacing scale and the small set
 * of components those screens need. Screens compose these instead of inventing colours and
 * paddings, so a change here moves every screen at once.
 *
 * <p>Contrast of the palette against the surfaces it is used on is asserted by
 * {@code bin/ui-contrast.py}; changing a colour without re-running it will fail the gate.
 * Every control is at least 48dp tall and keeps its label as text, so TalkBack and large font
 * scales work without extra code.
 */
public final class Ui {

    // The palette is taken from the app icon: deep navy field, sunset amber, warm off-white.
    // Surfaces
    public static final int BG = 0xFF0B111B;
    public static final int SURFACE = 0xFF151F32;
    public static final int SURFACE_2 = 0xFF1F2C45;
    public static final int STROKE = 0xFF3A4A66;

    // Content
    public static final int TEXT = 0xFFF3EEE6;
    public static final int MUTED = 0xFFB7C0CE;
    public static final int FAINT = 0xFF8C97A8;

    // Roles
    public static final int ACCENT = 0xFFEF7D2B;
    public static final int ACCENT_INK = 0xFF1B1206;
    public static final int OK = 0xFF43BF8F;
    public static final int WARN = 0xFFF2C14E;
    public static final int ERR = 0xFFFF8A7A;

    // Button kinds
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int GHOST = 2;
    public static final int DANGER = 3;

    private static final float RADIUS = 14f;
    private static final float RADIUS_SMALL = 10f;

    private Ui() {
    }

    public interface OnPick {
        void onPick(int index);
    }

    public static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable round(Context c, float radiusDp, int fill, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(c, radiusDp));
        bg.setColor(fill);
        if (stroke != 0) {
            bg.setStroke(dp(c, 1), stroke);
        }
        return bg;
    }

    private static void ripple(View view, GradientDrawable content) {
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), content, null);
        view.setBackground(ripple);
        view.setElevation(0);
        if (view instanceof Button) {
            ((Button) view).setStateListAnimator(null);
        }
    }

    // ---------------------------------------------------------------- layout

    public static LinearLayout column(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        return root;
    }

    /** Scroll container with the screen gutter already applied. */
    public static ScrollView scroll(Context c, View content) {
        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        int pad = dp(c, 20);
        scroll.setPadding(pad, dp(c, 8), pad, dp(c, 32));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    public static LinearLayout card(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(c, RADIUS, SURFACE, STROKE));
        int pad = dp(c, 16);
        box.setPadding(pad, pad, pad, pad);
        return box;
    }

    public static LinearLayout row(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static void gap(Context c, LinearLayout parent, float heightDp) {
        View spacer = new View(c);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        parent.addView(spacer);
    }

    public static View divider(Context c) {
        View line = new View(c);
        line.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        params.topMargin = dp(c, 12);
        params.bottomMargin = dp(c, 12);
        line.setLayoutParams(params);
        return line;
    }

    public static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams fill(int topMarginDp, Context c) {
        LinearLayout.LayoutParams params = fill();
        params.topMargin = dp(c, topMarginDp);
        return params;
    }

    public static LinearLayout.LayoutParams weight(Context c, float weight, float endMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMarginEnd(dp(c, endMarginDp));
        return params;
    }

    // ---------------------------------------------------------------- text

    private static TextView text(Context c, String value, float sizeSp, int colour, int style) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextColor(colour);
        v.setTextSize(sizeSp);
        if (style != Typeface.NORMAL) {
            v.setTypeface(Typeface.create("sans-serif", style));
        }
        v.setLineSpacing(dp(c, 3), 1f);
        return v;
    }

    public static TextView eyebrow(Context c, String value) {
        TextView v = text(c, value.toUpperCase(), 11f, MUTED, Typeface.BOLD);
        v.setLetterSpacing(0.14f);
        return v;
    }

    public static TextView title(Context c, String value) {
        TextView v = text(c, value, 26f, TEXT, Typeface.BOLD);
        v.setLetterSpacing(-0.01f);
        return v;
    }

    public static TextView subtitle(Context c, String value) {
        return text(c, value, 14f, MUTED, Typeface.NORMAL);
    }

    public static TextView section(Context c, String value) {
        TextView v = eyebrow(c, value);
        v.setPadding(0, 0, 0, dp(c, 10));
        return v;
    }

    public static TextView body(Context c, String value) {
        return text(c, value, 14f, MUTED, Typeface.NORMAL);
    }

    public static TextView label(Context c, String value) {
        return text(c, value, 13f, MUTED, Typeface.NORMAL);
    }

    public static TextView value(Context c, String value) {
        TextView v = text(c, value, 14f, TEXT, Typeface.BOLD);
        v.setGravity(Gravity.END);
        return v;
    }

    public static TextView mono(Context c, String value) {
        TextView v = text(c, value, 12.5f, MUTED, Typeface.NORMAL);
        v.setTypeface(Typeface.MONOSPACE);
        v.setTextIsSelectable(true);
        return v;
    }

    /** Small state badge. Tone is OK, WARN, ERR or MUTED. */
    public static TextView pill(Context c, String value, int tone) {
        TextView v = text(c, value, 12f, tone, Typeface.BOLD);
        v.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        GradientDrawable bg = round(c, 999f, blend(BG, tone, 0.16f), blend(SURFACE, tone, 0.45f));
        v.setBackground(bg);
        return v;
    }

    /** Mixes {@code colour} into {@code base}, amount in 0..1. Keeps badges on palette. */
    static int blend(int base, int colour, float amount) {
        float f = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(((base >> 16) & 0xFF) * (1 - f) + ((colour >> 16) & 0xFF) * f);
        int g = Math.round(((base >> 8) & 0xFF) * (1 - f) + ((colour >> 8) & 0xFF) * f);
        int b = Math.round((base & 0xFF) * (1 - f) + (colour & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- controls

    public static Button button(Context c, String value, int kind) {
        Button b = new Button(c);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(15f);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 50));
        b.setMinimumHeight(dp(c, 50));
        b.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        b.setFocusable(true);
        b.setDefaultFocusHighlightEnabled(true);

        int fill;
        int stroke;
        int ink;
        switch (kind) {
            case PRIMARY:
                fill = ACCENT;
                stroke = ACCENT;
                ink = ACCENT_INK;
                break;
            case DANGER:
                fill = blend(SURFACE, ERR, 0.18f);
                stroke = blend(SURFACE, ERR, 0.55f);
                ink = ERR;
                break;
            case GHOST:
                fill = 0x00000000;
                stroke = 0;
                ink = ACCENT;
                break;
            default:
                fill = SURFACE_2;
                stroke = STROKE;
                ink = TEXT;
                break;
        }
        b.setTextColor(ink);
        ripple(b, round(c, RADIUS_SMALL, fill, stroke));
        pressable(b);
        return b;
    }

    /**
     * Same button, with an icon before the label. The icon is tinted with the label colour, so a
     * secondary button never ends up with a white glyph on a light surface.
     */
    public static Button button(Context c, String value, int kind, int iconRes) {
        Button b = button(c, value, kind);
        Drawable glyph = icon(c, iconRes, b.getCurrentTextColor());
        if (glyph != null) {
            int size = dp(c, 18);
            glyph.setBounds(0, 0, size, size);
            b.setCompoundDrawables(glyph, null, null, null);
            b.setCompoundDrawablePadding(dp(c, 8));
        }
        return b;
    }

    /** A drawable from resources, tinted. Returns null instead of throwing on a bad id. */
    public static Drawable icon(Context c, int resId, int tint) {
        Drawable raw = ContextCompat.getDrawable(c, resId);
        if (raw == null) {
            return null;
        }
        Drawable tinted = DrawableCompat.wrap(raw.mutate());
        DrawableCompat.setTint(tinted, tint);
        return tinted;
    }

    /**
     * True when the system animation scale is above zero. Transitions are decoration: with the
     * scale at zero nothing here waits on an animation to finish.
     */
    public static boolean motionEnabled(Context c) {
        try {
            return Settings.Global.getFloat(c.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Fades a view in from slightly below, so a screen assembles instead of appearing at once.
     * The stagger is capped at eight items so a long list does not animate for a second.
     */
    public static void fadeIn(View view, int index) {
        if (view == null) {
            return;
        }
        if (!motionEnabled(view.getContext())) {
            view.setAlpha(1f);
            return;
        }
        float lift = dp(view.getContext(), 6);
        view.setAlpha(0f);
        view.setTranslationY(lift);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(Math.min(index, 8) * 24L)
                .setDuration(160L)
                .start();
    }

    /**
     * Two or more mutually exclusive choices rendered as one control. The selected item is
     * filled with the accent colour, the others stay on the surface, and the row reports the
     * new index so the caller can persist it.
     */
    public static LinearLayout segmented(Context c, String[] labels, int selected, OnPick onPick) {
        LinearLayout row = row(c);
        row.setBackground(round(c, RADIUS_SMALL + 2, SURFACE_2, STROKE));
        row.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        final Button[] buttons = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) {
            Button item = new Button(c);
            item.setText(labels[i]);
            item.setAllCaps(false);
            item.setTextSize(14f);
            item.setGravity(Gravity.CENTER);
            item.setMinHeight(dp(c, 44));
            item.setMinimumHeight(dp(c, 44));
            item.setStateListAnimator(null);
            item.setElevation(0);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginEnd(i == labels.length - 1 ? 0 : dp(c, 4));
            item.setLayoutParams(params);
            buttons[i] = item;
            paintSegment(c, item, i == selected);
            final int index = i;
            item.setOnClickListener(v -> {
                for (int k = 0; k < buttons.length; k++) {
                    paintSegment(c, buttons[k], k == index);
                }
                if (onPick != null) {
                    onPick.onPick(index);
                }
            });
            row.addView(item);
        }
        return row;
    }

    private static void paintSegment(Context c, Button item, boolean active) {
        item.setActivated(active);
        item.setTextColor(active ? ACCENT_INK : MUTED);
        GradientDrawable bg = round(c, RADIUS_SMALL - 3, active ? ACCENT : 0x00000000, 0);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), bg, null);
        item.setBackground(ripple);
    }

    /** Language picker wired to the stored choice. */
    public static LinearLayout languageSwitch(Context c) {
        String[] labels = {
                c.getString(R.string.language_in),
                c.getString(R.string.language_en)
        };
        int selected = Language.indexOf(LanguageStore.current(c));
        return segmented(c, labels, selected, index -> {
            LanguageStore.choose(c, Language.TAGS[index]);
            if (c instanceof android.app.Activity) {
                ((android.app.Activity) c).recreate();
            }
        });
    }

    public static EditText input(Context c, String hint, int inputType) {
        EditText field = new EditText(c);
        field.setHint(hint);
        field.setTextSize(15f);
        field.setTextColor(TEXT);
        field.setHintTextColor(FAINT);
        field.setInputType(inputType == 0 ? InputType.TYPE_CLASS_TEXT : inputType);
        field.setMinHeight(dp(c, 50));
        field.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        field.setBackground(round(c, RADIUS_SMALL, SURFACE_2, STROKE));
        field.setSelectAllOnFocus(false);
        return field;
    }

    public static CheckBox toggle(Context c, String label) {
        CheckBox box = new CheckBox(c);
        box.setText(label);
        box.setTextColor(TEXT);
        box.setTextSize(14f);
        box.setMinHeight(dp(c, 48));
        box.setPadding(0, dp(c, 8), 0, dp(c, 8));
        box.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{ACCENT, FAINT}));
        return box;
    }

    public static ProgressBar progress(Context c) {
        ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(STROKE));
        bar.setMinimumHeight(dp(c, 6));
        bar.setLayoutParams(fill(dp(c, 12), c));
        return bar;
    }

    /**
     * Touch feedback that does not depend on a ripple alone: a short haptic tick plus a two percent
     * press scale. Both are switched off when the system animation scale is zero, because that
     * setting means the user asked for less movement, not for none of the confirmation.
     */
    public static void pressable(View view) {
        if (view == null) {
            return;
        }
        view.setClickable(true);
        final boolean motion = motionEnabled(view.getContext());
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (motion) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    }
                    v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(90L).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
                    break;
                default:
                    break;
            }
            return false;
        });
        view.setOnLongClickListener(v -> {
            if (motion) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
            return false;
        });
    }

    /**
     * A card that explains why a list is empty and offers the one action that fills it. Empty
     * states get the same care as content: a bare sentence in the middle of a card reads like a
     * failure, a labelled action reads like a first step.
     */
    public static LinearLayout emptyState(Context c, int iconRes, String titleText,
                                          String bodyText, String actionText,
                                          View.OnClickListener action) {
        LinearLayout card = card(c);
        Drawable glyph = iconRes == 0 ? null : icon(c, iconRes, FAINT);
        if (glyph != null) {
            int size = dp(c, 22);
            glyph.setBounds(0, 0, size, size);
            android.widget.ImageView mark = new android.widget.ImageView(c);
            mark.setImageDrawable(glyph);
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(mark);
            gap(c, card, 10);
        }
        TextView head = value(c, titleText);
        head.setTextSize(15f);
        head.setTextColor(TEXT);
        card.addView(head);
        if (bodyText != null && !bodyText.isEmpty()) {
            gap(c, card, 4);
            card.addView(body(c, bodyText));
        }
        if (actionText != null && action != null) {
            gap(c, card, 12);
            Button go = button(c, actionText, SECONDARY);
            go.setOnClickListener(action);
            pressable(go);
            card.addView(go, fill());
        }
        fadeIn(card, 0);
        return card;
    }

    /** Label on the left, value on the right, one line, used by the status cards. */
    public static LinearLayout statusRow(Context c, String labelText, String valueText, int toneOrZero) {
        LinearLayout row = row(c);
        row.setPadding(0, dp(c, 7), 0, dp(c, 7));
        row.setMinimumHeight(dp(c, 48));
        if (toneOrZero != 0) {
            View dot = new View(c);
            android.graphics.drawable.GradientDrawable shape =
                    new android.graphics.drawable.GradientDrawable();
            shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            shape.setColor(toneOrZero);
            dot.setBackground(shape);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(c, 8), dp(c, 8));
            dotParams.setMargins(0, 0, dp(c, 10), 0);
            row.addView(dot, dotParams);
        }
        TextView key = label(c, labelText);
        row.addView(key, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = value(c, valueText);
        if (toneOrZero != 0) {
            val.setTextColor(toneOrZero);
        }
        row.addView(val);
        return row;
    }

    /**
     * Opens a link outside the app. Returns false when nothing can handle it, so the caller can
     * tell the player instead of leaving a tap with no visible result.
     */
    public static boolean openUrl(Context c, String url) {
        if (c == null || url == null || url.isEmpty()) {
            return false;
        }
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(intent);
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            android.util.Log.w("Ui", "link could not be opened: " + e.getMessage());
            return false;
        }
    }

    public static void setText(TextView view, String value) {
        if (view != null) {
            view.setText(value);
        }
    }

    /**
     * Keeps content out of the system bars. Android 15 forces every app that targets SDK 35 or
     * newer to draw edge to edge, so without this the first line of a screen sits under the
     * clock and the last button under the gesture bar.
     */
    public static void applyInsets(android.app.Activity activity, View root) {
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(baseLeft + bars.left, baseTop + bars.top,
                    baseRight + bars.right, baseBottom + bars.bottom);
            return insets;
        });
        android.view.View decor = activity.getWindow().getDecorView();
        androidx.core.view.ViewCompat.requestApplyInsets(decor);
    }

    /** The value view of a status row, whatever else the row grows in front of it. */
    public static TextView statusValue(LinearLayout row) {
        for (int i = row.getChildCount() - 1; i >= 0; i--) {
            View child = row.getChildAt(i);
            if (child instanceof TextView) {
                return (TextView) child;
            }
        }
        return null;
    }

    /**
     * Paints the window before the first frame so a screen never flashes the light app theme
     * and the system bars match the surface.
     */
    public static void paintWindow(android.app.Activity activity) {
        android.view.Window window = activity.getWindow();
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        android.view.View decor = window.getDecorView();
        decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
}
