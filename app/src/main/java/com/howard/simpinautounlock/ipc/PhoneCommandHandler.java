package com.howard.simpinautounlock.ipc;

import com.howard.simpinautounlock.core.CredentialRepository;
import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;

public final class PhoneCommandHandler {
    private final CredentialRepository repository;
    private final SimIdentityProvider identities;

    public PhoneCommandHandler(
            CredentialRepository repository,
            SimIdentityProvider identities) {
        this.repository = repository;
        this.identities = identities;
    }

    public int configure(int slotId, String pin) {
        if (!isValidSlot(slotId) || pin == null || !pin.matches("[0-9]{4,8}")) {
            return ConfigContract.RESULT_INVALID_REQUEST;
        }
        String iccid = identities.currentIccid(slotId).orElse(null);
        if (iccid == null || iccid.length() < 4) {
            return ConfigContract.RESULT_SIM_UNAVAILABLE;
        }
        try {
            repository.save(SlotRecord.armed(slotId, iccid, pin));
            return ConfigContract.RESULT_OK;
        } catch (RuntimeException error) {
            return ConfigContract.RESULT_STORAGE_ERROR;
        }
    }

    public int setEnabled(int slotId, boolean enabled) {
        if (!isValidSlot(slotId)) {
            return ConfigContract.RESULT_INVALID_REQUEST;
        }
        try {
            SlotRecord record = repository.load(slotId).orElse(null);
            if (record == null) {
                return ConfigContract.RESULT_INVALID_REQUEST;
            }

            SlotState requested = enabled ? SlotState.ARMED : SlotState.DISABLED;
            if (record.state() == requested) {
                return ConfigContract.RESULT_OK;
            }
            boolean validTransition = (record.state() == SlotState.ARMED && !enabled)
                    || (record.state() == SlotState.DISABLED && enabled);
            if (!validTransition) {
                return ConfigContract.RESULT_INVALID_REQUEST;
            }

            repository.save(record.transition(
                    requested,
                    record.lastAttemptBootCount(),
                    record.lastResultCode(),
                    record.remainingAttempts()));
            return ConfigContract.RESULT_OK;
        } catch (RuntimeException error) {
            return ConfigContract.RESULT_STORAGE_ERROR;
        }
    }

    public int clearSlot(int slotId) {
        if (!isValidSlot(slotId)) {
            return ConfigContract.RESULT_INVALID_REQUEST;
        }
        try {
            repository.clear(slotId);
            return ConfigContract.RESULT_OK;
        } catch (RuntimeException error) {
            return ConfigContract.RESULT_STORAGE_ERROR;
        }
    }

    public int clearAll() {
        try {
            repository.clearAll();
            return ConfigContract.RESULT_OK;
        } catch (RuntimeException error) {
            return ConfigContract.RESULT_STORAGE_ERROR;
        }
    }

    public SlotStatus querySlot(int slotId) {
        String current = identities.currentIccid(slotId).orElse("");
        SlotRecord record = repository.load(slotId).orElse(null);
        if (record == null) {
            return new SlotStatus(
                    slotId,
                    SlotState.EMPTY,
                    "",
                    IccidMasker.lastFour(current),
                    false,
                    SlotRecord.RESULT_UNKNOWN,
                    -1,
                    -1);
        }

        boolean matches = !current.isEmpty() && record.iccid().equals(current);
        SlotState state = record.state();
        if (!current.isEmpty() && !matches && state != SlotState.NEEDS_CONFIRMATION) {
            state = SlotState.SIM_MISMATCH;
            record = record.transition(
                    state,
                    record.lastAttemptBootCount(),
                    record.lastResultCode(),
                    record.remainingAttempts());
            repository.save(record);
        }
        return new SlotStatus(
                slotId,
                state,
                IccidMasker.lastFour(record.iccid()),
                IccidMasker.lastFour(current),
                matches,
                record.lastResultCode(),
                record.remainingAttempts(),
                record.lastAttemptBootCount());
    }

    private static boolean isValidSlot(int slotId) {
        return slotId == 0 || slotId == 1;
    }
}
