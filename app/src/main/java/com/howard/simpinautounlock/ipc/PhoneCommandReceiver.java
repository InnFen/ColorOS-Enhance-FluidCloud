package com.howard.simpinautounlock.ipc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.util.concurrent.Executors;

public final class PhoneCommandReceiver extends BroadcastReceiver {
    private final PhoneCommandReceiverLogic logic;

    public PhoneCommandReceiver(PhoneCommandHandler handler) {
        logic = new PhoneCommandReceiverLogic(handler, Executors.newSingleThreadExecutor());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        logic.receive(new IntentRequestSource(intent), new PendingResultSink(pendingResult));
    }

    private static final class IntentRequestSource
            implements PhoneCommandReceiverLogic.RequestSource {
        private final Intent intent;
        private String requestId;

        private IntentRequestSource(Intent intent) {
            this.intent = intent;
        }

        @Override
        public PhoneCommandReceiverLogic.Request extract() {
            String action = intent.getAction();
            requestId = intent.getStringExtra(ConfigContract.EXTRA_REQUEST_ID);
            String pin = ConfigContract.ACTION_CONFIGURE.equals(action)
                    ? intent.getStringExtra(ConfigContract.EXTRA_PIN)
                    : null;
            int slotId = requiresSlot(action)
                    ? intent.getIntExtra(ConfigContract.EXTRA_SLOT_ID, -1)
                    : -1;
            if (ConfigContract.ACTION_SET_ENABLED.equals(action)) {
                boolean enabledPresent = intent.hasExtra(ConfigContract.EXTRA_ENABLED);
                Bundle extras = enabledPresent ? intent.getExtras() : null;
                Boolean rawEnabled = extras == null
                        ? null
                        : extras.getSerializable(ConfigContract.EXTRA_ENABLED, Boolean.class);
                return PhoneCommandReceiverLogic.Request.fromRawEnabled(
                        action,
                        requestId,
                        slotId,
                        enabledPresent,
                        rawEnabled);
            }
            return new PhoneCommandReceiverLogic.Request(
                    action,
                    requestId,
                    slotId,
                    pin,
                    false,
                    false,
                    false);
        }

        @Override
        public String requestId() {
            return requestId;
        }

        @Override
        public void removePin() {
            intent.removeExtra(ConfigContract.EXTRA_PIN);
        }
    }

    private static final class PendingResultSink
            implements PhoneCommandReceiverLogic.ResultSink {
        private final PendingResult pendingResult;

        private PendingResultSink(PendingResult pendingResult) {
            this.pendingResult = pendingResult;
        }

        @Override
        public void setResult(PhoneCommandReceiverLogic.Outcome outcome) {
            Bundle response = new Bundle();
            response.putString(ConfigContract.EXTRA_REQUEST_ID, outcome.requestId());
            if (outcome.status() != null) {
                response.putInt(ConfigContract.EXTRA_SLOT_ID, outcome.status().slotId());
                response.putString(
                        ConfigContract.EXTRA_STATE, outcome.status().state().name());
                response.putString(
                        ConfigContract.EXTRA_BOUND_ICCID_SUFFIX,
                        outcome.status().boundIccidSuffix());
                response.putString(
                        ConfigContract.EXTRA_CURRENT_ICCID_SUFFIX,
                        outcome.status().currentIccidSuffix());
                response.putBoolean(
                        ConfigContract.EXTRA_SIM_MATCHES, outcome.status().simMatches());
                response.putInt(
                        ConfigContract.EXTRA_LAST_RESULT, outcome.status().lastResultCode());
                response.putInt(
                        ConfigContract.EXTRA_REMAINING_ATTEMPTS,
                        outcome.status().remainingAttempts());
                response.putInt(
                        ConfigContract.EXTRA_LAST_ATTEMPT_BOOT,
                        outcome.status().lastAttemptBootCount());
            }
            pendingResult.setResultCode(outcome.resultCode());
            pendingResult.setResultExtras(response);
        }

        @Override
        public void finish() {
            pendingResult.finish();
        }
    }

    private static boolean requiresSlot(String action) {
        return ConfigContract.ACTION_CONFIGURE.equals(action)
                || ConfigContract.ACTION_SET_ENABLED.equals(action)
                || ConfigContract.ACTION_CLEAR_SLOT.equals(action)
                || ConfigContract.ACTION_QUERY.equals(action);
    }
}
