package dev.howard.colorosenhance;

import static org.junit.Assert.*;
import android.app.Application;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class SettingsLayoutTest {
    @Test public void eachExistingPreferenceAppearsExactlyOnceAcrossItsPresentationPages(){
        var context=RuntimeEnvironment.getApplication();
        for(var group:SettingsCatalog.GROUPS){
            Map<String,Integer> occurrences=new HashMap<>();
            for(String route:SettingsLayout.routes())if(group.key().equals(SettingsLayout.group(route)))
                for(var item:SettingsLayout.items(context,route))occurrences.merge(item.key(),1,Integer::sum);
            var expected=SettingsCatalog.find(group.key(),context).items();
            assertEquals(group.key(),expected.size(),occurrences.size());
            for(var item:expected)assertEquals(group.key()+":"+item.key(),Integer.valueOf(1),occurrences.get(item.key()));
        }
    }
    @Test public void homeCategoriesCoverEveryFeatureWithoutDuplication(){
        var listed=new ArrayList<>(SettingsLayout.DISPLAY_GROUPS);listed.addAll(SettingsLayout.SYSTEM_GROUPS);
        var expected=SettingsCatalog.GROUPS.stream().map(SettingsCatalog.Group::key).collect(java.util.stream.Collectors.toSet());
        assertEquals(expected,new HashSet<>(listed));assertEquals(expected.size(),listed.size());
    }
    @Test public void allNestedRoutesLeadBackToHomeAndKeepOriginalPreferenceGroups(){
        for(String route:SettingsLayout.routes()){
            if(route.equals("home"))continue;
            var page=SettingsLayout.page(route);assertNotNull(page);assertNotNull(SettingsCatalog.find(page.group()));
            String cursor=route;Set<String> visited=new HashSet<>();
            while(!cursor.equals("home")){
                assertTrue("Route cycle at "+cursor,visited.add(cursor));
                var node=SettingsLayout.page(cursor);assertNotNull(node);cursor=node.parent();
            }
            if(!route.equals(page.group()))assertFalse("Presentation route must not create a preference namespace",SettingsCoordinator.knownGroup(route));
        }
    }
    @Test public void topicPagesSeparateMediaCardsAndOrientationWithoutChangingDefaults(){
        var context=RuntimeEnvironment.getApplication();
        assertTrue(SettingsLayout.items(context,"cloud").isEmpty());
        assertTrue(SettingsLayout.items(context,"cloud_media").stream().anyMatch(item->item.key().equals("realtime_media_wave")&&!item.defaultFlag()));
        assertTrue(SettingsLayout.items(context,"cloud_cards").stream().allMatch(item->item.section().equals("展开卡片")));
        assertTrue(SettingsLayout.items(context,"cloud_portrait").stream().allMatch(item->!item.key().startsWith("landscape_")));
        assertTrue(SettingsLayout.items(context,"cloud_landscape").stream().allMatch(item->item.key().startsWith("landscape_")&&!item.key().equals("landscape_follow_portrait")));
        assertNull(SettingsLayout.page("memory"));assertTrue(SettingsLayout.items(context,"memory").isEmpty());
    }
}
