package com.howard.simpinautounlock.pending;

import android.net.Uri;

public final class PendingPinContract {
    public static final String AUTHORITY = dev.howard.colorosenhance.BuildConfig.APPLICATION_ID + ".sim.pending-pins";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_CLAIM = "claimPendingPin";
    public static final String METHOD_ACK = "ackPendingPin";
    public static final String EXTRA_SLOT_ID = "slot_id";
    public static final String EXTRA_PIN = "pin";
    public static final String EXTRA_CLAIM_TOKEN = "claim_token";
    public static final String EXTRA_SUCCESS = "success";

    private PendingPinContract() {}
}
