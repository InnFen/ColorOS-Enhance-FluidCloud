package com.howard.simpinautounlock.storage;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class PreferenceBackedKeyProvider implements KeyProvider {
    static final String KEY_NAME = "remote.pending.aes.key";
    private static final int KEY_BYTES = 32;

    private final PreferenceStore preferences;
    private final SecureRandom random;

    public PreferenceBackedKeyProvider(PreferenceStore preferences) {
        this(preferences, new SecureRandom());
    }

    PreferenceBackedKeyProvider(PreferenceStore preferences, SecureRandom random) {
        this.preferences = preferences;
        this.random = random;
    }

    @Override
    public synchronized SecretKey getOrCreate() throws GeneralSecurityException {
        Optional<SecretKey> existing = getIfPresent();
        if (existing.isPresent()) {
            return existing.get();
        }
        byte[] keyBytes = new byte[KEY_BYTES];
        random.nextBytes(keyBytes);
        boolean committed = preferences.edit()
                .putString(KEY_NAME, Base64.getEncoder().encodeToString(keyBytes))
                .commit();
        if (!committed) {
            throw new GeneralSecurityException("shared pending key persistence failed");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public synchronized Optional<SecretKey> getIfPresent() throws GeneralSecurityException {
        if (!preferences.contains(KEY_NAME)) {
            return Optional.empty();
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(preferences.getString(KEY_NAME, ""));
            if (keyBytes.length != KEY_BYTES) {
                throw new GeneralSecurityException("invalid shared pending key");
            }
            return Optional.of(new SecretKeySpec(keyBytes, "AES"));
        } catch (IllegalArgumentException | ClassCastException error) {
            throw new GeneralSecurityException("invalid shared pending key", error);
        }
    }

    @Override
    public synchronized void delete() throws GeneralSecurityException {
        if (!preferences.edit().remove(KEY_NAME).commit()) {
            throw new GeneralSecurityException("shared pending key deletion failed");
        }
    }
}
