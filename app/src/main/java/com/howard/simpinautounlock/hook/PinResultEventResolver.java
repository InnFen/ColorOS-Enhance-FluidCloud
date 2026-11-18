package com.howard.simpinautounlock.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.OptionalInt;

final class PinResultEventResolver {
    private static final String SUPPLY_PIN_DONE_NAME = "SUPPLY_ICC_PIN_DONE";
    private static final int MAX_EVENT_SCAN = 64;

    private PinResultEventResolver() {}

    static OptionalInt resolve(Class<?> profileClass) {
        if (profileClass == null) {
            return OptionalInt.empty();
        }
        OptionalInt fieldEvent = resolveField(profileClass);
        if (fieldEvent.isPresent()) {
            return fieldEvent;
        }
        try {
            Method eventToString = profileClass.getDeclaredMethod("eventToString", int.class);
            eventToString.setAccessible(true);
            for (int event = 1; event <= MAX_EVENT_SCAN; event++) {
                Object name = eventToString.invoke(null, event);
                if (SUPPLY_PIN_DONE_NAME.equals(name)) {
                    return OptionalInt.of(event);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A field-based fallback is handled separately for older telephony builds.
        }
        return OptionalInt.empty();
    }

    private static OptionalInt resolveField(Class<?> profileClass) {
        try {
            Field eventField = profileClass.getDeclaredField("EVENT_SUPPLY_ICC_PIN_DONE");
            eventField.setAccessible(true);
            Object value = eventField.get(null);
            if (value instanceof Number number && number.intValue() > 0) {
                return OptionalInt.of(number.intValue());
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Some ROM builds inline this field; eventToString remains discoverable.
        }
        return OptionalInt.empty();
    }
}
