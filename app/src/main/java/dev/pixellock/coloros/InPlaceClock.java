package dev.pixellock.coloros;

import android.content.Context;
import android.graphics.Canvas;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns ColorOS's own clock view (a TextView, a row of digit views or a custom-drawn clock) into the Pixel clock:
 * its drawing is replaced by ours and its measuring asks for room for the two big rows. The view stays where
 * ColorOS puts it, so ColorOS keeps moving, shrinking and fading it for notifications, the always-on display and
 * unlock animations, and there is only ever one clock. While notifications or live cards are showing it draws one
 * line inside ColorOS's normal clock box.
 */
final class InPlaceClock {
    private static final Pattern TWO_NUMBERS = Pattern.compile("(\\d{1,2})\\D+(\\d{2})");
    private static final long FADE_MS = 320;
    /** Two rows <-> corner clock: digits fly and scale between the two layouts over this time. */
    private static final long MORPH_MS = 520;
    /** Lock screen <-> always-on display: the digits change weight smoothly over this time. */
    private static final long WEIGHT_MS = 650;
    /** Corner (notification) clock: top-left position and digit height, as fractions of the screen. */
    private static final float CORNER_LEFT = 0.075f, CORNER_TOP = 0.125f, CORNER_DIGITS = 0.15f;

    static final class State {
        final ClockRenderer normal = new ClockRenderer();
        final ClockRenderer aod = new ClockRenderer();
        TextView date;
        boolean compact;
        boolean hideDate;
        boolean aodProcess;
        boolean measure;
        /** ColorOS 17 plugin clock: also the panoramic AOD clock, so it stays Pixel while the screen is off. */
        boolean plugin;
        /** ColorOS's date is part of the clock view we replace, so we always draw a date ourselves. */
        boolean dateInside;
        boolean restoreWillNotDraw;
        int offsetPx;
        float appliedShift;
        long switchedAt;
        boolean wasCompact;
        long boundAt;
        int draws;
        boolean firstDrawLogged;
        boolean drewInOnDraw;
        long nextTick;
        String describe = "";
        float cornerDigits;
        /** Extra upward shift so the corner clock fits inside the view when ColorOS puts the view lower down. */
        float cornerShift;
        boolean shiftPosted;
        /** The view gave back its two-row size after the morph to the corner clock finished. */
        boolean shrunk;
        int weightFrom = -1, weightTo = -1;
        long weightAt;
    }

    private static final Map<View, State> states = Collections.synchronizedMap(new WeakHashMap<>());
    /** Fast identity check for the global draw / measure hooks, which run for every view. */
    @SuppressWarnings("unchecked")
    private static volatile WeakReference<View>[] bound = new WeakReference[0];
    private static Method setMeasured;
    private static PowerManager power;
    private static HookEntry module;

    private InPlaceClock() { }

    static State get(Object view) {
        WeakReference<View>[] refs = bound;
        if (refs.length == 0) return null;
        for (WeakReference<View> r : refs) {
            if (r.get() == view) return states.get(view);
        }
        return null;
    }

    static boolean isBound(View v) { return states.containsKey(v); }

    /**
     * In SystemUI while the screen is off, ColorOS's own clock draws as usual: the always-on display is the
     * system's (its clock is restyled inside the AOD app), we never draw a second AOD of our own.
     */
    static boolean stockNow(State s, View v) {
        return !s.aodProcess && !s.plugin && !interactive(v.getContext());
    }

    /** Screen went off in SystemUI: give ColorOS its date back while its own clock shows. */
    static void dozing(View v) {
        State s = states.get(v);
        if (s == null || s.plugin) return;
        if (s.date != null) s.date.setTransitionAlpha(1f);
        v.requestLayout();
        v.invalidate();
    }

    /** How many times ColorOS asked the bound view to draw since it was bound, and for how long it's been bound. */
    static int draws(View v) { State s = states.get(v); return s == null ? -1 : s.draws; }

    static long boundFor(View v) {
        State s = states.get(v);
        return s == null ? 0 : SystemClock.uptimeMillis() - s.boundAt;
    }

    @SuppressWarnings("unchecked")
    private static void rebuildIndex() {
        List<WeakReference<View>> refs = new ArrayList<>();
        synchronized (states) { for (View v : states.keySet()) if (v != null) refs.add(new WeakReference<>(v)); }
        bound = refs.toArray(new WeakReference[0]);
    }

    /** (Re)binds and restyles. Main thread. */
    static void bind(HookEntry m, View v, TextView date, Config cfg, Palette palette, boolean compact, boolean measure) {
        bind(m, v, date, cfg, palette, compact, measure, false);
    }

    static void bind(HookEntry m, View v, TextView date, Config cfg, Palette palette, boolean compact, boolean measure,
                     boolean plugin) {
        module = m;
        m.hookClockClass(v.getClass());
        State s = states.get(v);
        boolean fresh = s == null;
        if (fresh) {
            s = new State();
            s.boundAt = SystemClock.uptimeMillis();
        }
        Context c = v.getContext();
        int screenMin = Math.min(c.getResources().getDisplayMetrics().widthPixels,
                c.getResources().getDisplayMetrics().heightPixels);
        float textSize = screenMin * 0.40f * cfg.clockScale / 100f;
        float dateSize = date != null && date.getTextSize() > 0
                ? date.getTextSize() : Views.dp(v, 17) * c.getResources().getConfiguration().fontScale;
        // The system AOD keeps its own layout: a wide clock box gets the one-line clock and ColorOS's own date.
        boolean lineBox = !measure && v.getHeight() > 0 && v.getHeight() < v.getWidth() * 0.6f;
        boolean dateInside = plugin || (date != null && Views.isAncestor(v, date));
        // The big layout keeps its date while the corner clock shows too, so the morph can slide it across.
        boolean withDate = (cfg.clockDate || dateInside) && !lineBox;
        // One renderer for both: its weight moves between the lock screen and AOD cuts (see draw).
        boolean changed = s.normal.style(Fonts.clock(m, c, cfg.clockRound, false), cfg.clockRound, textSize,
                Fonts.date(m, c), dateSize, palette, withDate, cfg.clockOverlap / 100f, cfg.clockGap / 1000f);
        s.cornerDigits = screenMin * CORNER_DIGITS * cfg.clockScale / 100f;
        int offset = Math.round(Views.dp(v, cfg.clockOffsetDp));
        changed |= s.compact != compact || s.offsetPx != offset || s.date != date || s.hideDate != cfg.clockDate
                || s.measure != measure;
        if (s.date != null && s.date != date) s.date.setTransitionAlpha(1f);
        if (!fresh && s.compact != compact) {
            s.wasCompact = s.compact;
            s.switchedAt = SystemClock.uptimeMillis();
            s.shrunk = false;
        }
        s.date = date;
        s.compact = compact;
        s.hideDate = cfg.clockDate;
        s.offsetPx = offset;
        s.measure = measure;
        s.plugin = plugin;
        s.dateInside = dateInside;
        // The position setting fine-tunes the big clock up or down from where ColorOS puts it; the corner clock
        // only moves the view if ColorOS placed it too low for the corner (worked out while drawing).
        if (!compact) s.cornerShift = 0;
        applyShift(s, v, compact ? s.cornerShift : offset);
        s.aodProcess = m.inAodApp();
        if (fresh) {
            // Group views usually skip draw(); make sure ColorOS's clock goes through it so we can replace it.
            if (v instanceof ViewGroup && v.willNotDraw()) {
                v.setWillNotDraw(false);
                s.restoreWillNotDraw = true;
            }
            states.put(v, s);
            rebuildIndex();
        }
        // The big clock carries its own date underneath, and the corner clock its own date under it, so ColorOS's
        // date only stays in the system AOD's own small clock box.
        if (date != null) {
            float a = withDate || compact ? 0f : 1f;
            if (lineBox && !compact) a = 1f;
            if (date.getTransitionAlpha() != a) date.setTransitionAlpha(a);
        }
        if (fresh || changed) {
            s.describe = Views.describe(v);
            m.info("In-place clock " + (fresh ? "bound" : "updated") + ": " + s.describe
                    + (compact ? " | one line in the top-left corner (notifications showing)" : " | two rows")
                    + (measure ? "" : " | keeps ColorOS size")
                    + " | date " + (date == null ? "none (own date)" : Views.describe(date))
                    + " | colors " + Palette.hex(palette.clockLight) + "/" + Palette.hex(palette.clockDark));
            v.requestLayout();
            v.invalidate();
        }
    }

    static void release(View v) {
        State s = states.remove(v);
        rebuildIndex();
        if (s == null) return;
        if (s.date != null) s.date.setTransitionAlpha(1f);
        if (s.appliedShift != 0) v.setTranslationY(v.getTranslationY() - s.appliedShift);
        if (s.restoreWillNotDraw) v.setWillNotDraw(true);
        v.requestLayout();
        v.invalidate();
    }

    private static void applyShift(State s, View v, float shift) {
        if (shift == s.appliedShift) return;
        v.setTranslationY(v.getTranslationY() - s.appliedShift + shift);
        s.appliedShift = shift;
    }

    /** Lock screen digits are bold, the AOD ones thin: animate the weight between the two instead of jumping. */
    private static void updateWeight(State s, View v, boolean aodStyle) {
        int target = aodStyle ? Fonts.WEIGHT_AOD : Fonts.WEIGHT_LOCK;
        long now = SystemClock.uptimeMillis();
        if (s.weightTo < 0) {
            s.weightFrom = s.weightTo = target;
        } else if (s.weightTo != target) {
            s.weightFrom = currentWeight(s, now);
            s.weightTo = target;
            s.weightAt = now;
        }
        int w = currentWeight(s, now);
        s.normal.setWeight(w);
        if (w != s.weightTo) v.postInvalidateOnAnimation();
    }

    private static int currentWeight(State s, long now) {
        float t = Math.min(1f, (now - s.weightAt) / (float) WEIGHT_MS);
        if (t >= 1f) return s.weightTo;
        // Ease in-out (cubic), so the stroke thins gently at both ends.
        float e = t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
        return Math.round(s.weightFrom + (s.weightTo - s.weightFrom) * e);
    }

    // Draw hooks. View.draw / a class's own draw replace everything; onDraw then dispatchDraw run when only those
    // are reached, and the flag keeps a group from drawing the clock twice in one pass.

    static void drawWhole(State s, View v, Canvas canvas) {
        s.drewInOnDraw = false;
        draw(s, v, canvas, "draw");
    }

    static void drawFromOnDraw(State s, View v, Canvas canvas) {
        draw(s, v, canvas, "onDraw");
        s.drewInOnDraw = true;
    }

    static void drawFromDispatch(State s, View v, Canvas canvas) {
        if (s.drewInOnDraw) { s.drewInOnDraw = false; return; }
        draw(s, v, canvas, "dispatchDraw");
    }

    private static void draw(State s, View v, Canvas canvas, String via) {
        s.draws++;
        boolean aodStyle = s.aodProcess || (s.plugin && !interactive(v.getContext()));
        ClockRenderer r = s.normal;
        updateWeight(s, v, aodStyle);
        String[] hm = time(v);
        s.normal.setTime(hm[0], hm[1]);
        String d = s.date != null ? Views.text(s.date).trim().replace('\n', ' ') : "";
        if (d.isEmpty()) d = DateText.format(v.getContext());
        s.normal.setDate(d);

        int left = v.getPaddingLeft(), top = v.getPaddingTop();
        float w = v.getWidth() - left - v.getPaddingRight();
        float h = v.getHeight() - top - v.getPaddingBottom();
        if (!s.firstDrawLogged && module != null) {
            s.firstDrawLogged = true;
            module.info("In-place clock drawing (via " + via + "): " + v.getClass().getName() + " " + Math.round(w)
                    + "x" + Math.round(h) + "px, " + hm[0] + ":" + hm[1] + (aodStyle ? ", AOD style" : ""));
        }
        scheduleMinuteTick(s, v);
        if (w <= 0 || h <= 0) return;
        int save = canvas.save();
        canvas.translate(v.getScrollX() + left, v.getScrollY() + top);
        float maxLine = v instanceof TextView ? ((TextView) v).getTextSize() * 1.15f : h;
        // Where ColorOS's box keeps its own size (the system AOD), a wide box gets the one-line clock.
        boolean lineBox = !s.measure && h < w * 0.6f;
        int mode = s.compact ? MODE_CORNER : lineBox ? MODE_LINE : MODE_BIG;
        int was = s.wasCompact ? MODE_CORNER : lineBox ? MODE_LINE : MODE_BIG;
        float[] corner = mode == MODE_CORNER || was == MODE_CORNER ? cornerOrigin(s, v, left, top) : null;
        // Short cross-fade when switching between the two-row and the corner clock.
        long since = SystemClock.uptimeMillis() - s.switchedAt;
        boolean morph = (was == MODE_BIG && mode == MODE_CORNER) || (was == MODE_CORNER && mode == MODE_BIG);
        if (morph && since < MORPH_MS) {
            float t = ease(since / (float) MORPH_MS);
            float[] bigDate = new float[3], cornerDate = new float[3];
            ClockRenderer.Glyph[] big = r.layoutBig(w, h, bigDate);
            ClockRenderer.Glyph[] cor = r.layoutCorner(corner[0], corner[1], s.cornerDigits, cornerDate);
            r.drawMorph(canvas, mode == MODE_CORNER ? t : 1 - t, big, bigDate, cor, cornerDate);
            v.postInvalidateOnAnimation();
        } else if (!morph && since < FADE_MS && was != mode) {
            float t = since / (float) FADE_MS;
            int a = canvas.saveLayerAlpha(null, Math.round(255 * (1 - t)));
            drawMode(r, canvas, w, h, was, maxLine, s, corner);
            canvas.restoreToCount(a);
            a = canvas.saveLayerAlpha(null, Math.round(255 * t));
            drawMode(r, canvas, w, h, mode, maxLine, s, corner);
            canvas.restoreToCount(a);
            v.postInvalidateOnAnimation();
        } else {
            drawMode(r, canvas, w, h, mode, maxLine, s, corner);
            if (mode == MODE_CORNER && !s.shrunk) {
                // The view kept its two-row size during the morph; give ColorOS its own size back now.
                s.shrunk = true;
                v.post(v::requestLayout);
            }
        }
        canvas.restoreToCount(save);
    }

    /** Custom clocks may not redraw on their own every minute once we draw for them: ask for it ourselves. */
    private static void scheduleMinuteTick(State s, View v) {
        long now = System.currentTimeMillis();
        long next = (now / 60_000L + 1) * 60_000L + 50;
        if (s.nextTick == next) return;
        s.nextTick = next;
        v.postInvalidateDelayed(next - now);
    }

    private static final int MODE_BIG = 0, MODE_LINE = 1, MODE_CORNER = 2;

    /** Fast out, slow in (cubic): the digits leave briskly and settle softly. */
    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    /**
     * Where the corner clock's top-left goes, in the view's content coordinates: a fixed spot under the status bar
     * on the left of the screen. If ColorOS put the view below that spot, the view is shifted up once so the clock
     * isn't cut off at the view's top edge.
     */
    private static float[] cornerOrigin(State s, View v, int padLeft, int padTop) {
        android.util.DisplayMetrics dm = v.getResources().getDisplayMetrics();
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float x = dm.widthPixels * CORNER_LEFT - loc[0] - padLeft;
        float y = dm.heightPixels * CORNER_TOP - loc[1] - padTop;
        // Worked out from where the view sits without our own shift, so it never adds up; skipped while ColorOS
        // itself is moving the view (unlock and doze animations).
        float desired = Math.min(0f, s.cornerShift + y);
        boolean ownMoveOnly = Math.abs(v.getTranslationY() - s.appliedShift) < 0.5f;
        if (s.compact && ownMoveOnly && Math.abs(desired - s.cornerShift) > 2 && !s.shiftPosted) {
            s.shiftPosted = true;
            v.post(() -> {
                s.shiftPosted = false;
                if (!s.compact) return;
                s.cornerShift = desired;
                applyShift(s, v, s.cornerShift);
                v.invalidate();
            });
        }
        return new float[]{Math.max(0, x), Math.max(0, y)};
    }

    private static void drawMode(ClockRenderer r, Canvas canvas, float w, float h, int mode, float maxLine, State s,
                                 float[] corner) {
        if (mode == MODE_CORNER) {
            r.drawCorner(canvas, corner[0], corner[1], s.cornerDigits);
            return;
        }
        if (mode == MODE_LINE) {
            if (s.dateInside) r.drawLineWithDate(canvas, w, h, maxLine);
            else r.drawLineFitted(canvas, w, h, maxLine);
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

    /** After View.measure of a bound clock: ask for room for the two rows (and date). */
    static void measure(State s, View v, int widthSpec, int heightSpec) {
        // While the morph to the corner clock runs the view keeps its two-row size, so nothing is cut off.
        if ((s.compact && s.shrunk) || !s.measure || stockNow(s, v)) return;
        String[] hm = time(v);
        s.normal.setTime(hm[0], hm[1]);
        int padW = v.getPaddingLeft() + v.getPaddingRight();
        int padH = v.getPaddingTop() + v.getPaddingBottom();
        int wantW = Math.max(v.getMeasuredWidth(), s.normal.blockWidth(false) + padW);
        int wantH = Math.max(v.getMeasuredHeight(), s.normal.blockHeight(false) + padH);
        int w = View.resolveSize(wantW, widthSpec);
        int h = View.resolveSize(wantH, heightSpec);
        if (w == v.getMeasuredWidth() && h == v.getMeasuredHeight()) return;
        try {
            if (setMeasured == null) {
                setMeasured = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
                setMeasured.setAccessible(true);
            }
            setMeasured.invoke(v, w, h);
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

    /** Hours and minutes from ColorOS's own text when there is one, so 12/24 h matches; otherwise system time. */
    static String[] time(View v) {
        String h = null, min = null;
        String t;
        if (v instanceof TextView) {
            t = Views.text(v);
        } else {
            StringBuilder sb = new StringBuilder();
            for (TextView tv : Views.texts(v)) {
                if (tv.getVisibility() == View.VISIBLE) sb.append(Views.text(tv)).append(' ');
            }
            t = sb.toString();
        }
        Matcher mt = ClockController.TIME.matcher(t);
        if (!mt.find()) mt = TWO_NUMBERS.matcher(t);
        else mt.reset();
        if (mt.find()) { h = mt.group(1); min = mt.group(2); }
        boolean is24 = DateFormat.is24HourFormat(v.getContext());
        Calendar now = Calendar.getInstance();
        if (h == null || Integer.parseInt(min) != now.get(Calendar.MINUTE)) {
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
