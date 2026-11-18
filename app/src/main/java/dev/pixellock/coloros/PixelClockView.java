package dev.pixellock.coloros;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;

/**
 * Pixel-style stacked clock: hours over minutes, digits slightly overlapping, alternating two tones
 * (top-left and bottom-right light, the other two a shade darker), with the date line underneath.
 */
final class PixelClockView extends View {
    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Rect bounds = new Rect();
    private String hours = "--", minutes = "--", date = "";
    private boolean showDate = true;
    private int light, dark;
    private final Runnable tick = this::onTick;
    private Runnable timeSource;

    PixelClockView(Context context) {
        super(context);
        datePaint.setTextAlign(Paint.Align.CENTER);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Called before every redraw so the time stays current even when nothing else on screen changes. */
    void setTimeSource(Runnable source) { timeSource = source; }

    boolean style(Typeface clockFace, float textSize, Typeface dateFace, float dateSize, Palette p, boolean withDate) {
        boolean changed = digits.getTypeface() != clockFace || digits.getTextSize() != textSize
                || datePaint.getTypeface() != dateFace || datePaint.getTextSize() != dateSize
                || light != p.clockLight || dark != p.clockDark || datePaint.getColor() != p.date
                || showDate != withDate;
        if (!changed) return false;
        digits.setTypeface(clockFace);
        digits.setTextSize(textSize);
        datePaint.setTypeface(dateFace);
        datePaint.setTextSize(dateSize);
        datePaint.setColor(p.date);
        light = p.clockLight;
        dark = p.clockDark;
        showDate = withDate;
        invalidate();
        return true;
    }

    void setTime(String h, String m) {
        if (h.equals(hours) && m.equals(minutes)) return;
        hours = h;
        minutes = m;
        invalidate();
    }

    void setDate(String d) {
        if (d == null) d = "";
        if (d.equals(date)) return;
        date = d;
        invalidate();
    }

    String hours() { return hours; }
    String minutes() { return minutes; }

    /** Height of one digit row (cap height of the digits). */
    float rowHeight() {
        digits.getTextBounds("0123456789", 0, 10, bounds);
        return bounds.height();
    }

    private float rowGap() { return -0.02f * rowHeight(); }

    private float dateGap() { return datePaint.getTextSize() * 1.1f; }

    /** Total height of the block this view needs. */
    int blockHeight() {
        float h = rowHeight() * 2 + rowGap();
        if (showDate) {
            Paint.FontMetrics fm = datePaint.getFontMetrics();
            h += dateGap() + (fm.descent - fm.ascent);
        }
        return (int) Math.ceil(h + 2);
    }

    int blockWidth() {
        float w = Math.max(rowWidth(hours), rowWidth(minutes));
        if (showDate) w = Math.max(w, datePaint.measureText(date));
        return (int) Math.ceil(w);
    }

    private float overlap() { return digits.getTextSize() * 0.06f; }

    private float rowWidth(String row) {
        float total = 0;
        for (int i = 0; i < row.length(); i++) total += digits.measureText(row, i, i + 1);
        return total - overlap() * Math.max(0, row.length() - 1);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        onTick();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void onTick() {
        if (timeSource != null) timeSource.run();
        removeCallbacks(tick);
        long now = System.currentTimeMillis();
        postDelayed(tick, 60_000 - (now % 60_000) + 50);
    }

    @Override protected void onDraw(Canvas canvas) {
        digits.getTextBounds("0123456789", 0, 10, bounds);
        float rowH = bounds.height();
        float top = 1 - bounds.top; // baseline of the first row
        drawRow(canvas, hours, top, 0);
        drawRow(canvas, minutes, top + rowH + rowGap(), 1);
        if (showDate && !date.isEmpty()) {
            Paint.FontMetrics fm = datePaint.getFontMetrics();
            float y = 1 + rowH * 2 + rowGap() + dateGap() - fm.ascent;
            canvas.drawText(date, getWidth() / 2f, y, datePaint);
        }
    }

    private void drawRow(Canvas canvas, String row, float baseline, int rowIndex) {
        float x = (getWidth() - rowWidth(row)) / 2f;
        for (int i = 0; i < row.length(); i++) {
            digits.setColor(((rowIndex + i) % 2 == 0) ? light : dark);
            canvas.drawText(row, i, i + 1, x, baseline, digits);
            x += digits.measureText(row, i, i + 1) - overlap();
        }
    }
}
