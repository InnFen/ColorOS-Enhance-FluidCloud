package com.howard.simpinautounlock.core;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public final class SimFeatureGateTest {
    private final Map<Integer, SlotRecord> records = new HashMap<>();
    private final CredentialRepository repository = new CredentialRepository() {
        public Optional<SlotRecord> load(int slot) { return Optional.ofNullable(records.get(slot)); }
        public void save(SlotRecord record) { records.put(record.slotId(), record); }
        public void clear(int slot) { records.remove(slot); }
        public void clearAll() { records.clear(); }
    };
    private final BootEnvironment boot = new BootEnvironment() {
        public int bootCount() { return 8; }
        public boolean bootCompleted() { return false; }
    };
    private int claims;
    private final PendingPinSource source = new PendingPinSource() {
        public Optional<Claim> claim(int slot) { claims++; return Optional.of(new Claim("2468", "synthetic")); }
        public boolean acknowledge(int slot, String token) { return true; }
    };
    @Test public void defaultOffNeverSuppliesOrImportsPin() {
        records.put(0, SlotRecord.armed(0, "synthetic-card", "1234"));
        AutoUnlockCoordinator coordinator = new AutoUnlockCoordinator(repository, boot,
                (task, delay) -> true, SlotStateLogger.NO_OP, source);
        assertEquals("", coordinator.maybeProvidePin(0, "synthetic-card", ""));
        assertEquals(0, claims);
        assertEquals(SlotState.ARMED, records.get(0).state());
    }
    @Test public void defaultOffNeverDefersNativeState() {
        PinStatePublicationGate gate = new PinStatePublicationGate(() -> 0L);
        gate.onPinProvided(0, "synthetic-card");
        assertFalse(gate.deferPinRequired(0, "synthetic-card", new Object(),
                (task, delay) -> true, () -> {}));
    }
    @Test public void disablingReleasesDeferredEvaluationOnlyOnce() {
        PinStatePublicationGate gate = new PinStatePublicationGate(() -> 0L);
        gate.setEnabled(true);
        gate.onPinProvided(0, "synthetic-card");
        java.util.List<Runnable> timers = new java.util.ArrayList<>();
        int[] refreshes = {0};
        assertTrue(gate.deferPinRequired(0, "synthetic-card", new Object(),
                (task, delay) -> { timers.add(task); return true; }, () -> refreshes[0]++));
        gate.setEnabled(false);
        assertEquals(1, refreshes[0]);
        gate.setEnabled(true);
        timers.get(0).run();
        assertEquals(1, refreshes[0]);
        assertFalse(gate.deferPinRequired(0, "synthetic-card", new Object(),
                (task, delay) -> true, () -> refreshes[0]++));
    }
    @Test public void togglingPreservesAttemptAndFinishesPendingResult() {
        records.put(0, SlotRecord.armed(0, "synthetic-card", "1234"));
        AutoUnlockCoordinator coordinator = new AutoUnlockCoordinator(repository, boot,
                (task, delay) -> true);
        coordinator.setFeatureEnabled(true);
        assertEquals("1234", coordinator.maybeProvidePin(0, "synthetic-card", ""));
        coordinator.setFeatureEnabled(false);
        coordinator.onSupplyResult(0, true, 3);
        assertEquals(SlotState.ARMED, records.get(0).state());
        assertEquals(8, records.get(0).lastAttemptBootCount());
        coordinator.setFeatureEnabled(true);
        assertEquals("", coordinator.maybeProvidePin(0, "synthetic-card", ""));
    }
    @Test public void disablingStillAllowsPendingTimeoutToRequireConfirmation() {
        records.put(0, SlotRecord.armed(0, "synthetic-card", "1234"));
        java.util.List<Runnable> timers = new java.util.ArrayList<>();
        AutoUnlockCoordinator coordinator = new AutoUnlockCoordinator(repository, boot,
                (task, delay) -> { timers.add(task); return true; });
        coordinator.setFeatureEnabled(true);
        assertEquals("1234", coordinator.maybeProvidePin(0, "synthetic-card", ""));
        coordinator.setFeatureEnabled(false);
        timers.get(0).run();
        assertEquals(SlotState.NEEDS_CONFIRMATION, records.get(0).state());
        assertEquals(8, records.get(0).lastAttemptBootCount());
    }
    @Test public void disabledModePreservesNativePinWithoutClaiming() {
        AutoUnlockCoordinator coordinator = new AutoUnlockCoordinator(repository, boot,
                (task, delay) -> true, SlotStateLogger.NO_OP, source);
        assertEquals("9876", coordinator.maybeProvidePin(0, "synthetic-card", "9876"));
        assertEquals(0, claims);
    }
}
