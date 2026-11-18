package dev.howard.colorosenhance;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SettingsCatalogCloudTest {
    @Test public void newChoicesAreSharedOptInControlsIndependentOfUnbindAndNumericWritesAreBounded(){
        var values=Map.<String,Object>of("enabled",true);
        for(String key:List.of("realtime_media_wave","hide_capsule_stroke","hide_capsule_arrows","skip_empty_capsule","card_dim_enabled",
                "capsule_corners_enabled","card_corners_enabled","capsule_size_enabled","selected_card_first")){
            assertFalse(item(key).defaultFlag());assertTrue(SettingsCatalog.controlEnabled("cloud",key,values));
            assertFalse(SettingsCatalog.controlEnabled("cloud",key,Map.of()));assertTrue(SettingsCoordinator.validValue("cloud",key,true));
        }
        var dim=item("card_dim_enabled");assertTrue(dim.adjustable());assertEquals("card_dim_percent",dim.valueKey());
        assertEquals(30,dim.valueDefault());assertEquals(0,dim.min());assertEquals(100,dim.max());
        assertTrue(SettingsCoordinator.validValue("cloud","card_dim_percent",0));assertTrue(SettingsCoordinator.validValue("cloud","card_dim_percent",100));
        assertFalse(SettingsCoordinator.validValue("cloud","card_dim_percent",101));assertFalse(SettingsCoordinator.validValue("cloud","card_dim_percent","40"));
    }
    @Test public void cloudCatalogContainsThreeScenePairsWithOemDefaults() {
        SettingsCatalog.Group cloud = SettingsCatalog.find("cloud");
        assertNotNull(cloud);
        assertEquals(List.of("keep_paused_music", "realtime_media_wave", "wave_interval_ms", "wave_sensitivity", "wave_attack_ms", "wave_release_ms", "wave_hold_paused", "capsule_size_enabled", "capsule_height_percent", "hide_capsule_stroke", "hide_capsule_arrows",
                "capsule_corners_enabled", "capsule_continuous_corners", "skip_empty_capsule", "block_capsule_click_top", "selected_card_first", "camera_card_expansion", "card_dim_enabled",
                "card_corners_enabled", "card_continuous_corners", "unbind_status_bar", "capsule_on_top", "capsule_keep_during_launch", "fullscreen", "fullscreen_animation",
                "recents", "recents_shrink", "shade_keep", "shade_collapse_on_open", "other_keep", "other_animation",
                "landscape_follow_portrait", "landscape_fullscreen", "landscape_fullscreen_animation", "landscape_recents", "landscape_recents_shrink",
                "landscape_shade_keep", "landscape_shade_collapse_on_open", "landscape_other_keep", "landscape_other_animation"),
                cloud.items().stream().map(SettingsCatalog.Item::key).toList());
        assertEquals(List.of("capsule_keep_during_launch"),
                cloud.items().stream().filter(SettingsCatalog.Item::defaultFlag).map(SettingsCatalog.Item::key).toList());
    }

    @Test public void capsuleDimensionsShareNumericInputAndDefaultResetContracts() {
        var width=item("capsule_size_enabled");var height=item("capsule_height_percent");
        assertEquals(100,width.valueDefault());assertEquals(100,height.valueDefault());
        assertEquals("capsule_width_percent",width.valueKey());
        for(String key:List.of(width.valueKey(),height.valueKey())) {
            assertTrue(SettingsCoordinator.validValue("cloud",key,50));
            assertTrue(SettingsCoordinator.validValue("cloud",key,200));
            assertFalse(SettingsCoordinator.validValue("cloud",key,49));
            assertFalse(SettingsCoordinator.validValue("cloud",key,201));
        }
        assertFalse(height.visible(Map.of("enabled",true)));
        assertFalse(SettingsCatalog.controlEnabled("cloud",height.key(),Map.of("enabled",true)));
        assertTrue(height.visible(Map.of("capsule_size_enabled",true)));
        assertTrue(SettingsCatalog.controlEnabled("cloud",height.key(),Map.of("enabled",true,"capsule_size_enabled",true)));
    }

    @Test public void unbindGateDisablesSceneControlsButLeavesMediaIndependent(){
        Map<String,Object> values=Map.of("enabled",true,"keep_paused_music",true,"fullscreen",true);
        assertTrue(SettingsCatalog.controlEnabled("cloud","keep_paused_music",values));
        assertTrue(SettingsCatalog.controlEnabled("cloud","block_capsule_click_top",values));
        assertTrue(SettingsCatalog.controlEnabled("cloud","camera_card_expansion",values));
        assertFalse(SettingsCatalog.controlEnabled("cloud","camera_card_expansion",Map.of()));
        assertTrue(SettingsCoordinator.validValue("cloud","camera_card_expansion",true));
        assertFalse(SettingsCoordinator.validValue("cloud","camera_card_expansion","true"));
        assertFalse(SettingsCatalog.controlEnabled("cloud","block_capsule_click_top",Map.of()));
        assertTrue(SettingsCatalog.controlEnabled("cloud","unbind_status_bar",values));
        for(String key:List.of("fullscreen","fullscreen_animation","recents","recents_shrink","shade_keep","other_keep","other_animation"))
            assertFalse(SettingsCatalog.controlEnabled("cloud",key,values));
        assertTrue(SettingsCatalog.controlEnabled("cloud","shade_keep",Map.of("enabled",true,"unbind_status_bar",true)));
        assertFalse(SettingsCatalog.controlEnabled("cloud","keep_paused_music",Map.of()));
    }

    @Test public void animationRowsAppearOnlyWhenKeepIsOffAndSummaryUsesThreeModes() {
        SettingsCatalog.Item keep = item("fullscreen");
        SettingsCatalog.Item animation = item("fullscreen_animation");
        assertFalse(animation.visible(Map.of("fullscreen", true)));
        assertTrue(animation.visible(Map.of("fullscreen", false)));
        assertTrue(animation.summary(Map.of()).endsWith("跟随系统"));
        assertTrue(animation.summary(Map.of("fullscreen_animation", true))
                .endsWith("使用收缩/展开动画"));
        assertTrue(animation.summary(Map.of("fullscreen", true, "fullscreen_animation", true))
                .endsWith("场景期间保持显示"));
        assertTrue(keep.summary(Map.of("fullscreen", true, "fullscreen_animation", true))
                .endsWith("场景期间保持显示"));
    }

    @Test public void everyScenePairUsesTheSameVisibilityAndSummaryRules() {
        for (String animationKey : List.of("fullscreen_animation", "recents_shrink", "other_animation",
                "landscape_fullscreen_animation", "landscape_recents_shrink", "landscape_other_animation")) {
            SettingsCatalog.Item animation = item(animationKey);
            SettingsCatalog.SceneSummary summary = animation.sceneSummary();
            assertNotNull(summary);
            assertFalse(animation.visible(Map.of(summary.keepKey(), true)));
            assertTrue(animation.summary(Map.of(summary.animationKey(), true))
                    .endsWith("使用收缩/展开动画"));
        }
    }

    private static SettingsCatalog.Item item(String key) {
        return SettingsCatalog.find("cloud").items().stream()
                .filter(value -> value.key().equals(key)).findFirst().orElseThrow();
    }
    @Test public void spectrumParametersAreNumericControlsGatedOnlyByTheMediaSwitch(){
        var on=Map.<String,Object>of("enabled",true,"realtime_media_wave",true);
        for(String key:List.of("wave_interval_ms","wave_sensitivity","wave_attack_ms","wave_release_ms")){
            var value=item(key);assertTrue(value.valueOnly());assertTrue(value.valueEnabled(on));assertTrue(value.visible(on));
            assertFalse(value.visible(Map.of()));assertTrue(SettingsCatalog.controlEnabled("cloud",key,on));
            assertFalse(SettingsCatalog.controlEnabled("cloud",key,Map.of("enabled",true,"unbind_status_bar",true)));
            assertTrue(SettingsCoordinator.validValue("cloud",key,value.valueDefault()));assertFalse(SettingsCoordinator.validValue("cloud",key,true));
            assertFalse(SettingsCoordinator.validValue("cloud",key,value.max()+value.step()));
        }
        assertEquals(80,item("wave_interval_ms").valueDefault());assertEquals(100,item("wave_sensitivity").valueDefault());
        assertEquals(20, item("wave_interval_ms").min());assertEquals(1000, item("wave_interval_ms").max());
        assertEquals(5, item("wave_sensitivity").min());assertEquals(500, item("wave_sensitivity").max());
        assertEquals(5, item("wave_attack_ms").min());assertEquals(1000, item("wave_attack_ms").max());
        assertEquals(10, item("wave_release_ms").min());assertEquals(2000, item("wave_release_ms").max());
        assertFalse(item("wave_hold_paused").defaultFlag());assertTrue(item("wave_hold_paused").visible(on));
        assertTrue(SettingsCoordinator.validValue("cloud","wave_hold_paused",true));
    }
    @Test public void collapseRowOnlyAppearsAndCanBeEditedWithShadeKeep(){
        var item=item("shade_collapse_on_open");assertFalse(item.defaultFlag());
        assertFalse(item.visible(Map.of()));assertTrue(item.visible(Map.of("shade_keep",true)));
        assertFalse(SettingsCatalog.controlEnabled("cloud",item.key(),Map.of("enabled",true,"unbind_status_bar",true)));
        assertTrue(SettingsCatalog.controlEnabled("cloud",item.key(),Map.of("enabled",true,"unbind_status_bar",true,"shade_keep",true)));
    }
    @Test public void landscapeShadeVisibilityAndEnablementNeverUsePortraitChoice() {
        var landscape=item("landscape_shade_collapse_on_open");
        var portrait=Map.<String,Object>of("enabled",true,"unbind_status_bar",true,"shade_keep",true);
        assertFalse(landscape.visible(portrait));
        assertFalse(SettingsCatalog.controlEnabled("cloud",landscape.key(),portrait));
        var horizontal=Map.<String,Object>of("enabled",true,"unbind_status_bar",true,"landscape_shade_keep",true);
        assertTrue(landscape.visible(horizontal));
        assertTrue(SettingsCatalog.controlEnabled("cloud",landscape.key(),horizontal));
        assertFalse(item("shade_collapse_on_open").visible(horizontal));
    }
    @Test public void mastersClearlyDescribeEnhancementsWithoutRenamingGroups() {
        for (String key:List.of("aod","cloud","battery")) {
            var group=SettingsCatalog.find(key);
            assertEquals("启用"+group.title()+"增强",SettingsCatalog.masterTitle(group));
        }
        assertEquals("启用安装器替换",SettingsCatalog.masterTitle(SettingsCatalog.find("installer")));
    }
    @Test public void inheritanceHidesOnlyIndependentLandscapeRowsAndTheirSections() {
        var values=Map.<String,Object>of("enabled",true,"unbind_status_bar",true,"landscape_follow_portrait",true);
        assertTrue(item("landscape_follow_portrait").visible(values));
        assertTrue(SettingsCatalog.controlEnabled("cloud","landscape_follow_portrait",values));
        assertTrue(SettingsCatalog.sectionVisible(SettingsCatalog.find("cloud"),"横屏配置",values));
        for(var item:SettingsCatalog.find("cloud").items()) {
            if(item.key().startsWith("landscape_") && !item.key().equals("landscape_follow_portrait")) {
                assertFalse(item.visible(values));
                assertFalse(SettingsCatalog.controlEnabled("cloud",item.key(),values));
                assertFalse(SettingsCatalog.sectionVisible(SettingsCatalog.find("cloud"),item.section(),values));
            }
        }
        assertTrue(item("fullscreen").visible(values));
    }
}
