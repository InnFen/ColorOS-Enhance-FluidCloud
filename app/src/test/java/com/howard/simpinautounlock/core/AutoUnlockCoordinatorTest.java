package com.howard.simpinautounlock.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class AutoUnlockCoordinatorTest {
    private static final String ICCID_1 = "8901000000000001234";
    private static final String ICCID_2 = "8901000000000005678";
    private final MemoryRepository repository = new MemoryRepository();
    private final MutableBootEnvironment boot = new MutableBootEnvironment();
    private final CapturingScheduler scheduler = new CapturingScheduler();
    private final RecordingPendingPinSource pendingSource = new RecordingPendingPinSource();
    private final AutoUnlockCoordinator coordinator =
            new AutoUnlockCoordinator(
                    repository, boot, scheduler, SlotStateLogger.NO_OP, pendingSource);

    @org.junit.Before public void enableFeature() { coordinator.setFeatureEnabled(true); }

    @Test
    public void diagnosticsDistinguishBootCompletedFromAlreadyAttemptedWithoutLeakingSecrets() {
        List<String> events = new ArrayList<>();
        AutoUnlockCoordinator subject = new AutoUnlockCoordinator(repository, boot, scheduler,
                (slot, count, event) -> events.add(event), pendingSource);
        subject.setFeatureEnabled(true);
        repository.save(new SlotRecord(0, ICCID_1, "1234", SlotState.ARMED,
                boot.count, SlotRecord.RESULT_SUCCESS, 3));
        assertEquals("", subject.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(List.of("SKIP_ALREADY_ATTEMPTED_THIS_BOOT"), events);
        events.clear();
        boot.completed = true;
        assertEquals("", subject.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(List.of("SKIP_BOOT_COMPLETED"), events);
        assertEquals(0, pendingSource.claimCalls.get());
        assertEquals(SlotState.ARMED, repository.load(0).orElseThrow().state());
    }

    @Test
    public void diagnosticsDistinguishDisabledFromMissingIdentity() {
        List<String> events = new ArrayList<>();
        AutoUnlockCoordinator subject = new AutoUnlockCoordinator(repository, boot, scheduler,
                (slot, count, event) -> events.add(event), pendingSource);
        assertEquals("", subject.maybeProvidePin(0, ICCID_1, ""));
        subject.setFeatureEnabled(true);
        assertEquals("", subject.maybeProvidePin(0, null, ""));
        assertEquals(List.of("SKIP_FEATURE_DISABLED", "SKIP_IDENTITY_UNAVAILABLE"), events);
        assertEquals(0, pendingSource.claimCalls.get());
    }

    @Test
    public void unavailableDiagnosticSinkDoesNotChangeSkipDecision() {
        AutoUnlockCoordinator subject = new AutoUnlockCoordinator(repository, boot, scheduler,
                (slot, count, event) -> { throw new IllegalStateException("sink unavailable"); },
                pendingSource);
        subject.setFeatureEnabled(true);
        boot.completed = true;
        assertEquals("", subject.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(0, pendingSource.claimCalls.get());
    }

    @Test
    public void preservesNonEmptyOriginalPin() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));

        assertEquals("9999", coordinator.maybeProvidePin(0, ICCID_1, "9999"));
        assertEquals(SlotState.ARMED, repository.load(0).orElseThrow().state());
        assertEquals(0, pendingSource.claimCalls.get());
    }

    @Test
    public void providesEachSlotOnlyOncePerBoot() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        repository.save(SlotRecord.armed(1, ICCID_2, "5678"));

        assertEquals("1234", coordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals("5678", coordinator.maybeProvidePin(1, ICCID_2, ""));
        coordinator.onSupplyResult(0, true, 3);
        coordinator.onSupplyResult(1, true, 3);

        assertEquals("", coordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals("", coordinator.maybeProvidePin(1, ICCID_2, ""));
    }

    @Test
    public void concurrentSameSlotProvidesPinOnlyOnce() throws Exception {
        RaceAmplifyingRepository concurrentRepository = new RaceAmplifyingRepository();
        MutableBootEnvironment concurrentBoot = new MutableBootEnvironment();
        AutoUnlockCoordinator concurrentCoordinator = new AutoUnlockCoordinator(
                concurrentRepository, concurrentBoot, new CapturingScheduler());
        concurrentCoordinator.setFeatureEnabled(true);
        concurrentRepository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(() -> {
                start.await();
                return concurrentCoordinator.maybeProvidePin(0, ICCID_1, "");
            });
            Future<String> second = executor.submit(() -> {
                start.await();
                return concurrentCoordinator.maybeProvidePin(0, ICCID_1, "");
            });
            start.countDown();

            int suppliedPins = 0;
            if ("1234".equals(first.get(5, TimeUnit.SECONDS))) {
                suppliedPins++;
            }
            if ("1234".equals(second.get(5, TimeUnit.SECONDS))) {
                suppliedPins++;
            }

            assertEquals("only one concurrent same-slot caller may receive a PIN", 1, suppliedPins);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void refusesAfterBootCompleted() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        boot.completed = true;

        assertEquals("", coordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(SlotState.ARMED, repository.load(0).orElseThrow().state());
        assertEquals(0, pendingSource.claimCalls.get());
    }

    @Test
    public void refusesWhenBootCountIsUnavailable() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        boot.count = -1;

        assertEquals("", coordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(SlotState.ARMED, repository.load(0).orElseThrow().state());
        assertEquals(0, pendingSource.claimCalls.get());
    }

    @Test
    public void invalidSlotsDoNotClaim() {
        pendingSource.stage(0, "2468", "token-0");

        assertEquals("", coordinator.maybeProvidePin(-1, ICCID_1, ""));
        assertEquals("", coordinator.maybeProvidePin(2, ICCID_1, ""));
        assertEquals(0, pendingSource.claimCalls.get());
    }

    @Test
    public void emptyCurrentIccidDoesNotClaim() {
        pendingSource.stage(0, "2468", "token-0");

        assertEquals("", coordinator.maybeProvidePin(0, "", ""));
        assertEquals("", coordinator.maybeProvidePin(0, null, ""));
        assertEquals(0, pendingSource.claimCalls.get());
    }

    @Test
    public void recordAttemptedThisBootDoesNotClaim() {
        repository.save(new SlotRecord(
                0,
                ICCID_1,
                "1234",
                SlotState.ARMED,
                42,
                SlotRecord.RESULT_SUCCESS,
                3));
        pendingSource.stage(0, "2468", "token-0");

        assertEquals("", coordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals("1234", repository.load(0).orElseThrow().pin());
        assertEquals(0, pendingSource.claimCalls.get());
    }

    @Test
    public void noPendingClaimLeavesDisabledRecordUnchanged() {
        SlotRecord disabled = new SlotRecord(
                0,
                ICCID_1,
                "1234",
                SlotState.DISABLED,
                -1,
                SlotRecord.RESULT_UNKNOWN,
                -1);
        repository.save(disabled);

        assertEquals("", coordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(disabled, repository.load(0).orElseThrow());
        assertEquals(1, pendingSource.claimCalls.get());
    }

    @Test
    public void noPendingClaimRejectsInvalidOfficialPin() {
        repository.save(SlotRecord.armed(0, ICCID_1, "12x4"));

        assertEquals("", coordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(SlotState.NEEDS_CONFIRMATION,
                repository.load(0).orElseThrow().state());
        assertEquals(1, pendingSource.claimCalls.get());
    }

    @Test
    public void importsClaimBeforeScheduleAckAndReturn() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        MemoryRepository eventRepository = new MemoryRepository(events);
        CapturingScheduler eventScheduler = new CapturingScheduler(events);
        RecordingPendingPinSource eventSource = new RecordingPendingPinSource(events);
        RecordingStateLogger logger = new RecordingStateLogger();
        eventSource.stage(0, "2468", "token-0");
        AutoUnlockCoordinator importingCoordinator = new AutoUnlockCoordinator(
                eventRepository, boot, eventScheduler, logger, eventSource);
        importingCoordinator.setFeatureEnabled(true);

        String returned = importingCoordinator.maybeProvidePin(0, ICCID_1, "");
        events.add("return");

        assertEquals("2468", returned);
        assertEquals(List.of(
                "claim:0",
                "save:PENDING_RESULT:42",
                "schedule",
                "ack:0",
                "return"), events);
        SlotRecord imported = eventRepository.load(0).orElseThrow();
        assertEquals(0, imported.slotId());
        assertEquals(ICCID_1, imported.iccid());
        assertEquals("2468", imported.pin());
        assertEquals(SlotState.PENDING_RESULT, imported.state());
        assertEquals(42, imported.lastAttemptBootCount());
        assertEquals(SlotRecord.RESULT_UNKNOWN, imported.lastResultCode());
        assertEquals(-1, imported.remainingAttempts());
        assertEquals(List.of(
                "0|42|PENDING_IMPORTED",
                "0|42|PENDING_ACKNOWLEDGED"), logger.transitions);
    }

    @Test
    public void newPendingGenerationReplacesAnExistingOfficialRecord() {
        SlotRecord old = new SlotRecord(
                0,
                ICCID_2,
                "9999",
                SlotState.DISABLED,
                41,
                SlotRecord.RESULT_FAILURE,
                2);
        repository.save(old);
        pendingSource.stage(0, "2468", "token-0");

        assertEquals("2468", coordinator.maybeProvidePin(0, ICCID_1, ""));

        SlotRecord imported = repository.load(0).orElseThrow();
        assertEquals(ICCID_1, imported.iccid());
        assertEquals("2468", imported.pin());
        assertEquals(SlotState.PENDING_RESULT, imported.state());
        assertEquals(42, imported.lastAttemptBootCount());
    }

    @Test
    public void consumedPendingGenerationFallsBackToArmedOfficialRecord() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        pendingSource.stage(0, "2468", "generation-1");
        PendingPinSource consuming = new com.howard.simpinautounlock.pending.ConsumingPendingPinSource(
                pendingSource,
                (slotId, generation) -> com.howard.simpinautounlock.pending
                        .PendingPinGenerationGate.Result.ALREADY_CONSUMED);
        AutoUnlockCoordinator consumingCoordinator = new AutoUnlockCoordinator(
                repository, boot, scheduler, SlotStateLogger.NO_OP, consuming);
        consumingCoordinator.setFeatureEnabled(true);

        assertEquals("1234", consumingCoordinator.maybeProvidePin(0, ICCID_1, ""));

        assertEquals(1, pendingSource.claimCalls.get());
        assertEquals("1234", repository.load(0).orElseThrow().pin());
    }

    @Test
    public void unavailablePendingConsumptionBlocksExistingOfficialPin() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        pendingSource.stage(0, "5678", "generation-1");
        PendingPinSource consuming = new com.howard.simpinautounlock.pending.ConsumingPendingPinSource(
                pendingSource,
                (slotId, generation) -> com.howard.simpinautounlock.pending
                        .PendingPinGenerationGate.Result.UNAVAILABLE);
        AutoUnlockCoordinator consumingCoordinator = new AutoUnlockCoordinator(
                repository, boot, scheduler, SlotStateLogger.NO_OP, consuming);
        consumingCoordinator.setFeatureEnabled(true);

        assertEquals("", consumingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(SlotState.ARMED, repository.load(0).orElseThrow().state());
        assertNull(scheduler.lastTask);
    }

    @Test
    public void reservedPendingGenerationBlocksOldPinAfterTransientOfficialSaveFailure() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        pendingSource.stage(0, "5678", "generation-1");
        AtomicInteger reservations = new AtomicInteger();
        PendingPinSource consuming = new com.howard.simpinautounlock.pending.ConsumingPendingPinSource(
                pendingSource,
                (slotId, generation) -> reservations.getAndIncrement() == 0
                        ? com.howard.simpinautounlock.pending.PendingPinGenerationGate.Result.ACCEPTED
                        : com.howard.simpinautounlock.pending.PendingPinGenerationGate.Result.IN_PROGRESS);
        AutoUnlockCoordinator consumingCoordinator = new AutoUnlockCoordinator(
                repository, boot, scheduler, SlotStateLogger.NO_OP, consuming);
        consumingCoordinator.setFeatureEnabled(true);
        repository.saveFailure = new IllegalStateException();

        assertEquals("", consumingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        repository.saveFailure = null;
        assertEquals("", consumingCoordinator.maybeProvidePin(0, ICCID_1, ""));

        assertEquals("1234", repository.load(0).orElseThrow().pin());
        assertEquals(SlotState.ARMED, repository.load(0).orElseThrow().state());
        assertNull(scheduler.lastTask);
    }

    @Test
    public void officialSaveFailureDoesNotScheduleAcknowledgeOrReturnPin() {
        MemoryRepository failingRepository = new MemoryRepository();
        failingRepository.saveFailure = new IllegalStateException();
        CapturingScheduler unusedScheduler = new CapturingScheduler();
        RecordingPendingPinSource source = new RecordingPendingPinSource();
        RecordingStateLogger logger = new RecordingStateLogger();
        source.stage(0, "2468", "token-0");
        AutoUnlockCoordinator importingCoordinator = new AutoUnlockCoordinator(
                failingRepository, boot, unusedScheduler, logger, source);
        importingCoordinator.setFeatureEnabled(true);

        assertEquals("", importingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertTrue(failingRepository.load(0).isEmpty());
        assertNull(unusedScheduler.lastTask);
        assertEquals(0, source.acknowledgeCalls.get());
        assertEquals(List.of("0|42|PENDING_IMPORT_FAILED"), logger.transitions);
    }

    @Test
    public void pendingImportSchedulerRejectionConvergesWithoutAcknowledgeOrPin() {
        RecordingPendingPinSource source = new RecordingPendingPinSource();
        RecordingStateLogger logger = new RecordingStateLogger();
        source.stage(0, "2468", "token-0");
        AutoUnlockCoordinator importingCoordinator = new AutoUnlockCoordinator(
                repository, boot, (task, delayMillis) -> false, logger, source);
        importingCoordinator.setFeatureEnabled(true);

        assertEquals("", importingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        SlotRecord record = repository.load(0).orElseThrow();
        assertEquals(SlotState.NEEDS_CONFIRMATION, record.state());
        assertEquals(42, record.lastAttemptBootCount());
        assertEquals(0, source.acknowledgeCalls.get());
        assertTrue(logger.transitions.contains("0|42|PENDING_IMPORT_FAILED"));
    }

    @Test
    public void pendingImportSchedulerExceptionConvergesWithoutAcknowledgeOrPin() {
        RecordingPendingPinSource source = new RecordingPendingPinSource();
        source.stage(0, "2468", "token-0");
        AutoUnlockCoordinator importingCoordinator = new AutoUnlockCoordinator(
                repository,
                boot,
                (task, delayMillis) -> {
                    throw new AssertionError();
                },
                SlotStateLogger.NO_OP,
                source);
        importingCoordinator.setFeatureEnabled(true);

        assertEquals("", importingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(SlotState.NEEDS_CONFIRMATION,
                repository.load(0).orElseThrow().state());
        assertEquals(0, source.acknowledgeCalls.get());
    }

    @Test
    public void acknowledgementFalseStillReturnsImportedPinOnlyOnce() {
        RecordingPendingPinSource source = new RecordingPendingPinSource();
        RecordingStateLogger logger = new RecordingStateLogger();
        source.stage(0, "2468", "token-0");
        source.acknowledgeResult = false;
        AutoUnlockCoordinator importingCoordinator = new AutoUnlockCoordinator(
                repository, boot, scheduler, logger, source);
        importingCoordinator.setFeatureEnabled(true);

        assertEquals("2468", importingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals("", importingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(1, source.claimCalls.get());
        assertEquals(1, source.acknowledgeCalls.get());
        assertEquals(SlotState.PENDING_RESULT, repository.load(0).orElseThrow().state());
        assertTrue(logger.transitions.contains("0|42|PENDING_ACK_FAILED"));
    }

    @Test
    public void acknowledgementExceptionStillReturnsImportedPinOnlyOnce() {
        RecordingPendingPinSource source = new RecordingPendingPinSource();
        source.stage(0, "2468", "token-0");
        source.throwOnAcknowledge = true;
        AutoUnlockCoordinator importingCoordinator = new AutoUnlockCoordinator(
                repository, boot, scheduler, SlotStateLogger.NO_OP, source);
        importingCoordinator.setFeatureEnabled(true);

        assertEquals("2468", importingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals("", importingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(1, source.claimCalls.get());
        assertEquals(1, source.acknowledgeCalls.get());
    }

    @Test
    public void failedReadOnlyImportIsNeverRetriedOnALaterBoot() {
        RecordingPendingPinSource source = new RecordingPendingPinSource();
        source.stage(0, "2468", "generation-1");
        java.util.Set<String> consumed = new java.util.HashSet<>();
        PendingPinSource consuming = new com.howard.simpinautounlock.pending.ConsumingPendingPinSource(
                source, (slotId, generation) -> consumed.add(slotId + ":" + generation)
                        ? com.howard.simpinautounlock.pending.PendingPinGenerationGate.Result.ACCEPTED
                        : com.howard.simpinautounlock.pending.PendingPinGenerationGate.Result.ALREADY_CONSUMED);
        MutableBootEnvironment mutableBoot = new MutableBootEnvironment();
        AutoUnlockCoordinator importingCoordinator = new AutoUnlockCoordinator(
                repository, mutableBoot, scheduler, SlotStateLogger.NO_OP, consuming);
        importingCoordinator.setFeatureEnabled(true);

        assertEquals("2468", importingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        importingCoordinator.onSupplyResult(0, false, 2);
        mutableBoot.count = 43;

        assertEquals("", importingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(2, source.claimCalls.get());
        assertEquals(SlotState.NEEDS_CONFIRMATION,
                repository.load(0).orElseThrow().state());
    }

    @Test
    public void importsTwoSlotsIndependently() {
        pendingSource.stage(0, "2468", "token-0");
        pendingSource.stage(1, "1357", "token-1");

        assertEquals("2468", coordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals("1357", coordinator.maybeProvidePin(1, ICCID_2, ""));

        assertEquals(ICCID_1, repository.load(0).orElseThrow().iccid());
        assertEquals(ICCID_2, repository.load(1).orElseThrow().iccid());
        assertEquals(2, pendingSource.claimCalls.get());
        assertEquals(2, pendingSource.acknowledgeCalls.get());
    }

    @Test
    public void concurrentPendingImportProvidesPinOnlyOnce() throws Exception {
        MemoryRepository concurrentRepository = new MemoryRepository();
        RecordingPendingPinSource concurrentSource = new RecordingPendingPinSource();
        concurrentSource.stage(0, "2468", "token-0");
        AutoUnlockCoordinator concurrentCoordinator = new AutoUnlockCoordinator(
                concurrentRepository,
                new MutableBootEnvironment(),
                new CapturingScheduler(),
                SlotStateLogger.NO_OP,
                concurrentSource);
        concurrentCoordinator.setFeatureEnabled(true);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(() -> {
                start.await();
                return concurrentCoordinator.maybeProvidePin(0, ICCID_1, "");
            });
            Future<String> second = executor.submit(() -> {
                start.await();
                return concurrentCoordinator.maybeProvidePin(0, ICCID_1, "");
            });
            start.countDown();

            int suppliedPins = 0;
            if ("2468".equals(first.get(5, TimeUnit.SECONDS))) {
                suppliedPins++;
            }
            if ("2468".equals(second.get(5, TimeUnit.SECONDS))) {
                suppliedPins++;
            }

            assertEquals("only one concurrent importer may receive a PIN", 1, suppliedPins);
            assertEquals(1, concurrentSource.claimCalls.get());
            assertEquals(1, concurrentSource.acknowledgeCalls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void mismatchedIccidNeverReturnsPin() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));

        assertEquals("", coordinator.maybeProvidePin(0, ICCID_2, ""));
        assertEquals(SlotState.SIM_MISMATCH, repository.load(0).orElseThrow().state());
    }

    @Test
    public void failureRequiresFreshConfiguration() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));
        repository.save(SlotRecord.armed(1, ICCID_2, "5678"));

        assertEquals("1234", coordinator.maybeProvidePin(0, ICCID_1, ""));
        coordinator.onSupplyResult(0, false, 2);
        assertEquals(SlotState.NEEDS_CONFIRMATION, repository.load(0).orElseThrow().state());
        assertEquals("5678", coordinator.maybeProvidePin(1, ICCID_2, ""));
        boot.count = 43;

        assertEquals("", coordinator.maybeProvidePin(0, ICCID_1, ""));
    }

    @Test
    public void missingResultTimesOutClosed() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));

        assertEquals("1234", coordinator.maybeProvidePin(0, ICCID_1, ""));
        scheduler.runLastTask();

        assertEquals(SlotState.NEEDS_CONFIRMATION, repository.load(0).orElseThrow().state());
    }

    @Test
    public void recreatedCoordinatorRecoversPersistedPendingSlotsWithoutResubmitting() {
        repository.save(SlotRecord.armed(0, ICCID_1, "1234").transition(
                SlotState.PENDING_RESULT, 42, SlotRecord.RESULT_UNKNOWN, -1));
        repository.save(SlotRecord.armed(1, ICCID_2, "5678").transition(
                SlotState.PENDING_RESULT, 42, SlotRecord.RESULT_UNKNOWN, -1));

        AutoUnlockCoordinator recovered = new AutoUnlockCoordinator(
                repository, boot, new CapturingScheduler());
        recovered.setFeatureEnabled(true);

        assertEquals(SlotState.NEEDS_CONFIRMATION, repository.load(0).orElseThrow().state());
        assertEquals(SlotState.NEEDS_CONFIRMATION, repository.load(1).orElseThrow().state());
        assertEquals("", recovered.maybeProvidePin(0, ICCID_1, ""));
        assertEquals("", recovered.maybeProvidePin(1, ICCID_2, ""));
    }

    @Test
    public void schedulerRejectionConvergesPendingWithoutReturningPin() {
        AutoUnlockCoordinator rejectingCoordinator = new AutoUnlockCoordinator(
                repository,
                boot,
                (task, delayMillis) -> false);
        rejectingCoordinator.setFeatureEnabled(true);
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));

        assertEquals("", rejectingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(SlotState.NEEDS_CONFIRMATION, repository.load(0).orElseThrow().state());
    }

    @Test
    public void schedulerExceptionConvergesPendingWithoutReturningPin() {
        AutoUnlockCoordinator throwingCoordinator = new AutoUnlockCoordinator(
                repository,
                boot,
                (task, delayMillis) -> {
                    throw new AssertionError();
                });
        throwingCoordinator.setFeatureEnabled(true);
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));

        assertEquals("", throwingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        assertEquals(SlotState.NEEDS_CONFIRMATION, repository.load(0).orElseThrow().state());
    }

    @Test
    public void stateLogsContainOnlyFixedTransitionFields() {
        RecordingStateLogger logger = new RecordingStateLogger();
        AutoUnlockCoordinator loggingCoordinator = new AutoUnlockCoordinator(
                repository, boot, scheduler, logger);
        loggingCoordinator.setFeatureEnabled(true);
        repository.save(SlotRecord.armed(0, ICCID_1, "1234"));

        assertEquals("1234", loggingCoordinator.maybeProvidePin(0, ICCID_1, ""));
        loggingCoordinator.onSupplyResult(0, true, 3);

        assertEquals(List.of(
                "0|42|ARMED->PENDING_RESULT:ATTEMPT_PENDING",
                "0|42|PENDING_RESULT->ARMED:RESULT_SUCCESS"), logger.transitions);
        for (String transition : logger.transitions) {
            assertFalse(transition.contains(ICCID_1));
            assertFalse(transition.contains("1234"));
        }
    }

    private static final class MemoryRepository implements CredentialRepository {
        private final ConcurrentMap<Integer, SlotRecord> records = new ConcurrentHashMap<>();
        private final List<String> events;
        RuntimeException saveFailure;

        MemoryRepository() {
            this(null);
        }

        MemoryRepository(List<String> events) {
            this.events = events;
        }

        @Override
        public Optional<SlotRecord> load(int slotId) {
            return Optional.ofNullable(records.get(slotId));
        }

        @Override
        public void save(SlotRecord record) {
            if (saveFailure != null) {
                throw saveFailure;
            }
            records.put(record.slotId(), record);
            if (events != null) {
                events.add("save:" + record.state().name()
                        + ":" + record.lastAttemptBootCount());
            }
        }

        @Override
        public void clear(int slotId) {
            records.remove(slotId);
        }

        @Override
        public void clearAll() {
            records.clear();
        }
    }

    private static final class RaceAmplifyingRepository implements CredentialRepository {
        private final ConcurrentMap<Integer, SlotRecord> records = new ConcurrentHashMap<>();
        private final AtomicInteger armedLoads = new AtomicInteger();
        private final CyclicBarrier simultaneousArmedLoads = new CyclicBarrier(2);

        @Override
        public Optional<SlotRecord> load(int slotId) {
            SlotRecord record = records.get(slotId);
            if (slotId == 0
                    && record != null
                    && record.state() == SlotState.ARMED
                    && armedLoads.getAndIncrement() < 2) {
                waitForOtherArmedLoad();
            }
            return Optional.ofNullable(record);
        }

        @Override
        public void save(SlotRecord record) {
            records.put(record.slotId(), record);
        }

        @Override
        public void clear(int slotId) {
            records.remove(slotId);
        }

        @Override
        public void clearAll() {
            records.clear();
        }

        private void waitForOtherArmedLoad() {
            try {
                simultaneousArmedLoads.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            } catch (BrokenBarrierException | TimeoutException ignored) {
                // A serialized coordinator permits just one caller into this repository at a time.
            }
        }
    }

    private static final class MutableBootEnvironment implements BootEnvironment {
        int count = 42;
        boolean completed;

        @Override
        public int bootCount() {
            return count;
        }

        @Override
        public boolean bootCompleted() {
            return completed;
        }
    }

    private static final class CapturingScheduler implements TimeoutScheduler {
        private final List<String> events;
        Runnable lastTask;

        CapturingScheduler() {
            this(null);
        }

        CapturingScheduler(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean schedule(Runnable task, long delayMillis) {
            assertEquals(30_000L, delayMillis);
            lastTask = task;
            if (events != null) {
                events.add("schedule");
            }
            return true;
        }

        void runLastTask() {
            assertNotNull(lastTask);
            lastTask.run();
        }
    }

    private static final class RecordingStateLogger implements SlotStateLogger {
        private final List<String> transitions = new ArrayList<>();

        @Override
        public void log(int slotId, int bootCount, String transition) {
            transitions.add(slotId + "|" + bootCount + "|" + transition);
        }
    }

    private static final class RecordingPendingPinSource implements PendingPinSource {
        private final ConcurrentMap<Integer, Claim> claims = new ConcurrentHashMap<>();
        private final List<String> events;
        private final AtomicInteger claimCalls = new AtomicInteger();
        private final AtomicInteger acknowledgeCalls = new AtomicInteger();
        boolean acknowledgeResult = true;
        boolean throwOnAcknowledge;

        RecordingPendingPinSource() {
            this(Collections.synchronizedList(new ArrayList<>()));
        }

        RecordingPendingPinSource(List<String> events) {
            this.events = events;
        }

        void stage(int slotId, String pin, String token) {
            claims.put(slotId, new Claim(pin, token));
        }

        @Override
        public Optional<Claim> claim(int slotId) {
            claimCalls.incrementAndGet();
            events.add("claim:" + slotId);
            return Optional.ofNullable(claims.get(slotId));
        }

        @Override
        public boolean acknowledge(int slotId, String token) {
            acknowledgeCalls.incrementAndGet();
            events.add("ack:" + slotId);
            if (throwOnAcknowledge) {
                throw new IllegalStateException();
            }
            return acknowledgeResult;
        }
    }
}
