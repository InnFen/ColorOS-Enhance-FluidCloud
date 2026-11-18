package dev.lsposed.fluidcloud;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Changes native measured bounds, so native hit regions and morph anchors use the same size. */
final class NativeCapsuleSize {
    final Method measure;
    private final Method measured = Reflect.method(View.class, "setMeasuredDimension", void.class, int.class, int.class);
    private final Class<?> capsule;
    private final WeakHashMap<View, CapsuleSize> views = new WeakHashMap<>();
    private final View.OnLayoutChangeListener layout = this::layout;
    NativeCapsuleSize(NativeCapsuleAppearance.Lookup lookup) throws ReflectiveOperationException {
        capsule = lookup.load(NativeCapsuleAppearance.VIEWS + "CapsuleView");
        measure = Reflect.method(capsule, "onMeasure", void.class, int.class, int.class);
        if (measure.getDeclaringClass() != capsule) throw new NoSuchMethodException("Native capsule measurement");
    }
    @android.annotation.SuppressLint("DiscouragedApi")
    void afterMeasure(View view, int widthSpec, CapsuleSize size) {
        if (size.original() && !views.containsKey(view)) return;
        if (!views.containsKey(view)) view.addOnLayoutChangeListener(layout);
        views.put(view, size);
        if (size.original()) return;
        int w = view.getMeasuredWidth(), h = view.getMeasuredHeight();
        int widthLimit = View.MeasureSpec.getMode(widthSpec) == View.MeasureSpec.UNSPECIFIED
                ? view.getResources().getDisplayMetrics().widthPixels : View.MeasureSpec.getSize(widthSpec);
        int heightLimit = h;
        int id = view.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id != 0) heightLimit = Math.max(h, view.getResources().getDimensionPixelSize(id));
        int width = CapsuleSize.dimension(w, size.widthPercent(), widthLimit);
        int height = CapsuleSize.dimension(h, size.heightPercent(), heightLimit);
        Reflect.call(measured, view, width | (view.getMeasuredWidthAndState() & View.MEASURED_STATE_MASK),
                height | (view.getMeasuredHeightAndState() & View.MEASURED_STATE_MASK));
    }
    private void layout(View view, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
        CapsuleSize size = views.get(view);
        if (size == null || size.original() || !(view instanceof ViewGroup group)) return;
        // Center the native content rather than stretching media icons, text, or FFT bars.
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            child.offsetLeftAndRight((view.getWidth() - child.getWidth()) / 2 - child.getLeft());
            child.offsetTopAndBottom((view.getHeight() - child.getHeight()) / 2 - child.getTop());
        }
    }
    void refresh(View root, CapsuleSize size) {
        if (capsule.isInstance(root) && !size.equals(views.get(root))) {
            if (views.containsKey(root)) views.put(root, size);
            root.requestLayout();
        }
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) refresh(group.getChildAt(i), size);
    }
    void close() {
        for (View view : new ArrayList<>(views.keySet())) { view.removeOnLayoutChangeListener(layout); view.requestLayout(); }
        views.clear();
    }
}
