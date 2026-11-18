package com.howard.simpinautounlock.pending;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.core.PendingPinSource;
import java.util.Optional;
import org.junit.Test;

public final class PendingPinResponseLogicTest {
    private final PendingPinResponseLogic logic = new PendingPinResponseLogic();

    @Test
    public void acceptsClaimForRequestedSlotWithValidPinAndToken() {
        Optional<PendingPinSource.Claim> claim =
                logic.parseClaim(1, 1, "2468", "token-1");

        assertEquals(new PendingPinSource.Claim("2468", "token-1"), claim.orElseThrow());
    }

    @Test
    public void rejectsClaimForDifferentReturnedSlot() {
        assertTrue(logic.parseClaim(0, 1, "2468", "token-0").isEmpty());
    }

    @Test
    public void rejectsClaimWithNonIntegerReturnedSlot() {
        assertTrue(logic.parseClaim(0, "0", "2468", "token-0").isEmpty());
    }

    @Test
    public void rejectsClaimWithNonStringPin() {
        assertTrue(logic.parseClaim(0, 0, 2468, "token-0").isEmpty());
    }

    @Test
    public void rejectsClaimWithNonStringToken() {
        assertTrue(logic.parseClaim(0, 0, "2468", new Object()).isEmpty());
    }

    @Test
    public void rejectsClaimWithInvalidPin() {
        assertTrue(logic.parseClaim(0, 0, "123", "token-0").isEmpty());
        assertTrue(logic.parseClaim(0, 0, "123456789", "token-0").isEmpty());
        assertTrue(logic.parseClaim(0, 0, "12x4", "token-0").isEmpty());
    }

    @Test
    public void rejectsClaimWithEmptyToken() {
        assertTrue(logic.parseClaim(0, 0, "2468", "").isEmpty());
    }

    @Test
    public void acceptsOnlyBooleanTrueAcknowledgement() {
        assertTrue(logic.parseAcknowledged(Boolean.TRUE));
        assertFalse(logic.parseAcknowledged(Boolean.FALSE));
    }

    @Test
    public void rejectsNonBooleanAcknowledgement() {
        assertFalse(logic.parseAcknowledged("true"));
        assertFalse(logic.parseAcknowledged(null));
    }
}
