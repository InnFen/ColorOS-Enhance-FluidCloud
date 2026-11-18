package dev.howard.colorosenhance.bridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class BridgeCallerPolicyTest {
    @Test public void ownAppAndVerifiedOwnerSettingsMayUseBridge() {
        assertTrue(BridgeCallerPolicy.allow(10456,10456,1000,true));
        assertTrue(BridgeCallerPolicy.allow(1000,10456,1000,true));
    }
    @Test public void packageClaimsPrivilegedShellAndOtherUsersCannotBypassUidBoundary() {
        for (int caller : new int[]{0,2000,10555,101000,110456})
            assertFalse(BridgeCallerPolicy.allow(caller,10456,1000,true));
        assertFalse(BridgeCallerPolicy.allow(1000,10456,1000,false));
        assertFalse(BridgeCallerPolicy.allow(10555,10456,10555,true));
        assertFalse(BridgeCallerPolicy.allow(1000,110456,1000,true));
    }
}
