package com.howard.simpinautounlock.core;

import java.util.Map;
import java.util.function.Supplier;

/** The SIM feature's framework preference contract. Missing or unreadable data stays off. */
public record SimFeatureSettings(boolean enabled, boolean suppressPinFlash) {
    public static final String GROUP = "sim";
    public static final String ENABLED = "enabled";
    public static final String SUPPRESS_PIN_FLASH = "suppress_pin_flash";
    public static final SimFeatureSettings OFF = new SimFeatureSettings(false, true);

    public static SimFeatureSettings read(Supplier<Map<String, ?>> preferences) {
        try {
            Map<String, ?> values = preferences.get();
            if (values == null) return OFF;
            Object enabled = values.get(ENABLED);
            Object suppress = values.get(SUPPRESS_PIN_FLASH);
            if ((enabled != null && !(enabled instanceof Boolean))
                    || (suppress != null && !(suppress instanceof Boolean))) return OFF;
            return new SimFeatureSettings(Boolean.TRUE.equals(enabled), !Boolean.FALSE.equals(suppress));
        } catch (Throwable ignored) {
            return OFF;
        }
    }
}
