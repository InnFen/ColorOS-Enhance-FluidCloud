package com.howard.simpinautounlock.storage;

import com.howard.simpinautounlock.pending.ClaimTokenGenerator;
import com.howard.simpinautounlock.pending.PendingPinRepository;
import com.howard.simpinautounlock.pending.PendingPinState;
import com.howard.simpinautounlock.pending.PendingPinStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;

public final class EncryptedPendingPinRepository implements PendingPinRepository {
    private static final String RECORD_PREFIX = "pending.record.";
    private static final String STATE_PREFIX = "pending.state.";
    private static final Pattern PIN_PATTERN = Pattern.compile("[0-9]{4,8}");

    private final PreferenceStore preferences;
    private final KeyProvider keyProvider;
    private final PendingPinCodec codec;
    private final AesGcmCipher cipher;
    private final ClaimTokenGenerator tokenGenerator;

    public EncryptedPendingPinRepository(
            PreferenceStore preferences,
            KeyProvider keyProvider,
            PendingPinCodec codec,
            AesGcmCipher cipher,
            ClaimTokenGenerator tokenGenerator) {
        this.preferences = preferences;
        this.keyProvider = keyProvider;
        this.codec = codec;
        this.cipher = cipher;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public synchronized void stage(int slotId, String pin) {
        validateSlot(slotId);
        validatePin(pin);
        try {
            PendingPinCodec.Payload payload = new PendingPinCodec.Payload(slotId, pin, "");
            byte[] sealed = cipher.encrypt(keyProvider.getOrCreate(), codec.encode(payload));
            boolean committed = preferences.edit()
                    .putString(recordKey(slotId), Base64.getEncoder().encodeToString(sealed))
                    .putString(stateKey(slotId), PendingPinState.STAGED.name())
                    .commit();
            requireCommit(committed);
        } catch (GeneralSecurityException | IOException | RuntimeException error) {
            throw persistenceFailure();
        }
    }

    @Override
    public synchronized PendingPinStatus status(int slotId) {
        validateSlot(slotId);
        return new PendingPinStatus(slotId, readState(slotId));
    }

    @Override
    public synchronized Optional<Claim> peek(int slotId) {
        validateSlot(slotId);
        try {
            boolean hasState = preferences.contains(stateKey(slotId));
            boolean hasRecord = preferences.contains(recordKey(slotId));
            if (!hasState && !hasRecord) {
                return Optional.empty();
            }
            if (!hasState) {
                throw readUnavailable();
            }
            PendingPinState state = PendingPinState.valueOf(
                    preferences.getString(stateKey(slotId), ""));
            if (state == PendingPinState.EMPTY) {
                if (hasRecord) {
                    throw readUnavailable();
                }
                return Optional.empty();
            }
            if (state != PendingPinState.STAGED) {
                return Optional.empty();
            }
            if (!hasRecord) {
                throw readUnavailable();
            }
            String value = preferences.getString(recordKey(slotId), "");
            SecretKey key = keyProvider.getIfPresent().orElseThrow();
            PendingPinCodec.Payload staged = codec.decode(
                    cipher.decrypt(key, Base64.getDecoder().decode(value)));
            validateStagedPayload(slotId, staged);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            String generation = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);
            return Optional.of(new Claim(staged.pin(), generation));
        } catch (Exception error) {
            throw readUnavailable();
        }
    }

    @Override
    public synchronized Optional<Claim> claim(int slotId) {
        validateSlot(slotId);
        if (readState(slotId) != PendingPinState.STAGED) {
            return Optional.empty();
        }

        try {
            String value = preferences.getString(recordKey(slotId), "");
            SecretKey key = keyProvider.getIfPresent().orElseThrow();
            PendingPinCodec.Payload staged = codec.decode(
                    cipher.decrypt(key, Base64.getDecoder().decode(value)));
            validateStagedPayload(slotId, staged);

            String token = tokenGenerator.nextToken();
            if (token == null || token.isEmpty()) {
                throw new GeneralSecurityException("invalid claim token");
            }
            PendingPinCodec.Payload claimed =
                    new PendingPinCodec.Payload(slotId, staged.pin(), token);
            byte[] sealed = cipher.encrypt(key, codec.encode(claimed));
            boolean committed = preferences.edit()
                    .putString(recordKey(slotId), Base64.getEncoder().encodeToString(sealed))
                    .putString(stateKey(slotId), PendingPinState.CLAIMED.name())
                    .commit();
            if (!committed) {
                quarantineBestEffort(slotId);
                return Optional.empty();
            }
            return Optional.of(new Claim(staged.pin(), token));
        } catch (Exception error) {
            quarantineOrThrow(slotId);
            return Optional.empty();
        }
    }

    @Override
    public synchronized boolean acknowledge(int slotId, String token) {
        validateSlot(slotId);
        if (token == null || token.isBlank()) {
            return false;
        }
        if (readState(slotId) != PendingPinState.CLAIMED) {
            return false;
        }

        try {
            String value = preferences.getString(recordKey(slotId), "");
            SecretKey key = keyProvider.getIfPresent().orElseThrow();
            PendingPinCodec.Payload payload = codec.decode(
                    cipher.decrypt(key, Base64.getDecoder().decode(value)));
            validateClaimedPayload(slotId, payload);
            boolean matches = MessageDigest.isEqual(
                    token.getBytes(StandardCharsets.UTF_8),
                    payload.claimToken().getBytes(StandardCharsets.UTF_8));
            if (!matches) {
                return false;
            }
            return preferences.edit()
                    .remove(recordKey(slotId))
                    .remove(stateKey(slotId))
                    .commit();
        } catch (Exception error) {
            quarantineOrThrow(slotId);
            return false;
        }
    }

    @Override
    public synchronized void clear(int slotId) {
        validateSlot(slotId);
        try {
            requireCommit(preferences.edit()
                    .remove(recordKey(slotId))
                    .remove(stateKey(slotId))
                    .commit());
        } catch (RuntimeException error) {
            throw persistenceFailure();
        }
    }

    @Override
    public synchronized void clearAll() {
        try {
            requireCommit(preferences.edit().clear().commit());
            keyProvider.delete();
        } catch (GeneralSecurityException | RuntimeException error) {
            throw persistenceFailure();
        }
    }

    private PendingPinState readState(int slotId) {
        try {
            boolean hasState = preferences.contains(stateKey(slotId));
            boolean hasRecord = preferences.contains(recordKey(slotId));
            if (!hasState) {
                if (!hasRecord) {
                    return PendingPinState.EMPTY;
                }
                return quarantineOrThrow(slotId);
            }

            PendingPinState state = PendingPinState.valueOf(
                    preferences.getString(stateKey(slotId), ""));
            if (state == PendingPinState.EMPTY) {
                return hasRecord ? quarantineOrThrow(slotId) : PendingPinState.EMPTY;
            }
            if (state == PendingPinState.STAGED && !hasRecord) {
                return quarantineOrThrow(slotId);
            }
            return state;
        } catch (ClassCastException | IllegalArgumentException | NullPointerException error) {
            return quarantineOrThrow(slotId);
        }
    }

    private static IllegalStateException readUnavailable() {
        return new IllegalStateException("pending PIN read unavailable");
    }

    private PendingPinState quarantineOrThrow(int slotId) {
        requireCommit(quarantine(slotId));
        return PendingPinState.CLAIMED;
    }

    private void quarantineBestEffort(int slotId) {
        try {
            quarantine(slotId);
        } catch (RuntimeException ignored) {
            // Claiming still fails closed if storage itself is unavailable.
        }
    }

    private boolean quarantine(int slotId) {
        return preferences.edit()
                .remove(recordKey(slotId))
                .putString(stateKey(slotId), PendingPinState.CLAIMED.name())
                .commit();
    }

    private static void validateStagedPayload(int slotId, PendingPinCodec.Payload payload)
            throws GeneralSecurityException {
        if (payload.slotId() != slotId
                || !isValidPin(payload.pin())
                || payload.claimToken() == null
                || !payload.claimToken().isEmpty()) {
            throw new GeneralSecurityException("invalid staged payload");
        }
    }

    private static void validateClaimedPayload(int slotId, PendingPinCodec.Payload payload)
            throws GeneralSecurityException {
        if (payload.slotId() != slotId
                || !isValidPin(payload.pin())
                || payload.claimToken() == null
                || payload.claimToken().isEmpty()) {
            throw new GeneralSecurityException("invalid claimed payload");
        }
    }

    private static void validatePin(String pin) {
        if (!isValidPin(pin)) {
            throw new IllegalArgumentException("PIN must contain 4 to 8 ASCII digits");
        }
    }

    private static boolean isValidPin(String pin) {
        return pin != null && PIN_PATTERN.matcher(pin).matches();
    }

    private static void validateSlot(int slotId) {
        if (slotId < 0 || slotId > 1) {
            throw new IllegalArgumentException("slot must be 0 or 1");
        }
    }

    private static void requireCommit(boolean committed) {
        if (!committed) {
            throw persistenceFailure();
        }
    }

    private static IllegalStateException persistenceFailure() {
        return new IllegalStateException("pending PIN persistence failed");
    }

    private static String recordKey(int slotId) {
        return RECORD_PREFIX + slotId;
    }

    private static String stateKey(int slotId) {
        return STATE_PREFIX + slotId;
    }
}
