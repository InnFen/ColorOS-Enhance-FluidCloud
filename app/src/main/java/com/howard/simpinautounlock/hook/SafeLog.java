package com.howard.simpinautounlock.hook;

import android.util.Log;
import io.github.libxposed.api.XposedModule;

public final class SafeLog {
    private static final String TAG = "SIMPinAutoUnlock";

    private static volatile XposedModule module;

    private SafeLog() {}

    static void attach(XposedModule value, String processName) {
        module = value;
    }

    public static void hookInstalled(String id) {
        log(Log.INFO, "hook installed id=" + id);
    }

    public static void slotState(int slotId, int bootCount, String transition) {
        log(Log.INFO, "slot=" + slotId + " boot=" + bootCount + " transition=" + transition);
    }

    public static void coordinatorState(int slotId, int bootCount, String event) {
        switch (event) {
            case "PENDING_IMPORTED",
                    "PENDING_ACKNOWLEDGED",
                    "PENDING_ACK_FAILED",
                    "PENDING_IMPORT_FAILED" -> pending(slotId, bootCount, event);
            default -> slotState(slotId, bootCount, event);
        }
    }

    public static void pending(int slotId, int bootCount, String event) {
        log(Log.INFO, "slot=" + slotId + " boot=" + bootCount + " pending=" + event);
    }

    public static void pendingBoundary(int slotId, String event) {
        log(Log.INFO, "slot=" + slotId + " pending-boundary=" + event);
    }

    static void pinBoundary(int slotId, PinStorageTraceEvent event) {
        log(Log.INFO, "slot=" + slotId + " pin-boundary=" + event.name());
    }

    static void runtimeBoundary(PinStorageTraceEvent event) {
        log(Log.INFO, "runtime-boundary=" + event.name());
    }

    static void pinStateBoundary(int slotId, String event) {
        log(Log.INFO, "slot=" + slotId + " pin-state=" + event);
    }

    public static void failure(String code) {
        log(Log.ERROR, "failure code=" + code);
    }

    static void featureState(boolean enabled, boolean suppressPinFlash) {
        log(Log.INFO, "feature-enabled=" + enabled + " suppress-pin-flash=" + suppressPinFlash);
    }

    private static void log(int priority, String message) {
        XposedModule current = module;
        if (current == null) {
            return;
        }
        try {
            current.log(priority, TAG, message);
        } catch (Throwable ignored) {
            // Logging must never affect the phone process.
        }
        try {
            // Independent sink for host-side early-boot capture if framework logs drop events.
            Log.println(priority, TAG, message);
        } catch (Throwable ignored) {
            // Logcat availability must not affect the phone process either.
        }
    }
}
