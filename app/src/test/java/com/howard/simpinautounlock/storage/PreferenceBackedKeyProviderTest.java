package com.howard.simpinautounlock.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.Test;

public final class PreferenceBackedKeyProviderTest {
    @Test
    public void persistsOneSharedAesKeyAcrossProviderInstances() throws Exception {
        InMemoryPreferenceStore preferences = new InMemoryPreferenceStore();
        PreferenceBackedKeyProvider first = new PreferenceBackedKeyProvider(preferences);
        SecretKey generated = first.getOrCreate();

        PreferenceBackedKeyProvider second = new PreferenceBackedKeyProvider(preferences);

        assertArrayEquals(generated.getEncoded(), second.getIfPresent().orElseThrow().getEncoded());
        assertTrue(preferences.contains("remote.pending.aes.key"));
    }

    @Test
    public void rejectsMalformedStoredKey() {
        InMemoryPreferenceStore preferences = new InMemoryPreferenceStore();
        preferences.edit().putString("remote.pending.aes.key", "not-base64").commit();

        PreferenceBackedKeyProvider provider = new PreferenceBackedKeyProvider(preferences);

        assertThrows(GeneralSecurityException.class, provider::getIfPresent);
    }

    @Test
    public void deleteRemovesTheSharedKey() throws Exception {
        InMemoryPreferenceStore preferences = new InMemoryPreferenceStore();
        PreferenceBackedKeyProvider provider = new PreferenceBackedKeyProvider(preferences);
        provider.getOrCreate();

        provider.delete();

        assertFalse(provider.getIfPresent().isPresent());
    }

    private static final class InMemoryPreferenceStore implements PreferenceStore {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defaultValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new MemoryEditor();
        }

        private final class MemoryEditor implements Editor {
            private final Map<String, Object> updates = new HashMap<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                updates.put(key, null);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                if (clear) {
                    values.clear();
                }
                updates.forEach((key, value) -> {
                    if (value == null) {
                        values.remove(key);
                    } else {
                        values.put(key, value);
                    }
                });
                return true;
            }
        }
    }
}
