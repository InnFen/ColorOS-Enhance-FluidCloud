package dev.howard.colorosenhance;

import static org.junit.Assert.*;
import dev.howard.colorosenhance.icons.IconOrder;
import java.util.Map;
import org.junit.Test;

/** Renderer-independent refresh/commit regressions retained from the former View-based shell. */
public class MainActivityRefreshTest {
    @Test public void spectrumSliderWritesAnIntegerAndTurnsOffWithTheMediaSwitch(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("cloud").values.putAll(Map.of("enabled",true,"realtime_media_wave",true));
        var item=SettingsUiControllerTest.item("cloud","wave_interval_ms");
        assertEquals("ok",f.ui.number("cloud",item,120,f.ui.state("cloud").revision()));
        assertEquals(120,f.ui.state("cloud").values().get("wave_interval_ms"));
        assertEquals("ok",f.ui.toggle("cloud","realtime_media_wave",false));
        assertFalse(item.visible(f.ui.state("cloud").values()));
        assertEquals("invalid",f.ui.number("cloud",item,80,f.ui.state("cloud").revision()));
        assertEquals(120,f.ui.state("cloud").values().get("wave_interval_ms"));
    }
    @Test public void iconOrderSaveIsOneRevisionedValueAndPreservesOtherRules(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("icons").values.putAll(Map.of("enabled",true,"shade_only.clock",true,"bluetooth_mode",2));
        String encoded=new IconOrder(java.util.List.of("volume","clock"),java.util.List.of("battery")).encode();
        assertEquals("ok",f.ui.put("icons",IconOrder.KEY,encoded,f.ui.state("icons").revision()));
        var state=f.ui.state("icons");
        assertEquals(encoded,state.values().get(IconOrder.KEY));
        assertEquals(true,state.values().get("shade_only.clock"));assertEquals(2,state.values().get("bluetooth_mode"));
        assertEquals(1,f.store("icons").writes);
    }
    @Test public void staleIconOrderDialogCannotOverwriteAnotherFrontend(){
        var f=new SettingsUiControllerTest.Fixture();long revision=f.ui.state("icons").revision();
        f.store("icons").values.put("force_hd_home",true);f.coordinator.refresh();
        assertEquals("conflict",f.ui.put("icons",IconOrder.KEY,"v1|clock|battery",revision));
        assertFalse(f.ui.state("icons").values().containsKey(IconOrder.KEY));assertEquals(0,f.store("icons").writes);
    }
    @Test public void disconnectedOldOrderDialogCannotWriteIntoANewSession(){
        var f=new SettingsUiControllerTest.Fixture();long revision=f.ui.state("icons").revision();
        var previous=f.source;f.reconnect();
        assertEquals("conflict",f.ui.put("icons",IconOrder.KEY,"v1|clock|battery",revision));
        assertEquals(0,previous.store("icons").writes);assertEquals(0,f.store("icons").writes);
    }
    @Test public void iconMasterPreservesSavedSelectionsAndBluetoothRule(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("icons").values.putAll(Map.of("enabled",true,"shade_only.clock",true));
        assertEquals("ok",f.ui.put("icons","bluetooth_mode",2,f.ui.state("icons").revision()));
        assertEquals("ok",f.ui.toggle("icons","enabled",false));
        var values=f.ui.state("icons").values();
        assertEquals(true,values.get("shade_only.clock"));assertEquals(2,values.get("bluetooth_mode"));
        assertFalse(SettingsCatalog.controlEnabled("icons","shade_only.clock",values));
        assertEquals("invalid",f.ui.toggle("icons","shade_only.clock",false));
    }
    @Test public void observerRefreshPublishesExternalValueWithoutPersistingItAgain(){
        var f=new SettingsUiControllerTest.Fixture();
        assertEquals("ok",f.ui.toggle("aod","hide_date",true));
        long revision=f.ui.state("aod").revision();
        f.store("aod").values.put("hide_date",false);f.coordinator.refresh();
        assertEquals(false,f.ui.state("aod").values().get("hide_date"));
        assertTrue(f.ui.state("aod").revision()>revision);assertEquals(1,f.store("aod").writes);
        for(int i=0;i<5;i++){f.coordinator.refresh();f.ui.state("aod");}
        assertEquals(1,f.store("aod").writes);
    }
    @Test public void listenersResolveTheCurrentApplicationSessionInsteadOfAnOldStore(){
        var f=new SettingsUiControllerTest.Fixture();f.ui.state("battery");var previous=f.source;
        f.reconnect();
        assertEquals("ok",f.ui.toggle("battery","enabled",true));
        assertEquals(0,previous.store("battery").writes);assertEquals(1,f.store("battery").writes);
    }
    @Test public void sliderPreviewCannotOverwriteANewerConfirmedValueAtTouchUp(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",570));
        long revision=f.ui.state("battery").revision();
        f.store("battery").values.put("weight",400);f.coordinator.refresh();
        assertEquals("conflict",f.ui.number("battery",SettingsUiControllerTest.item("battery","weight_enabled"),700,revision));
        assertEquals(400,f.ui.state("battery").values().get("weight"));assertEquals(0,f.store("battery").writes);
    }
    @Test public void disconnectReturnsReadOnlyStateAndRejectsALateNumberCommit(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",570));
        long revision=f.ui.state("battery").revision();var previous=f.source;f.source=null;
        assertFalse(f.ui.state("battery").writable());
        assertEquals("unavailable",f.ui.number("battery",SettingsUiControllerTest.item("battery","weight_enabled"),700,revision));
        assertEquals(0,previous.store("battery").writes);
    }
    @Test public void recoveredConnectionRequiresANewGestureRevision(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",570));
        long oldRevision=f.ui.state("battery").revision();var previous=f.source;
        f.source=null;f.ui.state("battery");f.reconnect();
        f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",620));
        var item=SettingsUiControllerTest.item("battery","weight_enabled");
        assertEquals("conflict",f.ui.number("battery",item,700,oldRevision));
        assertEquals(620,f.ui.state("battery").values().get("weight"));
        assertEquals("ok",f.ui.number("battery",item,650,f.ui.state("battery").revision()));
        assertEquals(650,f.ui.state("battery").values().get("weight"));
        assertEquals(0,previous.store("battery").writes);assertEquals(1,f.store("battery").writes);
    }
    @Test public void uncertainInputCannotBeCommittedAfterAReplacementSessionArrives(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",570));
        long oldRevision=f.ui.state("battery").revision();var original=f.store("battery");
        original.unreadable=true;f.coordinator.refresh();assertFalse(f.ui.state("battery").writable());
        assertEquals("uncertain",f.ui.number("battery",SettingsUiControllerTest.item("battery","weight_enabled"),700,oldRevision));
        f.reconnect();f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",620));
        assertEquals("conflict",f.ui.number("battery",SettingsUiControllerTest.item("battery","weight_enabled"),700,oldRevision));
        assertEquals(620,f.ui.state("battery").values().get("weight"));assertEquals(0,original.writes);assertEquals(0,f.store("battery").writes);
    }
    @Test public void externallyDisabledParameterRejectsItsOldDragAndPreservesItsNewValue(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",570));
        long revision=f.ui.state("battery").revision();
        f.store("battery").values.putAll(Map.of("weight_enabled",false,"weight",400));f.coordinator.refresh();
        var item=SettingsUiControllerTest.item("battery","weight_enabled");
        assertEquals("conflict",f.ui.number("battery",item,700,revision));
        assertEquals("invalid",f.ui.number("battery",item,700,f.ui.state("battery").revision()));
        assertEquals(400,f.ui.state("battery").values().get("weight"));assertEquals(0,f.store("battery").writes);
    }
    @Test public void cloudSceneVisibilityChangesWithoutDeletingTheAnimationChoice(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("cloud").values.putAll(Map.of("enabled",true,"unbind_status_bar",true,"fullscreen_animation",true));
        var animation=SettingsUiControllerTest.item("cloud","fullscreen_animation");
        assertTrue(animation.visible(f.ui.state("cloud").values()));
        assertEquals("ok",f.ui.toggle("cloud","fullscreen",true));
        assertFalse(animation.visible(f.ui.state("cloud").values()));
        assertEquals(true,f.ui.state("cloud").values().get("fullscreen_animation"));
        assertEquals("ok",f.ui.toggle("cloud","fullscreen",false));assertTrue(animation.visible(f.ui.state("cloud").values()));
    }
    @Test public void cloudMasterDisablesChildrenButKeepsTheirCheckedValues(){
        var f=new SettingsUiControllerTest.Fixture();f.store("cloud").values.putAll(Map.of("enabled",false,"other_keep",true));
        var values=f.ui.state("cloud").values();
        assertEquals(true,values.get("other_keep"));assertFalse(SettingsCatalog.controlEnabled("cloud","other_keep",values));
        assertEquals("invalid",f.ui.toggle("cloud","other_keep",false));assertEquals(0,f.store("cloud").writes);
    }
    @Test public void inheritanceHidesLandscapeItemsAndRetainsIndependentChoices(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("cloud").values.putAll(Map.of("enabled",true,"unbind_status_bar",true,"landscape_shade_keep",true));
        var shade=SettingsUiControllerTest.item("cloud","landscape_shade_keep");
        assertTrue(shade.visible(f.ui.state("cloud").values()));
        assertEquals("ok",f.ui.toggle("cloud","landscape_follow_portrait",true));
        assertFalse(shade.visible(f.ui.state("cloud").values()));assertEquals(true,f.ui.state("cloud").values().get("landscape_shade_keep"));
        assertEquals("invalid",f.ui.toggle("cloud","landscape_shade_keep",false));
        assertEquals("ok",f.ui.toggle("cloud","landscape_follow_portrait",false));
        assertTrue(shade.visible(f.ui.state("cloud").values()));assertEquals(true,f.ui.state("cloud").values().get("landscape_shade_keep"));
    }
    @Test public void staleResetCannotEraseChangesMadeFromTheEmbeddedPage(){
        var f=new SettingsUiControllerTest.Fixture();f.store("aod").values.put("hide_date",true);
        long revision=f.ui.state("aod").revision();
        f.store("aod").values.put("hide_week",true);f.coordinator.refresh();
        assertEquals("conflict",f.ui.reset("aod",revision));
        assertEquals(Map.of("hide_date",true,"hide_week",true),f.ui.state("aod").values());assertEquals(0,f.store("aod").writes);
    }
}
