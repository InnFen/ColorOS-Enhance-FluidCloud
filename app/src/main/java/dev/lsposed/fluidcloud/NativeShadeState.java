package dev.lsposed.fluidcloud;

import android.view.ViewGroup;
import android.view.View;
import android.view.ViewParent;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Tracks the native shade renderer's own lifetime, including the collapse animation tail. */
final class NativeShadeState {
    private final Supplier<ViewGroup> container;
    private final Consumer<Method> hookSetter;
    private final Map<Class<?>, Method> getters = new HashMap<>();
    NativeShadeState(Supplier<ViewGroup> container, Consumer<Method> hookSetter) {
        this.container = container;
        this.hookSetter = hookSetter;
    }
    boolean isActive() {
        ViewGroup parent = container.get();
        if (parent == null || !parent.isAttachedToWindow()) return false;
        boolean active = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (!child.getClass().getSimpleName().equals("FakePluginFrame")) continue;
            Method getter = getters.get(child.getClass());
            if (getter == null) {
                try {
                    getter = Reflect.method(child.getClass(), "getShouldInvalidate", boolean.class);
                    hookSetter.accept(Reflect.method(child.getClass(), "setShouldInvalidate", void.class, boolean.class));
                    getters.put(child.getClass(), getter);
                } catch (NoSuchMethodException error) {
                    throw new IllegalStateException("Unknown native shade rendering contract", error);
                }
            }
            if ((boolean) Reflect.call(getter, child) && visible(child)) active = true;
        }
        return active;
    }
    boolean related(View target) {
        ViewGroup parent = container.get();
        if (parent == null) return false;
        for (View view = target; view != null; view = parent(view)) if (view == parent) return true;
        for (View view = parent; view != null; view = parent(view)) if (view == target) return true;
        return false;
    }
    private static boolean visible(View view) {
        for (View v = view; v != null; v = parent(v))
            if (v.getVisibility() != View.VISIBLE || !(v.getAlpha() > 0f)) return false;
        return true;
    }
    private static View parent(View view) {
        ViewParent parent = view.getParent();
        return parent instanceof View ancestor ? ancestor : null;
    }
}
