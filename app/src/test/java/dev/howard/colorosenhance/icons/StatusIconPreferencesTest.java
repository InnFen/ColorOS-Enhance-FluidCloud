package dev.howard.colorosenhance.icons;

import dev.howard.colorosenhance.PreferenceSession;
import dev.howard.colorosenhance.SettingsCoordinator;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StatusIconPreferencesTest {
    @Test public void legacyMutePlacementAndShadeRuleMigrateToTheNativeRingerSlotOnce(){
        var store=new Store();store.data.putAll(Map.of("enabled",true,"shade_only.mute",true,"shade_only.volume",false,
                IconOrder.KEY,new IconOrder(List.of("mute","clock"),List.of("wifi","volume","battery")).encode(),"future",42));
        StatusIconPreferences.initialize(store);
        assertEquals(Boolean.TRUE,store.data.get("shade_only.volume"));assertFalse(store.data.containsKey("shade_only.mute"));
        var order=IconOrder.decode((String)store.data.get(IconOrder.KEY));assertEquals(List.of("volume","clock"),order.left());assertFalse(order.right().contains("volume"));
        assertEquals(42,store.data.get("future"));StatusIconPreferences.initialize(store);assertEquals(1,store.writes);
    }
    @Test public void genericUnavailableMuteAndResetLayoutsStayUntouchedUntilTheFirmwareCapabilityIsKnown(){
        var store=new Store();store.data.putAll(Map.of("shade_only.mute",true,IconOrder.KEY,""));
        var source=source(store);var unknown=new SettingsCoordinator(()->source,()->{});
        assertEquals(Boolean.TRUE,unknown.read("icons").values().get("shade_only.mute"));assertEquals(0,store.writes);
        assertEquals("",StatusIconPreferences.migrateLegacy(Map.of(IconOrder.KEY,"")).get(IconOrder.KEY));
    }
    @Test public void bothFrontendsSeeCanonicalValuesAndCanSwitchTheMigratedRuleOff(){
        var store=new Store();store.data.putAll(Map.of("enabled",true,"shade_only.mute",true));
        var source=source(store);var coordinator=new SettingsCoordinator(()->source,()->{},()->true);
        var state=coordinator.read("icons");assertTrue(state.writable());assertEquals(Boolean.TRUE,state.values().get("shade_only.volume"));
        assertEquals("ok",coordinator.write("icons","shade_only.volume",false,state.revision()).status());
        assertFalse(StatusIconConfig.from(coordinator.read("icons").values()).shadeOnly().contains("volume"));
        assertFalse(store.data.containsKey("shade_only.mute"));
    }
    @Test public void savedRealNativeMuteRemainsIndependentWhenItIsActuallyAvailable(){
        var saved=new IconOrder(List.of("mute"),List.of("volume","wifi"));
        assertEquals(saved,saved.withAvailable(List.of("mute","volume","wifi")));
        var legacyOnly=new IconOrder(List.of("mute"),List.of("wifi"));
        assertEquals("mute",legacyOnly.withAvailable(List.of("mute","wifi")).left().getFirst());
        assertEquals("mute",saved.withAvailable(List.of("volume","wifi")).left().getFirst()); // Generic model never guesses firmware aliases.
        assertEquals("volume",saved.ringerSlot().left().getFirst());
    }
    @Test public void ringerAliasCapabilityIsRestrictedToKnownFirmwareApiAndAnActualVolumeSlot(){
        assertTrue(StatusIconCatalog.usesColorOsRingerSlot(List.of("volume"),36));
        assertTrue(StatusIconCatalog.usesColorOsRingerSlot(List.of("volume"),37));
        assertFalse(StatusIconCatalog.usesColorOsRingerSlot(List.of("volume"),38));
        assertFalse(StatusIconCatalog.usesColorOsRingerSlot(List.of("mute"),37));
    }
    private static SettingsCoordinator.Source source(Store store){return new SettingsCoordinator.Source(){
        public Object identity(){return store;}public PreferenceSession.Store open(String group){return store;}
    };}
    private static final class Store implements PreferenceSession.Store {
        Map<String,Object> data=new HashMap<>();int writes;
        public Map<String,?> read(){return Map.copyOf(data);}public boolean replace(Map<String,Object> values){writes++;data=new HashMap<>(values);return true;}
    }
}
