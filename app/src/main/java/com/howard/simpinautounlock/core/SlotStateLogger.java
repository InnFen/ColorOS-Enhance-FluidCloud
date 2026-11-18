package com.howard.simpinautounlock.core;

@FunctionalInterface
public interface SlotStateLogger {
    SlotStateLogger NO_OP = (slotId, bootCount, transition) -> {};

    void log(int slotId, int bootCount, String transition);
}
