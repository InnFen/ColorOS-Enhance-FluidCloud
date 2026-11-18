package com.howard.simpinautounlock.ui;

import static org.junit.Assert.assertEquals;

import com.howard.simpinautounlock.ipc.ConfigContract;
import org.junit.Test;

public final class SaveRoutingPolicyTest {
    @Test
    public void knownUnavailableSlotStagesWithoutCallingPhoneBridge() {
        assertEquals(
                SaveRoutingPolicy.Route.LOCAL_STAGE,
                SaveRoutingPolicy.initial(SaveRoutingPolicy.Availability.UNAVAILABLE));
    }

    @Test
    public void unknownAndAvailableSlotsTryPhoneBridgeFirst() {
        assertEquals(
                SaveRoutingPolicy.Route.PHONE_BRIDGE,
                SaveRoutingPolicy.initial(SaveRoutingPolicy.Availability.UNKNOWN));
        assertEquals(
                SaveRoutingPolicy.Route.PHONE_BRIDGE,
                SaveRoutingPolicy.initial(SaveRoutingPolicy.Availability.AVAILABLE));
    }

    @Test
    public void onlyModuleUnavailableFallsBackAfterPhoneBridgeResult() {
        assertEquals(
                SaveRoutingPolicy.Route.LOCAL_STAGE,
                SaveRoutingPolicy.afterBridgeResult(ConfigContract.RESULT_MODULE_UNAVAILABLE));

        for (int resultCode : new int[] {
                ConfigContract.RESULT_SIM_UNAVAILABLE,
                ConfigContract.RESULT_STORAGE_ERROR,
                ConfigContract.RESULT_INVALID_REQUEST,
                999
        }) {
            assertEquals(
                    Integer.toString(resultCode),
                    SaveRoutingPolicy.Route.REPORT_ERROR,
                    SaveRoutingPolicy.afterBridgeResult(resultCode));
        }
    }

    @Test
    public void successfulPhoneSaveRemainsOnPhoneRoute() {
        assertEquals(
                SaveRoutingPolicy.Route.PHONE_BRIDGE,
                SaveRoutingPolicy.afterBridgeResult(ConfigContract.RESULT_OK));
    }
}
