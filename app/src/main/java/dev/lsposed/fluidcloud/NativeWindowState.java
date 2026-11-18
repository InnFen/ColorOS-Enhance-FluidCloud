package dev.lsposed.fluidcloud;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Window-state ownership contract shared by the legacy controller and per-display repository. */
final class NativeWindowState {
    private final Object source, events;
    private final Field display, legacyState;
    private final Class<?> callback;
    private final Method add, remove, getFlow, getValue;
    private Object listener;
    private int current = -1;

    private NativeWindowState(Object source, Object events, Field display, Field legacyState,
            Class<?> callback, Method add, Method remove, Method getFlow, Method getValue) {
        this.source = source; this.events = events; this.display = display; this.legacyState = legacyState;
        this.callback = callback; this.add = add; this.remove = remove;
        this.getFlow = getFlow; this.getValue = getValue;
    }
    static int decode(Object value) {
        if (value instanceof Integer number) return number >= 0 && number <= 2 ? number : -1;
        if (value instanceof Enum<?> state) return switch (state.name()) {
            case "Showing" -> 0;
            case "Hiding" -> 1;
            case "Hidden" -> 2;
            default -> -1;
        };
        return -1;
    }
    static NativeWindowState repository(Object source, Class<?> repository, Class<?> callback,
            Class<?> queue, Class<?> flow) throws ReflectiveOperationException {
        Reflect.method(callback, "setWindowState", void.class, int.class, int.class, int.class);
        return new NativeWindowState(source, Reflect.get(Reflect.field(repository, "commandQueue", queue), source),
                Reflect.field(repository, "thisDisplayId", int.class), null, callback,
                Reflect.method(queue, "addCallback", void.class, callback),
                Reflect.method(queue, "removeCallback", void.class, callback),
                Reflect.method(repository, "getWindowState", flow), Reflect.method(flow, "getValue", Object.class));
    }
    static NativeWindowState legacy(Object source, Class<?> controller, Class<?> listener)
            throws ReflectiveOperationException {
        return new NativeWindowState(source, source, Reflect.field(controller, "thisDisplayId", int.class),
                Reflect.field(controller, "windowState", int.class), listener,
                Reflect.method(controller, "addListener", void.class, listener),
                Reflect.method(controller, "removeListener", void.class, listener), null, null);
    }
    void start(int expectedDisplay, Runnable signal) {
        if ((int) Reflect.get(display, source) != expectedDisplay)
            throw new IllegalStateException("Mismatched state display");
        if (listener != null) return;
        if (legacyState == null) current = decode(Reflect.call(getValue, Reflect.call(getFlow, source)));
        listener = Reflect.listener(callback, (method, args) -> {
            if (legacyState == null) {
                if (!method.getName().equals("setWindowState") || (int) args[0] != expectedDisplay
                        || (int) args[1] != 1) return;
                current = decode(args[2]);
            }
            signal.run();
        });
        // CommandQueue is the repository's own source. Subscribe on the host's main thread;
        // no coroutine internals, global StateFlow hooks or delayed state polling are needed.
        Reflect.call(add, events, listener);
    }
    int value() { return legacyState == null ? current : decode(Reflect.get(legacyState, source)); }
    void close() {
        Object previous = listener;
        listener = null;
        if (previous != null) Reflect.call(remove, events, previous);
    }
}
