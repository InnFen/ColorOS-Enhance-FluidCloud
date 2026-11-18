package com.howard.simpinautounlock.pending;

import android.content.SharedPreferences;
import com.howard.simpinautounlock.storage.AesGcmCipher;
import com.howard.simpinautounlock.storage.AndroidPreferenceStore;
import com.howard.simpinautounlock.storage.EncryptedPendingPinRepository;
import com.howard.simpinautounlock.storage.PendingPinCodec;
import com.howard.simpinautounlock.storage.PreferenceBackedKeyProvider;

public final class RemotePendingPinRepositoryFactory {
    public static final String DATA_GROUP = "dev.howard.colorosenhance.sim_pending_data_v1";
    public static final String KEY_GROUP = "dev.howard.colorosenhance.sim_pending_key_v1";
    public static final String META_GROUP = "dev.howard.colorosenhance.sim_pending_meta_v1";
    public static final String CHANNEL_VERSION_KEY = "channel.version";
    public static final int CHANNEL_VERSION = 1;

    private RemotePendingPinRepositoryFactory() {}

    public static PendingPinRepository create(
            SharedPreferences dataPreferences,
            SharedPreferences keyPreferences) {
        return new EncryptedPendingPinRepository(
                new AndroidPreferenceStore(dataPreferences),
                new PreferenceBackedKeyProvider(new AndroidPreferenceStore(keyPreferences)),
                new PendingPinCodec(),
                new AesGcmCipher(),
                new SecureClaimTokenGenerator());
    }
}
