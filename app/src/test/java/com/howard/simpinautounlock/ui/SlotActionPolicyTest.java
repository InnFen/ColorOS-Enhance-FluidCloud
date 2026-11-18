package com.howard.simpinautounlock.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;
import com.howard.simpinautounlock.pending.PendingPinState;
import com.howard.simpinautounlock.pending.PendingPinStatus;
import org.junit.Test;

public final class SlotActionPolicyTest {
    @Test
    public void unreadableCurrentSimKeepsClearButSuppressesToggle() {
        for (SlotState state : new SlotState[] {SlotState.ARMED, SlotState.DISABLED}) {
            SlotStatus status = status(state, "", false);

            assertTrue(
                    state.name(),
                    SlotActionPolicy.canClear(
                            SaveRoutingPolicy.Availability.AVAILABLE,
                            status,
                            pending(PendingPinState.EMPTY)));
            assertFalse(
                    state.name(),
                    SlotActionPolicy.canToggle(
                            SaveRoutingPolicy.Availability.AVAILABLE, status));
        }
    }

    @Test
    public void matchedCurrentSimAllowsExistingArmedAndDisabledActions() {
        for (SlotState state : new SlotState[] {SlotState.ARMED, SlotState.DISABLED}) {
            SlotStatus status = status(state, "1234", true);

            assertTrue(
                    state.name(),
                    SlotActionPolicy.canClear(
                            SaveRoutingPolicy.Availability.AVAILABLE,
                            status,
                            pending(PendingPinState.EMPTY)));
            assertTrue(
                    state.name(),
                    SlotActionPolicy.canToggle(
                            SaveRoutingPolicy.Availability.AVAILABLE, status));
        }
    }

    @Test
    public void unavailablePhoneDisablesToggleEvenForMatchedArmedSlot() {
        assertFalse(SlotActionPolicy.canToggle(
                SaveRoutingPolicy.Availability.UNAVAILABLE,
                status(SlotState.ARMED, "1234", true)));
    }

    @Test
    public void unavailablePhoneAllowsClearOnlyForLocalPendingRecord() {
        assertFalse(SlotActionPolicy.canClear(
                SaveRoutingPolicy.Availability.UNAVAILABLE,
                status(SlotState.ARMED, "1234", true),
                pending(PendingPinState.EMPTY)));
        assertTrue(SlotActionPolicy.canClear(
                SaveRoutingPolicy.Availability.UNAVAILABLE,
                null,
                pending(PendingPinState.STAGED)));
        assertTrue(SlotActionPolicy.canClear(
                SaveRoutingPolicy.Availability.UNAVAILABLE,
                null,
                pending(PendingPinState.CLAIMED)));
    }

    @Test
    public void availablePhoneAllowsClearForEitherFormalOrPendingRecord() {
        assertTrue(SlotActionPolicy.canClear(
                SaveRoutingPolicy.Availability.AVAILABLE,
                status(SlotState.EMPTY, "1234", false),
                pending(PendingPinState.STAGED)));
        assertTrue(SlotActionPolicy.canClear(
                SaveRoutingPolicy.Availability.AVAILABLE,
                status(SlotState.DISABLED, "1234", true),
                pending(PendingPinState.EMPTY)));
        assertFalse(SlotActionPolicy.canClear(
                SaveRoutingPolicy.Availability.AVAILABLE,
                status(SlotState.EMPTY, "1234", false),
                pending(PendingPinState.EMPTY)));
    }

    private static SlotStatus status(
            SlotState state,
            String currentSuffix,
            boolean simMatches) {
        return new SlotStatus(
                0,
                state,
                "1234",
                currentSuffix,
                simMatches,
                SlotRecord.RESULT_UNKNOWN,
                -1,
                42);
    }

    private static PendingPinStatus pending(PendingPinState state) {
        return new PendingPinStatus(0, state);
    }
}
