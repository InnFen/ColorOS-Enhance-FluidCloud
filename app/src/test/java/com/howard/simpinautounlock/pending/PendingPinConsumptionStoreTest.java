package com.howard.simpinautounlock.pending;

import static org.junit.Assert.assertEquals;

import com.howard.simpinautounlock.storage.PreferenceStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class PendingPinConsumptionStoreTest {
    @Test
    public void persistsPerSlotGenerationConsumption() {
        InMemoryPreferenceStore preferences = new InMemoryPreferenceStore();
        PendingPinConsumptionStore first = new PendingPinConsumptionStore(preferences);

        assertEquals(PendingPinGenerationGate.Result.ACCEPTED,
                first.consume(0, "generation-1"));

        PendingPinConsumptionStore second = new PendingPinConsumptionStore(preferences);
        assertEquals(PendingPinGenerationGate.Result.IN_PROGRESS,
                second.consume(0, "generation-1"));
        assertEquals(true, second.acknowledge(0, "generation-1"));
        assertEquals(PendingPinGenerationGate.Result.ALREADY_CONSUMED,
                second.consume(0, "generation-1"));
        assertEquals(PendingPinGenerationGate.Result.ACCEPTED,
                second.consume(0, "generation-2"));
        assertEquals(PendingPinGenerationGate.Result.ACCEPTED,
                second.consume(1, "generation-1"));
    }

    @Test
    public void reportsUnavailableWhenConsumptionCannotBePersisted() {
        InMemoryPreferenceStore preferences = new InMemoryPreferenceStore();
        preferences.commitSucceeds = false;

        assertEquals(PendingPinGenerationGate.Result.UNAVAILABLE,
                new PendingPinConsumptionStore(preferences).consume(0, "generation-1"));
    }

    private static final class InMemoryPreferenceStore implements PreferenceStore {
        private final Map<String, Object> values = new HashMap<>();
        private boolean commitSucceeds = true;

        @Override public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }
        @Override public int getInt(String key, int defaultValue) { return defaultValue; }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { return new MemoryEditor(); }

        private final class MemoryEditor implements Editor {
            private final Map<String, Object> updates = new HashMap<>();
            private boolean clear;
            @Override public Editor putString(String key, String value) {
                updates.put(key, value);
                return this;
            }
            @Override public Editor putInt(String key, int value) {
                updates.put(key, value);
                return this;
            }
            @Override public Editor remove(String key) {
                updates.put(key, null);
                return this;
            }
            @Override public Editor clear() {
                clear = true;
                return this;
            }
            @Override public boolean commit() {
                if (!commitSucceeds) return false;
                if (clear) values.clear();
                updates.forEach((key, value) -> {
                    if (value == null) values.remove(key);
                    else values.put(key, value);
                });
                return true;
            }
        }
    }
}
