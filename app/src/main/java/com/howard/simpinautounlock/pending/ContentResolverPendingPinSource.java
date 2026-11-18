package com.howard.simpinautounlock.pending;

import android.content.ContentResolver;
import android.os.Bundle;
import com.howard.simpinautounlock.core.PendingPinSource;
import com.howard.simpinautounlock.hook.SafeLog;
import java.util.Optional;

public final class ContentResolverPendingPinSource implements PendingPinSource {
    private final ContentResolver contentResolver;
    private final PendingPinResponseLogic responseLogic = new PendingPinResponseLogic();

    public ContentResolverPendingPinSource(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    @Override
    public Optional<Claim> claim(int slotId) {
        SafeLog.pendingBoundary(slotId, "SOURCE_CALL_STARTED");
        try {
            Bundle request = new Bundle();
            request.putInt(PendingPinContract.EXTRA_SLOT_ID, slotId);
            Bundle response = contentResolver.call(
                    PendingPinContract.URI,
                    PendingPinContract.METHOD_CLAIM,
                    null,
                    request);
            if (response == null) {
                SafeLog.pendingBoundary(slotId, "SOURCE_RESPONSE_MISSING");
                return Optional.empty();
            }
            Optional<Claim> claim = responseLogic.parseClaim(
                    slotId,
                    rawValue(response, PendingPinContract.EXTRA_SLOT_ID),
                    rawValue(response, PendingPinContract.EXTRA_PIN),
                    rawValue(response, PendingPinContract.EXTRA_CLAIM_TOKEN));
            SafeLog.pendingBoundary(
                    slotId, claim.isPresent() ? "SOURCE_CLAIM_RETURNED" : "SOURCE_NO_CLAIM");
            return claim;
        } catch (SecurityException ignored) {
            SafeLog.pendingBoundary(slotId, "SOURCE_SECURITY_FAILURE");
            return Optional.empty();
        } catch (RuntimeException ignored) {
            SafeLog.pendingBoundary(slotId, "SOURCE_CALL_FAILED");
            return Optional.empty();
        }
    }

    @Override
    public boolean acknowledge(int slotId, String token) {
        try {
            Bundle request = new Bundle();
            request.putInt(PendingPinContract.EXTRA_SLOT_ID, slotId);
            request.putString(PendingPinContract.EXTRA_CLAIM_TOKEN, token);
            Bundle response = contentResolver.call(
                    PendingPinContract.URI,
                    PendingPinContract.METHOD_ACK,
                    null,
                    request);
            return response != null && responseLogic.parseAcknowledged(
                    rawValue(response, PendingPinContract.EXTRA_SUCCESS));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation") // BaseBundle#get preserves raw types for validation.
    private static Object rawValue(Bundle bundle, String key) {
        return bundle.get(key);
    }
}
