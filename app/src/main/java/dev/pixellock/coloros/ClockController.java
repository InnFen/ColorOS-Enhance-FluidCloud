package dev.pixellock.coloros;

import android.content.Context;
import android.graphics.Rect;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
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
    private final boolean aod;
    private boolean avoid = true;
    private float boundTextSize;
    private List<View> obstacles = Collections.emptyList();
    private final int[] rootScreen = new int[2];
    private String lastMode = "";
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

    ClockController(HookEntry m, ViewGroup root, boolean aod) {
        this.m = m;
        this.root = root;
        this.aod = aod;
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
            boundTextSize = newClock instanceof TextView ? ((TextView) newClock).getTextSize() : 0;
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
        avoid = cfg.clockAvoid;

        Context c = root.getContext();
        int screenW = Math.min(c.getResources().getDisplayMetrics().widthPixels,
                c.getResources().getDisplayMetrics().heightPixels);
        float textSize = screenW * 0.40f * cfg.clockScale / 100f;
        float dateSize = dateView != null && dateView.getTextSize() > 0
                ? dateView.getTextSize() : Views.dp(root, 17) * c.getResources().getConfiguration().fontScale;
        view.style(Fonts.clock(m, c, cfg.clockRound, aod), cfg.clockRound, textSize, Fonts.date(m, c), dateSize, palette, hideDate,
                cfg.clockOverlap / 100f, cfg.clockGap / 1000f);

        if (!added) {
            root.getOverlay().add(view);
            added = true;
        }
        // On the always-on display nothing sits below the clock, so its layout is left as it is.
        if (cfg.clockReserve && !aod) reserve(); else unreserve();

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

    /** Views (notifications, live cards) the clock must stay above. Their positions are read every frame. */
    void setObstacles(List<View> list) { obstacles = list; }

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
            hideOriginal(true);
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
        int top = Math.round(anchor + offsetPx);
        int w = root.getWidth();

        // Room left above the first notification / live card. ColorOS shrinking its own clock is a hint too.
        float room = avoid ? roomBelow(top, w) : Float.MAX_VALUE;
        boolean shrunk = avoid && boundTextSize > 0 && clock instanceof TextView
                && ((TextView) clock).getTextSize() < boundTextSize * 0.8f;
        float full = view.blockHeight(false) * sy, line = view.blockHeight(true) * sy;
        boolean compact;
        float fit = 1f;
        String mode;
        if (!shrunk && room >= full) { compact = false; mode = "full"; }
        else if (!shrunk && room >= full * 0.6f) { compact = false; fit = room / full; mode = "scaled"; }
        else if (room >= line) { compact = true; mode = "line"; }
        else if (room >= line * 0.6f) { compact = true; fit = room / line; mode = "line-scaled"; }
        else {
            // Not even one small line fits: give the space back to ColorOS's own clock.
            logMode("stock (room " + Math.round(room) + "px)");
            hideOriginal(false);
            hide();
            return;
        }
        logMode(mode);
        hideOriginal(true);
        view.setCompact(compact);

        int h = view.blockHeight();
        int left = Math.round(centerX - w / 2f);
        if (view.getLeft() != left || view.getTop() != top || view.getWidth() != w || view.getHeight() != h) {
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            view.layout(left, top, left + w, top + h);
        }
        view.setPivotX(w / 2f);
        view.setPivotY(0);
        float scaleX = sx * fit, scaleY = sy * fit;
        if (view.getScaleX() != scaleX) view.setScaleX(scaleX);
        if (view.getScaleY() != scaleY) view.setScaleY(scaleY);
        if (view.getAlpha() != alpha) view.setAlpha(alpha);
        if (view.getVisibility() != View.VISIBLE) view.setVisibility(View.VISIBLE);
    }

    /** Pixels between the clock top and the nearest obstacle below it, minus a margin. */
    private float roomBelow(int top, int w) {
        if (obstacles.isEmpty() && !m.hasFloatingCards()) return Float.MAX_VALUE;
        root.getLocationOnScreen(rootScreen);
        float room = Float.MAX_VALUE;
        float margin = Views.dp(root, 12);
        for (View o : obstacles) room = Math.min(room, roomFor(o, top, w, margin));
        for (View o : m.floatingCards()) room = Math.min(room, roomFor(o, top, w, margin));
        return room;
    }

    private float roomFor(View o, int top, int w, float margin) {
        if (!Views.shownOnThisThread(o)) return Float.MAX_VALUE;
        Rect r = Views.screen(o);
        r.offset(-rootScreen[0], -rootScreen[1]);
        if (r.right < w * 0.15f || r.left > w * 0.85f) return Float.MAX_VALUE;
        if (r.bottom <= top + 1) return Float.MAX_VALUE; // entirely above the clock
        return r.top - top - margin;
    }

    private void logMode(String mode) {
        if (mode.equals(lastMode)) return;
        lastMode = mode;
        m.info("Clock layout: " + mode + (aod ? " (AOD)" : ""));
    }

    /** Hides (or shows again) ColorOS's clock and, when we draw our own date, its date line. */
    private void hideOriginal(boolean hidden) {
        float a = hidden ? 0f : 1f;
        if (clock.getTransitionAlpha() != a) clock.setTransitionAlpha(a);
        if (hideDate && dateView != null && dateView.getTransitionAlpha() != a) dateView.setTransitionAlpha(a);
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
