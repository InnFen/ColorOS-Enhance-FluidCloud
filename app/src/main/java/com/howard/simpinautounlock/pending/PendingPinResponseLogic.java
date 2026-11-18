package com.howard.simpinautounlock.pending;

import com.howard.simpinautounlock.core.PendingPinSource;
import java.util.Optional;

final class PendingPinResponseLogic {
    Optional<PendingPinSource.Claim> parseClaim(
            int requestedSlot,
            Object returnedSlot,
            Object pinValue,
            Object tokenValue) {
        if (!(returnedSlot instanceof Integer slotId)
                || slotId != requestedSlot
                || !(pinValue instanceof String pin)
                || !pin.matches("[0-9]{4,8}")
                || !(tokenValue instanceof String token)
                || token.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PendingPinSource.Claim(pin, token));
    }

    boolean parseAcknowledged(Object successValue) {
        return Boolean.TRUE.equals(successValue);
    }
}
