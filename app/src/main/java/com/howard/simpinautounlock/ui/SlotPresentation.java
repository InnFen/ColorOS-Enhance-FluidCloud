package com.howard.simpinautounlock.ui;

import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;
import com.howard.simpinautounlock.pending.PendingPinState;
import com.howard.simpinautounlock.pending.PendingPinStatus;

public record SlotPresentation(
        String displayText,
        boolean toggleEnabled,
        boolean toggleState,
        boolean clearEnabled,
        boolean pendingWarningVisible) {
    public static SlotPresentation create(
            SaveRoutingPolicy.Availability availability,
            SlotStatus phoneStatus,
            PendingPinStatus pendingStatus) {
        PendingPinState pendingState = pendingState(pendingStatus);
        boolean phoneAvailable = availability == SaveRoutingPolicy.Availability.AVAILABLE;
        return new SlotPresentation(
                UiTextFormatter.mergedStatus(availability, phoneStatus, pendingStatus),
                SlotActionPolicy.canToggle(availability, phoneStatus),
                phoneAvailable
                        && phoneStatus != null
                        && phoneStatus.state() == SlotState.ARMED,
                SlotActionPolicy.canClear(availability, phoneStatus, pendingStatus),
                pendingState == PendingPinState.STAGED
                        || pendingState == PendingPinState.CLAIMED);
    }

    private static PendingPinState pendingState(PendingPinStatus pendingStatus) {
        return pendingStatus == null || pendingStatus.state() == null
                ? PendingPinState.EMPTY
                : pendingStatus.state();
    }
}
