package dev.howard.colorosenhance;

import static org.junit.Assert.*;
import dev.lsposed.fluidcloud.MediaWaveBands;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class SettingsUiControllerTest {
    @Test public void disconnectedStateIsReadOnlyAndDoesNotInventConfiguration(){
        var f=new Fixture();f.source=null;
        var state=f.ui.state("battery");
        assertEquals(-1,state.revision());assertFalse(state.writable());assertTrue(state.values().isEmpty());
        assertEquals("unavailable",f.ui.toggle("battery","enabled",true));
    }
    @Test public void readsAndInvalidCommandsNeverPersistDefaultsOrNotifyObservers(){
        var f=new Fixture();var state=f.ui.state("battery");
        assertTrue(state.writable());assertTrue(state.values().isEmpty());
        assertEquals("invalid",f.ui.toggle("battery","not_a_setting",true));
        assertEquals("invalid",f.ui.put("battery","weight","700",state.revision()));
        assertEquals(0,f.store("battery").writes);assertEquals(0,f.notifications);
        assertEquals(-1,f.ui.state("unknown").revision());
    }
    @Test public void acknowledgedMasterTogglePublishesNewImmutableConfirmedState(){
        var f=new Fixture();var before=f.ui.state("battery");
        assertEquals("ok",f.ui.toggle("battery","enabled",true));
        var after=f.ui.state("battery");
        assertFalse(before.values().containsKey("enabled"));assertEquals(true,after.values().get("enabled"));
        assertTrue(after.revision()>before.revision());assertEquals(1,f.store("battery").writes);
        assertEquals(1,f.notifications);
        try{after.values().put("enabled",false);fail("Snapshots must not be editable by a renderer");}
        catch(UnsupportedOperationException expected){ }
    }
    @Test public void adjustableValuesUseCatalogBoundsAndStepsBeforeAnyWrite(){
        var f=new Fixture();f.store("battery").values.put("weight_enabled",true);
        var item=item("battery","weight_enabled");long revision=f.ui.state("battery").revision();
        for(int value:new int[]{99,1001,575})assertEquals("invalid",f.ui.number("battery",item,value,revision));
        assertEquals(0,f.store("battery").writes);
        assertEquals("ok",f.ui.number("battery",item,700,revision));
        assertEquals(700,f.ui.state("battery").values().get("weight"));
    }
    @Test public void aDisabledParameterCannotBeSavedBySliderOrGenericCommit(){
        var f=new Fixture();long revision=f.ui.state("battery").revision();
        assertEquals("invalid",f.ui.number("battery",item("battery","weight_enabled"),700,revision));
        assertEquals("invalid",f.ui.put("battery","weight",700,revision));
        assertEquals(0,f.store("battery").writes);
    }
    @Test public void numericMetadataCannotBeUsedToWriteAnotherGroup(){
        var f=new Fixture();var item=item("battery","weight_enabled");
        assertEquals("invalid",f.ui.number("aod",item,700,f.ui.state("aod").revision()));
        assertEquals("invalid",f.ui.number("battery",null,700,f.ui.state("battery").revision()));
        assertEquals(0,f.store("aod").writes);assertEquals(0,f.store("battery").writes);
    }
    @Test public void externalChangeInvalidatesACompletedSliderGesture(){
        var f=new Fixture();f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",570));
        long dragRevision=f.ui.state("battery").revision();
        f.store("battery").values.put("weight",400);f.coordinator.refresh();
        assertEquals("conflict",f.ui.number("battery",item("battery","weight_enabled"),700,dragRevision));
        assertEquals(400,f.ui.state("battery").values().get("weight"));assertEquals(0,f.store("battery").writes);
    }
    @Test public void unreadableSessionRetainsConfirmedValuesButRejectsFurtherInput(){
        var f=new Fixture();f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",570));
        long revision=f.ui.state("battery").revision();f.store("battery").unreadable=true;f.coordinator.refresh();
        var state=f.ui.state("battery");assertFalse(state.writable());assertEquals(570,state.values().get("weight"));
        assertEquals("uncertain",f.ui.number("battery",item("battery","weight_enabled"),700,revision));
        assertEquals("uncertain",f.ui.toggle("battery","enabled",true));assertEquals(0,f.store("battery").writes);
    }
    @Test public void failedSaveRestoresConfirmedValueWithoutARecursiveWrite(){
        var f=new Fixture();f.ui.state("battery");
        f.store("battery").outcomes.add(false);f.store("battery").outcomes.add(true);
        assertEquals("failed",f.ui.toggle("battery","enabled",true));
        assertFalse(f.ui.state("battery").values().containsKey("enabled"));
        assertTrue(f.ui.state("battery").writable());assertEquals(2,f.store("battery").writes);
    }
    @Test public void anUnconfirmedRollbackStopsAllSubsequentWrites(){
        var f=new Fixture();f.ui.state("battery");
        f.store("battery").outcomes.add(false);f.store("battery").outcomes.add(false);
        assertEquals("uncertain",f.ui.toggle("battery","enabled",true));
        assertFalse(f.ui.state("battery").writable());
        assertEquals("uncertain",f.ui.toggle("battery","enabled",true));assertEquals(2,f.store("battery").writes);
    }
    @Test public void dynamicShadeOnlyKeysAreValidatedAndRequireTheIconMaster(){
        var f=new Fixture();
        assertEquals("invalid",f.ui.toggle("icons","shade_only.vendor.slot",true));
        assertEquals("ok",f.ui.toggle("icons","enabled",true));
        assertEquals("ok",f.ui.toggle("icons","shade_only.vendor.slot",true));
        assertEquals(true,f.ui.state("icons").values().get("shade_only.vendor.slot"));
        assertEquals("invalid",f.ui.toggle("icons","shade_only.",true));
        assertEquals(2,f.store("icons").writes);
    }
    @Test public void bluetoothRuleRequiresEnabledIconsButOrderCanBePreparedBeforeEnabling(){
        var f=new Fixture();long revision=f.ui.state("icons").revision();
        assertEquals("invalid",f.ui.put("icons","bluetooth_mode",2,revision));
        assertEquals("ok",f.ui.put("icons","order_layout","v1|volume,clock|battery",revision));
        assertEquals("ok",f.ui.toggle("icons","enabled",true));
        assertEquals("ok",f.ui.put("icons","bluetooth_mode",2,f.ui.state("icons").revision()));
        assertEquals(2,f.ui.state("icons").values().get("bluetooth_mode"));
    }
    @Test public void hiddenOrUnavailableCloudControlsCannotCommitFromLateCallbacks(){
        var f=new Fixture();f.store("cloud").values.putAll(Map.of("enabled",true,"unbind_status_bar",true,"fullscreen",true));
        f.ui.state("cloud");
        assertEquals("invalid",f.ui.toggle("cloud","fullscreen_animation",true));
        assertEquals("invalid",f.ui.toggle("cloud","shade_collapse_on_open",true));
        assertEquals(0,f.store("cloud").writes);
        assertEquals("ok",f.ui.toggle("cloud","fullscreen",false));
        assertEquals("ok",f.ui.toggle("cloud","fullscreen_animation",true));
    }
    @Test public void disabledRealtimeWaveKeepsSavedNumbersButRejectsParameterChanges(){
        var f=new Fixture();f.store("cloud").values.putAll(Map.of("enabled",true,"wave_interval_ms",40));
        var parameter=item("cloud","wave_interval_ms");long revision=f.ui.state("cloud").revision();
        assertEquals("invalid",f.ui.number("cloud",parameter,120,revision));
        assertEquals(40,f.ui.state("cloud").values().get("wave_interval_ms"));
        assertEquals("ok",f.ui.toggle("cloud","realtime_media_wave",true));
        assertEquals("ok",f.ui.number("cloud",parameter,120,f.ui.state("cloud").revision()));
        assertEquals(120,f.ui.state("cloud").values().get("wave_interval_ms"));
    }

    @Test public void bandEditsAreValidatedAndRestoreOnlyTheBandsInOneWrite() {
        var f = new Fixture();
        var store = f.store("cloud");
        store.values.putAll(Map.of("enabled", true, "realtime_media_wave", true, "wave_interval_ms", 20));
        long original = f.ui.state("cloud").revision();
        assertEquals("invalid", f.ui.put("cloud", MediaWaveBands.KEY, "40,125,125,800,2000,5000,16000", original));
        assertEquals(0, store.writes);
        assertEquals("ok", f.ui.put("cloud", MediaWaveBands.KEY, "30, 100, 300, 800, 2500, 6000, 18000", original));
        assertEquals("30,100,300,800,2500,6000,18000", f.ui.state("cloud").values().get(MediaWaveBands.KEY));
        assertEquals("ok", f.ui.put("cloud", MediaWaveBands.KEY, MediaWaveBands.DEFAULTS.encode(), f.ui.state("cloud").revision()));
        assertEquals(20, store.values.get("wave_interval_ms"));
        assertEquals(MediaWaveBands.DEFAULTS, MediaWaveBands.from(store.values));
        assertEquals(2, store.writes);
    }

    @Test public void waveResetIsRevisionedAndClearsOnlyTuningParameters() {
        var f = new Fixture();
        var store = f.store("cloud");
        var unrelated = Map.<String,Object>of("enabled", true, "realtime_media_wave", true, "hide_capsule_arrows", true);
        store.values.putAll(unrelated);
        store.values.putAll(Map.of(MediaWaveBands.KEY, "30,100,300,800,2500,6000,18000", "wave_interval_ms", 1000,
                "wave_sensitivity", 500, "wave_attack_ms", 1000, "wave_release_ms", 2000, "wave_hold_paused", true));
        long stale = f.ui.state("cloud").revision();
        assertEquals("ok", f.ui.toggle("cloud", "hide_capsule_arrows", false));
        assertEquals("conflict", f.ui.resetWave(stale));
        assertEquals(1000, store.values.get("wave_interval_ms"));
        assertEquals("ok", f.ui.resetWave(f.ui.state("cloud").revision()));
        assertEquals(Map.of("enabled", true, "realtime_media_wave", true, "hide_capsule_arrows", false), store.values);
        assertEquals(2, store.writes);
    }

    @Test public void bridgeWritesAndResetsCannotChangeWaveTuningWhileDisabled() {
        var f = new Fixture();
        var store = f.store("cloud");
        store.values.putAll(Map.of("enabled", true, "wave_interval_ms", 20));
        long revision = f.ui.state("cloud").revision();
        assertEquals("invalid", f.coordinator.write("cloud", MediaWaveBands.KEY, MediaWaveBands.DEFAULTS.encode(), revision).status());
        assertEquals("invalid", f.coordinator.resetWave(revision).status());
        assertEquals(0, store.writes);
        assertEquals(20, store.values.get("wave_interval_ms"));
    }
    @Test public void resetIsRevisionedAndLimitedToOneFeatureGroup(){
        var f=new Fixture();f.store("battery").values.putAll(Map.of("enabled",true,"weight",700));
        f.store("aod").values.put("hide_date",true);
        assertEquals("ok",f.ui.reset("battery",f.ui.state("battery").revision()));
        assertTrue(f.ui.state("battery").values().isEmpty());assertEquals(true,f.ui.state("aod").values().get("hide_date"));
        assertEquals("invalid",f.ui.reset("sim",f.ui.state("sim").revision()));
        assertEquals(0,f.store("sim").writes);assertEquals(0,f.store("aod").writes);
    }
    @Test public void installerChoiceUsesTheCoordinatorMigrationAndResetDefaults(){
        var f=new Fixture();var initial=f.ui.state("installer");assertEquals("",initial.values().get("target_package"));
        assertEquals("ok",f.ui.put("installer","target_package","com.example.installer",initial.revision()));
        assertEquals("ok",f.ui.reset("installer",f.ui.state("installer").revision()));
        assertEquals(Map.of("target_package",""),f.ui.state("installer").values());
    }

    static SettingsCatalog.Item item(String group,String key){
        return SettingsCatalog.find(group).items().stream().filter(item->item.key().equals(key)).findFirst().orElseThrow();
    }
    static final class Fixture {
        Source source=new Source();int notifications;
        final SettingsCoordinator coordinator=new SettingsCoordinator(()->source,()->notifications++);
        final SettingsUiController ui=new SettingsUiController(coordinator);
        Store store(String group){return source.store(group);}
        void reconnect(){source=new Source();}
    }
    static final class Source implements SettingsCoordinator.Source {
        final Map<String,Store> stores=new HashMap<>();
        public Object identity(){return this;}
        public PreferenceSession.Store open(String group){return store(group);}
        Store store(String group){return stores.computeIfAbsent(group,key->new Store());}
    }
    static final class Store implements PreferenceSession.Store {
        final Map<String,Object> values=new HashMap<>();final ArrayDeque<Boolean> outcomes=new ArrayDeque<>();
        int writes;boolean unreadable;
        public Map<String,?> read(){if(unreadable)throw new IllegalStateException("store unavailable");return new HashMap<>(values);}
        public boolean replace(Map<String,Object> next){writes++;values.clear();values.putAll(next);return outcomes.isEmpty()||outcomes.removeFirst();}
    }
}
