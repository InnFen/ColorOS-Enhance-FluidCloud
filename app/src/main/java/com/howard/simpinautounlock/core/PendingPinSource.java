package com.howard.simpinautounlock.core;

import java.util.Optional;

public interface PendingPinSource {
    PendingPinSource NONE = new PendingPinSource() {
        @Override public Optional<Claim> claim(int slotId) { return Optional.empty(); }
        @Override public boolean acknowledge(int slotId, String token) { return false; }
    };

    Optional<Claim> claim(int slotId);

    default Resolution resolve(int slotId) {
        return new Resolution(claim(slotId), true);
    }

    boolean acknowledge(int slotId, String token);

    record Claim(String pin, String token) {}

    record Resolution(Optional<Claim> claim, boolean allowFallback) {}
}
