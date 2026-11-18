package dev.pixellock.coloros;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.view.View;

/** Overlay fallback for clocks that are not a plain TextView: draws {@link ClockRenderer} in its own view. */
final class PixelClockView extends View {
    private final ClockRenderer r = new ClockRenderer();
    private boolean compact;
    private final Runnable tick = this::onTick;
    private Runnable timeSource;

    PixelClockView(Context context) {
        super(context);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setTimeSource(Runnable source) { timeSource = source; }

    void style(Typeface clockFace, int round, float textSize, Typeface dateFace, float dateSize, Palette p,
               boolean withDate, float overlap, float gap) {
        if (r.style(clockFace, round, textSize, dateFace, dateSize, p, withDate, overlap, gap)) invalidate();
    }

    void setCompact(boolean line) {
        if (compact == line) return;
        compact = line;
        invalidate();
    }

    void setTime(String h, String m) { if (r.setTime(h, m)) invalidate(); }

    void setDate(String d) { if (r.setDate(d)) invalidate(); }

    int blockHeight() { return r.blockHeight(compact); }

    int blockHeight(boolean line) { return r.blockHeight(line); }

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

    @Override protected void onDraw(Canvas canvas) { r.draw(canvas, getWidth(), compact); }
}
