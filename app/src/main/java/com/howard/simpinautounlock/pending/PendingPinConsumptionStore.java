package com.howard.simpinautounlock.pending;

import com.howard.simpinautounlock.storage.PreferenceStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

public final class PendingPinConsumptionStore implements PendingPinGenerationGate {
    private static final String KEY_PREFIX = "remote.pending.consumed.";
    private static final String RESERVED_PREFIX = "reserved:";
    private static final String IMPORTED_PREFIX = "imported:";

    private final PreferenceStore preferences;

    public PendingPinConsumptionStore(PreferenceStore preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    @Override
    public synchronized Result consume(int slotId, String generation) {
        if (slotId < 0 || slotId > 1
                || generation == null
                || generation.isEmpty()
                || generation.length() > 256) {
            return Result.UNAVAILABLE;
        }
        try {
            String key = KEY_PREFIX + slotId;
            String consumed = preferences.getString(key, "");
            if (matches(consumed, IMPORTED_PREFIX + generation)) {
                return Result.ALREADY_CONSUMED;
            }
            if (matches(consumed, RESERVED_PREFIX + generation)
                    || matches(consumed, generation)) {
                return Result.IN_PROGRESS;
            }
            return preferences.edit()
                    .putString(key, RESERVED_PREFIX + generation)
                    .commit()
                    ? Result.ACCEPTED
                    : Result.UNAVAILABLE;
        } catch (RuntimeException ignored) {
            return Result.UNAVAILABLE;
        }
    }

    @Override
    public synchronized boolean acknowledge(int slotId, String generation) {
        if (slotId < 0 || slotId > 1
                || generation == null
                || generation.isEmpty()
                || generation.length() > 256) {
            return false;
        }
        try {
            String key = KEY_PREFIX + slotId;
            String current = preferences.getString(key, "");
            if (matches(current, IMPORTED_PREFIX + generation)) {
                return true;
            }
            if (!matches(current, RESERVED_PREFIX + generation)
                    && !matches(current, generation)) {
                return false;
            }
            return preferences.edit()
                    .putString(key, IMPORTED_PREFIX + generation)
                    .commit();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean matches(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8));
    }
}
