package com.howard.simpinautounlock.pending;

import com.howard.simpinautounlock.core.PendingPinSource;
import com.howard.simpinautounlock.hook.SafeLog;
import java.util.Objects;
import java.util.Optional;

public final class ConsumingPendingPinSource implements PendingPinSource {
    private final PendingPinSource delegate;
    private final PendingPinGenerationGate generationGate;

    public ConsumingPendingPinSource(
            PendingPinSource delegate,
            PendingPinGenerationGate generationGate) {
        this.delegate = Objects.requireNonNull(delegate);
        this.generationGate = Objects.requireNonNull(generationGate);
    }

    @Override
    public Optional<Claim> claim(int slotId) {
        return resolve(slotId).claim();
    }

    @Override
    public Resolution resolve(int slotId) {
        Resolution candidateResolution = delegate.resolve(slotId);
        if (candidateResolution.claim().isEmpty()) {
            return candidateResolution;
        }
        Claim claim = candidateResolution.claim().orElseThrow();
        PendingPinGenerationGate.Result result = generationGate.consume(slotId, claim.token());
        if (result == PendingPinGenerationGate.Result.ACCEPTED) {
            SafeLog.pendingBoundary(slotId, "REMOTE_GENERATION_ACCEPTED");
            return new Resolution(Optional.of(claim), false);
        }
        if (result == PendingPinGenerationGate.Result.ALREADY_CONSUMED) {
            SafeLog.pendingBoundary(slotId, "REMOTE_GENERATION_ALREADY_CONSUMED");
            return new Resolution(Optional.empty(), true);
        }
        if (result == PendingPinGenerationGate.Result.IN_PROGRESS) {
            SafeLog.pendingBoundary(slotId, "REMOTE_GENERATION_IN_PROGRESS");
            return new Resolution(Optional.empty(), false);
        }
        SafeLog.pendingBoundary(slotId, "REMOTE_GENERATION_GATE_UNAVAILABLE");
        return new Resolution(Optional.empty(), false);
    }

    @Override
    public boolean acknowledge(int slotId, String token) {
        try {
            boolean acknowledged = generationGate.acknowledge(slotId, token);
            SafeLog.pendingBoundary(
                    slotId,
                    acknowledged
                            ? "REMOTE_CONSUMPTION_RECORDED"
                            : "REMOTE_CONSUMPTION_RECORD_FAILED");
            return acknowledged;
        } catch (RuntimeException error) {
            SafeLog.pendingBoundary(slotId, "REMOTE_CONSUMPTION_RECORD_FAILED");
            return false;
        }
    }
}
