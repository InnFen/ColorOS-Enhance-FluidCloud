package com.howard.simpinautounlock.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.core.CredentialRepository;
import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Test;

public final class PhoneCommandReceiverLogicTest {
    private static final String ICCID = "8901000000000001234";

    private final MemoryRepository repository = new MemoryRepository();
    private final PhoneCommandHandler handler =
            new PhoneCommandHandler(repository, slotId -> Optional.of(ICCID));

    @Test
    public void missingEnabledExtraDoesNotDisableArmedSlot() {
        repository.save(SlotRecord.armed(0, ICCID, "1234"));
        PhoneCommandReceiverLogic logic = new PhoneCommandReceiverLogic(handler, Runnable::run);
        CaptureSink sink = new CaptureSink();

        logic.receive(
                new FixedSource(PhoneCommandReceiverLogic.Request.fromRawEnabled(
                        ConfigContract.ACTION_SET_ENABLED,
                        "missing-enabled",
                        0,
                        false,
                        null)),
                sink);

        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, sink.outcome.resultCode());
        assertEquals(SlotState.ARMED, repository.load(0).orElseThrow().state());
        assertEquals(1, sink.finishCount);
    }

    @Test
    public void wrongTypedEnabledExtraDoesNotDisableArmedSlot() {
        repository.save(SlotRecord.armed(0, ICCID, "1234"));
        PhoneCommandReceiverLogic logic = new PhoneCommandReceiverLogic(handler, Runnable::run);
        CaptureSink sink = new CaptureSink();

        logic.receive(
                new FixedSource(PhoneCommandReceiverLogic.Request.fromRawEnabled(
                        ConfigContract.ACTION_SET_ENABLED,
                        "wrong-enabled-type",
                        0,
                        true,
                        "false")),
                sink);

        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, sink.outcome.resultCode());
        assertEquals(SlotState.ARMED, repository.load(0).orElseThrow().state());
        assertEquals(1, sink.finishCount);
    }

    @Test
    public void extractionFailureRemovesPinAndCompletesExactlyOnce() {
        PhoneCommandReceiverLogic logic = new PhoneCommandReceiverLogic(handler, Runnable::run);
        ThrowingSource source = new ThrowingSource("extract-failure");
        CaptureSink sink = new CaptureSink();

        logic.receive(source, sink);

        assertEquals(ConfigContract.RESULT_INVALID_REQUEST, sink.outcome.resultCode());
        assertEquals("extract-failure", sink.outcome.requestId());
        assertEquals(1, source.removePinCount);
        assertEquals(1, sink.setResultCount);
        assertEquals(1, sink.finishCount);
    }

    @Test
    public void submissionFailureRemovesPinAndCompletesExactlyOnce() {
        repository.save(SlotRecord.armed(0, ICCID, "1234"));
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("simulated rejection");
        };
        PhoneCommandReceiverLogic logic = new PhoneCommandReceiverLogic(handler, rejectingExecutor);
        FixedSource source = new FixedSource(new PhoneCommandReceiverLogic.Request(
                ConfigContract.ACTION_CLEAR_SLOT,
                "submission-failure",
                0,
                null,
                false,
                false,
                false));
        CaptureSink sink = new CaptureSink();

        logic.receive(source, sink);

        assertEquals(ConfigContract.RESULT_STORAGE_ERROR, sink.outcome.resultCode());
        assertEquals("submission-failure", sink.outcome.requestId());
        assertTrue(repository.load(0).isPresent());
        assertEquals(1, source.removePinCount);
        assertEquals(1, sink.setResultCount);
        assertEquals(1, sink.finishCount);
    }

    private static final class FixedSource implements PhoneCommandReceiverLogic.RequestSource {
        private final PhoneCommandReceiverLogic.Request request;
        private int removePinCount;

        private FixedSource(PhoneCommandReceiverLogic.Request request) {
            this.request = request;
        }

        @Override
        public PhoneCommandReceiverLogic.Request extract() {
            return request;
        }

        @Override
        public String requestId() {
            return request.requestId();
        }

        @Override
        public void removePin() {
            removePinCount++;
        }
    }

    private static final class ThrowingSource implements PhoneCommandReceiverLogic.RequestSource {
        private final String requestId;
        private int removePinCount;

        private ThrowingSource(String requestId) {
            this.requestId = requestId;
        }

        @Override
        public PhoneCommandReceiverLogic.Request extract() {
            throw new IllegalStateException("simulated unparcel failure");
        }

        @Override
        public String requestId() {
            return requestId;
        }

        @Override
        public void removePin() {
            removePinCount++;
        }
    }

    private static final class CaptureSink implements PhoneCommandReceiverLogic.ResultSink {
        private PhoneCommandReceiverLogic.Outcome outcome;
        private int setResultCount;
        private int finishCount;

        @Override
        public void setResult(PhoneCommandReceiverLogic.Outcome value) {
            outcome = value;
            setResultCount++;
        }

        @Override
        public void finish() {
            finishCount++;
        }
    }

    private static final class MemoryRepository implements CredentialRepository {
        private final Map<Integer, SlotRecord> records = new HashMap<>();

        @Override
        public Optional<SlotRecord> load(int slotId) {
            return Optional.ofNullable(records.get(slotId));
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
    }
}
