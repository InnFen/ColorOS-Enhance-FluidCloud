package com.howard.simpinautounlock.storage;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class AndroidKeystoreKeyProvider implements KeyProvider {
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private final String keyAlias;

    public AndroidKeystoreKeyProvider() {
        this(PhoneCredentialStorage.KEY_ALIAS);
    }

    public AndroidKeystoreKeyProvider(String keyAlias) {
        this.keyAlias = Objects.requireNonNull(keyAlias);
    }

    @Override
    public SecretKey getOrCreate() throws GeneralSecurityException {
        Optional<SecretKey> existing = getIfPresent();
        if (existing.isPresent()) {
            return existing.get();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setUnlockedDeviceRequired(false)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    @Override
    public Optional<SecretKey> getIfPresent() throws GeneralSecurityException {
        KeyStore keyStore = loadKeyStore();
        if (!keyStore.containsAlias(keyAlias)) {
            return Optional.empty();
        }
        Key key = keyStore.getKey(keyAlias, null);
        if (!(key instanceof SecretKey)) {
            throw new GeneralSecurityException("invalid credential key");
        }
        return Optional.of((SecretKey) key);
    }

    @Override
    public void delete() throws GeneralSecurityException {
        KeyStore keyStore = loadKeyStore();
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias);
        }
    }

    private static KeyStore loadKeyStore() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        try {
            keyStore.load(null);
        } catch (IOException error) {
            throw new GeneralSecurityException("credential key store unavailable");
        }
        return keyStore;
    }
}
