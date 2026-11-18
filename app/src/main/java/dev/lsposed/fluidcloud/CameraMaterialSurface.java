package dev.lsposed.fluidcloud;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import java.util.List;

/** Full-card decoration plane outside the OEM body's blur/alpha layer. No SDK view is moved. */
@android.annotation.SuppressLint("ViewConstructor")
final class CameraMaterialSurface extends View {
    record Paint(CardHeaderDrawable drawable, float alpha) { }
    private List<Paint> paints = List.of();
    private final Runnable changed = this::invalidate;
    private Object outlineFrame;
    private final android.graphics.Rect materialBounds = new android.graphics.Rect();
    CameraMaterialSurface(Context context, View owner) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false); setClickable(false);
        var reference = new java.lang.ref.WeakReference<>(owner);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                View card = reference.get();
                var provider = card == null ? null : card.getOutlineProvider();
                if (provider != null) provider.getOutline(card, outline);
                else outline.setRect(0, 0, view.getWidth(), view.getHeight());
            }
        });
        setClipToOutline(true);
    }
    void update(List<Paint> next) {
        for (Paint old : paints) if (next.stream().noneMatch(p -> p.drawable() == old.drawable()))
            old.drawable().materialOwner(null);
        for (Paint paint : next) paint.drawable().materialOwner(changed);
        if (!paints.equals(next)) { paints = List.copyOf(next); invalidate(); }
    }
    void release() { update(List.of()); }
    String describe() {
        StringBuilder result = new StringBuilder();
        for (Paint paint : paints) result.append('[').append(paint.drawable().nativeDrawable.getClass().getName())
                .append(" a=").append(paint.alpha()).append('/').append(paint.drawable().getAlpha())
                .append(" bounds=").append(paint.drawable().nativeDrawable.getBounds()).append(']');
        return result.toString();
    }
    void updateOutline(Object frame, android.graphics.Rect bounds) {
        boolean resized = !materialBounds.equals(bounds);
        if (resized) materialBounds.set(bounds);
        if (!java.util.Objects.equals(outlineFrame, frame)) { outlineFrame = frame; invalidateOutline(); }
        if (resized) invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        for (Paint paint : paints) {
            if (paint.alpha() <= 0) continue;
            int save = paint.alpha() < 1 ? canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(),
                    Math.round(paint.alpha() * 255)) : canvas.save();
            try { paint.drawable().drawMaterial(canvas, materialBounds.left, materialBounds.top,
                    materialBounds.width(), materialBounds.height()); }
            finally { canvas.restoreToCount(save); }
        }
    }
}
