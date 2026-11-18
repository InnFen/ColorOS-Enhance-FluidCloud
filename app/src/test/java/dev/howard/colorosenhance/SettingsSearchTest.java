package dev.howard.colorosenhance;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36,application=Application.class)
public class SettingsSearchTest {
    private Context context(){return RuntimeEnvironment.getApplication();}

    @Test public void pinFirstOffersTheMainFeatureAndSlotManagementInsteadOfOnlyTheFlashOption(){
        var results=SettingsSearch.search(context(),"PIN");
        assertTrue(results.size()>=3);
        assertEquals("sim",results.get(0).route());
        assertEquals("SIM PIN 自动解锁",results.get(0).title());
        assertEquals("sim_slots",results.get(1).route());
        assertEquals("卡槽与 PIN",results.get(1).title());
        assertTrue(results.stream().anyMatch(result->result.title().equals("暂缓 PIN 页面闪现")));
    }
    @Test public void navigationActionsAreSearchableEvenThoughTheyAreNotPreferenceItems(){
        assertTrue(SettingsSearch.search(context(),"蓝牙显示规则").stream()
                .anyMatch(result->result.route().equals("icons")&&result.title().equals("蓝牙显示规则")));
        assertTrue(SettingsSearch.search(context(),"编辑图标顺序").stream()
                .anyMatch(result->result.route().equals("icons")&&result.title().equals("编辑图标顺序")));
        assertTrue(SettingsSearch.search(context(),"选择安装器").stream()
                .anyMatch(result->result.route().equals("installer")&&result.title().equals("选择安装器")));
    }
    @Test public void fullScreenSearchFindsBothOrientationsWithTheirSceneContext(){
        var results=SettingsSearch.search(context(),"全屏");
        assertTrue(results.stream().anyMatch(result->result.route().equals("cloud_portrait")));
        assertTrue(results.stream().anyMatch(result->result.route().equals("cloud_landscape")));
        assertTrue(results.stream().anyMatch(result->result.title().equals("保持显示")&&result.summary().contains("进入/退出全屏")));
    }
    @Test public void pageAndIconSearchResultsLeadToTheirOwnSupportedRoutesWithoutDuplicateOptions(){
        var results=SettingsSearch.search(context(),"时钟");
        assertEquals(1,results.stream().filter(result->result.title().equals("时钟")).count());
        assertTrue(results.stream().anyMatch(result->result.title().equals("时钟")&&result.route().equals("icons_visibility")));
        assertTrue(results.stream().allMatch(result->SettingsLayout.known(result.route())));
        assertEquals(results.size(),new java.util.HashSet<>(results).size());
    }
    @Test public void resultOrderIsStableAndCaseWhitespaceAndFullWidthDoNotHidePinEntries(){
        var expected=SettingsSearch.search(context(),"PIN");
        assertEquals(expected,SettingsSearch.search(context(),"  p i n  "));
        assertEquals(expected,SettingsSearch.search(context(),"ＰＩＮ"));
        for(int attempt=0;attempt<5;attempt++)assertEquals(expected,SettingsSearch.search(context(),"pin"));
    }
    @Test public void configuredTextAndSyntheticSecretsNeverBecomeSearchMetadata(){
        context().getSharedPreferences("search_safety_fixture",Context.MODE_PRIVATE).edit()
                .putString("pin","739251").putString("display_name","synthetic_private_alias_9041").commit();
        assertTrue(SettingsSearch.search(context(),"739251").isEmpty());
        assertTrue(SettingsSearch.search(context(),"synthetic_private_alias_9041").isEmpty());
        assertTrue(SettingsSearch.search(context(),"PIN").stream().noneMatch(result->(result.title()+result.summary()).contains("739251")));
    }
    @Test public void blankQueriesReturnNoSuggestionsAndUnknownTermsDoNotInventRoutes(){
        assertTrue(SettingsSearch.search(context(),null).isEmpty());
        assertTrue(SettingsSearch.search(context()," \t ").isEmpty());
        assertTrue(SettingsSearch.search(context(),"no_such_feature_928134").isEmpty());
    }
}
