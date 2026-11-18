package dev.howard.colorosenhance.icons;

import dev.howard.colorosenhance.SettingsCoordinator;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StatusIconConfigTest {
    @Test public void disabledAndMissingPreferencesLeaveEveryLocationNative() {
        var config=StatusIconConfig.from(Map.of("shade_only.wifi",true,"bluetooth_mode",2));
        for(String location:List.of("HOME","QS","KEYGUARD","SHADE_CARRIER_GROUP"))
            for(String slot:List.of("wifi","bluetooth","clock","battery"))
                assertFalse(config.restricts(slot,location,new StatusIconConfig.BluetoothState(true,false,false)));
        assertEquals(0,StatusIconConfig.from(Map.of("bluetooth_mode",99)).bluetoothMode());
    }
    @Test public void shadeRulesDoNotAffectHeadersLockscreenOrUnselectedSlots() {
        var config=StatusIconConfig.from(Map.of("enabled",true,"shade_only.wifi",true,"shade_only.clock",true,"keyguard_follow",false));
        assertTrue(config.restricts("wifi","HOME",StatusIconConfig.BluetoothState.unknown()));
        assertFalse(config.restricts("mobile","HOME",StatusIconConfig.BluetoothState.unknown()));
        for(String location:List.of("QS","KEYGUARD","SHADE_CARRIER_GROUP","unknown"))
            assertFalse(config.restricts("wifi",location,StatusIconConfig.BluetoothState.unknown()));
        assertFalse(config.restricts("wifi",null,StatusIconConfig.BluetoothState.unknown()));
    }
    @Test public void bluetoothRulesComposeWithShadeOnlyAndUnknownStateFailsOpen() {
        var connected=StatusIconConfig.from(Map.of("enabled",true,"bluetooth_mode",1));
        var qualified=StatusIconConfig.from(Map.of("enabled",true,"bluetooth_mode",2,"shade_only.bluetooth",true,"keyguard_follow",false));
        var no=new StatusIconConfig.BluetoothState(true,false,false);
        var unqualified=new StatusIconConfig.BluetoothState(true,true,false);
        var yes=new StatusIconConfig.BluetoothState(true,true,true);
        assertTrue(connected.restricts("bluetooth","HOME",no));
        assertFalse(connected.restricts("bluetooth","HOME",unqualified));
        assertTrue(qualified.restricts("bluetooth","QS",unqualified));
        assertFalse(qualified.restricts("bluetooth","QS",yes));
        assertTrue(qualified.restricts("bluetooth","HOME",yes));
        assertFalse(qualified.restricts("bluetooth","QS",StatusIconConfig.BluetoothState.unknown()));
        assertFalse(qualified.restricts("bluetooth","QS",new StatusIconConfig.BluetoothState(true,true,null)));
        assertFalse(qualified.restricts("bluetooth","KEYGUARD",no));
    }
    @Test public void bridgeAcceptsTypedVendorSlotsButRejectsMalformedKeysAndModes() {
        assertTrue(SettingsCoordinator.validValue("icons","bluetooth_mode",2));
        assertTrue(SettingsCoordinator.validValue("icons","force_hd_home",true));
        assertTrue(SettingsCoordinator.validValue("icons","keyguard_follow",false));
        assertFalse(SettingsCoordinator.validValue("icons","keyguard_follow",0));
        assertFalse(SettingsCoordinator.validValue("icons","force_hd_home",1));
        assertTrue(SettingsCoordinator.validValue("icons","shade_only.vendor_icon",true));
        assertFalse(SettingsCoordinator.validValue("icons","bluetooth_mode",3));
        assertFalse(SettingsCoordinator.validValue("icons","bluetooth_mode",true));
        assertFalse(SettingsCoordinator.validValue("icons","shade_only.",true));
        assertFalse(SettingsCoordinator.validValue("icons","shade_only.bad/slot",true));
        assertFalse(SettingsCoordinator.validValue("icons","shade_only.clock","true"));
    }
    @Test public void firmwareInventoryKeepsUnknownSlotsAndRemovesDuplicatesAndInvalidEntries() {
        var items=StatusIconCatalog.items(List.of("clock","battery","wifi","wifi","future_vendor_icon","bad/slot",""));
        assertEquals(7,items.size());
        assertEquals("order_enabled",items.get(0).key());assertFalse(items.get(0).defaultFlag());
        assertEquals("keyguard_follow",items.get(1).key());assertTrue(items.get(1).defaultFlag());
        assertEquals("force_hd_home",items.get(2).key());
        assertEquals("shade_only.future_vendor_icon",items.get(6).key());
        assertTrue(items.get(6).title().contains("future_vendor_icon"));
        assertTrue(items.stream().filter(i->!i.key().equals("keyguard_follow")).noneMatch(i->i.defaultFlag()));
    }
    @Test public void lockscreenRulesDefaultOnForNewAndUpgradedPreferencesButRespectExplicitOptOut() {
        assertTrue(StatusIconConfig.from(Map.of()).keyguardFollow());
        var config=StatusIconConfig.from(Map.of("enabled",true,"shade_only.wifi",true,"bluetooth_mode",1));
        assertTrue(config.restricts("wifi","KEYGUARD",StatusIconConfig.BluetoothState.unknown()));
        assertTrue(config.restrictsBluetooth("bluetooth","KEYGUARD",new StatusIconConfig.BluetoothState(true,false,false)));
        var off=StatusIconConfig.from(Map.of("enabled",true,"keyguard_follow",false,"shade_only.wifi",true));
        assertFalse(off.appliesTo("KEYGUARD"));assertTrue(off.restricts("wifi","HOME",StatusIconConfig.BluetoothState.unknown()));
        assertFalse(config.appliesTo("AOD"));assertFalse(config.appliesTo(null));
    }
}
