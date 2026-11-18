package dev.lsposed.fluidcloud;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;
public class CloudConfigTest {
    @Test public void capsuleLayerAndLaunchFadeHaveIndependentSettings() {
        var config=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true));
        assertFalse(config.capsuleOnTop());
        assertTrue(config.keepCapsuleDuringLaunch());
        assertTrue(config.withCapsuleOnTop(true).keepCapsuleDuringLaunch());
        var elevated=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,
                "capsule_on_top",true,"capsule_keep_during_launch",false));
        assertTrue(elevated.capsuleOnTop());
        assertFalse(elevated.keepCapsuleDuringLaunch());
        assertFalse(CloudConfig.from(Map.of("enabled",true)).keepCapsuleDuringLaunch());
    }

    @Test public void realtimeWaveIsStrictlyOptInAndIndependentOfOrientationAndUnbind(){
        assertFalse(CloudConfig.from(Map.of()).realtimeMediaWave());
        assertFalse(CloudConfig.from(Map.of("enabled",true)).realtimeMediaWave());
        assertFalse(CloudConfig.from(Map.of("enabled",true,"realtime_media_wave","true")).realtimeMediaWave());
        assertFalse(CloudConfig.from(Map.of("realtime_media_wave",true)).realtimeMediaWave());
        var selected=CloudConfig.from(Map.of("enabled",true,"realtime_media_wave",true));
        assertTrue(selected.realtimeMediaWave());assertFalse(selected.scenesEnabled());
        assertTrue(selected.forOrientation(true).realtimeMediaWave());
    }
    @Test public void newCapsuleChoicesDefaultToNativeAndAreIndependentOfSceneUnbind(){
        var defaults=CloudConfig.from(Map.of("enabled",true));
        assertFalse(defaults.hideCapsuleStroke());assertFalse(defaults.hideCapsuleArrows());assertFalse(defaults.skipEmptyCapsule());assertFalse(defaults.dimCardBackground());
        var values=new java.util.HashMap<String,Object>(Map.of("enabled",true,"hide_capsule_stroke",true,"hide_capsule_arrows",true,
                "skip_empty_capsule",true,"card_dim_enabled",true,"card_dim_percent",65));
        var selected=CloudConfig.from(values);assertTrue(selected.hideCapsuleStroke());assertTrue(selected.hideCapsuleArrows());
        assertTrue(selected.skipEmptyCapsule());assertTrue(selected.dimCardBackground());assertFalse(selected.scenesEnabled());
        assertEquals(selected.extras(),selected.forOrientation(true).extras());assertEquals(65,selected.extras().dimPercent());
        values.put("enabled",false);var disabled=CloudConfig.from(values);
        assertFalse(disabled.hideCapsuleStroke());assertFalse(disabled.hideCapsuleArrows());assertFalse(disabled.skipEmptyCapsule());assertFalse(disabled.dimCardBackground());
    }
    @Test public void malformedExtrasNeverEnableOverridesAndDimIsBounded(){
        var malformed=CloudConfig.from(Map.of("enabled",true,"hide_capsule_stroke","true","card_dim_enabled","true","card_dim_percent","80"));
        assertFalse(malformed.hideCapsuleStroke());assertFalse(malformed.dimCardBackground());assertEquals(30,malformed.extras().dimPercent());
        assertEquals(0,CloudConfig.from(Map.of("card_dim_percent",-1)).extras().dimPercent());
        assertEquals(100,CloudConfig.from(Map.of("card_dim_percent",1000)).extras().dimPercent());
    }
    @Test public void cameraExpansionIsOptInIndependentOfUnbindAndSurvivesOrientationProfiles() {
        assertFalse(CloudConfig.from(Map.of("enabled",true)).expandFromCamera());
        assertFalse(CloudConfig.from(Map.of("camera_card_expansion",true)).expandFromCamera());
        assertFalse(CloudConfig.from(Map.of("enabled",true,"camera_card_expansion","true")).expandFromCamera());
        var config=CloudConfig.from(Map.of("enabled",true,"camera_card_expansion",true));
        assertTrue(config.expandFromCamera());assertFalse(config.scenesEnabled());
        assertTrue(config.forOrientation(true).expandFromCamera());
    }
    @Test public void inheritanceUsesAllPortraitScenesAndTurningItOffRestoresLandscapeChoices() {
        var values=new java.util.HashMap<String,Object>(Map.of("enabled",true,"unbind_status_bar",true,
                "fullscreen",true,"recents_shrink",true,"shade_keep",true,"shade_collapse_on_open",true,
                "landscape_fullscreen_animation",true,"landscape_follow_portrait",true));
        var inherited=CloudConfig.from(values);
        assertSame(inherited,inherited.forOrientation(true));
        assertTrue(inherited.landscape().fullscreen().animate());
        values.put("landscape_follow_portrait",false);
        var independent=CloudConfig.from(values).forOrientation(true);
        assertEquals(CloudSceneMode.ANIMATE,independent.mode(CloudScene.FULLSCREEN));
        assertEquals(CloudSceneMode.OEM,independent.mode(CloudScene.RECENTS));
        assertFalse(independent.retainInShade());
    }
    @Test public void upgradingKeepsPortraitChoicesAndLandscapeDefaultsToNative() {
        var saved=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,"fullscreen",true,
                "recents_shrink",true,"shade_keep",true,"shade_collapse_on_open",true,
                "keep_paused_music",true,"block_capsule_click_top",true));
        assertSame(saved,saved.forOrientation(false));
        var landscape=saved.forOrientation(true);
        for (var scene:CloudScene.values()) assertEquals(CloudSceneMode.OEM,landscape.mode(scene));
        assertFalse(landscape.retainInShade());assertFalse(landscape.collapseBeforeOpen());
        assertTrue(landscape.retainPausedMusic());assertTrue(landscape.preventCapsuleClickTop());
        assertTrue(saved.retainInShade());assertTrue(saved.collapseBeforeOpen());
    }
    @Test public void orientationProfilesAreIndependentAndMasterStillGatesBoth() {
        var values=new java.util.HashMap<String,Object>(Map.of("enabled",true,"unbind_status_bar",true,
                "fullscreen",true,"landscape_fullscreen_animation",true,"landscape_recents",true,
                "landscape_other_animation",true,"landscape_shade_keep",true,"landscape_shade_collapse_on_open",true));
        var config=CloudConfig.from(values);
        assertEquals(CloudSceneMode.KEEP,config.mode(CloudScene.FULLSCREEN));
        assertEquals(CloudSceneMode.OEM,config.mode(CloudScene.RECENTS));assertFalse(config.retainInShade());
        var horizontal=config.forOrientation(true);
        assertEquals(CloudSceneMode.ANIMATE,horizontal.mode(CloudScene.FULLSCREEN));
        assertEquals(CloudSceneMode.KEEP,horizontal.mode(CloudScene.RECENTS));
        assertEquals(CloudSceneMode.ANIMATE,horizontal.mode(CloudScene.OTHER));
        assertTrue(horizontal.collapseBeforeOpen());
        values.put("unbind_status_bar",false);
        var disabled=CloudConfig.from(values).forOrientation(true);
        for(var scene:CloudScene.values())assertEquals(CloudSceneMode.OEM,disabled.mode(scene));
        assertFalse(disabled.retainInShade());
    }
    @Test public void clickTopFixDefaultsOffAndDoesNotRequireSceneUnbind(){
        assertFalse(CloudConfig.from(Map.of("enabled",true)).preventCapsuleClickTop());
        assertFalse(CloudConfig.from(Map.of("block_capsule_click_top",true)).preventCapsuleClickTop());
        assertFalse(CloudConfig.from(Map.of("enabled",true,"block_capsule_click_top","true")).preventCapsuleClickTop());
        var selected=CloudConfig.from(Map.of("enabled",true,"block_capsule_click_top",true));
        assertTrue(selected.preventCapsuleClickTop());assertFalse(selected.scenesEnabled());
    }
    @Test public void existingKeysKeepTheirMeaningAndNewKeysDefaultToOem() {
        CloudConfig value=CloudConfig.from(Map.of(
                "enabled",true,"unbind_status_bar",true,"fullscreen",true,"recents",false,"recents_shrink",true));
        assertEquals(CloudSceneMode.KEEP,value.mode(CloudScene.FULLSCREEN));
        assertEquals(CloudSceneMode.ANIMATE,value.mode(CloudScene.RECENTS));
        assertEquals(CloudSceneMode.OEM,value.mode(CloudScene.OTHER));
    }

    @Test public void keepWinsWithoutErasingSavedAnimationAndMasterForcesOem() {
        CloudConfig value=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,
                "fullscreen",true,"fullscreen_animation",true,
                "other_keep",true,"other_animation",true));
        assertEquals(CloudSceneMode.KEEP,value.mode(CloudScene.FULLSCREEN));
        assertTrue(value.fullscreen().animate());
        CloudConfig disabled=CloudConfig.from(Map.of("fullscreen",true,"fullscreen_animation",true));
        assertEquals(CloudSceneMode.OEM,disabled.mode(CloudScene.FULLSCREEN));
        assertTrue(disabled.fullscreen().keep());
    }

    @Test public void malformedValuesNeverEnableAScene() {
        CloudConfig value=CloudConfig.from(Map.of("enabled","true","other_keep","true"));
        for(CloudScene scene:CloudScene.values()) assertEquals(CloudSceneMode.OEM,value.mode(scene));
    }

    @Test public void pausedMusicChoiceIsIndependentAndOffByDefault() {
        CloudConfig ordinary = CloudConfig.from(Map.of("enabled", true));
        CloudConfig selected = CloudConfig.from(Map.of("enabled", true, "keep_paused_music", true));
        assertNotEquals(ordinary, selected);
        assertFalse(ordinary.retainPausedMusic());
        assertTrue(selected.retainPausedMusic());
        assertFalse(CloudConfig.from(Map.of("keep_paused_music", true)).retainPausedMusic());
        assertEquals(ordinary, CloudConfig.from(Map.of("enabled", true, "keep_paused_music", "true")));
    }

    @Test public void newUnbindGateDefaultsOffWithoutErasingOldChoicesOrDisablingMedia(){
        CloudConfig value=CloudConfig.from(Map.of("enabled",true,"fullscreen",true,"recents_shrink",true,"keep_paused_music",true));
        assertFalse(value.scenesEnabled());
        assertTrue(value.fullscreen().keep());assertTrue(value.recents().animate());
        for(CloudScene scene:CloudScene.values())assertEquals(CloudSceneMode.OEM,value.mode(scene));
        assertTrue(value.retainPausedMusic());
    }

    @Test public void shadeKeepNeedsBothMasterAndUnbindAndDefaultsOff(){
        assertFalse(CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true)).retainInShade());
        assertFalse(CloudConfig.from(Map.of("enabled",true,"shade_keep",true)).retainInShade());
        assertFalse(CloudConfig.from(Map.of("unbind_status_bar",true,"shade_keep",true)).retainInShade());
        assertTrue(CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,"shade_keep",true)).retainInShade());
    }
    @Test public void autoCollapseDefaultsOffAndRequiresAllShadeGates(){
        assertFalse(CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,"shade_keep",true)).collapseBeforeOpen());
        assertTrue(CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,"shade_keep",true,"shade_collapse_on_open",true)).collapseBeforeOpen());
        for(String missing:java.util.List.of("enabled","unbind_status_bar","shade_keep")){
            var values=new java.util.HashMap<String,Object>(Map.of("enabled",true,"unbind_status_bar",true,"shade_keep",true,"shade_collapse_on_open",true));
            values.remove(missing);assertFalse(CloudConfig.from(values).collapseBeforeOpen());
        }
    }
}
