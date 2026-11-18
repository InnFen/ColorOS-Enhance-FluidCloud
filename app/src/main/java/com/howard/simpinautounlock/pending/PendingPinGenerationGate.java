package com.howard.simpinautounlock.pending;

@FunctionalInterface
public interface PendingPinGenerationGate {
    Result consume(int slotId, String generation);

    default boolean acknowledge(int slotId, String generation) {
        return true;
    }

    enum Result {
        ACCEPTED,
        ALREADY_CONSUMED,
        IN_PROGRESS,
        UNAVAILABLE
    }
}
