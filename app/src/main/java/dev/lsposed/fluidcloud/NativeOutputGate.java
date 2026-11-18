package dev.lsposed.fluidcloud;

import android.view.View;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Hides only the owned RenderNode; logical alpha and direct native drawing stay intact. */
final class NativeOutputGate {
    private final Method read = Reflect.method(View.class, "getTransitionAlpha", float.class);
    private final Method write = Reflect.method(View.class, "setTransitionAlpha", void.class, float.class);
    private final WeakHashMap<View, Float> owned = new WeakHashMap<>();
    NativeOutputGate() throws ReflectiveOperationException { }
    void hide(View view) {
        if (view == null) return;
        float current = (float) Reflect.call(read, view);
        if (!owned.containsKey(view) || current != 0f) owned.put(view, current);
        if (current != 0f) Reflect.call(write, view, 0f);
    }
    void restore(View view) {
        if (view == null) return;
        Float previous = owned.remove(view);
        if (previous != null && (float) Reflect.call(read, view) == 0f) {
            Reflect.call(write, view, previous);
            view.invalidate();
        }
    }
    void restore() {
        Throwable failure = null;
        for (View view : new ArrayList<>(owned.keySet())) if (view != null) try { restore(view); }
        catch (RuntimeException | LinkageError error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
        }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof LinkageError error) throw error;
    }
}
