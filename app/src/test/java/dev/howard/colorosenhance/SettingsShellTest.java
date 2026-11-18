package dev.howard.colorosenhance;

import static org.junit.Assert.*;
import android.content.Intent;
import dev.howard.colorosenhance.settings.SettingsRoutes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Shared shell/navigation contracts; Compose rendering is checked separately from these metadata tests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=36,application=EnhanceApplication.class)
public class SettingsShellTest {
    @Test public void retiredAndUnknownSavedRoutesRestoreToHome(){
        for(String route:new String[]{null,"memory","unknown","../cloud","cloud/settings"})
            assertEquals("home",MainActivity.normalizeRoute(route));
        assertFalse(SettingsLayout.routes().contains("memory"));
    }
    @Test public void everyFeatureAndNestedSavedRouteRestoresToThatExactPage(){
        for(String route:SettingsLayout.routes())assertEquals(route,MainActivity.normalizeRoute(route));
        assertEquals("cloud_portrait",MainActivity.normalizeRoute("cloud_portrait"));
        assertEquals("sim_slots",MainActivity.normalizeRoute("sim_slots"));
    }
    @Test public void offlineFeatureStatesRemainReadableButNeverWritable(){
        var f=new SettingsUiControllerTest.Fixture();f.source=null;
        for(var group:SettingsCatalog.GROUPS){
            assertEquals(group.key(),SettingsLayout.group(group.key()));
            var state=f.ui.state(group.key());assertFalse(state.writable());assertTrue(state.values().isEmpty());
            assertEquals("unavailable",f.ui.toggle(group.key(),"enabled",true));
        }
    }
    @Test public void featureCategoriesContainEveryIndependentGroupOnce(){
        var categories=new java.util.ArrayList<>(SettingsLayout.DISPLAY_GROUPS);categories.addAll(SettingsLayout.SYSTEM_GROUPS);
        assertEquals(SettingsCatalog.GROUPS.size(),categories.size());
        assertEquals(SettingsCatalog.GROUPS.stream().map(SettingsCatalog.Group::key).collect(java.util.stream.Collectors.toSet()),Set.copyOf(categories));
        assertEquals(categories.size(),new HashSet<>(categories).size());
    }
    @Test public void nestedPagesReachHomeWithoutCyclesAndKeepOriginalPreferenceGroups(){
        for(String route:SettingsLayout.routes())if(!route.equals("home")){
            var page=SettingsLayout.page(route);assertNotNull(page);assertNotNull(SettingsCatalog.find(page.group()));
            assertFalse(page.title().isBlank());assertFalse(page.summary().isBlank());
            Set<String> visited=new HashSet<>();String current=route;
            while(!current.equals("home")){
                assertTrue("Navigation cycle at "+current,visited.add(current));
                var child=SettingsLayout.page(current);assertNotNull("Parent route must be recognized",child);
                current=child.parent();assertTrue(SettingsLayout.known(current));
            }
        }
        assertEquals("cloud",SettingsLayout.group("cloud_media"));
        assertEquals("cloud",SettingsLayout.group("cloud_landscape"));
        assertEquals("icons",SettingsLayout.group("icons_visibility"));
        assertFalse("Presentation routes must not create new preference namespaces",SettingsCoordinator.knownGroup("cloud_media"));
    }
    @Test public void reorganizedPagesExposeEveryCatalogItemExactlyOnce(){
        var context=RuntimeEnvironment.getApplication();
        for(var group:SettingsCatalog.GROUPS){
            var catalog=SettingsCatalog.find(group.key(),context);Map<String,Integer> counts=new HashMap<>();
            for(String route:SettingsLayout.routes())if(group.key().equals(SettingsLayout.group(route)))
                for(var item:SettingsLayout.items(context,route))counts.merge(item.key(),1,Integer::sum);
            assertEquals("No setting may disappear during regrouping: "+group.key(),
                    catalog.items().stream().map(SettingsCatalog.Item::key).collect(java.util.stream.Collectors.toSet()),counts.keySet());
            for(int count:counts.values())assertEquals("Each setting has one canonical page",1,count);
        }
    }
    @Test public void embeddedAndStandaloneSubpagesUseTheSameApprovedRoutesAndTitles(){
        for(String route:SettingsLayout.routes()){
            Intent intent=SettingsRoutes.intent(route);
            assertEquals(route,SettingsRoutes.route(intent.getBundleExtra(":settings:show_fragment_args")));
            assertTrue(SettingsRoutes.accepts(SettingsRoutes.FRAGMENT,intent));
            assertEquals(SettingsRoutes.title(route),intent.getStringExtra(":settings:show_fragment_title"));
            if(!route.equals("home"))assertEquals(SettingsLayout.page(route).title(),SettingsRoutes.title(route));
        }
        assertFalse(SettingsRoutes.accepts(SettingsRoutes.FRAGMENT,new Intent()));
    }
    @Test public void launcherManagementWorksWithoutTheRemoteConfigurationService(){
        var app=(EnhanceApplication)RuntimeEnvironment.getApplication();
        var launcher=new LauncherVisibility(app);boolean previous=launcher.visible();
        try{
            assertTrue(app.setLauncherVisible(false));assertFalse(launcher.visible());
            assertTrue(app.setLauncherVisible(true));assertTrue(launcher.visible());
        }finally{assertTrue(app.setLauncherVisible(previous));}
    }
    @Test public void readingManagementStatusDoesNotStartARestart(){
        var app=(EnhanceApplication)RuntimeEnvironment.getApplication();
        assertFalse(app.anyRestarting());
        for(int i=0;i<3;i++)assertFalse(app.managementState().getBoolean("restarting"));
        assertFalse(app.anyRestarting());
    }
}
