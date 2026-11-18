package dev.howard.colorosenhance.icons;

import dev.howard.colorosenhance.SettingsCoordinator;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class IconOrderTest {
    @Test public void twoSidesRoundTripAndInvalidOrDuplicateSlotsCannotCrossTheBridge(){
        var value=new IconOrder(List.of("wifi","clock"),List.of("battery","mobile"));
        assertEquals(value,IconOrder.decode(value.encode()));
        assertTrue(SettingsCoordinator.validValue("icons",IconOrder.KEY,value.encode()));
        for(String bad:List.of("v2||","v1|wifi|wifi","v1|wifi,|battery","v1|bad/slot|","v1|clock|battery|wifi")){
            assertFalse(bad,SettingsCoordinator.validValue("icons",IconOrder.KEY,bad));
            assertEquals(IconOrder.EMPTY,StatusIconConfig.from(Map.of(IconOrder.KEY,bad)).order());
        }
        assertFalse(SettingsCoordinator.validValue("icons",IconOrder.KEY,true));
    }
    @Test public void crossSideAndWithinSideMovesAreAtomicAndNeverDuplicate(){
        var initial=new IconOrder(List.of("clock"),List.of("wifi","mobile","battery"));
        var moved=initial.move("battery",true,0).move("clock",false,1).move("mobile",false,0);
        assertEquals(List.of("battery"),moved.left());assertEquals(List.of("mobile","wifi","clock"),moved.right());
        assertEquals(4,new HashSet<>(IconOrder.decode(moved.encode()).withAvailable(List.of()).left()).size()+moved.right().size());
        assertEquals(List.of("clock"),initial.left());
    }
    @Test public void newFirmwareSlotsAppendWithoutDiscardingUnavailableSavedSlotsOrSubscriptionOrder(){
        var saved=new IconOrder(List.of("clock","wifi"),List.of("future_vendor","battery"));
        var merged=saved.withAvailable(List.of("mobile","mobile","volte","battery","clock"));
        assertEquals(List.of("clock","wifi"),merged.left());
        assertEquals(List.of("future_vendor","mobile","volte","battery"),merged.right());
    }
    @Test public void masterAndOrderSwitchBothGateLayoutAndMissingPreferencesRemainNative(){
        assertFalse(StatusIconConfig.from(Map.of()).ordersHome());
        assertFalse(StatusIconConfig.from(Map.of("order_enabled",true)).ordersHome());
        assertFalse(StatusIconConfig.from(Map.of("enabled",true)).ordersHome());
        assertTrue(StatusIconConfig.from(Map.of("enabled",true,"order_enabled",true)).ordersHome());
        assertTrue(SettingsCoordinator.validValue("icons","order_enabled",false));
    }
}
