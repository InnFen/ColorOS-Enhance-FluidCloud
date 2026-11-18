package dev.lsposed.fluidcloud;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Extends decoration without changing the layout or hit targets of its children. */
final class CardHeaderDrawable extends Drawable implements Drawable.Callback {
    final Drawable nativeDrawable;
    private final NativeCardMaterial materials;
    private final Drawable.Callback previousCallback;
    private int inset;
    private Runnable materialInvalidation;
    CardHeaderDrawable(Drawable drawable, int inset, NativeCardMaterial materials) {
        nativeDrawable = drawable; this.inset = inset; this.materials = materials;
        previousCallback = drawable.getCallback(); drawable.setCallback(this);
    }
    void inset(int value) { if (inset != value) { inset = value; invalidateSelf(); } }
    void bind() { nativeDrawable.setCallback(this); }
    @Override public void draw(Canvas canvas) {
        if (materialInvalidation != null) return;
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        drawMaterial(canvas, bounds.left, bounds.top - inset, bounds.width(), bounds.height() + inset);
    }
    void materialOwner(Runnable invalidation) {
        if (materialInvalidation == invalidation) return;
        materialInvalidation = invalidation;
        invalidateSelf();
    }
    void drawMaterial(Canvas canvas, int left, int top, int width, int height) {
        if (width <= 0 || height <= 0) return;
        // The SDK can replace shader uniforms between layout and draw (e.g. a song change).
        materials.resize(nativeDrawable, width, height);
        nativeDrawable.setBounds(0, 0, width, height);
        int save = canvas.save();
        try { canvas.translate(left, top); nativeDrawable.draw(canvas); }
        finally { canvas.restoreToCount(save); }
    }
    void release() {
        materialInvalidation = null;
        materials.restore(nativeDrawable);
        if (nativeDrawable.getCallback() == this) nativeDrawable.setCallback(previousCallback);
        nativeDrawable.setBounds(getBounds());
    }
    @Override public void setAlpha(int alpha) { nativeDrawable.setAlpha(alpha); }
    @Override public int getAlpha() { return nativeDrawable.getAlpha(); }
    @Override public boolean getPadding(Rect padding) { return nativeDrawable.getPadding(padding); }
    @Override public int getIntrinsicWidth() { return nativeDrawable.getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { return nativeDrawable.getIntrinsicHeight(); }
    @Override public void setColorFilter(ColorFilter filter) { nativeDrawable.setColorFilter(filter); }
    @SuppressWarnings("deprecation") @Override public int getOpacity() {
        return materialInvalidation == null ? nativeDrawable.getOpacity() : android.graphics.PixelFormat.TRANSPARENT;
    }
    @Override public boolean isStateful() { return nativeDrawable.isStateful(); }
    @Override protected boolean onStateChange(int[] state) { return nativeDrawable.setState(state); }
    @Override protected boolean onLevelChange(int level) { return nativeDrawable.setLevel(level); }
    @Override public boolean setVisible(boolean visible, boolean restart) {
        return super.setVisible(visible, restart) | nativeDrawable.setVisible(visible, restart);
    }
    @Override public void invalidateDrawable(Drawable who) {
        invalidateSelf();
        if (materialInvalidation != null) materialInvalidation.run();
    }
    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
    @Override public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
}
