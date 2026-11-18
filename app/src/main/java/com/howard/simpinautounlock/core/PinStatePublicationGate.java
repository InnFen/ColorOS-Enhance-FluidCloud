package com.howard.simpinautounlock.core;

import java.util.function.LongSupplier;

/** A short, per-SIM publication window, never a source of PINs or synthetic READY states. */
public final class PinStatePublicationGate {
    private static final long MAX_WAIT_MILLIS = 5_000L;
    private boolean enabled;
    private final LongSupplier uptimeMillis;
    private final Attempt[] attempts = new Attempt[2];

    public PinStatePublicationGate(LongSupplier uptimeMillis) {
        this.uptimeMillis = uptimeMillis;
    }

    public void setEnabled(boolean enabled) {
        java.util.List<Runnable> refreshes = new java.util.ArrayList<>();
        synchronized (this) {
            this.enabled = enabled;
            if (!enabled) {
                for (int slot = 0; slot < attempts.length; slot++) {
                    Attempt attempt = attempts[slot];
                    attempts[slot] = null;
                    if (attempt != null && attempt.refresh != null) {
                        refreshes.add(attempt.refresh);
                    }
                }
            }
        }
        // Clear every guard before re-evaluating native state; an old timer becomes a no-op.
        for (Runnable refresh : refreshes) {
            try {
                refresh.run();
            } catch (Throwable ignored) {
                // One failed/disposed profile must not prevent the other slot being released.
            }
        }
    }

    public synchronized void onPinProvided(int slotId, String iccid) {
        if (!enabled || !validSlot(slotId) || iccid == null || iccid.isEmpty()) {
            return;
        }
        Attempt current = attempts[slotId];
        if (current == null || !current.iccid.equals(iccid)) {
            attempts[slotId] = new Attempt(iccid, uptimeMillis.getAsLong() + MAX_WAIT_MILLIS);
        }
    }

    public synchronized boolean deferPinRequired(
            int slotId, String iccid, Object profile,
            TimeoutScheduler scheduler, Runnable refreshCurrentState) {
        if (!enabled || !validSlot(slotId)) {
            return false;
        }
        Attempt attempt = attempts[slotId];
        if (attempt == null) {
            return false;
        }
        long remaining = attempt.deadline - uptimeMillis.getAsLong();
        if (!attempt.iccid.equals(iccid) || remaining <= 0
                || (attempt.profile != null && attempt.profile != profile)) {
            attempts[slotId] = null;
            return false;
        }
        if (attempt.refresh == null) {
            attempt.profile = profile;
            attempt.refresh = refreshCurrentState;
            try {
                if (!scheduler.schedule(() -> expire(slotId, attempt), remaining)) {
                    attempts[slotId] = null;
                    return false;
                }
            } catch (Throwable ignored) {
                attempts[slotId] = null;
                return false;
            }
        }
        return attempts[slotId] == attempt;
    }

    public void onSupplyResult(int slotId, boolean success) {
        if (success) {
            // Success is not APPSTATE_READY: cached APPSTATE_PIN can still be evaluated again.
            return;
        }
        Runnable refresh = null;
        synchronized (this) {
            if (validSlot(slotId) && attempts[slotId] != null) {
                refresh = attempts[slotId].refresh;
                attempts[slotId] = null;
            }
        }
        if (refresh != null) {
            refresh.run();
        }
    }

    public synchronized void onStateChanged(int slotId, String state) {
        if (validSlot(slotId) && !"PIN_REQUIRED".equals(state) && !"NOT_READY".equals(state)) {
            attempts[slotId] = null;
        }
    }

    private void expire(int slotId, Attempt expected) {
        Runnable refresh;
        synchronized (this) {
            if (attempts[slotId] != expected) {
                return;
            }
            attempts[slotId] = null;
            refresh = expected.refresh;
        }
        // Refresh the current card status, not a saved PIN_REQUIRED request. Guard is gone first.
        refresh.run();
    }

    private static boolean validSlot(int slotId) {
        return slotId >= 0 && slotId < 2;
    }

    private static final class Attempt {
        final String iccid;
        final long deadline;
        Object profile;
        Runnable refresh;

        Attempt(String iccid, long deadline) {
            this.iccid = iccid;
            this.deadline = deadline;
        }
    }
}
