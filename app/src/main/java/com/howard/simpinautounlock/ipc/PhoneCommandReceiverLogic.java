package com.howard.simpinautounlock.ipc;

import com.howard.simpinautounlock.core.SlotStatus;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

final class PhoneCommandReceiverLogic {
    private final PhoneCommandHandler handler;
    private final Executor executor;

    PhoneCommandReceiverLogic(PhoneCommandHandler handler, Executor executor) {
        this.handler = handler;
        this.executor = executor;
    }

    void receive(RequestSource source, ResultSink sink) {
        Completion completion = new Completion(sink);
        Request request = null;
        boolean extractionFailed = false;
        boolean pinRemovalFailed = false;
        try {
            request = source.extract();
        } catch (RuntimeException error) {
            extractionFailed = true;
        } finally {
            try {
                source.removePin();
            } catch (RuntimeException error) {
                pinRemovalFailed = true;
            }
        }

        if (extractionFailed || pinRemovalFailed || request == null) {
            completion.complete(new Outcome(
                    ConfigContract.RESULT_INVALID_REQUEST,
                    safeRequestId(source),
                    null));
            return;
        }

        Request submittedRequest = request;
        try {
            executor.execute(() -> {
                Outcome outcome;
                try {
                    outcome = dispatch(submittedRequest);
                } catch (RuntimeException error) {
                    outcome = new Outcome(
                            ConfigContract.RESULT_STORAGE_ERROR,
                            submittedRequest.requestId(),
                            null);
                }
                completion.complete(outcome);
            });
        } catch (RuntimeException error) {
            completion.complete(new Outcome(
                    ConfigContract.RESULT_STORAGE_ERROR,
                    submittedRequest.requestId(),
                    null));
        }
    }

    private Outcome dispatch(Request request) {
        int resultCode;
        SlotStatus status = null;
        if (ConfigContract.ACTION_CONFIGURE.equals(request.action())) {
            resultCode = handler.configure(request.slotId(), request.pin());
        } else if (ConfigContract.ACTION_SET_ENABLED.equals(request.action())) {
            if (!request.enabledPresent() || !request.enabledValid()) {
                resultCode = ConfigContract.RESULT_INVALID_REQUEST;
            } else {
                resultCode = handler.setEnabled(request.slotId(), request.enabled());
            }
        } else if (ConfigContract.ACTION_CLEAR_SLOT.equals(request.action())) {
            resultCode = handler.clearSlot(request.slotId());
        } else if (ConfigContract.ACTION_CLEAR_ALL.equals(request.action())) {
            resultCode = handler.clearAll();
        } else if (ConfigContract.ACTION_QUERY.equals(request.action())) {
            if (request.slotId() != 0 && request.slotId() != 1) {
                resultCode = ConfigContract.RESULT_INVALID_REQUEST;
            } else {
                status = handler.querySlot(request.slotId());
                resultCode = ConfigContract.RESULT_OK;
            }
        } else {
            resultCode = ConfigContract.RESULT_INVALID_REQUEST;
        }
        return new Outcome(resultCode, request.requestId(), status);
    }

    private static String safeRequestId(RequestSource source) {
        try {
            return source.requestId();
        } catch (RuntimeException error) {
            return null;
        }
    }

    record Request(
            String action,
            String requestId,
            int slotId,
            String pin,
            boolean enabledPresent,
            boolean enabledValid,
            boolean enabled) {

        static Request fromRawEnabled(
                String action,
                String requestId,
                int slotId,
                boolean enabledPresent,
                Object rawEnabled) {
            boolean enabledValid = enabledPresent && rawEnabled instanceof Boolean;
            boolean enabled = enabledValid && (Boolean) rawEnabled;
            return new Request(
                    action,
                    requestId,
                    slotId,
                    null,
                    enabledPresent,
                    enabledValid,
                    enabled);
        }
    }

    record Outcome(int resultCode, String requestId, SlotStatus status) {}

    interface RequestSource {
        Request extract();

        String requestId();

        void removePin();
    }

    interface ResultSink {
        void setResult(Outcome outcome);

        void finish();
    }

    private static final class Completion {
        private final ResultSink sink;
        private final AtomicBoolean completed = new AtomicBoolean();

        private Completion(ResultSink sink) {
            this.sink = sink;
        }

        private void complete(Outcome outcome) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            try {
                sink.setResult(outcome);
            } finally {
                sink.finish();
            }
        }
    }
}
