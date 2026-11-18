package dev.pixellock.coloros;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Material 3 Expressive style key: a filled circle at rest that morphs into a rounded square (and shifts tone)
 * while pressed, with a small spring on release. Drawn centred in a square so wide cells still get circles.
 */
final class MorphKeyDrawable extends Drawable {
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int color, pressedColor;
    private float fraction;
    private boolean pressed;
    private ValueAnimator animator;
    private final float pressedCorner;

    /** @param pressedCorner corner radius when pressed, as a fraction of the side (0.5 = stays a circle). */
    MorphKeyDrawable(int color, int pressedColor, float pressedCorner) {
        this.color = color;
        this.pressedColor = pressedColor;
        this.pressedCorner = pressedCorner;
    }

    void setColors(int color, int pressedColor) {
        if (this.color == color && this.pressedColor == pressedColor) return;
        this.color = color;
        this.pressedColor = pressedColor;
        invalidateSelf();
    }

    int color() { return color; }
    int pressedColor() { return pressedColor; }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(int[] state) {
        boolean p = false;
        for (int s : state) if (s == android.R.attr.state_pressed) { p = true; break; }
        if (p == pressed) return false;
        pressed = p;
        animateTo(p ? 1f : 0f);
        return true;
    }

    @Override public boolean hasFocusStateSpecified() { return false; }

    private void animateTo(float target) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(fraction, target);
        if (target > fraction) {
            animator.setDuration(140);
            animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        } else {
            animator.setDuration(380);
            animator.setInterpolator(new OvershootInterpolator(2.2f));
        }
        animator.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.start();
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float w = b.width(), h = b.height();
        float ratio = w / h;
        if (ratio > 1.2f || ratio < 0.83f) {
            float side = Math.min(w, h);
            rect.set(b.exactCenterX() - side / 2f, b.exactCenterY() - side / 2f,
                    b.exactCenterX() + side / 2f, b.exactCenterY() + side / 2f);
        } else {
            rect.set(b);
        }
        float side = Math.min(rect.width(), rect.height());
        float f = fraction;
        float clamped = Math.max(0f, Math.min(1f, f));
        float radius = side * (0.5f + (pressedCorner - 0.5f) * f);
        radius = Math.max(0f, Math.min(side / 2f, radius));
        paint.setColor((Integer) ARGB.evaluate(clamped, color, pressedColor));
        paint.setAlpha(Math.round(Color.alphaOf(paint.getColor()) * alpha / 255f));
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    private int alpha = 255;

    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private static final class Color {
        static int alphaOf(int c) { return c >>> 24; }
    }
}
