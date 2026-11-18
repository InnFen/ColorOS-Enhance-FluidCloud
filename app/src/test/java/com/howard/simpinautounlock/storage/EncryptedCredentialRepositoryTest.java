package com.howard.simpinautounlock.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotState;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.Before;
import org.junit.Test;

public final class EncryptedCredentialRepositoryTest {
    private InMemoryPreferenceStore preferences;
    private TestKeyProvider keys;
    private CredentialCodec codec;
    private AesGcmCipher cipher;
    private EncryptedCredentialRepository repository;

    @Before
    public void setUp() throws Exception {
        preferences = new InMemoryPreferenceStore();
        keys = new TestKeyProvider();
        codec = new CredentialCodec();
        cipher = new AesGcmCipher();
        repository = new EncryptedCredentialRepository(preferences, keys, codec, cipher);
    }

    @Test
    public void storesEncryptedCredentialAndSeparateStateMetadata() {
        SlotRecord input = new SlotRecord(
                0, "8901000000000001234", "1234", SlotState.ARMED, 42,
                SlotRecord.RESULT_SUCCESS, 3);
        repository.save(input);

        String sealed = preferences.getString("record.0", "");
        String envelopeBytes = new String(
                Base64.getDecoder().decode(sealed), StandardCharsets.ISO_8859_1);
        assertFalse(envelopeBytes.contains("1234"));
        assertFalse(envelopeBytes.contains("8901000000000001234"));
        assertEquals(SlotState.ARMED.name(), preferences.getString("state.0", ""));
        assertEquals(42, preferences.getInt("boot.0", -1));
        assertEquals(input, repository.load(0).orElseThrow());
    }

    @Test
    public void unreadableCiphertextFailsClosedAndKeepsVisibleState() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        preferences.edit().putString("record.0", "not-base64").commit();

        assertUnreadableResult(repository.load(0).orElseThrow());
    }

    @Test
    public void missingKeyFailsClosed() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        keys.key = null;
        keys.failGetOrCreate = true;

        assertUnreadableResult(repository.load(0).orElseThrow());
        assertEquals(1, keys.getOrCreateCount);
    }

    @Test
    public void malformedEnvelopeFailsClosed() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        preferences.edit()
                .putString("record.0", Base64.getEncoder().encodeToString(new byte[] {1, 12, 0}))
                .commit();

        assertUnreadableResult(repository.load(0).orElseThrow());
    }

    @Test
    public void authenticationFailureFailsClosed() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        byte[] sealed = Base64.getDecoder().decode(preferences.getString("record.0", ""));
        sealed[sealed.length - 1] ^= 0x01;
        preferences.edit()
                .putString("record.0", Base64.getEncoder().encodeToString(sealed))
                .commit();

        assertUnreadableResult(repository.load(0).orElseThrow());
    }

    @Test
    public void credentialDecodeFailureFailsClosed() throws Exception {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        byte[] unsupportedPayload;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(2);
            unsupportedPayload = bytes.toByteArray();
        }
        putSealedRecord(0, unsupportedPayload);

        assertUnreadableResult(repository.load(0).orElseThrow());
    }

    @Test
    public void credentialSlotMismatchFailsClosed() throws Exception {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        putSealedRecord(
                0,
                codec.encode(new CredentialCodec.Payload(1, "8901000000000001234", "1234")));

        assertUnreadableResult(repository.load(0).orElseThrow());
    }

    @Test
    public void invalidStateFailsClosed() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        preferences.edit().putString("state.0", "INVALID").commit();

        assertUnreadableResult(repository.load(0).orElseThrow());
    }

    @Test
    public void missingStateWithCiphertextFailsClosed() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        preferences.edit().remove("state.0").commit();

        assertUnreadableResult(repository.load(0).orElseThrow());
    }

    @Test
    public void emptyStateWithCiphertextFailsClosed() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        preferences.edit().putString("state.0", SlotState.EMPTY.name()).commit();

        assertUnreadableResult(repository.load(0).orElseThrow());
    }

    @Test
    public void wrongTypedStateWithCiphertextFailsClosed() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        preferences.edit().putInt("state.0", 7).commit();

        assertUnreadableResult(repository.load(0).orElseThrow());
    }

    @Test
    public void wrongTypedIntegerMetadataFailsClosed() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        preferences.edit().putString("boot.0", "invalid").commit();

        SlotRecord output = repository.load(0).orElseThrow();

        assertUnreadableResult(output);
        assertEquals(-1, output.lastAttemptBootCount());
    }

    @Test
    public void noRecordAndNoStateRemainsEmpty() {
        assertTrue(repository.load(0).isEmpty());
    }

    @Test
    public void clearSlotLeavesOtherSlot() {
        SlotRecord other = SlotRecord.armed(1, "8901000000000005678", "5678");
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        repository.save(other);

        repository.clear(0);

        assertTrue(repository.load(0).isEmpty());
        assertEquals(other, repository.load(1).orElseThrow());
    }

    @Test
    public void clearAllRemovesBothSlotsAndDeletesKeyOnce() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        repository.save(SlotRecord.armed(1, "8901000000000005678", "5678"));
        repository.clearAll();

        assertTrue(repository.load(0).isEmpty());
        assertTrue(repository.load(1).isEmpty());
        assertEquals(1, keys.deleteCount);
    }

    @Test
    public void saveRejectsFailedPreferenceCommit() {
        preferences.failNextCommit = true;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> repository.save(SlotRecord.armed(0, "8901000000000001234", "1234")));

        assertSanitized(error);
    }

    @Test
    public void saveKeyFailureIsSanitized() {
        keys.failGetOrCreate = true;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> repository.save(SlotRecord.armed(0, "8901000000000001234", "1234")));

        assertSanitized(error);
    }

    @Test
    public void markUnreadableCommitFailureIsSanitized() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        preferences.edit().putString("record.0", "not-base64").commit();
        preferences.failNextCommit = true;

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> repository.load(0));

        assertSanitized(error);
        assertTrue(preferences.contains("record.0"));
    }

    @Test
    public void clearSlotCommitFailureIsSanitized() {
        SlotRecord input = SlotRecord.armed(0, "8901000000000001234", "1234");
        repository.save(input);
        preferences.failNextCommit = true;

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> repository.clear(0));

        assertSanitized(error);
        assertEquals(input, repository.load(0).orElseThrow());
    }

    @Test
    public void clearAllPreferenceFailureDoesNotDeleteKey() {
        SlotRecord input = SlotRecord.armed(0, "8901000000000001234", "1234");
        repository.save(input);
        preferences.failNextCommit = true;

        IllegalStateException error =
                assertThrows(IllegalStateException.class, repository::clearAll);

        assertSanitized(error);
        assertEquals(0, keys.deleteCount);
        assertEquals(input, repository.load(0).orElseThrow());
    }

    @Test
    public void keyDeletionFailureIsSanitized() {
        repository.save(SlotRecord.armed(0, "8901000000000001234", "1234"));
        keys.failDelete = true;

        IllegalStateException error =
                assertThrows(IllegalStateException.class, repository::clearAll);

        assertSanitized(error);
        assertEquals(1, keys.deleteCount);
        assertTrue(repository.load(0).isEmpty());
    }

    private void putSealedRecord(int slotId, byte[] plaintext) throws Exception {
        byte[] sealed = cipher.encrypt(keys.getOrCreate(), plaintext);
        preferences.edit()
                .putString("record." + slotId, Base64.getEncoder().encodeToString(sealed))
                .commit();
    }

    private void assertUnreadableResult(SlotRecord output) {
        assertEquals(SlotState.NEEDS_CONFIRMATION, output.state());
        assertEquals("", output.iccid());
        assertEquals("", output.pin());
        assertFalse(preferences.contains("record.0"));
        assertEquals(
                SlotState.NEEDS_CONFIRMATION.name(),
                preferences.getString("state.0", ""));
        assertEquals(SlotRecord.RESULT_UNKNOWN, output.lastResultCode());
        assertEquals(-1, output.remainingAttempts());
    }

    private static void assertSanitized(IllegalStateException error) {
        assertEquals("credential persistence failed", error.getMessage());
        assertEquals(null, error.getCause());
    }

    private static final class TestKeyProvider implements KeyProvider {
        private SecretKey key;
        private int deleteCount;
        private int getOrCreateCount;
        private boolean failDelete;
        private boolean failGetOrCreate;

        private TestKeyProvider() throws GeneralSecurityException {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            key = generator.generateKey();
        }

        @Override
        public SecretKey getOrCreate() throws GeneralSecurityException {
            getOrCreateCount++;
            if (failGetOrCreate) {
                throw new GeneralSecurityException("test key creation failure");
            }
            if (key == null) {
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(256);
                key = generator.generateKey();
            }
            return key;
        }

        @Override
        public Optional<SecretKey> getIfPresent() {
            return Optional.ofNullable(key);
        }

        @Override
        public void delete() throws GeneralSecurityException {
            deleteCount++;
            if (failDelete) {
                throw new GeneralSecurityException("test key deletion failure");
            }
            key = null;
        }
    }

    private static final class InMemoryPreferenceStore implements PreferenceStore {
        private final Map<String, Object> values = new HashMap<>();
        private boolean failNextCommit;

        @Override
        public String getString(String key, String defaultValue) {
            if (!values.containsKey(key)) {
                return defaultValue;
            }
            Object value = values.get(key);
            if (!(value instanceof String)) {
                throw new ClassCastException("value is not a string");
            }
            return (String) value;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            if (!values.containsKey(key)) {
                return defaultValue;
            }
            Object value = values.get(key);
            if (!(value instanceof Integer)) {
                throw new ClassCastException("value is not an integer");
            }
            return (Integer) value;
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
            private final Map<String, Object> writes = new HashMap<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                writes.put(key, value);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                writes.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                writes.put(key, this);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                if (failNextCommit) {
                    failNextCommit = false;
                    return false;
                }
                if (clear) {
                    values.clear();
                }
                for (Map.Entry<String, Object> entry : writes.entrySet()) {
                    if (entry.getValue() == this) {
                        values.remove(entry.getKey());
                    } else {
                        values.put(entry.getKey(), entry.getValue());
                    }
                }
                return true;
            }
        }
    }
}
