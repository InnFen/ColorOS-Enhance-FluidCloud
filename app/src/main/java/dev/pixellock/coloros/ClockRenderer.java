package dev.pixellock.coloros;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the Pixel-style clock. Big mode: hours over minutes, digits overlapping tightly in two alternating tones,
 * each digit cutting a thin transparent ring out of what was drawn before it (the digit to its left and the row
 * above), with the date underneath. Line mode: one row "10:50" with the same digit style, used while notifications are showing.
 * Shared by the in-place clock (drawn inside ColorOS's own clock view) and the overlay fallback.
 */
final class ClockRenderer {
    /** Size of the one-line clock relative to the big digits (overlay fallback only). */
    static final float LINE_SCALE = 0.42f;

    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint knock = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Rect bounds = new Rect();
    private String hours = "--", minutes = "--", date = "";
    private boolean showDate = true;
    private int light, dark;
    private int lockLight, lockDark, lockDate, aodLight, aodDark, aodDate;
    /** 0 = lock screen colours, 1 = always-on display colours; animated with the weight. */
    private float aodMix;
    private float overlapFrac = 0.12f, gapFrac = 0.025f;
    /** Variable-font weight animation: faces derived from the clock face, cached per weight step. */
    private Typeface baseFace;
    private int round;
    private int weight = -1;
    private final Map<Integer, Typeface> faces = new HashMap<>();

    ClockRenderer() {
        datePaint.setTextAlign(Paint.Align.CENTER);
        knock.setStyle(Paint.Style.STROKE);
        knock.setStrokeJoin(Paint.Join.ROUND);
        knock.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    /**
     * @param overlap horizontal overlap between neighbouring digits, as a fraction of the text size
     * @param gap     width of the cut-out ring around each digit, as a fraction of the text size
     * @return true when anything changed
     */
    boolean style(Typeface clockFace, int round, float textSize, Typeface dateFace, float dateSize, Palette p,
                  boolean withDate, float overlap, float gap) {
        if (baseFace != clockFace || this.round != round) {
            baseFace = clockFace;
            this.round = round;
            faces.clear();
            weight = -1;
        }
        boolean changed = (weight < 0 && digits.getTypeface() != clockFace) || digits.getTextSize() != textSize
                || datePaint.getTypeface() != dateFace || datePaint.getTextSize() != dateSize
                || lockLight != p.clockLight || lockDark != p.clockDark || lockDate != p.date
                || aodLight != p.aodLight || aodDark != p.aodDark || aodDate != p.aodDate
                || showDate != withDate || overlapFrac != overlap || gapFrac != gap;
        if (!changed) return false;
        if (weight < 0) {
            digits.setTypeface(clockFace);
            knock.setTypeface(clockFace);
        }
        digits.setTextSize(textSize);
        knock.setTextSize(textSize);
        datePaint.setTypeface(dateFace);
        datePaint.setTextSize(dateSize);
        lockLight = p.clockLight;
        lockDark = p.clockDark;
        lockDate = p.date;
        aodLight = p.aodLight;
        aodDark = p.aodDark;
        aodDate = p.aodDate;
        applyAodMix();
        showDate = withDate;
        overlapFrac = overlap;
        gapFrac = gap;
        return true;
    }

    /**
     * Sets the digit weight (100..1000, Google Sans Flex 'wght' axis), in steps of 10 so the derived faces can be
     * cached. Used to fade smoothly between the bold lock screen digits and the thin always-on display ones.
     */
    void setWeight(int w) {
        if (baseFace == null) return;
        w = Math.max(100, Math.min(1000, Math.round(w / 10f) * 10));
        if (w == weight) return;
        Typeface f = faces.get(w);
        if (f == null) {
            Paint scratch = new Paint();
            scratch.setTypeface(baseFace);
            boolean ok = scratch.setFontVariationSettings("'wght' " + w + ", 'wdth' 100, 'opsz' 144, 'ROND' " + round);
            f = ok && scratch.getTypeface() != null ? scratch.getTypeface() : Typeface.create(baseFace, w, false);
            faces.put(w, f);
        }
        digits.setTypeface(f);
        knock.setTypeface(f);
        weight = w;
    }

    /** Blends the lock screen colours toward the AOD ones (0..1), for a smooth change when the screen dozes. */
    void setAodMix(float f) {
        f = Math.max(0f, Math.min(1f, f));
        if (f == aodMix) return;
        aodMix = f;
        applyAodMix();
    }

    private void applyAodMix() {
        light = mix(lockLight, aodLight, aodMix);
        dark = mix(lockDark, aodDark, aodMix);
        datePaint.setColor(mix(lockDate, aodDate, aodMix));
    }

    /** Height of the corner clock block (one line plus the date under it) for a given digit height. */
    float cornerHeight(float digitHeight) {
        if (date.isEmpty()) return digitHeight;
        Paint.FontMetrics fm = datePaint.getFontMetrics();
        return digitHeight + datePaint.getTextSize() * 0.9f + (fm.descent - fm.ascent);
    }

    /**
     * Notification layout: a smaller one-line clock ("10:50") with its top-left corner at (x, y), the same digit
     * style as the big clock, and the date left-aligned right under it, like Pixel's small clock.
     */
    void drawCorner(Canvas canvas, float x, float y, float digitHeight) {
        String text = lineText();
        float gap = gapFrac * digits.getTextSize();
        float rowW = rowWidth(text) + 2 * gap;
        float scale = digitHeight / Math.max(1f, rowHeight());
        int save = canvas.save();
        canvas.translate(x - gap * scale, y - gap * scale);
        canvas.scale(scale, scale);
        drawSingleRow(canvas, rowW, gap);
        canvas.restoreToCount(save);
        if (date.isEmpty()) return;
        Paint.FontMetrics fm = datePaint.getFontMetrics();
        Paint.Align align = datePaint.getTextAlign();
        datePaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(date, x, y + digitHeight + datePaint.getTextSize() * 0.9f - fm.ascent, datePaint);
        datePaint.setTextAlign(align);
    }

    // Morph between the centred two-row clock and the corner clock: every digit flies from its place in one
    // layout to its place in the other while scaling, the colon fades in, and the date slides along.

    /** One character placed in the view: baseline origin (x, y) and scale relative to the big text size. */
    static final class Glyph {
        final char ch;
        final float x, y, scale;
        final int color;
        Glyph(char ch, float x, float y, float scale, int color) {
            this.ch = ch; this.x = x; this.y = y; this.scale = scale; this.color = color;
        }
    }

    /** Big clock glyphs (hours then minutes) as drawn in a w x h box; date[] = {left, baseline, scale} or NaN. */
    Glyph[] layoutBig(float w, float h, float[] dateOut) {
        float bw = blockWidth(false), bh = blockHeight(false);
        float sc = Math.min(1f, Math.min(w / bw, h / bh));
        float ox = (w - bw * sc) / 2f, oy = (h - bh * sc) / 2f;
        digits.getTextBounds("0123456789", 0, 10, bounds);
        float rowH = bounds.height();
        float top = 1 - bounds.top;
        Glyph[] out = new Glyph[hours.length() + minutes.length()];
        int n = 0;
        String[] rows = {hours, minutes};
        for (int r = 0; r < 2; r++) {
            String row = rows[r];
            float x = (bw - rowWidth(row)) / 2f;
            float base = r == 0 ? top : top + rowH + rowGap();
            int digit = 0;
            for (int i = 0; i < row.length(); i++) {
                boolean isDigit = Character.isDigit(row.charAt(i));
                int color = !isDigit || (r + digit) % 2 == 0 ? light : dark;
                if (isDigit) digit++;
                out[n++] = new Glyph(row.charAt(i), ox + x * sc, oy + base * sc, sc, color);
                x += digits.measureText(row, i, i + 1) - overlapAfter(row, i);
            }
        }
        if (showDate && !date.isEmpty()) {
            Paint.FontMetrics fm = datePaint.getFontMetrics();
            float baseline = 1 + rowH * 2 + rowGap() + dateGap() - fm.ascent;
            dateOut[0] = ox + (bw / 2f) * sc - datePaint.measureText(date) * sc / 2f;
            dateOut[1] = oy + baseline * sc;
            dateOut[2] = sc;
        } else {
            dateOut[0] = Float.NaN;
        }
        return out;
    }

    /** Corner clock glyphs (hours, colon, minutes) with the block's top-left at (x, y); same date[] as above. */
    Glyph[] layoutCorner(float x, float y, float digitHeight, float[] dateOut) {
        String text = lineText();
        float s = digitHeight / Math.max(1f, rowHeight());
        digits.getTextBounds("0123456789", 0, 10, bounds);
        float base = y + (1 - bounds.top) * s;
        Glyph[] out = new Glyph[text.length()];
        float cx = 0;
        int digit = 0;
        for (int i = 0; i < text.length(); i++) {
            boolean isDigit = Character.isDigit(text.charAt(i));
            int color = !isDigit || digit % 2 == 0 ? light : dark;
            if (isDigit) digit++;
            out[i] = new Glyph(text.charAt(i), x + cx * s, base, s, color);
            cx += digits.measureText(text, i, i + 1) - overlapAfter(text, i);
        }
        if (!date.isEmpty()) {
            Paint.FontMetrics fm = datePaint.getFontMetrics();
            dateOut[0] = x;
            dateOut[1] = y + digitHeight + datePaint.getTextSize() * 0.9f - fm.ascent;
            dateOut[2] = 1f;
        } else {
            dateOut[0] = Float.NaN;
        }
        return out;
    }

    /** t = 0: big layout, t = 1: corner layout. */
    void drawMorph(Canvas canvas, float t, Glyph[] big, float[] bigDate, Glyph[] corner, float[] cornerDate) {
        int hoursLen = hours.length();
        float gap = gapFrac * digits.getTextSize();
        knock.setStrokeWidth(gap * 2);
        int layer = canvas.saveLayer(null, null);
        for (int i = 0; i < big.length && i < corner.length; i++) {
            int ci = i < hoursLen ? i : i + 1; // the corner row has the colon after the hours
            if (ci >= corner.length) break;
            Glyph a = big[i], b = corner[ci];
            drawGlyph(canvas, a.ch, lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.scale, b.scale, t),
                    mix(a.color, b.color, t), 255, true);
            if (i == hoursLen - 1 && hoursLen < corner.length) {
                Glyph colon = corner[hoursLen];
                // Fades in on top of the row; its ring is cut only once the morph has settled.
                drawGlyph(canvas, colon.ch, colon.x, colon.y, colon.scale, colon.color, Math.round(255 * t), false);
            }
        }
        canvas.restoreToCount(layer);
        if (!date.isEmpty()) {
            float alphaA = Float.isNaN(bigDate[0]) ? 0 : 1, alphaB = Float.isNaN(cornerDate[0]) ? 0 : 1;
            float[] a = Float.isNaN(bigDate[0]) ? cornerDate : bigDate;
            float[] b = Float.isNaN(cornerDate[0]) ? bigDate : cornerDate;
            if (!Float.isNaN(a[0])) {
                int alpha = Math.round(255 * lerp(alphaA, alphaB, t));
                Paint.Align align = datePaint.getTextAlign();
                int oldAlpha = datePaint.getAlpha();
                datePaint.setTextAlign(Paint.Align.LEFT);
                datePaint.setAlpha(oldAlpha * alpha / 255);
                int save = canvas.save();
                canvas.translate(lerp(a[0], b[0], t), lerp(a[1], b[1], t));
                float ds = lerp(a[2], b[2], t);
                canvas.scale(ds, ds);
                canvas.drawText(date, 0, 0, datePaint);
                canvas.restoreToCount(save);
                datePaint.setTextAlign(align);
                datePaint.setAlpha(oldAlpha);
            }
        }
    }

    private void drawGlyph(Canvas canvas, char ch, float x, float y, float scale, int color, int alpha, boolean cut) {
        String g = String.valueOf(ch);
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        if (cut && gapFrac > 0) canvas.drawText(g, 0, 0, knock);
        digits.setColor(color);
        digits.setAlpha(Color.alpha(color) * alpha / 255);
        canvas.drawText(g, 0, 0, digits);
        canvas.restoreToCount(save);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static int mix(int a, int b, float t) {
        return Color.argb(Math.round(lerp(Color.alpha(a), Color.alpha(b), t)),
                Math.round(lerp(Color.red(a), Color.red(b), t)),
                Math.round(lerp(Color.green(a), Color.green(b), t)),
                Math.round(lerp(Color.blue(a), Color.blue(b), t)));
    }

    /** @return true when the time changed */
    boolean setTime(String h, String m) {
        if (h.equals(hours) && m.equals(minutes)) return false;
        hours = h;
        minutes = m;
        return true;
    }

    boolean setDate(String d) {
        if (d == null) d = "";
        if (d.equals(date)) return false;
        date = d;
        return true;
    }

    float textSize() { return digits.getTextSize(); }

    private float rowHeight() {
        digits.getTextBounds("0123456789", 0, 10, bounds);
        return bounds.height();
    }

    private float lineHeight() { return rowHeight() * LINE_SCALE; }

    /** Rows sit tight like on Pixel: the second row tucks in just under the first. */
    private float rowGap() { return -overlapFrac * 0.1f * digits.getTextSize(); }

    private float dateGap() { return datePaint.getTextSize() * 1.1f; }

    private float dateHeight() {
        if (!showDate || date.isEmpty()) return 0;
        Paint.FontMetrics fm = datePaint.getFontMetrics();
        return dateGap() + (fm.descent - fm.ascent);
    }

    int blockHeight(boolean line) {
        float h = line ? lineHeight() : rowHeight() * 2 + rowGap();
        return (int) Math.ceil(h + dateHeight() + 2);
    }

    int blockWidth(boolean line) {
        float w = line ? rowWidth(lineText()) * LINE_SCALE
                : Math.max(rowWidth(hours), rowWidth(minutes)) + 2 * gapFrac * digits.getTextSize();
        if (showDate && !date.isEmpty()) w = Math.max(w, datePaint.measureText(date));
        return (int) Math.ceil(w + 2);
    }

    private String lineText() { return hours + ":" + minutes; }

    private float overlap() { return digits.getTextSize() * overlapFrac; }

    /** Digits overlap fully; next to the colon only half as much so it stays readable. */
    private float overlapAfter(String row, int i) {
        if (i + 1 >= row.length()) return 0;
        boolean colon = row.charAt(i) == ':' || row.charAt(i + 1) == ':';
        return overlap() * (colon ? 0.5f : 1f);
    }

    private float rowWidth(String row) {
        float total = 0;
        for (int i = 0; i < row.length(); i++) total += digits.measureText(row, i, i + 1) - overlapAfter(row, i);
        return total;
    }

    /** Draws the block with its top edge at y=0, centred horizontally in a box of the given width. */
    void draw(Canvas canvas, float width, boolean line) {
        float y;
        if (line) {
            float rowH = rowHeight();
            int save = canvas.save();
            canvas.scale(LINE_SCALE, LINE_SCALE);
            drawSingleRow(canvas, width / LINE_SCALE, 0);
            canvas.restoreToCount(save);
            y = 1 + rowH * LINE_SCALE;
        } else {
            digits.getTextBounds("0123456789", 0, 10, bounds);
            float rowH = bounds.height();
            float top = 1 - bounds.top; // baseline of the first row
            float gap = gapFrac * digits.getTextSize();
            knock.setStrokeWidth(gap * 2);
            // Offscreen layer so the cut-out rings only erase our own digits, never what is behind the clock.
            int save = canvas.saveLayer(0, 0, width, rowH * 2 + gap * 4 + 4, null);
            drawRow(canvas, width, hours, top, 0, gap > 0);
            drawRow(canvas, width, minutes, top + rowH + rowGap(), 1, gap > 0);
            canvas.restoreToCount(save);
            y = 1 + rowH * 2 + rowGap();
        }
        if (showDate && !date.isEmpty()) {
            Paint.FontMetrics fm = datePaint.getFontMetrics();
            canvas.drawText(date, width / 2f, y + dateGap() - fm.ascent, datePaint);
        }
    }

    /**
     * Single-row clock ("10:50") fitted into a box: used inside ColorOS's own clock view while notifications are
     * showing. Same digits, tones and cut-out gaps as the big clock, as large as the box allows (at most
     * maxTextSize), vertically centred, no date (ColorOS's own date line stays).
     */
    void drawLineFitted(Canvas canvas, float w, float h, float maxTextSize) {
        String text = lineText();
        float rowW = rowWidth(text) + 2 * gapFrac * digits.getTextSize();
        float rowH = rowHeight() + 2 * gapFrac * digits.getTextSize();
        float scale = Math.min(maxTextSize / digits.getTextSize(),
                Math.min(w * 0.98f / Math.max(1f, rowW), h * 0.95f / Math.max(1f, rowH)));
        int save = canvas.save();
        canvas.translate((w - rowW * scale) / 2f, (h - rowH * scale) / 2f);
        canvas.scale(scale, scale);
        drawSingleRow(canvas, rowW, gapFrac * digits.getTextSize());
        canvas.restoreToCount(save);
    }

    /** One-line clock with the date line above it, like ColorOS's own small clock, fitted into the box. */
    void drawLineWithDate(Canvas canvas, float w, float h, float maxTextSize) {
        if (date.isEmpty()) { drawLineFitted(canvas, w, h, maxTextSize); return; }
        Paint.FontMetrics fm = datePaint.getFontMetrics();
        float dateH = (fm.descent - fm.ascent) * 1.25f;
        if (h < dateH * 2) { drawLineFitted(canvas, w, h, maxTextSize); return; }
        canvas.drawText(date, w / 2f, -fm.ascent, datePaint);
        int save = canvas.save();
        canvas.translate(0, dateH);
        drawLineFitted(canvas, w, h - dateH, maxTextSize);
        canvas.restoreToCount(save);
    }

    /** "HH:MM" in one row at the big text size, top edge at y=top. */
    private void drawSingleRow(Canvas canvas, float width, float top) {
        digits.getTextBounds("0123456789", 0, 10, bounds);
        float gap = gapFrac * digits.getTextSize();
        knock.setStrokeWidth(gap * 2);
        int save = canvas.saveLayer(0, 0, width, top + bounds.height() + gap * 2 + 4, null);
        drawRow(canvas, width, lineText(), top + 1 - bounds.top, 0, gap > 0);
        canvas.restoreToCount(save);
    }

    private void drawRow(Canvas canvas, float width, String row, float baseline, int rowIndex, boolean cut) {
        float x = (width - rowWidth(row)) / 2f;
        int digit = 0;
        for (int i = 0; i < row.length(); i++) {
            // Later digits sit in front: first erase a ring around this digit, then fill it.
            if (cut) canvas.drawText(row, i, i + 1, x, baseline, knock);
            boolean isDigit = Character.isDigit(row.charAt(i));
            digits.setColor(!isDigit || (rowIndex + digit) % 2 == 0 ? light : dark);
            if (isDigit) digit++;
            canvas.drawText(row, i, i + 1, x, baseline, digits);
            x += digits.measureText(row, i, i + 1) - overlapAfter(row, i);
        }
    }
}
