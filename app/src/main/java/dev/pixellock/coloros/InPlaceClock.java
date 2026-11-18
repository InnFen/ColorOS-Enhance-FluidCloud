package dev.pixellock.coloros;

import android.content.Context;
import android.graphics.Canvas;
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns ColorOS's own clock TextView into the Pixel clock: its onDraw is replaced by ours and its onMeasure asks
 * for room for the two big rows. The view stays where ColorOS puts it, so ColorOS keeps moving, shrinking and
 * fading it for notifications, the always-on display and unlock animations, and there is only ever one clock.
 * While notifications or live cards are showing it draws one small line inside ColorOS's normal clock box.
 */
final class InPlaceClock {
    private static final Pattern TWO_NUMBERS = Pattern.compile("(\\d{1,2})\\D+(\\d{2})");

    static final class State {
        final ClockRenderer normal = new ClockRenderer();
        final ClockRenderer aod = new ClockRenderer();
        TextView date;
        boolean compact;
        boolean hideDate;
        boolean aodProcess;
        int offsetPx;
        float appliedShift;
        long switchedAt;
        boolean wasCompact;
    }

    private static final Map<TextView, State> states = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean any;
    private static Method setMeasured;
    private static PowerManager power;

    private InPlaceClock() { }

    static State get(Object view) {
        if (!any || !(view instanceof TextView)) return null;
        return states.get(view);
    }

    static boolean isBound(TextView tv) { return states.containsKey(tv); }

    /** (Re)binds and restyles. Main thread. */
    static void bind(HookEntry m, TextView tv, TextView date, Config cfg, Palette palette, boolean compact) {
        m.hookClockClass(tv.getClass());
        State s = states.get(tv);
        boolean fresh = s == null;
        if (fresh) s = new State();
        Context c = tv.getContext();
        int screenMin = Math.min(c.getResources().getDisplayMetrics().widthPixels,
                c.getResources().getDisplayMetrics().heightPixels);
        float textSize = screenMin * 0.40f * cfg.clockScale / 100f;
        float dateSize = date != null && date.getTextSize() > 0
                ? date.getTextSize() : Views.dp(tv, 17) * c.getResources().getConfiguration().fontScale;
        boolean withDate = cfg.clockDate && !compact;
        boolean changed = s.normal.style(Fonts.clock(m, c, cfg.clockRound, false), textSize, Fonts.date(m, c),
                dateSize, palette, withDate, cfg.clockOverlap / 100f, cfg.clockGap / 1000f);
        changed |= s.aod.style(Fonts.clock(m, c, cfg.clockRound, true), textSize, Fonts.date(m, c),
                dateSize, palette, withDate, cfg.clockOverlap / 100f, cfg.clockGap / 1000f);
        int offset = Math.round(Views.dp(tv, cfg.clockOffsetDp));
        changed |= s.compact != compact || s.offsetPx != offset || s.date != date || s.hideDate != cfg.clockDate;
        if (s.date != null && s.date != date) s.date.setTransitionAlpha(1f);
        if (!fresh && s.compact != compact) {
            s.wasCompact = s.compact;
            s.switchedAt = android.os.SystemClock.uptimeMillis();
        }
        s.date = date;
        s.compact = compact;
        s.hideDate = cfg.clockDate;
        s.offsetPx = offset;
        // The position setting fine-tunes the big clock up or down from where ColorOS puts it.
        float shift = compact ? 0 : offset;
        if (shift != s.appliedShift) {
            tv.setTranslationY(tv.getTranslationY() - s.appliedShift + shift);
            s.appliedShift = shift;
        }
        s.aodProcess = m.inAodApp();
        if (fresh) {
            states.put(tv, s);
            any = true;
        }
        // The big clock carries its own date underneath; in the notification layout ColorOS's date stays.
        if (date != null) {
            float a = (!compact && cfg.clockDate) ? 0f : 1f;
            if (date.getTransitionAlpha() != a) date.setTransitionAlpha(a);
        }
        if (fresh || changed) {
            m.info("In-place clock " + (fresh ? "bound" : "updated") + ": " + Views.describe(tv)
                    + (compact ? " | one line (notifications showing)" : " | two rows")
                    + " | colors " + Palette.hex(palette.clockLight) + "/" + Palette.hex(palette.clockDark));
            tv.requestLayout();
            tv.invalidate();
        }
    }

    static void release(TextView tv) {
        State s = states.remove(tv);
        any = !states.isEmpty();
        if (s == null) return;
        if (s.date != null) s.date.setTransitionAlpha(1f);
        if (s.appliedShift != 0) tv.setTranslationY(tv.getTranslationY() - s.appliedShift);
        tv.requestLayout();
        tv.invalidate();
    }

    /** Replacement for TextView.onDraw of a bound clock. */
    static void draw(State s, TextView tv, Canvas canvas) {
        boolean aodStyle = s.aodProcess || !interactive(tv.getContext());
        ClockRenderer r = aodStyle ? s.aod : s.normal;
        String[] hm = time(tv);
        s.normal.setTime(hm[0], hm[1]);
        s.aod.setTime(hm[0], hm[1]);
        String d = s.date != null ? Views.text(s.date).trim().replace('\n', ' ') : "";
        if (d.isEmpty()) d = DateText.format(tv.getContext());
        s.normal.setDate(d);
        s.aod.setDate(d);

        int left = tv.getPaddingLeft(), top = tv.getPaddingTop();
        float w = tv.getWidth() - left - tv.getPaddingRight();
        float h = tv.getHeight() - top - tv.getPaddingBottom();
        if (w <= 0 || h <= 0) return;
        int save = canvas.save();
        canvas.translate(tv.getScrollX() + left, tv.getScrollY() + top);
        // Short cross-fade when switching between the two-row and one-row clock.
        long since = android.os.SystemClock.uptimeMillis() - s.switchedAt;
        if (since < FADE_MS && s.wasCompact != s.compact) {
            float t = since / (float) FADE_MS;
            int a = canvas.saveLayerAlpha(0, 0, w, h, Math.round(255 * (1 - t)));
            drawMode(r, canvas, w, h, s.wasCompact, tv);
            canvas.restoreToCount(a);
            a = canvas.saveLayerAlpha(0, 0, w, h, Math.round(255 * t));
            drawMode(r, canvas, w, h, s.compact, tv);
            canvas.restoreToCount(a);
            tv.postInvalidateOnAnimation();
        } else {
            drawMode(r, canvas, w, h, s.compact, tv);
        }
        canvas.restoreToCount(save);
    }

    private static final long FADE_MS = 280;

    private static void drawMode(ClockRenderer r, Canvas canvas, float w, float h, boolean compact, TextView tv) {
        if (compact) {
            r.drawLineFitted(canvas, w, h, tv.getTextSize() * 1.15f);
            return;
        }
        float bw = r.blockWidth(false), blockH = r.blockHeight(false);
        float scale = Math.min(1f, Math.min(w / bw, h / blockH));
        int save = canvas.save();
        canvas.translate((w - bw * scale) / 2f, (h - blockH * scale) / 2f);
        canvas.scale(scale, scale);
        r.draw(canvas, bw, false);
        canvas.restoreToCount(save);
    }

    /** After TextView.onMeasure of a bound clock: ask for room for the two rows (and date). */
    static void measure(State s, TextView tv, int widthSpec, int heightSpec) {
        if (s.compact) return;
        String[] hm = time(tv);
        s.normal.setTime(hm[0], hm[1]);
        int padW = tv.getPaddingLeft() + tv.getPaddingRight();
        int padH = tv.getPaddingTop() + tv.getPaddingBottom();
        int wantW = Math.max(tv.getMeasuredWidth(), s.normal.blockWidth(false) + padW);
        int wantH = s.normal.blockHeight(false) + padH;
        int w = View.resolveSize(wantW, widthSpec);
        int h = View.resolveSize(wantH, heightSpec);
        try {
            if (setMeasured == null) {
                setMeasured = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
                setMeasured.setAccessible(true);
            }
            setMeasured.invoke(tv, w, h);
        } catch (ReflectiveOperationException ignored) {
            // Keep ColorOS's size; the clock is then scaled down to fit.
        }
    }

    private static boolean interactive(Context c) {
        try {
            if (power == null) power = c.getSystemService(PowerManager.class);
            return power == null || power.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Hours and minutes from ColorOS's own text, so 12/24 h and time zone match the system. */
    static String[] time(TextView tv) {
        String h = null, min = null;
        String t = Views.text(tv);
        Matcher mt = ClockController.TIME.matcher(t);
        if (!mt.find()) mt = TWO_NUMBERS.matcher(t);
        else mt.reset();
        if (mt.find()) { h = mt.group(1); min = mt.group(2); }
        boolean is24 = DateFormat.is24HourFormat(tv.getContext());
        if (h == null) {
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            if (!is24) { hour = hour % 12; if (hour == 0) hour = 12; }
            h = is24 ? String.format(Locale.ROOT, "%02d", hour) : String.valueOf(hour);
            min = String.format(Locale.ROOT, "%02d", now.get(Calendar.MINUTE));
        } else if (is24 && h.length() == 1) {
            h = "0" + h;
        }
        return new String[]{h, min};
    }
}
