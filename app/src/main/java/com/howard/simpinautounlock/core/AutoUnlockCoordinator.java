package com.howard.simpinautounlock.core;

public final class AutoUnlockCoordinator {
    private static final long RESULT_TIMEOUT_MILLIS = 30_000L;
    private static final String PENDING_IMPORTED = "PENDING_IMPORTED";
    private static final String PENDING_ACKNOWLEDGED = "PENDING_ACKNOWLEDGED";
    private static final String PENDING_ACK_FAILED = "PENDING_ACK_FAILED";
    private static final String PENDING_IMPORT_FAILED = "PENDING_IMPORT_FAILED";

    private boolean featureEnabled;
    private final CredentialRepository repository;
    private final BootEnvironment bootEnvironment;
    private final TimeoutScheduler timeoutScheduler;
    private final SlotStateLogger stateLogger;
    private final PendingPinSource pendingPinSource;

    public AutoUnlockCoordinator(
            CredentialRepository repository,
            BootEnvironment bootEnvironment,
            TimeoutScheduler timeoutScheduler) {
        this(
                repository,
                bootEnvironment,
                timeoutScheduler,
                SlotStateLogger.NO_OP,
                PendingPinSource.NONE);
    }

    public AutoUnlockCoordinator(
            CredentialRepository repository,
            BootEnvironment bootEnvironment,
            TimeoutScheduler timeoutScheduler,
            SlotStateLogger stateLogger) {
        this(repository, bootEnvironment, timeoutScheduler, stateLogger, PendingPinSource.NONE);
    }

    public AutoUnlockCoordinator(
            CredentialRepository repository,
            BootEnvironment bootEnvironment,
            TimeoutScheduler timeoutScheduler,
            SlotStateLogger stateLogger,
            PendingPinSource pendingPinSource) {
        this.repository = repository;
        this.bootEnvironment = bootEnvironment;
        this.timeoutScheduler = timeoutScheduler;
        this.stateLogger = stateLogger;
        this.pendingPinSource = pendingPinSource;
        recoverPendingResults();
    }

    public synchronized void setFeatureEnabled(boolean enabled) {
        featureEnabled = enabled;
    }

    public synchronized String maybeProvidePin(int slotId, String currentIccid, String originalPin) {
        String original = originalPin == null ? "" : originalPin;
        if (!featureEnabled) {
            traceSkip(slotId, -1, "SKIP_FEATURE_DISABLED");
            return original;
        }
        if (!original.isEmpty()) {
            traceSkip(slotId, -1, "SKIP_NATIVE_PIN_PRESENT");
            return original;
        }
        if (bootEnvironment.bootCompleted()) {
            traceSkip(slotId, -1, "SKIP_BOOT_COMPLETED");
            return original;
        }
        if (slotId < 0 || slotId > 1) {
            traceSkip(slotId, -1, "SKIP_INVALID_SLOT");
            return original;
        }
        if (currentIccid == null || currentIccid.isEmpty()) {
            traceSkip(slotId, -1, "SKIP_IDENTITY_UNAVAILABLE");
            return original;
        }

        int bootCount = bootEnvironment.bootCount();
        if (bootCount < 0) {
            traceSkip(slotId, bootCount, "SKIP_BOOT_COUNT_UNAVAILABLE");
            return original;
        }

        SlotRecord record = repository.load(slotId).orElse(null);
        if (record != null && record.lastAttemptBootCount() == bootCount) {
            traceSkip(slotId, bootCount, "SKIP_ALREADY_ATTEMPTED_THIS_BOOT");
            return original;
        }

        PendingPinSource.Resolution pendingResolution = pendingPinSource.resolve(slotId);
        PendingPinSource.Claim claim = pendingResolution.claim().orElse(null);
        if (claim != null) {
            return importClaim(slotId, currentIccid, original, bootCount, claim);
        }
        if (!pendingResolution.allowFallback()) {
            traceSkip(slotId, bootCount, "SKIP_PENDING_SOURCE_BLOCKED");
            return original;
        }

        if (record == null || record.state() != SlotState.ARMED) {
            traceSkip(slotId, bootCount, record == null
                    ? "SKIP_NO_RECORD" : "SKIP_RECORD_NOT_ARMED");
            return original;
        }
        if (!record.iccid().equals(currentIccid)) {
            saveTransition(record,
                    SlotState.SIM_MISMATCH,
                    record.lastAttemptBootCount(),
                    record.lastResultCode(),
                    record.remainingAttempts(),
                    "SIM_MISMATCH");
            return original;
        }
        if (!record.pin().matches("[0-9]{4,8}")) {
            saveTransition(record,
                    SlotState.NEEDS_CONFIRMATION,
                    record.lastAttemptBootCount(),
                    SlotRecord.RESULT_UNKNOWN,
                    -1,
                    "INVALID_RECORD");
            return original;
        }

        SlotRecord pending = saveTransition(
                record,
                SlotState.PENDING_RESULT,
                bootCount,
                SlotRecord.RESULT_UNKNOWN,
                -1,
                "ATTEMPT_PENDING");
        try {
            if (!timeoutScheduler.schedule(
                    () -> onResultTimeout(slotId, bootCount), RESULT_TIMEOUT_MILLIS)) {
                saveTransition(
                        pending,
                        SlotState.NEEDS_CONFIRMATION,
                        bootCount,
                        SlotRecord.RESULT_UNKNOWN,
                        -1,
                        "SCHEDULER_FAILURE");
                return original;
            }
        } catch (Throwable ignored) {
            saveTransition(
                    pending,
                    SlotState.NEEDS_CONFIRMATION,
                    bootCount,
                    SlotRecord.RESULT_UNKNOWN,
                    -1,
                    "SCHEDULER_FAILURE");
            return original;
        }
        return pending.pin();
    }

    private void traceSkip(int slotId, int bootCount, String reason) {
        try {
            stateLogger.log(slotId, bootCount, reason);
        } catch (Throwable ignored) {
            // Diagnostics must not change authentication or consume an attempt.
        }
    }

    private String importClaim(
            int slotId,
            String currentIccid,
            String original,
            int bootCount,
            PendingPinSource.Claim claim) {
        SlotRecord imported = new SlotRecord(
                slotId,
                currentIccid,
                claim.pin(),
                SlotState.PENDING_RESULT,
                bootCount,
                SlotRecord.RESULT_UNKNOWN,
                -1);
        try {
            repository.save(imported);
        } catch (Throwable ignored) {
            stateLogger.log(slotId, bootCount, PENDING_IMPORT_FAILED);
            return original;
        }
        stateLogger.log(slotId, bootCount, PENDING_IMPORTED);

        try {
            if (!timeoutScheduler.schedule(
                    () -> onResultTimeout(slotId, bootCount), RESULT_TIMEOUT_MILLIS)) {
                failImportedRecord(imported, bootCount);
                return original;
            }
        } catch (Throwable ignored) {
            failImportedRecord(imported, bootCount);
            return original;
        }

        try {
            if (pendingPinSource.acknowledge(slotId, claim.token())) {
                stateLogger.log(slotId, bootCount, PENDING_ACKNOWLEDGED);
            } else {
                stateLogger.log(slotId, bootCount, PENDING_ACK_FAILED);
            }
        } catch (Throwable ignored) {
            stateLogger.log(slotId, bootCount, PENDING_ACK_FAILED);
        }
        return claim.pin();
    }

    private void failImportedRecord(SlotRecord imported, int bootCount) {
        try {
            saveTransition(
                    imported,
                    SlotState.NEEDS_CONFIRMATION,
                    bootCount,
                    SlotRecord.RESULT_UNKNOWN,
                    -1,
                    "SCHEDULER_FAILURE");
        } catch (Throwable ignored) {
            // The persisted current-boot PENDING_RESULT still prevents another submission.
        }
        stateLogger.log(imported.slotId(), bootCount, PENDING_IMPORT_FAILED);
    }

    public synchronized void onSupplyResult(int slotId, boolean success, int remainingAttempts) {
        SlotRecord record = repository.load(slotId).orElse(null);
        int bootCount = bootEnvironment.bootCount();
        if (record == null
                || record.state() != SlotState.PENDING_RESULT
                || record.lastAttemptBootCount() != bootCount) {
            return;
        }
        saveTransition(record,
                success ? SlotState.ARMED : SlotState.NEEDS_CONFIRMATION,
                bootCount,
                success ? SlotRecord.RESULT_SUCCESS : SlotRecord.RESULT_FAILURE,
                remainingAttempts,
                success ? "RESULT_SUCCESS" : "RESULT_FAILURE");
    }

    private synchronized void onResultTimeout(int slotId, int bootCount) {
        SlotRecord record = repository.load(slotId).orElse(null);
        if (record != null
                && record.state() == SlotState.PENDING_RESULT
                && record.lastAttemptBootCount() == bootCount) {
            saveTransition(record,
                    SlotState.NEEDS_CONFIRMATION,
                    bootCount,
                    SlotRecord.RESULT_UNKNOWN,
                    -1,
                    "TIMEOUT");
        }
    }

    private void recoverPendingResults() {
        for (int slotId = 0; slotId < 2; slotId++) {
            SlotRecord record = repository.load(slotId).orElse(null);
            if (record != null && record.state() == SlotState.PENDING_RESULT) {
                saveTransition(
                        record,
                        SlotState.NEEDS_CONFIRMATION,
                        record.lastAttemptBootCount(),
                        SlotRecord.RESULT_UNKNOWN,
                        -1,
                        "RECOVERY");
            }
        }
    }

    private SlotRecord saveTransition(
            SlotRecord record,
            SlotState next,
            int bootCount,
            int result,
            int remaining,
            String reason) {
        SlotRecord updated = record.transition(next, bootCount, result, remaining);
        repository.save(updated);
        stateLogger.log(record.slotId(), bootCount, record.state().name() + "->" + next.name()
                + ":" + reason);
        return updated;
    }
}
