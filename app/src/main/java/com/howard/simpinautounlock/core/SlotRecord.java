package com.howard.simpinautounlock.core;

public record SlotRecord(
        int slotId,
        String iccid,
        String pin,
        SlotState state,
        int lastAttemptBootCount,
        int lastResultCode,
        int remainingAttempts) {

    public static final int RESULT_UNKNOWN = -1;
    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_FAILURE = 1;

    public static SlotRecord armed(int slotId, String iccid, String pin) {
        return new SlotRecord(slotId, iccid, pin, SlotState.ARMED, -1, RESULT_UNKNOWN, -1);
    }

    public SlotRecord transition(SlotState next, int bootCount, int result, int remaining) {
        return new SlotRecord(slotId, iccid, pin, next, bootCount, result, remaining);
    }
}
