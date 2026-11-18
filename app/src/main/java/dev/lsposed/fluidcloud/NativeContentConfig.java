package dev.lsposed.fluidcloud;

import android.os.Bundle;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class NativeContentConfig {
    private static final String FULLSCREEN = "isFullScreen";
    private final ViewGroup host;
    private final BooleanSupplier nativeHidden;
    private final Consumer<Method> hook;
    private final Field listenerInfo, attachListeners;
    private final Map<Object, Binding> bindings = new IdentityHashMap<>();
    private final Map<Class<?>, Optional<Shape>> shapes = new HashMap<>();
    private final Set<Method> hooked = new HashSet<>();
    private final Set<View> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ThreadLocal<Boolean> replaying = new ThreadLocal<>();
    private boolean closed;

    NativeContentConfig(ViewGroup host, BooleanSupplier nativeHidden, Consumer<Method> hook)
            throws ReflectiveOperationException {
        this.host = host;
        this.nativeHidden = nativeHidden;
        this.hook = hook;
        listenerInfo = Reflect.field(View.class, "mListenerInfo", Object.class);
        attachListeners = Reflect.field(listenerInfo.getType(), "mOnAttachStateChangeListeners", List.class);
    }

    // Only layout/ownership is observed here. Native animators and other config keys are untouched.
    void update(boolean active) {
        if (closed) return;
        if (active) {
            Set<View> live = Collections.newSetFromMap(new IdentityHashMap<>());
            scan(host, live);
            visited.retainAll(live);
        }
        List<Binding> snapshot;
        synchronized (bindings) { snapshot = new ArrayList<>(bindings.values()); }
        for (Binding binding : snapshot) {
            boolean present = belongs(binding.root);
            setEnabled(binding, active && present);
            if (!present) synchronized (bindings) { bindings.remove(binding.owner); }
        }
    }

    boolean hasActiveOverride() {
        synchronized (bindings) {
            for (Binding binding : bindings.values()) if (binding.enabled) return true;
            return false;
        }
    }

    Bundle filter(Object owner, Bundle original) {
        if (Boolean.TRUE.equals(replaying.get()) || original == null) return original;
        synchronized (bindings) {
            Binding binding = bindings.get(owner);
            if (binding == null || !original.containsKey(FULLSCREEN)) return original;
            binding.nativeValue = original.getBoolean(FULLSCREEN);
            if (!binding.enabled || !binding.nativeValue || !belongs(binding.root)) return original;
            // Providers reuse their Bundle across updates. Never mutate the provider's copy.
            Bundle result = new Bundle(original);
            result.putBoolean(FULLSCREEN, false);
            return result;
        }
    }

    Object filterTransform(Object owner, Object original) {
        if (Boolean.TRUE.equals(replaying.get()) || original == null) return original;
        Binding binding;
        synchronized (bindings) { binding = bindings.get(owner); }
        if (binding == null || binding.transform == null) return original;
        NativeContentTransform transform = binding.transform;
        return transform.wrap(state -> {
            Object nativeInput;
            synchronized (bindings) {
                // A partial native copy must see the native fullscreen value, not our override.
                nativeInput = binding.enabled ? transform.withFullscreen(state, binding.nativeValue) : state;
            }
            Object result = transform.invoke(original, nativeInput);
            synchronized (bindings) {
                binding.nativeValue = transform.fullscreen(result);
                return binding.enabled && belongs(binding.root)
                        ? transform.withFullscreen(result, false) : result;
            }
        });
    }

    private void scan(View view, Set<View> live) {
        live.add(view);
        if (belongs(view) && visited.add(view)) {
            Object info = Reflect.get(listenerInfo, view);
            Object listeners = info == null ? null : Reflect.get(attachListeners, info);
            if (listeners instanceof List<?> list) {
                for (Object listener : new ArrayList<>(list)) {
                    for (Field field : listener.getClass().getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                        field.setAccessible(true);
                        Object candidate = Reflect.get(field, listener);
                        if (candidate == null || candidate instanceof View) continue;
                        Optional<Shape> shape = shapes.computeIfAbsent(candidate.getClass(), NativeContentConfig::shape);
                        if (shape.isEmpty()) continue;
                        int matches = 0;
                        for (Field root : shape.get().roots) if (Reflect.get(root, candidate) == view) matches++;
                        if (matches != 1) continue;
                        Shape contract = shape.get();
                        if (hooked.add(contract.config)) hook.accept(contract.config);
                        synchronized (bindings) {
                            if (!bindings.containsKey(candidate)) bindings.put(candidate,
                                    new Binding(candidate, view, contract.config, contract.transform,
                                            contract.transform == null ? nativeHidden.getAsBoolean()
                                                    : contract.transform.fullscreen(contract.transform.current(candidate))));
                        }
                    }
                }
            }
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i), live);
        }
    }

    private boolean belongs(View view) {
        boolean capsule = false;
        for (View current = view; current != null; ) {
            if (current == host) return capsule && host.isAttachedToWindow();
            capsule |= current.getClass().getSimpleName().equals("CapsuleView");
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return false;
    }

    // Discover the template wrapper through its own attach listener and a unique contract,
    // without executing unknown getters or depending on firmware-obfuscated class names.
    private static Optional<Shape> shape(Class<?> type) {
        try { Reflect.method(type, "notifyConfigurationChanged", void.class, Configuration.class); }
        catch (NoSuchMethodException missing) { return Optional.empty(); }
        List<Field> roots = new ArrayList<>();
        Method config = null;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != FrameLayout.class) continue;
                field.setAccessible(true);
                roots.add(field);
            }
            for (Method method : c.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()
                        || method.getReturnType() != void.class || method.getParameterCount() != 1
                        || (method.getParameterTypes()[0] != Bundle.class
                            && !method.getParameterTypes()[0].getName().equals("kotlin.jvm.functions.Function1"))) continue;
                if (config != null) return Optional.empty();
                config = method;
            }
        }
        if (roots.isEmpty() || config == null) return Optional.empty();
        config.setAccessible(true);
        try {
            NativeContentTransform transform = config.getParameterTypes()[0] == Bundle.class
                    ? null : new NativeContentTransform(type, config);
            return Optional.of(new Shape(roots, config, transform));
        } catch (ReflectiveOperationException incompatible) { return Optional.empty(); }
    }

    private void setEnabled(Binding binding, boolean enabled) {
        boolean value;
        synchronized (bindings) {
            if (binding.enabled == enabled) return;
            binding.enabled = enabled;
            value = !enabled && binding.nativeValue;
        }
        Object delta;
        if (binding.transform == null) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(FULLSCREEN, value);
            delta = bundle;
        } else {
            // Use the provider's own update path, preserving flow publication and transition work.
            delta = binding.transform.wrap(state -> binding.transform.withFullscreen(state, value));
        }
        replaying.set(true);
        try { Reflect.call(binding.config, binding.owner, delta); }
        finally { replaying.remove(); }
    }

    void close() {
        if (closed) return;
        closed = true;
        List<Binding> snapshot;
        synchronized (bindings) { snapshot = new ArrayList<>(bindings.values()); }
        RuntimeException failure = null;
        for (Binding binding : snapshot) {
            try { setEnabled(binding, false); }
            catch (RuntimeException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        synchronized (bindings) { bindings.clear(); }
        visited.clear();
        shapes.clear();
        if (failure != null) throw failure;
    }
    private record Shape(List<Field> roots, Method config, NativeContentTransform transform) { }
    private static final class Binding {
        final Object owner;
        final View root;
        final Method config;
        final NativeContentTransform transform;
        boolean nativeValue, enabled;
        Binding(Object owner, View root, Method config, NativeContentTransform transform, boolean nativeValue) {
            this.owner = owner; this.root = root; this.config = config; this.transform = transform;
            this.nativeValue = nativeValue;
        }
    }
}
