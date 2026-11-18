package com.howard.simpinautounlock.hook;

import java.util.function.Consumer;

final class PinStorageResultPolicy {
    private PinStorageResultPolicy() {}

    static Object resolve(
            Object originalObject,
            Consumer<PinStorageTraceEvent> trace,
            EmptyStringResolver resolver) throws Throwable {
        if (!(originalObject instanceof String original)) {
            trace.accept(PinStorageTraceEvent.GET_PIN_ORIGINAL_UNAVAILABLE);
            return originalObject;
        }
        if (!original.isEmpty()) {
            trace.accept(PinStorageTraceEvent.GET_PIN_ORIGINAL_PRESENT);
            return originalObject;
        }
        trace.accept(PinStorageTraceEvent.GET_PIN_ORIGINAL_EMPTY);
        Object resolved = resolver.resolve(original);
        trace.accept(resolved instanceof String pin && !pin.isEmpty()
                ? PinStorageTraceEvent.MODULE_PIN_RETURNED
                : PinStorageTraceEvent.MODULE_PIN_NOT_RETURNED);
        return resolved;
    }

    static PinStorageTraceEvent runtimeEvent(boolean initialized) {
        return initialized
                ? PinStorageTraceEvent.PHONE_RUNTIME_REUSED
                : PinStorageTraceEvent.PHONE_RUNTIME_INIT_LAZY;
    }

    @FunctionalInterface
    interface EmptyStringResolver {
        Object resolve(String original) throws Throwable;
    }
}
