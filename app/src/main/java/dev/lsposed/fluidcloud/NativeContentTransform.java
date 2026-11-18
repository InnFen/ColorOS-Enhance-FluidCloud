package dev.lsposed.fluidcloud;

import android.graphics.RectF;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

/** ColorOS 17 UIConfig copy contract, verified against the plugin's model.g and parse.w. */
final class NativeContentTransform {
    private final Class<?> function;
    private final Field stateFlow, fullscreen;
    private final Method getValue, invoke, copy;
    private final Object[] copyDefaults;

    NativeContentTransform(Class<?> owner, Method update) throws ReflectiveOperationException {
        function = update.getParameterTypes()[0];
        if (!function.isInterface() || !function.getName().equals("kotlin.jvm.functions.Function1"))
            throw new NoSuchMethodException("UIConfig transform interface");
        invoke = function.getMethod("invoke", Object.class);
        stateFlow = Reflect.field(owner, "e", Object.class);
        getValue = Reflect.method(stateFlow.getType(), "getValue", Object.class);
        Class<?> model = Reflect.type(owner.getClassLoader(), "com.oplus.systemui.plugins.shared.data.model.g");
        fullscreen = Reflect.field(model, "k", boolean.class);
        Class<?> b = boolean.class, i = int.class;
        Class<?>[] signature = {model, b, b, b, RectF.class, float.class, b, b, b, b, b, b,
                i, b, b, i, i, b, b, b, b, i, b, i, b, b, ArrayList.class, i, b, i, i, b, i};
        copy = Reflect.method(model, "a", model, signature);
        if (!Modifier.isStatic(copy.getModifiers())) throw new NoSuchMethodException("UIConfig copy must be static");
        copyDefaults = new Object[signature.length];
        for (int index = 1; index < signature.length; index++) {
            Class<?> type = signature[index];
            if (type == boolean.class) copyDefaults[index] = false;
            else if (type == int.class) copyDefaults[index] = 0;
            else if (type == float.class) copyDefaults[index] = 0f;
        }
        // Native copy mask keeps every field except k/isFullScreen (bit 10).
        // cardStyle has no copy argument on this firmware and is always preserved by a().
        copyDefaults[32] = ~1024;
    }

    Object current(Object owner) { return Reflect.call(getValue, Reflect.get(stateFlow, owner)); }
    boolean fullscreen(Object state) { return (boolean) Reflect.get(fullscreen, state); }

    Object withFullscreen(Object state, boolean value) {
        if (fullscreen(state) == value) return state;
        Object[] args = copyDefaults.clone();
        args[0] = state;
        args[11] = value;
        return Reflect.call(copy, null, args);
    }

    Object invoke(Object transform, Object state) throws Throwable {
        try { return invoke.invoke(transform, state); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }

    interface Transform { Object apply(Object state) throws Throwable; }
    Object wrap(Transform transform) {
        return Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[]{function}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "FluidCloudContentTransform";
                    default -> null;
                };
            }
            return transform.apply(args[0]);
        });
    }
}
