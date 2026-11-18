package com.howard.simpinautounlock.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.pending.ClaimTokenGenerator;
import com.howard.simpinautounlock.pending.PendingPinRepository;
import com.howard.simpinautounlock.pending.PendingPinState;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.Before;
import org.junit.Test;

public final class EncryptedPendingPinRepositoryTest {
    private static final String RECORD_0 = "pending.record.0";
    private static final String RECORD_1 = "pending.record.1";
    private static final String STATE_0 = "pending.state.0";
    private static final String STATE_1 = "pending.state.1";

    private InMemoryPreferenceStore preferences;
    private TestKeyProvider keys;
    private PendingPinCodec codec;
    private AesGcmCipher cipher;
    private DeterministicTokenGenerator tokens;
    private EncryptedPendingPinRepository repository;

    @Before
    public void setUp() throws Exception {
        preferences = new InMemoryPreferenceStore();
        keys = new TestKeyProvider();
        codec = new PendingPinCodec();
        cipher = new AesGcmCipher();
        tokens = new DeterministicTokenGenerator();
        repository = new EncryptedPendingPinRepository(
                preferences, keys, codec, cipher, tokens);
    }

    @Test
    public void stageMarksOnlyTheSelectedSlotStaged() {
        repository.stage(0, "1234");

        assertEquals(PendingPinState.STAGED, repository.status(0).state());
        assertEquals(PendingPinState.EMPTY, repository.status(1).state());
    }

    @Test
    public void stagingTheSamePinTwiceUsesFreshCiphertext() {
        repository.stage(0, "1234");
        String first = preferences.getString(RECORD_0, "");

        repository.stage(0, "1234");
        String second = preferences.getString(RECORD_0, "");

        assertNotEquals(first, second);
    }

    @Test
    public void claimPersistsClaimedStateBeforeReturningPinAndToken() {
        repository.stage(0, "1234");
        PendingPinState[] stateSeenByGenerator = new PendingPinState[1];
        tokens.beforeReturn = () -> stateSeenByGenerator[0] = repository.status(0).state();

        PendingPinRepository.Claim claim = repository.claim(0).orElseThrow();

        assertEquals(PendingPinState.STAGED, stateSeenByGenerator[0]);
        assertEquals(new PendingPinRepository.Claim("1234", "token-1"), claim);
        assertEquals(PendingPinState.CLAIMED.name(), preferences.getString(STATE_0, ""));
        assertEquals(PendingPinState.CLAIMED, repository.status(0).state());
    }

    @Test
    public void peekReadsStagedPinWithoutMutatingRemoteState() {
        repository.stage(0, "1234");

        PendingPinRepository.Claim first = repository.peek(0).orElseThrow();
        PendingPinRepository.Claim second = repository.peek(0).orElseThrow();

        assertEquals("1234", first.pin());
        assertFalse(first.token().isEmpty());
        assertEquals(first.token(), second.token());
        assertEquals(PendingPinState.STAGED, repository.status(0).state());
    }

    @Test
    public void restagingCreatesANewReadOnlyGeneration() {
        repository.stage(0, "1234");
        String firstGeneration = repository.peek(0).orElseThrow().token();

        repository.stage(0, "1234");

        assertNotEquals(firstGeneration, repository.peek(0).orElseThrow().token());
    }

    @Test
    public void missingKeyMakesReadOnlyPeekUnavailableWithoutMutatingRemoteState() {
        repository.stage(0, "1234");
        keys.key = null;

        assertThrows(IllegalStateException.class, () -> repository.peek(0));

        assertTrue(preferences.contains(RECORD_0));
        assertEquals(PendingPinState.STAGED.name(), preferences.getString(STATE_0, ""));
    }

    @Test
    public void aSecondClaimReturnsEmpty() {
        repository.stage(0, "1234");
        assertTrue(repository.claim(0).isPresent());

        assertTrue(repository.claim(0).isEmpty());
    }

    @Test
    public void wrongAcknowledgementTokenKeepsClaimedRecord() {
        repository.stage(0, "1234");
        repository.claim(0).orElseThrow();

        assertFalse(repository.acknowledge(0, "wrong-token"));
        assertEquals(PendingPinState.CLAIMED, repository.status(0).state());
        assertTrue(preferences.contains(RECORD_0));
    }

    @Test
    public void exactAcknowledgementTokenConsumesClaimedRecord() {
        repository.stage(0, "1234");
        String token = repository.claim(0).orElseThrow().token();

        assertTrue(repository.acknowledge(0, token));
        assertEquals(PendingPinState.EMPTY, repository.status(0).state());
        assertFalse(preferences.contains(RECORD_0));
        assertFalse(preferences.contains(STATE_0));
    }

    @Test
    public void restagingClaimedSlotReplacesCiphertextAndClearsToken() throws Exception {
        repository.stage(0, "1234");
        repository.claim(0).orElseThrow();
        String claimedCiphertext = preferences.getString(RECORD_0, "");

        repository.stage(0, "5678");

        assertNotEquals(claimedCiphertext, preferences.getString(RECORD_0, ""));
        assertEquals(PendingPinState.STAGED, repository.status(0).state());
        assertEquals(
                new PendingPinCodec.Payload(0, "5678", ""),
                decryptStoredPayload(0));
    }

    @Test
    public void oldTokenCannotAcknowledgeRestagedOrNewlyClaimedRecord() {
        repository.stage(0, "1234");
        String oldToken = repository.claim(0).orElseThrow().token();

        repository.stage(0, "5678");
        assertFalse(repository.acknowledge(0, oldToken));
        assertEquals(PendingPinState.STAGED, repository.status(0).state());

        PendingPinRepository.Claim newClaim = repository.claim(0).orElseThrow();
        assertEquals("token-2", newClaim.token());
        assertFalse(repository.acknowledge(0, oldToken));
        assertEquals(PendingPinState.CLAIMED, repository.status(0).state());
    }

    @Test
    public void invalidPinsAreRejectedBeforeStorageIsTouched() {
        String[] invalidPins = {null, "", "123", "123456789", "12a4", "１２３４"};

        for (String pin : invalidPins) {
            preferences.resetTouches();
            assertThrows(IllegalArgumentException.class, () -> repository.stage(0, pin));
            assertEquals(0, preferences.touchCount);
        }
        assertEquals(0, keys.getOrCreateCount);
    }

    @Test
    public void stagedPayloadForAnotherSlotIsQuarantined() throws Exception {
        putStagedPayload(0, new PendingPinCodec.Payload(1, "1234", ""));

        assertClaimQuarantinesSlot(0);
    }

    @Test
    public void stagedPayloadWithInvalidPinIsQuarantined() throws Exception {
        putStagedPayload(0, new PendingPinCodec.Payload(0, "12a4", ""));

        assertClaimQuarantinesSlot(0);
    }

    @Test
    public void corruptBase64IsQuarantined() {
        putRawStagedRecord(0, "not-base64");

        assertClaimQuarantinesSlot(0);
    }

    @Test
    public void corruptClaimSurfacesFailedQuarantineCommit() {
        putRawStagedRecord(0, "not-base64");
        preferences.failNextCommit = true;

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> repository.claim(0));

        assertEquals("pending PIN persistence failed", error.getMessage());
        assertEquals(null, error.getCause());
        assertTrue(preferences.contains(RECORD_0));
        assertEquals(PendingPinState.STAGED.name(), preferences.getString(STATE_0, ""));
    }

    @Test
    public void tamperedGcmDataIsQuarantined() {
        repository.stage(0, "1234");
        byte[] sealed = Base64.getDecoder().decode(preferences.getString(RECORD_0, ""));
        sealed[sealed.length - 1] ^= 1;
        putRawStagedRecord(0, Base64.getEncoder().encodeToString(sealed));

        assertClaimQuarantinesSlot(0);
    }

    @Test
    public void missingKeyIsQuarantinedWithoutCreatingAReplacement() {
        repository.stage(0, "1234");
        keys.key = null;
        int createCount = keys.getOrCreateCount;

        assertClaimQuarantinesSlot(0);
        assertEquals(createCount, keys.getOrCreateCount);
    }

    @Test
    public void wrongCodecVersionIsQuarantined() throws Exception {
        byte[] encoded = codec.encode(new PendingPinCodec.Payload(0, "1234", ""));
        encoded[3] = 2;
        putStagedPlaintext(0, encoded);

        assertClaimQuarantinesSlot(0);
    }

    @Test
    public void wrongTypedRecordIsQuarantined() {
        repository.stage(0, "1234");
        preferences.edit().putInt(RECORD_0, 7).commit();

        assertClaimQuarantinesSlot(0);
    }

    @Test
    public void wrongTypedStateIsQuarantined() {
        repository.stage(0, "1234");
        preferences.edit().putInt(STATE_0, 7).commit();

        assertClaimQuarantinesSlot(0);
    }

    @Test
    public void invalidStateMetadataIsQuarantined() {
        repository.stage(0, "1234");
        preferences.edit().putString(STATE_0, "INVALID").commit();

        assertClaimQuarantinesSlot(0);
    }

    @Test
    public void stagedPayloadWithExistingTokenIsQuarantined() throws Exception {
        putStagedPayload(0, new PendingPinCodec.Payload(0, "1234", "old-token"));

        assertClaimQuarantinesSlot(0);
    }

    @Test
    public void failedClaimCommitReturnsNoPinAndQuarantinesRecord() {
        repository.stage(0, "1234");
        preferences.failNextCommit = true;

        assertTrue(repository.claim(0).isEmpty());
        assertEquals(PendingPinState.CLAIMED, repository.status(0).state());
        assertFalse(preferences.contains(RECORD_0));
    }

    @Test
    public void emptyAcknowledgementTokensReturnBeforeDecryption() {
        repository.stage(0, "1234");
        repository.claim(0).orElseThrow();
        int keyReadCount = keys.getIfPresentCount;

        assertFalse(repository.acknowledge(0, ""));
        assertFalse(repository.acknowledge(0, null));
        assertEquals(keyReadCount, keys.getIfPresentCount);
        assertEquals(PendingPinState.CLAIMED, repository.status(0).state());
    }

    @Test
    public void corruptAcknowledgementSurfacesFailedQuarantineCommit() {
        repository.stage(0, "1234");
        repository.claim(0).orElseThrow();
        preferences.edit().putString(RECORD_0, "not-base64").commit();
        preferences.failNextCommit = true;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> repository.acknowledge(0, "token-1"));

        assertEquals("pending PIN persistence failed", error.getMessage());
        assertEquals(null, error.getCause());
        assertTrue(preferences.contains(RECORD_0));
        assertEquals(PendingPinState.CLAIMED.name(), preferences.getString(STATE_0, ""));
    }

    @Test
    public void clearSelectedSlotLeavesOtherSlotIntact() {
        repository.stage(0, "1234");
        repository.stage(1, "5678");

        repository.clear(0);

        assertEquals(PendingPinState.EMPTY, repository.status(0).state());
        assertEquals(new PendingPinRepository.Claim("5678", "token-1"),
                repository.claim(1).orElseThrow());
        assertTrue(preferences.contains(RECORD_1));
        assertEquals(PendingPinState.CLAIMED.name(), preferences.getString(STATE_1, ""));
    }

    @Test
    public void clearAllClearsSlotsBeforeDeletingPendingKey() {
        repository.stage(0, "1234");
        repository.stage(1, "5678");
        keys.preferencesEmpty = preferences::isEmpty;

        repository.clearAll();

        assertTrue(keys.preferencesWereEmptyAtDelete);
        assertEquals(1, keys.deleteCount);
        assertTrue(preferences.isEmpty());
        assertEquals(PendingPinState.EMPTY, repository.status(0).state());
        assertEquals(PendingPinState.EMPTY, repository.status(1).state());
    }

    @Test
    public void invalidSlotsThrowBeforePreferencesAreTouched() {
        assertInvalidSlotDoesNotTouchPreferences(() -> repository.stage(-1, "1234"));
        assertInvalidSlotDoesNotTouchPreferences(() -> repository.status(2));
        assertInvalidSlotDoesNotTouchPreferences(() -> repository.claim(-1));
        assertInvalidSlotDoesNotTouchPreferences(() -> repository.acknowledge(2, "token-1"));
        assertInvalidSlotDoesNotTouchPreferences(() -> repository.clear(-1));
    }

    private PendingPinCodec.Payload decryptStoredPayload(int slotId) throws Exception {
        String value = preferences.getString(recordKey(slotId), "");
        byte[] sealed = Base64.getDecoder().decode(value);
        return codec.decode(cipher.decrypt(keys.key, sealed));
    }

    private void putStagedPayload(int slotId, PendingPinCodec.Payload payload) throws Exception {
        putStagedPlaintext(slotId, codec.encode(payload));
    }

    private void putStagedPlaintext(int slotId, byte[] plaintext) throws Exception {
        byte[] sealed = cipher.encrypt(keys.getOrCreate(), plaintext);
        putRawStagedRecord(slotId, Base64.getEncoder().encodeToString(sealed));
    }

    private void putRawStagedRecord(int slotId, String value) {
        preferences.edit()
                .putString(recordKey(slotId), value)
                .putString(stateKey(slotId), PendingPinState.STAGED.name())
                .commit();
    }

    private void assertClaimQuarantinesSlot(int slotId) {
        Optional<PendingPinRepository.Claim> claim = repository.claim(slotId);

        assertTrue(claim.isEmpty());
        assertFalse(preferences.contains(recordKey(slotId)));
        assertEquals(PendingPinState.CLAIMED.name(),
                preferences.getString(stateKey(slotId), ""));
        assertEquals(PendingPinState.CLAIMED, repository.status(slotId).state());
    }

    private void assertInvalidSlotDoesNotTouchPreferences(Runnable operation) {
        preferences.resetTouches();
        assertThrows(IllegalArgumentException.class, operation::run);
        assertEquals(0, preferences.touchCount);
    }

    private static String recordKey(int slotId) {
        return "pending.record." + slotId;
    }

    private static String stateKey(int slotId) {
        return "pending.state." + slotId;
    }

    private static final class DeterministicTokenGenerator implements ClaimTokenGenerator {
        private int count;
        private Runnable beforeReturn;

        @Override
        public String nextToken() {
            count++;
            if (beforeReturn != null) {
                beforeReturn.run();
            }
            return "token-" + count;
        }
    }

    private static final class TestKeyProvider implements KeyProvider {
        private SecretKey key;
        private int deleteCount;
        private int getIfPresentCount;
        private int getOrCreateCount;
        private BooleanSupplier preferencesEmpty;
        private boolean preferencesWereEmptyAtDelete;

        private TestKeyProvider() throws GeneralSecurityException {
            key = newKey();
        }

        @Override
        public SecretKey getOrCreate() throws GeneralSecurityException {
            getOrCreateCount++;
            if (key == null) {
                key = newKey();
            }
            return key;
        }

        @Override
        public Optional<SecretKey> getIfPresent() {
            getIfPresentCount++;
            return Optional.ofNullable(key);
        }

        @Override
        public void delete() {
            deleteCount++;
            preferencesWereEmptyAtDelete = preferencesEmpty != null && preferencesEmpty.getAsBoolean();
            key = null;
        }

        private static SecretKey newKey() throws GeneralSecurityException {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        }
    }

    private static final class InMemoryPreferenceStore implements PreferenceStore {
        private final Map<String, Object> values = new HashMap<>();
        private boolean failNextCommit;
        private int touchCount;

        @Override
        public String getString(String key, String defaultValue) {
            touchCount++;
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
            touchCount++;
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
            touchCount++;
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            touchCount++;
            return new MemoryEditor();
        }

        private void resetTouches() {
            touchCount = 0;
        }

        private boolean isEmpty() {
            return values.isEmpty();
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
