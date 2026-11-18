package dev.lsposed.fluidcloud;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** Last child of a card: above its material, below the OEM dispatchDraw spotlight. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic decoration, never inflated from XML.
final class CameraHeaderSurface extends View {
    private final java.util.function.Consumer<Canvas> painter;
    private Object frame;
    CameraHeaderSurface(Context context, java.util.function.Consumer<Canvas> painter) {
        super(context);
        this.painter = painter;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        setClickable(false);
    }
    void update(Object value) {
        if (!java.util.Objects.equals(frame, value)) { frame = value; invalidate(); }
    }
    @Override protected void onDraw(Canvas canvas) { painter.accept(canvas); }
}
