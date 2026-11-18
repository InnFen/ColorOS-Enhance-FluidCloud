package com.howard.simpinautounlock.ui;

import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;
import com.howard.simpinautounlock.pending.PendingPinState;
import com.howard.simpinautounlock.pending.PendingPinStatus;

final class SlotActionPolicy {
    private SlotActionPolicy() {}

    static boolean canToggle(
            SaveRoutingPolicy.Availability availability,
            SlotStatus status) {
        if (availability != SaveRoutingPolicy.Availability.AVAILABLE || status == null) {
            return false;
        }
        SlotState state = status.state();
        return (state == SlotState.ARMED || state == SlotState.DISABLED)
                && status.currentIccidSuffix() != null
                && !status.currentIccidSuffix().isEmpty()
                && status.simMatches();
    }

    static boolean canClear(
            SaveRoutingPolicy.Availability availability,
            SlotStatus status,
            PendingPinStatus pendingStatus) {
        boolean hasPending = pendingStatus != null
                && pendingStatus.state() != null
                && pendingStatus.state() != PendingPinState.EMPTY;
        if (availability == SaveRoutingPolicy.Availability.UNAVAILABLE) {
            return hasPending;
        }
        return availability == SaveRoutingPolicy.Availability.AVAILABLE
                && (hasPending || (status != null && status.state() != SlotState.EMPTY));
    }
}
