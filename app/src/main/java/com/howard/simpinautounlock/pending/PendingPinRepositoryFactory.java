package com.howard.simpinautounlock.pending;

import android.content.Context;
import com.howard.simpinautounlock.storage.AesGcmCipher;
import com.howard.simpinautounlock.storage.AndroidKeystoreKeyProvider;
import com.howard.simpinautounlock.storage.AndroidPreferenceStore;
import com.howard.simpinautounlock.storage.EncryptedPendingPinRepository;
import com.howard.simpinautounlock.storage.PendingPinCodec;
import java.util.Objects;

public final class PendingPinRepositoryFactory {
    static final String PREFERENCE_NAME = "dev.howard.colorosenhance.sim_pending";
    static final String KEY_ALIAS = "dev.howard.colorosenhance.sim.pending.aes.v1";

    private static PendingPinRepository instance;

    private PendingPinRepositoryFactory() {}

    public static synchronized PendingPinRepository create(Context context) {
        if (instance == null) {
            Context deviceContext = Objects.requireNonNull(context)
                    .createDeviceProtectedStorageContext();
            instance = new EncryptedPendingPinRepository(
                    new AndroidPreferenceStore(deviceContext, PREFERENCE_NAME),
                    new AndroidKeystoreKeyProvider(KEY_ALIAS),
                    new PendingPinCodec(),
                    new AesGcmCipher(),
                    new SecureClaimTokenGenerator());
        }
        return instance;
    }
}
