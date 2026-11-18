package dev.lsposed.fluidcloud;

import android.view.View;
import android.view.ViewGroup;
import java.util.IdentityHashMap;

/** Identity-only gates for framework callbacks; unrelated views never walk a parent chain. */
final class CapsuleViewScope {
    private final ViewGroup source;
    private ViewGroup shade;
    private final IdentityHashMap<View, Boolean> sourceAncestors = new IdentityHashMap<>();
    private final IdentityHashMap<View, Boolean> sourceViews = new IdentityHashMap<>();
    private final IdentityHashMap<View, Boolean> shadeProperties = new IdentityHashMap<>();
    CapsuleViewScope(ViewGroup source) { this.source = source; }
    void refresh(ViewGroup shade) {
        this.shade = shade;
        sourceAncestors.clear();shadeProperties.clear();
        refreshSources();
        for (View view = parent(source); view != null; view = parent(view)) sourceAncestors.put(view, true);
        for (View view = shade; view != null; view = parent(view)) shadeProperties.put(view, true);
        if (shade != null) for (int i = 0; i < shade.getChildCount(); i++) {
            View child = shade.getChildAt(i);
            if (child.getClass().getSimpleName().equals("FakePluginFrame")) shadeProperties.put(child, true);
        }
    }
    ViewGroup shade() { return shade; }
    void refreshSources() { sourceViews.clear(); collect(source); }
    private void collect(View view) {
        sourceViews.put(view, true);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i));
    }
    boolean nativeSource(Object target) { return sourceViews.containsKey(target); }
    boolean sourceProperty(Object target) { return nativeSource(target) || sourceAncestors.containsKey(target); }
    boolean shadeProperty(Object target) {
        return shadeProperties.containsKey(target) || shade != null && target instanceof View view && view.getParent() == shade
                && view.getClass().getSimpleName().equals("FakePluginFrame");
    }
    boolean layout(Object target, boolean busy) { return nativeSource(target) || !busy && sourceAncestors.containsKey(target); }
    private static View parent(View view) { return view.getParent() instanceof View p ? p : null; }
}
