package dev.howard.colorosenhance.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

import dev.lsposed.fluidcloud.MediaWaveBands;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/** An editor for the seven shared boundaries of the six frequency bands. */
public final class WaveBandAxisView extends View {
    private static final int BAND_COUNT = MediaWaveBands.EDGE_COUNT - 1;
    private static final double LOG_SPAN = Math.log((double) MediaWaveBands.MAX_HZ / MediaWaveBands.MIN_HZ);
    private static final int[] TICKS = {20, 100, 1000, 10000, 24000};
    private static final String[] TICK_LABELS = {"20", "100", "1k", "10k", "24k"};
    private static final int[] BAND_COLORS = {0xffd45764, 0xffcb7639, 0xff9b8522, 0xff388e72, 0xff3989c4, 0xff8b68c5};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int touchSlop;
    private MediaWaveBands bands = MediaWaveBands.DEFAULTS;
    private Consumer<MediaWaveBands> onBandsChanged;
    private int textColor, mutedColor, accentColor;
    private int selected = -1;
    private int activePointer = -1;
    private int tapClusterAnchor = -1;
    private boolean dragging;
    private float downX, downY, handleOffset;
    private int legendColumns = 3;

    public WaveBandAxisView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        try (var colors = context.obtainStyledAttributes(new int[]{
                android.R.attr.textColorPrimary, android.R.attr.textColorSecondary, android.R.attr.colorAccent})) {
            textColor = colors.getColor(0, 0xff222222);
            mutedColor = colors.getColor(1, 0xff666666);
            accentColor = colors.getColor(2, 0xff3767bb);
        }
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setBands(MediaWaveBands bands) {
        MediaWaveBands value = Objects.requireNonNull(bands);
        if (this.bands.equals(value)) return;
        this.bands = value;
        invalidate();
    }

    public void setOnBandsChanged(Consumer<MediaWaveBands> listener) {
        onBandsChanged = listener;
    }

    public void setPalette(int text, int muted, int accent) {
        if (textColor == text && mutedColor == muted && accentColor == accent) return;
        textColor = text;
        mutedColor = muted;
        accentColor = accent;
        invalidate();
    }

    @Override public void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) return;
        if (!enabled) finishTouch();
        super.setEnabled(enabled);
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(Math.round(dp(340)) + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
        float available = width - getPaddingLeft() - getPaddingRight() - dp(20);
        paint.setTextSize(sp(11));
        float legendCell = paint.measureText("24000–24000") + dp(12);
        legendColumns = Math.max(1, Math.min(3, (int) (available / legendCell)));
        int legendRows = (BAND_COUNT + legendColumns - 1) / legendColumns;
        int height = (int) Math.ceil(legendTop() + legendRows * legendRowHeight() + dp(4));
        setMeasuredDimension(width, resolveSize(height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (axisRight() <= axisLeft()) return;
        int restore = canvas.save();
        canvas.translate(0, getPaddingTop());
        float axisY = axisY();
        paint.setTextSize(sp(12));
        paint.setColor(textColor);
        String title = "频率轴（Hz）";
        canvas.drawText(title, legendLeft(), dp(10) + sp(12), paint);
        if (selected >= 0 && isEnabled()) {
            String frequency = "分界 " + (selected + 1) + " · " + bands.edgeHz(selected) + " Hz";
            float right = getWidth() - getPaddingRight() - dp(10);
            float available = right - legendLeft() - paint.measureText(title) - dp(8);
            if (paint.measureText(frequency) > available) frequency = bands.edgeHz(selected) + " Hz";
            paint.setColor(accentColor);
            if (paint.measureText(frequency) <= available) {
                canvas.drawText(frequency, right - paint.measureText(frequency), dp(10) + sp(12), paint);
            }
        }

        paint.setColor(withAlpha(mutedColor, 65));
        canvas.drawRoundRect(axisLeft(), axisY - dp(4), axisRight(), axisY + dp(4), dp(4), dp(4), paint);
        for (int band = 0; band < BAND_COUNT; band++) {
            paint.setColor(bandColor(band));
            canvas.drawRect(position(bands.edgeHz(band)), axisY - dp(4),
                    position(bands.edgeHz(band + 1)), axisY + dp(4), paint);
        }

        for (int edge = 0; edge < MediaWaveBands.EDGE_COUNT; edge++) {
            if (edge != selected) drawHandle(canvas, edge, axisY, false);
        }
        if (selected >= 0) drawHandle(canvas, selected, axisY, isEnabled());
        drawTicks(canvas, axisY);
        drawLegend(canvas);
        canvas.restoreToCount(restore);
    }

    private void drawHandle(Canvas canvas, int index, float y, boolean active) {
        float x = position(bands.edgeHz(index));
        float radius = dp(active ? 7 : 5.5f);
        paint.setColor(withAlpha(active ? accentColor : textColor, isEnabled() ? 255 : 160));
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(withAlpha(Color.WHITE, active ? 245 : 220));
        canvas.drawCircle(x, y, radius - dp(active ? 2.5f : 1.5f), paint);
    }

    private void drawTicks(Canvas canvas, float y) {
        paint.setColor(mutedColor);
        paint.setStrokeWidth(dp(1));
        paint.setTextSize(sp(10));
        for (int index = 0; index < TICKS.length; index++) {
            float x = position(TICKS[index]);
            canvas.drawLine(x, y + dp(10), x, y + dp(14), paint);
            String label = TICK_LABELS[index];
            float width = paint.measureText(label);
            if (index > 0 && index < TICKS.length - 1) {
                float next = position(TICKS[index + 1]);
                if (x + width / 2 + dp(2) > next - paint.measureText(TICK_LABELS[index + 1]) / 2) continue;
            }
            float left = Math.max(getPaddingLeft() + dp(2), Math.min(x - width / 2,
                    getWidth() - getPaddingRight() - dp(2) - width));
            canvas.drawText(label, left, y + dp(16) + sp(10), paint);
        }
    }

    private void drawLegend(Canvas canvas) {
        float left = legendLeft();
        float width = (getWidth() - getPaddingRight() - dp(10) - left) / legendColumns;
        float textSize = sp(11);
        paint.setTextSize(textSize);
        for (int band = 0; band < BAND_COUNT; band++) {
            float x = left + (band % legendColumns) * width;
            float y = legendTop() + (band / legendColumns) * legendRowHeight();
            paint.setColor(bandColor(band));
            canvas.drawRoundRect(x, y + textSize - dp(7), x + dp(8), y + textSize, dp(2), dp(2), paint);
            paint.setColor(textColor);
            canvas.drawText("第 " + (band + 1) + " 柱", x + dp(12), y + textSize, paint);
            paint.setColor(mutedColor);
            canvas.drawText(range(band), x, y + textSize * 2 + dp(3), paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                float x = event.getX();
                float y = event.getY();
                if (Math.abs(y - getPaddingTop() - axisY()) > dp(24) || axisRight() <= axisLeft()) return false;
                int nearest = nearestHandle(x);
                if (nearest < 0) return false;
                float nearestX = position(bands.edgeHz(nearest));
                boolean sameCluster = selected >= 0 && Math.abs(position(bands.edgeHz(selected)) - nearestX) <= dp(8);
                tapClusterAnchor = sameCluster ? nearest : -1;
                if (!sameCluster) selected = nearest;
                activePointer = event.getPointerId(0);
                downX = x;
                downY = y;
                handleOffset = position(bands.edgeHz(selected)) - x;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                int index = event.findPointerIndex(activePointer);
                if (index < 0) return false;
                float x = event.getX(index);
                if (!dragging) {
                    float horizontal = Math.abs(x - downX);
                    float vertical = Math.abs(event.getY(index) - downY);
                    if (vertical > touchSlop && vertical > horizontal) {
                        finishTouch();
                        return false;
                    }
                    if (horizontal <= touchSlop) return true;
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                moveBoundary(selected, frequencyAt(x + handleOffset));
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (activePointer < 0) return false;
                if (dragging) moveBoundary(selected, frequencyAt(event.getX() + handleOffset));
                else {
                    if (tapClusterAnchor >= 0) cycleCoincidentHandles();
                    performClick();
                }
                finishTouch();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.getActionIndex()) == activePointer) finishTouch();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                finishTouch();
                return true;
            }
            default -> { return activePointer >= 0; }
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isEnabled()) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                int direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                selected = selected < 0 ? 0 : Math.max(0, Math.min(MediaWaveBands.EDGE_COUNT - 1, selected + direction));
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP -> {
                if (selected < 0) selected = 0;
                int increment = keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? -1 : 1;
                moveBoundary(selected, bands.edgeHz(selected) + increment);
                invalidate();
                return true;
            }
            default -> { return super.onKeyDown(keyCode, event); }
        }
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        StringBuilder summary = new StringBuilder("对数频率轴（Hz）。");
        for (int band = 0; band < BAND_COUNT; band++) {
            summary.append("第 ").append(band + 1).append(" 柱 ").append(range(band)).append(" Hz。");
        }
        info.setContentDescription(summary.append("可在下方输入具体分界频率。"));
    }

    private void finishTouch() {
        if (dragging && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        activePointer = -1;
        tapClusterAnchor = -1;
        dragging = false;
    }

    private void cycleCoincidentHandles() {
        float anchor = position(bands.edgeHz(tapClusterAnchor));
        for (int step = 1; step < MediaWaveBands.EDGE_COUNT; step++) {
            int candidate = (selected + step) % MediaWaveBands.EDGE_COUNT;
            if (Math.abs(position(bands.edgeHz(candidate)) - anchor) <= dp(8)) {
                selected = candidate;
                invalidate();
                return;
            }
        }
    }

    private int nearestHandle(float x) {
        int nearest = -1;
        float distance = dp(24);
        for (int index = 0; index < MediaWaveBands.EDGE_COUNT; index++) {
            float current = Math.abs(position(bands.edgeHz(index)) - x);
            if (current < distance) {
                distance = current;
                nearest = index;
            }
        }
        return nearest;
    }

    private void moveBoundary(int index, int frequency) {
        int low = index == 0 ? MediaWaveBands.MIN_HZ : bands.edgeHz(index - 1) + 1;
        int high = index == MediaWaveBands.EDGE_COUNT - 1 ? MediaWaveBands.MAX_HZ : bands.edgeHz(index + 1) - 1;
        int value = Math.max(low, Math.min(high, frequency));
        if (bands.edgeHz(index) == value) return;
        var edges = new ArrayList<>(bands.edges());
        edges.set(index, value);
        setBands(new MediaWaveBands(edges));
        if (onBandsChanged != null) onBandsChanged.accept(bands);
    }

    private float position(int hz) {
        double fraction = Math.log((double) hz / MediaWaveBands.MIN_HZ) / LOG_SPAN;
        return axisLeft() + (float) (fraction * (axisRight() - axisLeft()));
    }

    private int frequencyAt(float x) {
        float fraction = Math.max(0, Math.min(1, (x - axisLeft()) / (axisRight() - axisLeft())));
        return (int) Math.round(MediaWaveBands.MIN_HZ * Math.exp(fraction * LOG_SPAN));
    }

    private float axisLeft() { return getPaddingLeft() + dp(16); }
    private float axisRight() { return getWidth() - getPaddingRight() - dp(16); }
    private float axisY() { return dp(30) + sp(12); }
    private float legendLeft() { return getPaddingLeft() + dp(10); }
    private float legendTop() { return axisY() + dp(26) + sp(10); }
    private float legendRowHeight() { return dp(12) + sp(22); }
    private String range(int band) { return bands.edgeHz(band) + "–" + bands.edgeHz(band + 1); }
    private int bandColor(int band) { return withAlpha(BAND_COLORS[band], isEnabled() ? 255 : 130); }
    private static int withAlpha(int color, int alpha) { return (color & 0x00ffffff) | (alpha << 24); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
