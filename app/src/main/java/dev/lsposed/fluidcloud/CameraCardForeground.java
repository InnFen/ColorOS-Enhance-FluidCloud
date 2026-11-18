package dev.lsposed.fluidcloud;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;

/** Keeps the OEM alpha animator intact while camera geometry owns the black blend. */
final class CameraCardForeground extends DrawableWrapper {
    private final Drawable.Callback previousCallback;
    CameraCardForeground(Drawable original) {
        super(null);
        previousCallback = original.getCallback();
        setDrawable(original);
    }
    void bind() { getDrawable().setCallback(this); }
    void release() {
        if (getDrawable().getCallback() == this) getDrawable().setCallback(previousCallback);
    }
    @Override public void draw(Canvas canvas) {
        // CameraHeaderSurface already draws the geometry-dependent black blend.
        // The separate timed OEM cover would obscure material after it expands.
    }
    @SuppressWarnings("deprecation") @Override public int getOpacity() {
        return android.graphics.PixelFormat.TRANSPARENT;
    }
}
