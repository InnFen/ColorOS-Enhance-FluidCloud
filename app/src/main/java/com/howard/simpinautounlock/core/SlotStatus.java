package com.howard.simpinautounlock.core;

public record SlotStatus(
        int slotId,
        SlotState state,
        String boundIccidSuffix,
        String currentIccidSuffix,
        boolean simMatches,
        int lastResultCode,
        int remainingAttempts,
        int lastAttemptBootCount) {}
