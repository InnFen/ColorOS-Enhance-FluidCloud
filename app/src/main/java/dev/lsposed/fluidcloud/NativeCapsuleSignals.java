package dev.lsposed.fluidcloud;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/** Observes plugin-owned overrides; never installs a framework View invalidation hook. */
final class NativeCapsuleSignals {
    private final Set<Class<?>> seen = new HashSet<>();
    private final Set<Method> methods = new HashSet<>();
    private final BiConsumer<Method, Boolean> install;
    NativeCapsuleSignals(BiConsumer<Method, Boolean> install) { this.install = install; }
    void discover(View view) {
        if (seen.add(view.getClass())) {
            observe(view.getClass(), "requestLayout", true);
            observe(view.getClass(), "invalidateDrawable", false, Drawable.class);
            if (view.getClass().getSimpleName().equals("CapsuleView")) {
                observe(view.getClass(), "b", false, float.class);
                observe(view.getClass(), "setHidden", true, boolean.class);
                observe(view.getClass(), "setCapsuleDrawableWidth", true, int.class);
            }
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) discover(group.getChildAt(i));
    }
    private void observe(Class<?> type, String name, boolean layout, Class<?>... args) {
        try {
            Method method = Reflect.method(type, name, void.class, args);
            // Reflect.method searches parents. Inherited framework implementations
            // would turn this supposedly local observer into a global hot hook.
            String owner = method.getDeclaringClass().getName();
            if (owner.startsWith("android.") || owner.startsWith("androidx.")) return;
            if (methods.add(method)) install.accept(method, layout);
        } catch (NoSuchMethodException ignored) { /* Root traversal remains the fallback. */ }
    }
}
