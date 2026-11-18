package com.howard.simpinautounlock.hook;

import android.content.Context;
import android.os.Handler;
import com.howard.simpinautounlock.core.AutoUnlockCoordinator;
import com.howard.simpinautounlock.core.CredentialRepository;
import com.howard.simpinautounlock.core.PendingPinSource;
import com.howard.simpinautounlock.ipc.PhoneCommandHandler;
import com.howard.simpinautounlock.ipc.PhoneCommandReceiver;
import com.howard.simpinautounlock.ipc.PhoneFactorySimIdentityProvider;
import com.howard.simpinautounlock.ipc.PhoneReceiverRegistrar;
import com.howard.simpinautounlock.pending.ConsumingPendingPinSource;
import com.howard.simpinautounlock.pending.PendingPinConsumptionStore;
import com.howard.simpinautounlock.storage.AesGcmCipher;
import com.howard.simpinautounlock.storage.AndroidKeystoreKeyProvider;
import com.howard.simpinautounlock.storage.AndroidPreferenceStore;
import com.howard.simpinautounlock.storage.CredentialCodec;
import com.howard.simpinautounlock.storage.EncryptedCredentialRepository;

public final class PhoneRuntime {
    private static boolean featureEnabled;
    private static final RetainedRegistrationGate<PhoneRuntime> RUNTIME =
            new RetainedRegistrationGate<>();

    private final Context deviceContext;
    private final CredentialRepository repository;
    private final AutoUnlockCoordinator coordinator;
    private final PhoneCommandReceiver receiver;

    private PhoneRuntime(
            Context deviceContext,
            CredentialRepository repository,
            AutoUnlockCoordinator coordinator,
            PhoneCommandReceiver receiver) {
        this.deviceContext = deviceContext;
        this.repository = repository;
        this.coordinator = coordinator;
        this.receiver = receiver;
    }

    public static synchronized PhoneRuntime initialize(
            Context context,
            ClassLoader classLoader,
            PendingPinSource pendingPinSource) {
        return RUNTIME.initialize(
                () -> {
                    Context deviceContext = context.createDeviceProtectedStorageContext();
                    CredentialRepository repository = new EncryptedCredentialRepository(
                            new AndroidPreferenceStore(deviceContext),
                            new AndroidKeystoreKeyProvider(),
                            new CredentialCodec(),
                            new AesGcmCipher());
                    AndroidBootEnvironment boot = new AndroidBootEnvironment(deviceContext);
                    HandlerTimeoutScheduler scheduler = new HandlerTimeoutScheduler(
                            new Handler(deviceContext.getMainLooper()));
                    PendingPinSource guardedPendingPinSource = pendingPinSource == PendingPinSource.NONE
                            ? pendingPinSource
                            : new ConsumingPendingPinSource(
                                    pendingPinSource,
                                    new PendingPinConsumptionStore(
                                            new AndroidPreferenceStore(
                                                    deviceContext,
                                                    com.howard.simpinautounlock.storage.PhoneCredentialStorage.CONSUMPTION)));
                    AutoUnlockCoordinator coordinator =
                            new AutoUnlockCoordinator(
                                    repository,
                                    boot,
                                    scheduler,
                                    SafeLog::coordinatorState,
                                    guardedPendingPinSource);
                    coordinator.setFeatureEnabled(featureEnabled);
                    PhoneCommandHandler commandHandler = new PhoneCommandHandler(
                            repository, new PhoneFactorySimIdentityProvider(classLoader));
                    PhoneCommandReceiver receiver = new PhoneCommandReceiver(commandHandler);
                    return new PhoneRuntime(
                            deviceContext, repository, coordinator, receiver);
                },
                runtime -> PhoneReceiverRegistrar.register(
                        runtime.deviceContext, runtime.receiver));
    }

    static synchronized void setFeatureEnabled(boolean enabled) {
        featureEnabled = enabled;
        PhoneRuntime runtime = RUNTIME.current();
        if (runtime != null) {
            runtime.coordinator.setFeatureEnabled(enabled);
        }
    }

    static synchronized PhoneRuntime current() {
        return RUNTIME.current();
    }

    public AutoUnlockCoordinator coordinator() {
        return coordinator;
    }
}
