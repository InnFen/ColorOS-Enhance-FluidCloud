package com.howard.simpinautounlock.ipc;

import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class PhoneBridgeResponseLogic {
    private PhoneBridgeResponseLogic() {}

    static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    static SlotStatus parseStatus(int expectedSlot, RawStatus raw) {
        if (raw == null
                || raw.slotId() == null
                || raw.slotId() != expectedSlot
                || raw.state() == null
                || raw.boundIccidSuffix() == null
                || raw.currentIccidSuffix() == null
                || raw.simMatches() == null
                || raw.lastResultCode() == null
                || raw.remainingAttempts() == null
                || raw.lastAttemptBootCount() == null
                || !safeSuffix(raw.boundIccidSuffix())
                || !safeSuffix(raw.currentIccidSuffix())
                || raw.remainingAttempts() < -1
                || raw.lastAttemptBootCount() < -1) {
            return null;
        }

        SlotState state;
        try {
            state = SlotState.valueOf(raw.state());
        } catch (IllegalArgumentException error) {
            return null;
        }
        if (!consistentStatus(
                state,
                raw.boundIccidSuffix(),
                raw.currentIccidSuffix(),
                raw.simMatches())) {
            return null;
        }
        return new SlotStatus(
                expectedSlot,
                state,
                raw.boundIccidSuffix(),
                raw.currentIccidSuffix(),
                raw.simMatches(),
                raw.lastResultCode(),
                raw.remainingAttempts(),
                raw.lastAttemptBootCount());
    }

    private static boolean consistentStatus(
            SlotState state,
            String boundSuffix,
            String currentSuffix,
            boolean simMatches) {
        return switch (state) {
            case EMPTY -> boundSuffix.isEmpty() && !simMatches;
            case ARMED, DISABLED, PENDING_RESULT ->
                    fullSuffix(boundSuffix)
                            && ((currentSuffix.isEmpty() && !simMatches)
                            || (boundSuffix.equals(currentSuffix) && simMatches));
            case SIM_MISMATCH ->
                    fullSuffix(boundSuffix)
                            && !simMatches
                            && (currentSuffix.isEmpty()
                            || (fullSuffix(currentSuffix)
                            && !boundSuffix.equals(currentSuffix)));
            case NEEDS_CONFIRMATION ->
                    !simMatches
                            || (fullSuffix(boundSuffix)
                            && boundSuffix.equals(currentSuffix));
        };
    }

    private static boolean fullSuffix(String value) {
        return value.length() == 4;
    }

    private static boolean safeSuffix(String value) {
        if (value.isEmpty()) {
            return true;
        }
        if (value.length() != 4) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    record RawStatus(
            Integer slotId,
            String state,
            String boundIccidSuffix,
            String currentIccidSuffix,
            Boolean simMatches,
            Integer lastResultCode,
            Integer remainingAttempts,
            Integer lastAttemptBootCount) {}

    record Decision(boolean shouldComplete, int resultCode) {
        private static Decision ignored() {
            return new Decision(false, ConfigContract.RESULT_MODULE_UNAVAILABLE);
        }
    }

    static final class Gate {
        private final String expectedRequestId;
        private final AtomicBoolean completed = new AtomicBoolean();

        Gate(String expectedRequestId) {
            this.expectedRequestId = expectedRequestId;
        }

        Decision onResponse(String echoedRequestId, int resultCode) {
            if (!completed.compareAndSet(false, true)) {
                return Decision.ignored();
            }
            if (!expectedRequestId.equals(echoedRequestId)) {
                return new Decision(true, ConfigContract.RESULT_MODULE_UNAVAILABLE);
            }
            return new Decision(true, resultCode);
        }

        Decision onTimeout() {
            if (!completed.compareAndSet(false, true)) {
                return Decision.ignored();
            }
            return new Decision(true, ConfigContract.RESULT_MODULE_UNAVAILABLE);
        }
    }
}
