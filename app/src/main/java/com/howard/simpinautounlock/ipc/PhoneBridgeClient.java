package com.howard.simpinautounlock.ipc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.howard.simpinautounlock.core.SlotStatus;

public final class PhoneBridgeClient {
    private static final long RESPONSE_TIMEOUT_MILLIS = 3_000L;

    private final Context context;
    private final Handler handler;

    public PhoneBridgeClient(Context context) {
        this.context = context.getApplicationContext();
        handler = new Handler(Looper.getMainLooper());
    }

    public void query(int slotId, Callback callback) {
        Intent request = newRequest(ConfigContract.ACTION_QUERY)
                .putExtra(ConfigContract.EXTRA_SLOT_ID, slotId);
        send(request, slotId, callback);
    }

    public void configure(int slotId, String pin, Callback callback) {
        Intent request = newRequest(ConfigContract.ACTION_CONFIGURE)
                .putExtra(ConfigContract.EXTRA_SLOT_ID, slotId)
                .putExtra(ConfigContract.EXTRA_PIN, pin);
        try {
            send(request, null, callback);
        } finally {
            request.removeExtra(ConfigContract.EXTRA_PIN);
        }
    }

    public void setEnabled(int slotId, boolean enabled, Callback callback) {
        Intent request = newRequest(ConfigContract.ACTION_SET_ENABLED)
                .putExtra(ConfigContract.EXTRA_SLOT_ID, slotId)
                .putExtra(ConfigContract.EXTRA_ENABLED, enabled);
        send(request, null, callback);
    }

    public void clearSlot(int slotId, Callback callback) {
        Intent request = newRequest(ConfigContract.ACTION_CLEAR_SLOT)
                .putExtra(ConfigContract.EXTRA_SLOT_ID, slotId);
        send(request, null, callback);
    }

    public void clearAll(Callback callback) {
        send(newRequest(ConfigContract.ACTION_CLEAR_ALL), null, callback);
    }

    private Intent newRequest(String action) {
        return new Intent(action)
                .setPackage(ConfigContract.TARGET_PACKAGE)
                .putExtra(
                        ConfigContract.EXTRA_REQUEST_ID,
                        PhoneBridgeResponseLogic.newRequestId());
    }

    private void send(Intent request, Integer querySlot, Callback callback) {
        String requestId = request.getStringExtra(ConfigContract.EXTRA_REQUEST_ID);
        RequestOperation operation = new RequestOperation(requestId, querySlot, callback);
        handler.postDelayed(operation.timeout(), RESPONSE_TIMEOUT_MILLIS);
        try {
            context.sendOrderedBroadcast(
                    request,
                    null,
                    operation.receiver(),
                    handler,
                    0,
                    null,
                    null);
        } catch (RuntimeException error) {
            operation.failUnavailable();
        }
    }

    public interface Callback {
        void onResult(Result result);
    }

    public record Result(int resultCode, SlotStatus status) {}

    private final class RequestOperation {
        private final PhoneBridgeResponseLogic.Gate gate;
        private final Integer querySlot;
        private final Callback callback;
        private final Runnable timeout = this::onTimeout;
        private final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onBroadcastResult(getResultCode(), getResultExtras(false));
            }
        };

        private RequestOperation(String requestId, Integer querySlot, Callback callback) {
            gate = new PhoneBridgeResponseLogic.Gate(requestId);
            this.querySlot = querySlot;
            this.callback = callback;
        }

        private Runnable timeout() {
            return timeout;
        }

        private BroadcastReceiver receiver() {
            return receiver;
        }

        private void onBroadcastResult(int resultCode, Bundle extras) {
            String echoedRequestId = safeString(extras, ConfigContract.EXTRA_REQUEST_ID);
            PhoneBridgeResponseLogic.Decision decision =
                    gate.onResponse(echoedRequestId, resultCode);
            if (!decision.shouldComplete()) {
                return;
            }
            handler.removeCallbacks(timeout);

            SlotStatus status = null;
            int safeResultCode = decision.resultCode();
            if (querySlot != null && safeResultCode == ConfigContract.RESULT_OK) {
                status = parseStatus(querySlot, extras);
                if (status == null) {
                    safeResultCode = ConfigContract.RESULT_MODULE_UNAVAILABLE;
                }
            }
            callback.onResult(new Result(safeResultCode, status));
        }

        private void onTimeout() {
            PhoneBridgeResponseLogic.Decision decision = gate.onTimeout();
            if (decision.shouldComplete()) {
                callback.onResult(new Result(decision.resultCode(), null));
            }
        }

        private void failUnavailable() {
            handler.removeCallbacks(timeout);
            PhoneBridgeResponseLogic.Decision decision = gate.onTimeout();
            if (decision.shouldComplete()) {
                callback.onResult(new Result(decision.resultCode(), null));
            }
        }
    }

    private static SlotStatus parseStatus(int expectedSlot, Bundle extras) {
        try {
            PhoneBridgeResponseLogic.RawStatus raw = new PhoneBridgeResponseLogic.RawStatus(
                    safeInteger(extras, ConfigContract.EXTRA_SLOT_ID),
                    safeString(extras, ConfigContract.EXTRA_STATE),
                    safeString(extras, ConfigContract.EXTRA_BOUND_ICCID_SUFFIX),
                    safeString(extras, ConfigContract.EXTRA_CURRENT_ICCID_SUFFIX),
                    safeBoolean(extras, ConfigContract.EXTRA_SIM_MATCHES),
                    safeInteger(extras, ConfigContract.EXTRA_LAST_RESULT),
                    safeInteger(extras, ConfigContract.EXTRA_REMAINING_ATTEMPTS),
                    safeInteger(extras, ConfigContract.EXTRA_LAST_ATTEMPT_BOOT));
            return PhoneBridgeResponseLogic.parseStatus(expectedSlot, raw);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String safeString(Bundle extras, String key) {
        if (extras == null) {
            return null;
        }
        try {
            return extras.getString(key);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Integer safeInteger(Bundle extras, String key) {
        if (extras == null) {
            return null;
        }
        try {
            return extras.getSerializable(key, Integer.class);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Boolean safeBoolean(Bundle extras, String key) {
        if (extras == null) {
            return null;
        }
        try {
            return extras.getSerializable(key, Boolean.class);
        } catch (RuntimeException error) {
            return null;
        }
    }
}
