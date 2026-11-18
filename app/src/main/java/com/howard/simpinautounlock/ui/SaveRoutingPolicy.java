package com.howard.simpinautounlock.ui;

import com.howard.simpinautounlock.ipc.ConfigContract;

public final class SaveRoutingPolicy {
    private SaveRoutingPolicy() {}

    public enum Availability {
        UNKNOWN,
        AVAILABLE,
        UNAVAILABLE
    }

    public enum Route {
        PHONE_BRIDGE,
        LOCAL_STAGE,
        REPORT_ERROR
    }

    public static Route initial(Availability availability) {
        return availability == Availability.UNAVAILABLE
                ? Route.LOCAL_STAGE
                : Route.PHONE_BRIDGE;
    }

    public static Route afterBridgeResult(int resultCode) {
        if (resultCode == ConfigContract.RESULT_OK) {
            return Route.PHONE_BRIDGE;
        }
        if (resultCode == ConfigContract.RESULT_MODULE_UNAVAILABLE) {
            return Route.LOCAL_STAGE;
        }
        return Route.REPORT_ERROR;
    }
}
