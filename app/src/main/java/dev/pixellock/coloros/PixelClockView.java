package dev.pixellock.coloros;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;

/**
 * Pixel-style stacked clock: hours over minutes, digits overlapping tightly in two alternating tones. Like the
 * Pixel clock, every digit cuts a thin transparent ring out of whatever was drawn before it (the digit to its left,
 * and the row above), so overlapping shapes still read as separate digits. In compact mode it draws one small
 * line ("1:11") instead, used when notifications or cards would otherwise sit under the big digits.
 */
final class PixelClockView extends View {
    /** Size of the one-line clock relative to the big digits. */
    static final float LINE_SCALE = 0.42f;

    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint knock = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Rect bounds = new Rect();
    private String hours = "--", minutes = "--", date = "";
    private boolean showDate = true;
    private boolean compact;
    private int light, dark;
    private float overlapFrac = 0.12f, gapFrac = 0.025f;
    private final Runnable tick = this::onTick;
    private Runnable timeSource;

    PixelClockView(Context context) {
        super(context);
        datePaint.setTextAlign(Paint.Align.CENTER);
        linePaint.setTextAlign(Paint.Align.CENTER);
        knock.setStyle(Paint.Style.STROKE);
        knock.setStrokeJoin(Paint.Join.ROUND);
        knock.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Called before every redraw so the time stays current even when nothing else on screen changes. */
    void setTimeSource(Runnable source) { timeSource = source; }

    /**
     * @param overlap horizontal overlap between neighbouring digits, as a fraction of the text size
     * @param gap     width of the cut-out ring around each digit, as a fraction of the text size
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
        linePaint.setTypeface(clockFace);
        linePaint.setTextSize(textSize * LINE_SCALE);
        linePaint.setColor(p.clockLight);
        datePaint.setTypeface(dateFace);
        datePaint.setTextSize(dateSize);
        datePaint.setColor(p.date);
        light = p.clockLight;
        dark = p.clockDark;
        showDate = withDate;
        overlapFrac = overlap;
        gapFrac = gap;
        invalidate();
        return true;
    }

    void setCompact(boolean line) {
        if (compact == line) return;
        compact = line;
        invalidate();
    }

    boolean compact() { return compact; }

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

    /** Height of one digit row (cap height of the digits). */
    float rowHeight() {
        digits.getTextBounds("0123456789", 0, 10, bounds);
        return bounds.height();
    }

    private float lineHeight() {
        linePaint.getTextBounds("0123456789", 0, 10, bounds);
        return bounds.height();
    }

    /** Rows sit tight like on Pixel: the second row tucks slightly under the first. */
    private float rowGap() { return -overlapFrac * 0.1f * digits.getTextSize(); }

    private float dateGap() { return datePaint.getTextSize() * 1.1f; }

    private float dateHeight() {
        if (!showDate) return 0;
        Paint.FontMetrics fm = datePaint.getFontMetrics();
        return dateGap() + (fm.descent - fm.ascent);
    }

    /** Total height of the block in the current mode. */
    int blockHeight() { return blockHeight(compact); }

    int blockHeight(boolean line) {
        float h = line ? lineHeight() : rowHeight() * 2 + rowGap();
        return (int) Math.ceil(h + dateHeight() + 2);
    }

    private float overlap() { return digits.getTextSize() * overlapFrac; }

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
        float y;
        if (compact) {
            linePaint.getTextBounds("0123456789", 0, 10, bounds);
            y = 1 + bounds.height();
            canvas.drawText(hours.replaceFirst("^0(?=\\d)", "") + ":" + minutes, getWidth() / 2f,
                    1 - bounds.top, linePaint);
        } else {
            digits.getTextBounds("0123456789", 0, 10, bounds);
            float rowH = bounds.height();
            float top = 1 - bounds.top; // baseline of the first row
            float gap = gapFrac * digits.getTextSize();
            knock.setStrokeWidth(gap * 2);
            // Offscreen layer so the cut-out rings only erase our own digits, never what is behind the clock.
            int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            drawRow(canvas, hours, top, 0, gap > 0);
            drawRow(canvas, minutes, top + rowH + rowGap(), 1, gap > 0);
            canvas.restoreToCount(save);
            y = 1 + rowH * 2 + rowGap();
        }
        if (showDate && !date.isEmpty()) {
            Paint.FontMetrics fm = datePaint.getFontMetrics();
            canvas.drawText(date, getWidth() / 2f, y + dateGap() - fm.ascent, datePaint);
        }
    }

    private void drawRow(Canvas canvas, String row, float baseline, int rowIndex, boolean cut) {
        float x = (getWidth() - rowWidth(row)) / 2f;
        for (int i = 0; i < row.length(); i++) {
            // Later digits sit in front: first erase a ring around this digit, then fill it.
            if (cut) canvas.drawText(row, i, i + 1, x, baseline, knock);
            digits.setColor(((rowIndex + i) % 2 == 0) ? light : dark);
            canvas.drawText(row, i, i + 1, x, baseline, digits);
            x += digits.measureText(row, i, i + 1) - overlap();
        }
    }
}
