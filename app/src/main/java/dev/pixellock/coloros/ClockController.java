package dev.pixellock.coloros;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hides the ColorOS lock screen clock (and the date above it) and draws a {@link PixelClockView} in the window
 * overlay at the same spot. Every frame it copies the original clock's visibility, alpha, scale and position,
 * so ColorOS's own animations (unlock, bouncer, notification shade, AOD hand-off) still apply.
 */
final class ClockController {
    static final Pattern TIME = Pattern.compile("(\\d{1,2})\\s*[:：∶.]\\s*(\\d{2})");
    private static final Pattern TWO_NUMBERS = Pattern.compile("(\\d{1,2})\\D+(\\d{2})");

    private final HookEntry m;
    private final ViewGroup root;
    final PixelClockView view;
    private View clock;
    private TextView dateView;
    private boolean dateAbove;
    private boolean hideDate;
    private int offsetPx;
    private boolean added;

    // Restore state for the space reservation.
    private int originalHeight = -1;
    private int savedMinHeight = -1;
    private int savedLpHeight = Integer.MIN_VALUE;
    private int appliedTarget = -1;

    private final int[] loc = new int[2];
    private final int[] rootLoc = new int[2];
    private String lastLog = "";

    ClockController(HookEntry m, ViewGroup root) {
        this.m = m;
        this.root = root;
        view = new PixelClockView(root.getContext());
        view.setTimeSource(this::readTime);
    }

    View clock() { return clock; }
    TextView dateView() { return dateView; }

    /** (Re)binds to the original clock and restyles. Called from the layout-pass scan. */
    void bind(View newClock, TextView newDate, Config cfg, Palette palette) {
        if (newClock != clock) {
            restoreOriginal();
            clock = newClock;
            originalHeight = newClock.getHeight();
        } else if (originalHeight <= 0 && appliedTarget < 0) {
            originalHeight = clock.getHeight();
        }
        if (newDate != dateView) {
            if (dateView != null) dateView.setTransitionAlpha(1f);
            dateView = newDate;
        }
        hideDate = cfg.clockDate;
        if (!hideDate && dateView != null) dateView.setTransitionAlpha(1f);
        dateAbove = false;
        if (dateView != null) {
            dateAbove = Views.screen(dateView).centerY() < Views.screen(clock).centerY();
        }
        offsetPx = Math.round(Views.dp(root, cfg.clockOffsetDp));

        Context c = root.getContext();
        int screenW = Math.min(c.getResources().getDisplayMetrics().widthPixels,
                c.getResources().getDisplayMetrics().heightPixels);
        float textSize = screenW * 0.40f * cfg.clockScale / 100f;
        float dateSize = dateView != null && dateView.getTextSize() > 0
                ? dateView.getTextSize() : Views.dp(root, 17) * c.getResources().getConfiguration().fontScale;
        view.style(Fonts.clock(m, c, cfg.clockRound), textSize, Fonts.date(m, c), dateSize, palette, hideDate);

        if (!added) {
            root.getOverlay().add(view);
            added = true;
        }
        if (cfg.clockReserve) reserve(); else unreserve();

        String log = "Clock bound: " + Views.describe(clock) + " text='" + safeText(clock) + "'"
                + (dateView != null ? " | date: " + Views.describe(dateView) + (dateAbove ? " (above)" : " (below)") : " | date: none")
                + " | pixel clock textSize=" + Math.round(textSize) + "px block=" + view.blockHeight() + "px"
                + " colors " + Palette.hex(palette.clockLight) + "/" + Palette.hex(palette.clockDark);
        if (!log.equals(lastLog)) {
            lastLog = log;
            m.info(log);
        }
        sync();
    }

    /** Makes the original clock taller so whatever ColorOS lays out below it moves down with the bigger clock. */
    private void reserve() {
        if (clock == null || originalHeight <= 0) return;
        int clockTop = Views.screen(clock).top;
        int anchor = clockTop;
        if (hideDate && dateAbove && dateView != null) anchor = Math.min(anchor, Views.screen(dateView).top);
        int need = view.blockHeight() + offsetPx - (clockTop + originalHeight - anchor);
        int target = originalHeight + Math.max(0, need);
        if (target == appliedTarget) return;
        if (savedMinHeight < 0) savedMinHeight = clock.getMinimumHeight();
        ViewGroup.LayoutParams lp = clock.getLayoutParams();
        if (lp != null && lp.height >= 0) {
            if (savedLpHeight == Integer.MIN_VALUE) savedLpHeight = lp.height;
            lp.height = target;
            clock.setLayoutParams(lp);
        }
        if (clock instanceof TextView) ((TextView) clock).setMinHeight(target); else clock.setMinimumHeight(target);
        appliedTarget = target;
        m.info("Reserved clock height " + originalHeight + "px -> " + target + "px");
    }

    private void unreserve() {
        if (clock == null || appliedTarget < 0) return;
        ViewGroup.LayoutParams lp = clock.getLayoutParams();
        if (lp != null && savedLpHeight != Integer.MIN_VALUE) {
            lp.height = savedLpHeight;
            clock.setLayoutParams(lp);
        }
        int min = Math.max(0, savedMinHeight);
        if (clock instanceof TextView) ((TextView) clock).setMinHeight(min); else clock.setMinimumHeight(min);
        savedLpHeight = Integer.MIN_VALUE;
        savedMinHeight = -1;
        appliedTarget = -1;
    }

    private void restoreOriginal() {
        if (clock != null) {
            unreserve();
            clock.setTransitionAlpha(1f);
        }
    }

    /** Puts everything back and removes the Pixel clock. */
    void release() {
        restoreOriginal();
        if (dateView != null) dateView.setTransitionAlpha(1f);
        if (added) {
            root.getOverlay().remove(view);
            added = false;
        }
        clock = null;
        dateView = null;
    }

    /** Runs before every frame of the window. Cheap: a parent walk and a few comparisons. */
    void sync() {
        if (!added) return;
        if (clock == null || !clock.isAttachedToWindow()) {
            hide();
            return;
        }
        if (clock.getTransitionAlpha() != 0f) clock.setTransitionAlpha(0f);
        if (hideDate && dateView != null && dateView.getTransitionAlpha() != 0f) dateView.setTransitionAlpha(0f);

        float alpha = clock.getAlpha();
        float sx = clock.getScaleX(), sy = clock.getScaleY();
        boolean visible = clock.getVisibility() == View.VISIBLE;
        ViewParent p = clock.getParent();
        while (visible && p instanceof View && p != root) {
            View v = (View) p;
            if (v.getVisibility() != View.VISIBLE) visible = false;
            alpha *= v.getAlpha() * v.getTransitionAlpha();
            sx *= v.getScaleX();
            sy *= v.getScaleY();
            p = v.getParent();
        }
        if (p != root) visible = false;
        alpha *= root.getAlpha();
        if (!visible || alpha <= 0.001f) {
            hide();
            return;
        }

        readTime();
        readDate();

        clock.getLocationInWindow(loc);
        root.getLocationInWindow(rootLoc);
        float clockTop = loc[1] - rootLoc[1];
        float centerX = loc[0] - rootLoc[0] + clock.getWidth() * sx / 2f;
        float anchor = clockTop;
        if (hideDate && dateAbove && dateView != null && dateView.isAttachedToWindow()) {
            dateView.getLocationInWindow(loc);
            anchor = Math.min(anchor, loc[1] - rootLoc[1]);
        }
        int w = root.getWidth();
        int h = view.blockHeight();
        int top = Math.round(anchor + offsetPx);
        int left = Math.round(centerX - w / 2f);
        if (view.getLeft() != left || view.getTop() != top || view.getWidth() != w || view.getHeight() != h) {
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            view.layout(left, top, left + w, top + h);
        }
        view.setPivotX(w / 2f);
        view.setPivotY(0);
        if (view.getScaleX() != sx) view.setScaleX(sx);
        if (view.getScaleY() != sy) view.setScaleY(sy);
        if (view.getAlpha() != alpha) view.setAlpha(alpha);
        if (view.getVisibility() != View.VISIBLE) view.setVisibility(View.VISIBLE);
    }

    private void hide() {
        if (view.getVisibility() != View.INVISIBLE) view.setVisibility(View.INVISIBLE);
    }

    /** Time comes from the original clock's own text when possible, so 12/24h and time zone match ColorOS. */
    private void readTime() {
        String h = null, min = null;
        if (clock != null) {
            String t = clockText(clock);
            Matcher mt = TIME.matcher(t);
            if (!mt.find()) mt = TWO_NUMBERS.matcher(t);
            else mt.reset();
            if (mt.find()) {
                h = mt.group(1);
                min = mt.group(2);
            }
        }
        boolean is24 = DateFormat.is24HourFormat(root.getContext());
        if (h == null) {
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            if (!is24) { hour = hour % 12; if (hour == 0) hour = 12; }
            h = is24 ? String.format(Locale.ROOT, "%02d", hour) : String.valueOf(hour);
            min = String.format(Locale.ROOT, "%02d", now.get(Calendar.MINUTE));
        } else if (is24 && h.length() == 1) {
            h = "0" + h;
        }
        view.setTime(h, min);
    }

    private void readDate() {
        String d = dateView != null ? Views.text(dateView).trim().replace('\n', ' ') : "";
        if (d.isEmpty()) d = DateText.format(root.getContext());
        view.setDate(d);
    }

    static String clockText(View clock) {
        if (clock instanceof TextView) return Views.text(clock);
        StringBuilder sb = new StringBuilder();
        for (TextView t : Views.texts(clock)) {
            if (t.getVisibility() == View.VISIBLE) sb.append(Views.text(t)).append(' ');
        }
        return sb.toString();
    }

    private static String safeText(View v) {
        String t = clockText(v).trim();
        return t.length() > 16 ? t.substring(0, 16) + "…" : t;
    }
}
