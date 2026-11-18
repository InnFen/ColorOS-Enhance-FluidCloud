package com.howard.simpinautounlock.pending;

import java.util.Optional;

public interface PendingPinRepository {
    void stage(int slotId, String pin);
    PendingPinStatus status(int slotId);
    default Optional<Claim> peek(int slotId) { return Optional.empty(); }
    Optional<Claim> claim(int slotId);
    boolean acknowledge(int slotId, String token);
    void clear(int slotId);
    void clearAll();

    record Claim(String pin, String token) {}
}
