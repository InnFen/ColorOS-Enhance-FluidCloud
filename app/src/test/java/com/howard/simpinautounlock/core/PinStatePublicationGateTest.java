package com.howard.simpinautounlock.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class PinStatePublicationGateTest {
    private long now;
    private final PinStatePublicationGate gate = new PinStatePublicationGate(() -> now);
    private final List<Runnable> timers = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private final Object profile0 = new Object();
    private final Object profile1 = new Object();
    private int refreshes;
    private final TimeoutScheduler scheduler = (task, delay) -> {
        timers.add(task);
        delays.add(delay);
        return true;
    };

    private boolean defer(int slot, String iccid) {
        return gate.deferPinRequired(
                slot, iccid, slot == 1 ? profile1 : profile0, scheduler, () -> refreshes++);
    }

    @org.junit.Before public void enableSuppression() { gate.setEnabled(true); }

    @Test public void normalManualPinPageIsNeverDeferred() {
        assertFalse(defer(0, "sim-a"));
        assertTrue(timers.isEmpty());
    }

    @Test public void boot58RepeatWhilePendingIsDeferredUntilRealReady() {
        now = 764;
        gate.onPinProvided(1, "sim-b");
        now = 951;
        assertTrue(defer(1, "sim-b"));
        now = 1508;
        gate.onSupplyResult(1, true);
        assertTrue(defer(1, "sim-b"));
        assertEquals(1, timers.size());
        now = 4601;
        gate.onStateChanged(1, "READY");
        now = 5764;
        timers.get(0).run();
        assertEquals(0, refreshes);
        assertFalse(defer(1, "sim-b"));
    }

    @Test public void boot58RepeatAfterSuccessIsAlsoDeferred() {
        now = 734;
        gate.onPinProvided(0, "sim-a");
        now = 894;
        gate.onSupplyResult(0, true);
        now = 921;
        assertTrue(defer(0, "sim-a"));
        assertEquals(Long.valueOf(4813), delays.get(0));
    }

    @Test public void failureImmediatelyRefreshesDeferredStateAndCancelsTimer() {
        gate.onPinProvided(0, "sim-a");
        assertTrue(defer(0, "sim-a"));
        gate.onSupplyResult(0, false);
        assertEquals(1, refreshes);
        assertFalse(defer(0, "sim-a"));
        now = 5000;
        timers.get(0).run();
        assertEquals(1, refreshes);
    }

    @Test public void failureBeforeFirstLockedPublicationDoesNotLeaveGuard() {
        gate.onPinProvided(0, "sim-a");
        gate.onSupplyResult(0, false);
        assertFalse(defer(0, "sim-a"));
        assertEquals(0, refreshes);
    }

    @Test public void timeoutReevaluatesCurrentStateAfterRemovingGuard() {
        gate.onPinProvided(0, "sim-a");
        now = 100;
        assertTrue(gate.deferPinRequired(0, "sim-a", profile0, scheduler, () -> {
            refreshes++;
            assertFalse(defer(0, "sim-a"));
        }));
        assertEquals(Long.valueOf(4900), delays.get(0));
        now = 5000;
        timers.get(0).run();
        timers.get(0).run();
        assertEquals(1, refreshes);
    }

    @Test public void repeatedQueriesAndSuccessNeverExtendDeadline() {
        gate.onPinProvided(0, "sim-a");
        now = 4999;
        gate.onPinProvided(0, "sim-a");
        gate.onSupplyResult(0, true);
        assertTrue(defer(0, "sim-a"));
        assertEquals(Long.valueOf(1), delays.get(0));
        now = 5000;
        assertFalse(defer(0, "sim-a"));
    }

    @Test public void slotsAreIndependent() {
        gate.onPinProvided(0, "sim-a");
        gate.onPinProvided(1, "sim-b");
        assertTrue(defer(0, "sim-a"));
        assertTrue(defer(1, "sim-b"));
        gate.onSupplyResult(0, false);
        assertFalse(defer(0, "sim-a"));
        assertTrue(defer(1, "sim-b"));
        assertEquals(1, refreshes);
    }

    @Test public void changedSimCannotInheritDeferralOrOldRefresh() {
        gate.onPinProvided(0, "sim-a");
        assertTrue(defer(0, "sim-a"));
        assertFalse(defer(0, "sim-replaced"));
        now = 5000;
        timers.get(0).run();
        assertEquals(0, refreshes);
        assertFalse(defer(0, "sim-a"));
    }

    @Test public void readyLoadedAndErrorStatesCancelDeferralWithoutReplayingLockedState() {
        for (String state : List.of("READY", "LOADED", "PUK_REQUIRED", "ABSENT",
                "PERM_DISABLED", "CARD_IO_ERROR", "CARD_RESTRICTED", "NETWORK_LOCKED", "UNKNOWN")) {
            gate.onPinProvided(0, "sim-a");
            assertTrue(defer(0, "sim-a"));
            gate.onStateChanged(0, state);
            assertFalse(state, defer(0, "sim-a"));
        }
        now = 5000;
        timers.forEach(Runnable::run);
        assertEquals(0, refreshes);
    }

    @Test public void transientNotReadyDoesNotEndTheBoundedWait() {
        gate.onPinProvided(0, "sim-a");
        gate.onStateChanged(0, "NOT_READY");
        assertTrue(defer(0, "sim-a"));
    }

    @Test public void rejectedOrThrowingSchedulerLeavesManualPageAvailable() {
        gate.onPinProvided(0, "sim-a");
        assertFalse(gate.deferPinRequired(0, "sim-a", profile0, (task, delay) -> false, () -> {}));
        assertFalse(defer(0, "sim-a"));
        gate.onPinProvided(0, "sim-a");
        assertFalse(gate.deferPinRequired(0, "sim-a", profile0, (task, delay) -> {
            throw new IllegalStateException("handler unavailable");
        }, () -> {}));
        assertFalse(defer(0, "sim-a"));
    }

    @Test public void timerFromAnOlderAttemptDoesNotEndNewAttempt() {
        gate.onPinProvided(0, "sim-a");
        assertTrue(defer(0, "sim-a"));
        gate.onStateChanged(0, "ABSENT");
        now = 100;
        gate.onPinProvided(0, "sim-b");
        assertTrue(defer(0, "sim-b"));
        now = 5000;
        timers.get(0).run();
        assertTrue(defer(0, "sim-b"));
        assertEquals(0, refreshes);
    }

    @Test public void invalidSlotsOrMissingIdentityCannotDefer() {
        gate.onPinProvided(-1, "sim-a");
        gate.onPinProvided(2, "sim-a");
        gate.onPinProvided(0, "");
        gate.onPinProvided(1, null);
        assertFalse(defer(-1, "sim-a"));
        assertFalse(defer(2, "sim-a"));
        assertFalse(defer(0, ""));
        assertFalse(defer(1, null));
    }

    @Test public void replacementProfileForSameSimCannotReuseOldDeferredCallback() {
        gate.onPinProvided(0, "sim-a");
        assertTrue(defer(0, "sim-a"));
        assertFalse(gate.deferPinRequired(
                0, "sim-a", new Object(), scheduler, () -> refreshes++));
        now = 5000;
        timers.get(0).run();
        assertEquals(0, refreshes);
        assertFalse(defer(0, "sim-a"));
    }
}
