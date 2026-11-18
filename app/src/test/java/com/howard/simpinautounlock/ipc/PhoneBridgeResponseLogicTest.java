package com.howard.simpinautounlock.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;
import java.util.UUID;
import org.junit.Test;

public final class PhoneBridgeResponseLogicTest {
    @Test
    public void generatesDistinctUuidRequestIds() {
        String first = PhoneBridgeResponseLogic.newRequestId();
        String second = PhoneBridgeResponseLogic.newRequestId();

        assertEquals(first, UUID.fromString(first).toString());
        assertEquals(second, UUID.fromString(second).toString());
        assertNotEquals(first, second);
    }

    @Test
    public void validNonceCompletesWithActualResultAndCancelsLaterTimeout() {
        PhoneBridgeResponseLogic.Gate gate = new PhoneBridgeResponseLogic.Gate("nonce-1");

        PhoneBridgeResponseLogic.Decision response =
                gate.onResponse("nonce-1", ConfigContract.RESULT_OK);
        PhoneBridgeResponseLogic.Decision timeout = gate.onTimeout();

        assertTrue(response.shouldComplete());
        assertEquals(ConfigContract.RESULT_OK, response.resultCode());
        assertFalse(timeout.shouldComplete());
    }

    @Test
    public void missingOrMismatchedNonceFailsClosedOnce() {
        PhoneBridgeResponseLogic.Gate missingGate =
                new PhoneBridgeResponseLogic.Gate("nonce-1");
        PhoneBridgeResponseLogic.Gate mismatchGate =
                new PhoneBridgeResponseLogic.Gate("nonce-2");

        PhoneBridgeResponseLogic.Decision missing = missingGate.onResponse(
                null, ConfigContract.RESULT_OK);
        PhoneBridgeResponseLogic.Decision mismatch = mismatchGate.onResponse(
                "different", ConfigContract.RESULT_OK);

        assertTrue(missing.shouldComplete());
        assertEquals(ConfigContract.RESULT_MODULE_UNAVAILABLE, missing.resultCode());
        assertFalse(missingGate.onTimeout().shouldComplete());
        assertTrue(mismatch.shouldComplete());
        assertEquals(ConfigContract.RESULT_MODULE_UNAVAILABLE, mismatch.resultCode());
        assertFalse(mismatchGate.onTimeout().shouldComplete());
    }

    @Test
    public void timeoutCompletesUnavailableAndIgnoresLateResponse() {
        PhoneBridgeResponseLogic.Gate gate = new PhoneBridgeResponseLogic.Gate("nonce-1");

        PhoneBridgeResponseLogic.Decision timeout = gate.onTimeout();
        PhoneBridgeResponseLogic.Decision response =
                gate.onResponse("nonce-1", ConfigContract.RESULT_OK);

        assertTrue(timeout.shouldComplete());
        assertEquals(ConfigContract.RESULT_MODULE_UNAVAILABLE, timeout.resultCode());
        assertFalse(response.shouldComplete());
    }

    @Test
    public void parsesCompleteSafeStatusForExpectedSlot() {
        PhoneBridgeResponseLogic.RawStatus raw = new PhoneBridgeResponseLogic.RawStatus(
                0,
                "ARMED",
                "1234",
                "1234",
                true,
                SlotRecord.RESULT_SUCCESS,
                3,
                42);

        SlotStatus status = PhoneBridgeResponseLogic.parseStatus(0, raw);

        assertEquals(0, status.slotId());
        assertEquals(SlotState.ARMED, status.state());
        assertEquals("1234", status.boundIccidSuffix());
        assertEquals("1234", status.currentIccidSuffix());
        assertTrue(status.simMatches());
        assertEquals(3, status.remainingAttempts());
    }

    @Test
    public void rejectsMalformedMissingWrongSlotOrUnsafeStatus() {
        PhoneBridgeResponseLogic.RawStatus complete = new PhoneBridgeResponseLogic.RawStatus(
                0, "ARMED", "1234", "1234", true,
                SlotRecord.RESULT_UNKNOWN, -1, 42);
        PhoneBridgeResponseLogic.RawStatus unsafeSuffix =
                new PhoneBridgeResponseLogic.RawStatus(
                        0, "ARMED", "8901000000000001234", "12A4", true,
                        SlotRecord.RESULT_UNKNOWN, -1, 42);
        PhoneBridgeResponseLogic.RawStatus unknownState =
                new PhoneBridgeResponseLogic.RawStatus(
                        0, "NOT_A_STATE", "1234", "1234", true,
                        SlotRecord.RESULT_UNKNOWN, -1, 42);

        assertNull(PhoneBridgeResponseLogic.parseStatus(0, null));
        assertNull(PhoneBridgeResponseLogic.parseStatus(1, complete));
        assertNull(PhoneBridgeResponseLogic.parseStatus(0, unsafeSuffix));
        assertNull(PhoneBridgeResponseLogic.parseStatus(0, unknownState));
    }

    @Test
    public void rejectsImpossibleMismatchRelationships() {
        assertNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("SIM_MISMATCH", "1234", "1234", false)));
        assertNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("SIM_MISMATCH", "1234", "5678", true)));
        assertNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("SIM_MISMATCH", "", "5678", false)));
    }

    @Test
    public void rejectsImpossibleActiveStateRelationships() {
        for (String state : new String[] {"ARMED", "DISABLED", "PENDING_RESULT"}) {
            assertNull(PhoneBridgeResponseLogic.parseStatus(
                    0, raw(state, "1234", "5678", true)));
            assertNull(PhoneBridgeResponseLogic.parseStatus(
                    0, raw(state, "1234", "1234", false)));
            assertNull(PhoneBridgeResponseLogic.parseStatus(
                    0, raw(state, "", "", true)));
        }
    }

    @Test
    public void rejectsImpossibleEmptyAndConfirmationRelationships() {
        assertNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("EMPTY", "1234", "1234", false)));
        assertNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("EMPTY", "", "", true)));
        assertNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("NEEDS_CONFIRMATION", "", "", true)));
        assertNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("NEEDS_CONFIRMATION", "1234", "5678", true)));
    }

    @Test
    public void acceptsOnlyRuledCrossFieldBoundaryCombinations() {
        assertNotNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("SIM_MISMATCH", "1234", "5678", false)));
        assertNotNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("EMPTY", "", "", false)));
        assertNotNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("EMPTY", "", "5678", false)));
        assertNotNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("NEEDS_CONFIRMATION", "", "", false)));
        assertNotNull(PhoneBridgeResponseLogic.parseStatus(
                0, raw("NEEDS_CONFIRMATION", "1234", "1234", true)));
    }

    @Test
    public void acceptsExistingRecordsWhenCurrentIccidIsTemporarilyUnreadable() {
        for (String state : new String[] {
                "ARMED", "DISABLED", "PENDING_RESULT", "SIM_MISMATCH"
        }) {
            SlotStatus status = PhoneBridgeResponseLogic.parseStatus(
                    0, raw(state, "1234", "", false));

            assertNotNull(state, status);
            assertEquals(state, status.state().name());
            assertEquals("1234", status.boundIccidSuffix());
            assertEquals("", status.currentIccidSuffix());
            assertFalse(status.simMatches());
        }
    }

    private static PhoneBridgeResponseLogic.RawStatus raw(
            String state,
            String boundSuffix,
            String currentSuffix,
            boolean simMatches) {
        return new PhoneBridgeResponseLogic.RawStatus(
                0,
                state,
                boundSuffix,
                currentSuffix,
                simMatches,
                SlotRecord.RESULT_UNKNOWN,
                -1,
                42);
    }
}
