package dev.pixellock.coloros;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;

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
    private float overlapFrac = 0.12f, gapFrac = 0.025f;

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
    boolean style(Typeface clockFace, float textSize, Typeface dateFace, float dateSize, Palette p, boolean withDate,
                  float overlap, float gap) {
        boolean changed = digits.getTypeface() != clockFace || digits.getTextSize() != textSize
                || datePaint.getTypeface() != dateFace || datePaint.getTextSize() != dateSize
                || light != p.clockLight || dark != p.clockDark || datePaint.getColor() != p.date
                || showDate != withDate || overlapFrac != overlap || gapFrac != gap;
        if (!changed) return false;
        digits.setTypeface(clockFace);
        digits.setTextSize(textSize);
        knock.setTypeface(clockFace);
        knock.setTextSize(textSize);
        datePaint.setTypeface(dateFace);
        datePaint.setTextSize(dateSize);
        datePaint.setColor(p.date);
        light = p.clockLight;
        dark = p.clockDark;
        showDate = withDate;
        overlapFrac = overlap;
        gapFrac = gap;
        return true;
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
