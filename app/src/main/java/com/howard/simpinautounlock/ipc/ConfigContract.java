package com.howard.simpinautounlock.ipc;

public final class ConfigContract {
    private static final String ID = dev.howard.colorosenhance.BuildConfig.APPLICATION_ID;
    public static final String TARGET_PACKAGE = "com.android.phone";
    public static final String PERMISSION =
            ID + ".sim.permission.CONFIGURE";
    public static final String ACTION_CONFIGURE =
            ID + ".sim.action.CONFIGURE_SLOT";
    public static final String ACTION_SET_ENABLED =
            ID + ".sim.action.SET_ENABLED";
    public static final String ACTION_CLEAR_SLOT =
            ID + ".sim.action.CLEAR_SLOT";
    public static final String ACTION_CLEAR_ALL =
            ID + ".sim.action.CLEAR_ALL";
    public static final String ACTION_QUERY =
            ID + ".sim.action.QUERY";
    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_SLOT_ID = "slot_id";
    public static final String EXTRA_PIN = "pin";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_BOUND_ICCID_SUFFIX = "bound_iccid_suffix";
    public static final String EXTRA_CURRENT_ICCID_SUFFIX = "current_iccid_suffix";
    public static final String EXTRA_SIM_MATCHES = "sim_matches";
    public static final String EXTRA_LAST_RESULT = "last_result";
    public static final String EXTRA_REMAINING_ATTEMPTS = "remaining_attempts";
    public static final String EXTRA_LAST_ATTEMPT_BOOT = "last_attempt_boot";
    public static final int RESULT_OK = 100;
    public static final int RESULT_INVALID_REQUEST = 101;
    public static final int RESULT_SIM_UNAVAILABLE = 102;
    public static final int RESULT_STORAGE_ERROR = 103;
    public static final int RESULT_MODULE_UNAVAILABLE = 104;

    private ConfigContract() {}
}
