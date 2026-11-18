package com.howard.simpinautounlock.storage;

import com.howard.simpinautounlock.core.CredentialRepository;
import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotState;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKey;

public final class EncryptedCredentialRepository implements CredentialRepository {
    private final PreferenceStore preferences;
    private final KeyProvider keyProvider;
    private final CredentialCodec codec;
    private final AesGcmCipher cipher;

    public EncryptedCredentialRepository(
            PreferenceStore preferences,
            KeyProvider keyProvider,
            CredentialCodec codec,
            AesGcmCipher cipher) {
        this.preferences = preferences;
        this.keyProvider = keyProvider;
        this.codec = codec;
        this.cipher = cipher;
    }

    @Override
    public void save(SlotRecord record) {
        try {
            CredentialCodec.Payload payload = new CredentialCodec.Payload(
                    record.slotId(), record.iccid(), record.pin());
            byte[] sealed = cipher.encrypt(keyProvider.getOrCreate(), codec.encode(payload));
            boolean committed = preferences.edit()
                    .putString(
                            recordKey(record.slotId()),
                            Base64.getEncoder().encodeToString(sealed))
                    .putString(stateKey(record.slotId()), record.state().name())
                    .putInt(bootKey(record.slotId()), record.lastAttemptBootCount())
                    .putInt(resultKey(record.slotId()), record.lastResultCode())
                    .putInt(remainingKey(record.slotId()), record.remainingAttempts())
                    .commit();
            requireCommit(committed);
        } catch (GeneralSecurityException | IOException error) {
            throw persistenceFailure();
        }
    }

    @Override
    public Optional<SlotRecord> load(int slotId) {
        int boot = -1;
        String stateName;
        String value;
        boolean hasRecord = preferences.contains(recordKey(slotId));
        try {
            stateName = preferences.getString(stateKey(slotId), "");
            value = preferences.getString(recordKey(slotId), "");
        } catch (ClassCastException error) {
            return Optional.of(markUnreadable(slotId, boot));
        }
        if (stateName == null
                || stateName.isEmpty()
                || SlotState.EMPTY.name().equals(stateName)) {
            if (!hasRecord) {
                return Optional.empty();
            }
            return Optional.of(markUnreadable(slotId, boot));
        }

        int result;
        int remaining;
        try {
            boot = preferences.getInt(bootKey(slotId), -1);
            result = preferences.getInt(resultKey(slotId), SlotRecord.RESULT_UNKNOWN);
            remaining = preferences.getInt(remainingKey(slotId), -1);
        } catch (ClassCastException error) {
            return Optional.of(markUnreadable(slotId, boot));
        }
        SlotState state;
        try {
            state = SlotState.valueOf(stateName);
        } catch (IllegalArgumentException error) {
            return Optional.of(markUnreadable(slotId, boot));
        }

        if (value.isEmpty()) {
            if (state == SlotState.NEEDS_CONFIRMATION) {
                return Optional.of(new SlotRecord(
                        slotId, "", "", state, boot, result, remaining));
            }
            return Optional.of(markUnreadable(slotId, boot));
        }
        try {
            SecretKey key = keyProvider.getIfPresent().orElseThrow();
            byte[] sealed = Base64.getDecoder().decode(value);
            CredentialCodec.Payload payload = codec.decode(cipher.decrypt(key, sealed));
            if (payload.slotId() != slotId) {
                throw new GeneralSecurityException("slot mismatch");
            }
            return Optional.of(new SlotRecord(
                    slotId,
                    payload.iccid(),
                    payload.pin(),
                    state,
                    boot,
                    result,
                    remaining));
        } catch (Exception error) {
            return Optional.of(markUnreadable(slotId, boot));
        }
    }

    @Override
    public void clear(int slotId) {
        boolean committed = preferences.edit()
                .remove(recordKey(slotId))
                .remove(stateKey(slotId))
                .remove(bootKey(slotId))
                .remove(resultKey(slotId))
                .remove(remainingKey(slotId))
                .commit();
        requireCommit(committed);
    }

    @Override
    public void clearAll() {
        requireCommit(preferences.edit().clear().commit());
        try {
            keyProvider.delete();
        } catch (GeneralSecurityException error) {
            throw persistenceFailure();
        }
    }

    private SlotRecord markUnreadable(int slotId, int boot) {
        boolean committed = preferences.edit()
                .remove(recordKey(slotId))
                .putString(stateKey(slotId), SlotState.NEEDS_CONFIRMATION.name())
                .putInt(bootKey(slotId), boot)
                .putInt(resultKey(slotId), SlotRecord.RESULT_UNKNOWN)
                .putInt(remainingKey(slotId), -1)
                .commit();
        requireCommit(committed);
        return new SlotRecord(
                slotId,
                "",
                "",
                SlotState.NEEDS_CONFIRMATION,
                boot,
                SlotRecord.RESULT_UNKNOWN,
                -1);
    }

    private static void requireCommit(boolean committed) {
        if (!committed) {
            throw persistenceFailure();
        }
    }

    private static IllegalStateException persistenceFailure() {
        return new IllegalStateException("credential persistence failed");
    }

    private static String recordKey(int slotId) {
        return "record." + slotId;
    }

    private static String stateKey(int slotId) {
        return "state." + slotId;
    }

    private static String bootKey(int slotId) {
        return "boot." + slotId;
    }

    private static String resultKey(int slotId) {
        return "result." + slotId;
    }

    private static String remainingKey(int slotId) {
        return "remaining." + slotId;
    }
}
