package com.howard.simpinautounlock.pending;

import com.howard.simpinautounlock.core.PendingPinSource;
import com.howard.simpinautounlock.hook.SafeLog;
import java.util.Optional;

public final class RepositoryPendingPinSource implements PendingPinSource {
    private final PendingPinRepository repository;

    public RepositoryPendingPinSource(PendingPinRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Claim> claim(int slotId) {
        return resolve(slotId).claim();
    }

    @Override
    public Resolution resolve(int slotId) {
        SafeLog.pendingBoundary(slotId, "REMOTE_CLAIM_STARTED");
        try {
            Optional<Claim> claim = repository.peek(slotId)
                    .map(value -> new Claim(value.pin(), value.token()));
            SafeLog.pendingBoundary(
                    slotId, claim.isPresent() ? "REMOTE_CLAIM_RETURNED" : "REMOTE_NO_CLAIM");
            return new Resolution(claim, true);
        } catch (RuntimeException error) {
            SafeLog.pendingBoundary(slotId, "REMOTE_CLAIM_FAILED");
            return new Resolution(Optional.empty(), false);
        }
    }

    @Override
    public boolean acknowledge(int slotId, String token) {
        SafeLog.pendingBoundary(slotId, "REMOTE_ACK_DEFERRED");
        return false;
    }
}
