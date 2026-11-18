package com.howard.simpinautounlock.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.core.CredentialRepository;
import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public final class PhoneCommandHandlerTest {
    private static final String ICCID_1 = "8901000000000001234";
    private static final String ICCID_2 = "8901000000000005678";

    private final MemoryRepository repository = new MemoryRepository();
    private final MutableIdentities identities = new MutableIdentities();
    private final PhoneCommandHandler handler = new PhoneCommandHandler(repository, identities);

    @Test
    public void configureReadsIccidInsidePhoneProcess() {
        identities.iccids.put(0, ICCID_1);

        assertEquals(ConfigContract.RESULT_OK, handler.configure(0, "1234"));

        SlotRecord record = repository.load(0).orElseThrow();
        assertEquals(ICCID_1, record.iccid());
        assertEquals(SlotState.ARMED, record.state());
    }

    @Test
    public void rejectsInvalidPinAndMissingSim() {
        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.configure(0, "12A4"));
        assertEquals(ConfigContract.RESULT_SIM_UNAVAILABLE, handler.configure(1, "5678"));
    }

    @Test
    public void configureRejectsInvalidSlotsAndPinLengths() {
        identities.iccids.put(0, ICCID_1);

        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.configure(-1, "1234"));
        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.configure(2, "1234"));
        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.configure(0, null));
        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.configure(0, "123"));
        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.configure(0, "123456789"));
    }

    @Test
    public void configureMapsStorageFailureToResultCode() {
        identities.iccids.put(0, ICCID_1);
        repository.failSave = true;

        assertEquals(ConfigContract.RESULT_STORAGE_ERROR, handler.configure(0, "1234"));
    }

    @Test
    public void queryReturnsOnlyLastFourDigits() {
        identities.iccids.put(0, ICCID_1);
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));

        SlotStatus status = handler.querySlot(0);

        assertEquals("1234", status.boundIccidSuffix());
        assertEquals("1234", status.currentIccidSuffix());
        assertTrue(status.simMatches());
        assertFalse(status.toString().contains(ICCID_1));
    }

    @Test
    public void queryEmptySlotExposesOnlyMaskedCurrentIdentity() {
        identities.iccids.put(1, ICCID_2);

        SlotStatus status = handler.querySlot(1);

        assertEquals(SlotState.EMPTY, status.state());
        assertEquals("", status.boundIccidSuffix());
        assertEquals("5678", status.currentIccidSuffix());
        assertFalse(status.simMatches());
    }

    @Test
    public void queryPersistsChangedSimAsMismatch() {
        identities.iccids.put(0, ICCID_2);
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));

        SlotStatus status = handler.querySlot(0);

        assertEquals(SlotState.SIM_MISMATCH, status.state());
        assertFalse(status.simMatches());
        assertEquals("1234", status.boundIccidSuffix());
        assertEquals("5678", status.currentIccidSuffix());
        assertEquals(SlotState.SIM_MISMATCH, repository.load(0).orElseThrow().state());
    }

    @Test
    public void queryDoesNotOverwriteNeedsConfirmationOnChangedSim() {
        identities.iccids.put(0, ICCID_2);
        repository.save(SlotRecord.armed(0, ICCID_1, "1234").transition(
                SlotState.NEEDS_CONFIRMATION, 42, SlotRecord.RESULT_FAILURE, 2));

        SlotStatus status = handler.querySlot(0);

        assertEquals(SlotState.NEEDS_CONFIRMATION, status.state());
        assertEquals(SlotState.NEEDS_CONFIRMATION, repository.load(0).orElseThrow().state());
    }

    @Test
    public void enabledToggleChangesOnlyArmedAndDisabledStates() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));

        assertEquals(ConfigContract.RESULT_OK, handler.setEnabled(0, false));
        assertEquals(SlotState.DISABLED, repository.load(0).orElseThrow().state());
        assertEquals(ConfigContract.RESULT_OK, handler.setEnabled(0, false));
        assertEquals(ConfigContract.RESULT_OK, handler.setEnabled(0, true));
        assertEquals(SlotState.ARMED, repository.load(0).orElseThrow().state());
        assertEquals(ConfigContract.RESULT_OK, handler.setEnabled(0, true));
    }

    @Test
    public void enabledToggleCannotRearmFailClosedStates() {
        for (SlotState state : new SlotState[] {
                SlotState.PENDING_RESULT,
                SlotState.NEEDS_CONFIRMATION,
                SlotState.SIM_MISMATCH
        }) {
            repository.save(SlotRecord.armed(0, ICCID_1, "1234").transition(
                    state, 42, SlotRecord.RESULT_FAILURE, 2));

            assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.setEnabled(0, true));
            assertEquals(state, repository.load(0).orElseThrow().state());
        }
    }

    @Test
    public void enabledToggleRejectsMissingRecordAndInvalidSlot() {
        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.setEnabled(0, true));
        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.setEnabled(2, true));
    }

    @Test
    public void enabledToggleMapsStorageFailuresToResultCode() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        repository.failSave = true;

        assertEquals(ConfigContract.RESULT_STORAGE_ERROR, handler.setEnabled(0, false));
    }

    @Test
    public void freshConfigureRearmsFailClosedSlot() {
        identities.iccids.put(0, ICCID_2);
        repository.save(SlotRecord.armed(0, ICCID_1, "1234").transition(
                SlotState.SIM_MISMATCH, -1, SlotRecord.RESULT_UNKNOWN, -1));

        assertEquals(ConfigContract.RESULT_OK, handler.configure(0, "5678"));

        SlotRecord configured = repository.load(0).orElseThrow();
        assertEquals(SlotState.ARMED, configured.state());
        assertEquals(ICCID_2, configured.iccid());
        assertEquals("5678", configured.pin());
    }

    @Test
    public void clearCommandsRemoveRequestedCredentials() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        repository.save(SlotRecord.armed(1, ICCID_2, "5678"));

        assertEquals(ConfigContract.RESULT_OK, handler.clearSlot(0));
        assertTrue(repository.load(0).isEmpty());
        assertTrue(repository.load(1).isPresent());
        assertEquals(ConfigContract.RESULT_OK, handler.clearAll());
        assertTrue(repository.load(1).isEmpty());
    }

    @Test
    public void clearCommandsValidateSlotAndMapStorageFailures() {
        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, handler.clearSlot(-1));
        repository.failClear = true;
        assertEquals(ConfigContract.RESULT_STORAGE_ERROR, handler.clearSlot(0));
        repository.failClear = false;
        repository.failClearAll = true;
        assertEquals(ConfigContract.RESULT_STORAGE_ERROR, handler.clearAll());
    }

    @Test
    public void maskerReturnsExactlyFourCharactersOrEmpty() {
        assertEquals("", IccidMasker.lastFour(null));
        assertEquals("", IccidMasker.lastFour(""));
        assertEquals("", IccidMasker.lastFour("123"));
        assertEquals("1234", IccidMasker.lastFour("1234"));
        assertEquals("5678", IccidMasker.lastFour(ICCID_2));
    }

    private static final class MutableIdentities implements SimIdentityProvider {
        private final Map<Integer, String> iccids = new HashMap<>();

        @Override
        public Optional<String> currentIccid(int slotId) {
            return Optional.ofNullable(iccids.get(slotId));
        }
    }

    private static final class MemoryRepository implements CredentialRepository {
        private final Map<Integer, SlotRecord> records = new HashMap<>();
        private boolean failSave;
        private boolean failClear;
        private boolean failClearAll;

        @Override
        public Optional<SlotRecord> load(int slotId) {
            return Optional.ofNullable(records.get(slotId));
        }

        @Override
        public void save(SlotRecord record) {
            if (failSave) {
                throw new IllegalStateException("simulated persistence failure");
            }
            records.put(record.slotId(), record);
        }

        @Override
        public void clear(int slotId) {
            if (failClear) {
                throw new IllegalStateException("simulated persistence failure");
            }
            records.remove(slotId);
        }

        @Override
        public void clearAll() {
            if (failClearAll) {
                throw new IllegalStateException("simulated persistence failure");
            }
            records.clear();
        }
    }
}
