package com.howard.simpinautounlock.hook;

import android.os.Handler;
import android.os.SystemClock;
import com.howard.simpinautounlock.core.PinStatePublicationGate;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Defers only the redundant PIN_REQUIRED publication from our own automatic attempt. */
public final class PinStateHookInstaller {
    private static final PinStatePublicationGate GATE =
            new PinStatePublicationGate(SystemClock::uptimeMillis);
    private static volatile boolean installed;

    private PinStateHookInstaller() {}

    @android.annotation.SuppressLint("BlockedPrivateApi")
    public static synchronized void install(XposedModule module, ClassLoader loader) {
        if (installed) {
            return;
        }
        try {
            Class<?> profileClass = Class.forName(
                    "com.android.internal.telephony.uicc.UiccProfile", false, loader);
            Class<?> stateClass = Class.forName(
                    "com.android.internal.telephony.IccCardConstants$State", false, loader);
            Class<?> recordsClass = Class.forName(
                    "com.android.internal.telephony.uicc.IccRecords", false, loader);
            Method setState = profileClass.getDeclaredMethod(
                    "setExternalState", stateClass, boolean.class);
            Method refresh = profileClass.getDeclaredMethod("updateExternalState");
            Method fullIccid = recordsClass.getMethod("getFullIccId");
            Field phoneId = profileClass.getDeclaredField("mPhoneId");
            Field records = profileClass.getDeclaredField("mIccRecords");
            Field handler = profileClass.getDeclaredField("mHandler");
            Field disposed = profileClass.getDeclaredField("mDisposed");
            Field lock = profileClass.getDeclaredField("mLock");
            setState.setAccessible(true);
            refresh.setAccessible(true);
            fullIccid.setAccessible(true);
            phoneId.setAccessible(true);
            records.setAccessible(true);
            handler.setAccessible(true);
            disposed.setAccessible(true);
            lock.setAccessible(true);
            module.hook(setState)
                    .setId("dev.howard.colorosenhance.sim:pin-state")
                    .intercept(chain -> PinStateInterceptorPolicy.intercept(() -> {
                        try {
                            Object profile = chain.getThisObject();
                            int slotId = phoneId.getInt(profile);
                            String state = ((Enum<?>) chain.getArg(0)).name();
                            if (!"PIN_REQUIRED".equals(state)) {
                                GATE.onStateChanged(slotId, state);
                                if ("READY".equals(state) || "LOADED".equals(state)) {
                                    SafeLog.pinStateBoundary(slotId, "SIM_READY_OBSERVED");
                                }
                                return false;
                            }
                            Object currentRecords = records.get(profile);
                            String iccid = currentRecords == null
                                    ? null : (String) fullIccid.invoke(currentRecords);
                            Handler profileHandler = (Handler) handler.get(profile);
                            boolean defer = GATE.deferPinRequired(
                                    slotId, iccid, profile, new HandlerTimeoutScheduler(profileHandler),
                                    () -> profileHandler.post(() -> refreshState(profile, refresh, disposed, lock, slotId)));
                            SafeLog.pinStateBoundary(slotId,
                                    defer ? "PIN_REQUIRED_DEFERRED" : "PIN_REQUIRED_PASSTHROUGH");
                            return defer;
                        } catch (Throwable ignored) {
                            SafeLog.failure("PIN_STATE_INTERCEPT_FAILED");
                            return false;
                        }
                    }, chain::proceed));
            installed = true;
            SafeLog.hookInstalled("pin-state");
        } catch (Throwable ignored) {
            SafeLog.failure("PIN_STATE_HOOK_MISSING");
        }
    }

    static void setSuppressionEnabled(boolean enabled) {
        GATE.setEnabled(enabled);
    }

    static void onPinProvided(int slotId, String iccid) {
        if (!installed) {
            return;
        }
        try {
            GATE.onPinProvided(slotId, iccid);
            SafeLog.pinStateBoundary(slotId, "AUTO_PIN_WINDOW_STARTED");
        } catch (Throwable ignored) {
            SafeLog.failure("PIN_STATE_WINDOW_FAILED");
        }
    }

    static void onSupplyResult(int slotId, boolean success) {
        if (!installed) {
            return;
        }
        try {
            GATE.onSupplyResult(slotId, success);
        } catch (Throwable ignored) {
            SafeLog.failure("PIN_STATE_RESULT_FAILED");
        }
    }

    private static void refreshState(
            Object profile, Method refresh, Field disposed, Field lock, int slotId) {
        try {
            PinStateInterceptorPolicy.refreshIfActive(
                    lock.get(profile), () -> disposed.getBoolean(profile), () -> {
                        SafeLog.pinStateBoundary(slotId, "DEFERRED_STATE_REFRESH");
                        return refresh.invoke(profile);
                    });
        } catch (Throwable ignored) {
            SafeLog.failure("PIN_STATE_REFRESH_FAILED");
        }
    }
}
