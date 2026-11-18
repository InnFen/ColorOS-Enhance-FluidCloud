package com.howard.simpinautounlock.hook;

import android.content.Context;
import com.howard.simpinautounlock.core.PendingPinSource;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

public final class PinStorageHookInstaller {
    private static boolean installed;

    private PinStorageHookInstaller() {}

    public static synchronized void install(
            XposedModule module,
            ClassLoader classLoader,
            PendingPinSource pendingPinSource) {
        if (installed) {
            return;
        }
        try {
            Class<?> pinStorage = Class.forName(
                    "com.android.internal.telephony.uicc.PinStorage", false, classLoader);
            Method getPin = pinStorage.getDeclaredMethod("getPin", int.class, String.class);
            getPin.setAccessible(true);
            module.hook(getPin)
                    .setId("dev.howard.colorosenhance.sim:get-pin")
                    .intercept(chain -> PinStorageInterceptorPolicy.intercept(
                            () -> (Integer) chain.getArg(0),
                            slotId -> SafeLog.pinBoundary(
                                    slotId, PinStorageTraceEvent.GET_PIN_ENTERED),
                            chain::proceed,
                            (slotId, originalObject) -> PinStorageResultPolicy.resolve(
                                    originalObject,
                                    event -> SafeLog.pinBoundary(slotId, event),
                                    original -> {
                                SafeLog.pinBoundary(
                                        slotId,
                                        PinStorageResultPolicy.runtimeEvent(
                                                PhoneRuntime.current() != null));
                                Context context = (Context) ReflectionAccess.field(
                                        chain.getThisObject(), "mContext");
                                PhoneRuntime runtime =
                                        PhoneRuntime.initialize(
                                                context, classLoader, pendingPinSource);
                                SafeLog.pinBoundary(
                                        slotId,
                                        PinStorageTraceEvent.PHONE_RUNTIME_READY_FROM_GET_PIN);
                                String iccid = (String) chain.getArg(1);
                                String pin = runtime.coordinator().maybeProvidePin(
                                        slotId, iccid, original);
                                if (!pin.isEmpty()) {
                                    PinStateHookInstaller.onPinProvided(slotId, iccid);
                                }
                                return pin;
                            }),
                            ignored -> SafeLog.failure("PIN_INTERCEPT_FAILED")));
            installed = true;
            SafeLog.hookInstalled("get-pin");
        } catch (Throwable error) {
            SafeLog.failure("PIN_HOOK_MISSING");
        }
    }
}
