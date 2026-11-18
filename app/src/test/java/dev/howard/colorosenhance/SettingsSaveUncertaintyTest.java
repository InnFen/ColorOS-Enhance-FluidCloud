package dev.howard.colorosenhance;

import static org.junit.Assert.*;
import java.util.Map;
import org.junit.Test;

/** Acknowledgement and cross-frontend contracts, independent of the replaced Material View tree. */
public final class SettingsSaveUncertaintyTest {
    @Test public void failedRollbackKeepsLastAcknowledgedValuesForHomeAndDetailAndBlocksEditors(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("battery").values.putAll(Map.of("enabled",true,"weight_enabled",true,"weight",570));
        var home=new SettingsUiController(f.coordinator);var detail=new SettingsUiController(f.coordinator);
        long revision=detail.state("battery").revision();
        var session=f.coordinator.session("battery");
        f.store("battery").outcomes.add(false);f.store("battery").outcomes.add(false);

        assertEquals("uncertain",detail.toggle("battery","enabled",false));
        assertEquals(true,home.state("battery").values().get("enabled"));
        assertEquals(570,detail.state("battery").values().get("weight"));
        assertFalse(home.state("battery").writable());assertFalse(detail.state("battery").writable());
        assertTrue("A known but unconfirmed session must be distinguished from no service",home.state("battery").revision()>=0);
        var presentation=PreferencePresentation.from(session);
        assertTrue(presentation.saveFailure().contains("当前状态未确认"));
        assertTrue(presentation.detail().contains("下方仅显示上次确认的设置"));
        assertEquals("状态未确认（上次确认：已启用）",presentation.status());
        assertEquals("uncertain",detail.number("battery",SettingsUiControllerTest.item("battery","weight_enabled"),700,revision));
        assertEquals("uncertain",detail.reset("battery",revision));
        assertEquals("uncertain",home.toggle("battery","enabled",false));
        assertFalse(session.save("enabled",false));assertEquals(2,f.store("battery").writes);
    }
    @Test public void successfulRollbackReportsRestoredValuesAndAllowsAnExplicitRetry(){
        var f=new SettingsUiControllerTest.Fixture();f.ui.state("battery");
        f.store("battery").outcomes.add(false);f.store("battery").outcomes.add(true);
        assertEquals("failed",f.ui.toggle("battery","enabled",true));
        var state=f.ui.state("battery");assertTrue(state.writable());assertFalse(state.values().containsKey("enabled"));
        var presentation=PreferencePresentation.from(f.coordinator.session("battery"));
        assertTrue(presentation.saveFailure().contains("已恢复上次设置"));
        assertFalse(presentation.detail().contains("下方仅显示上次确认的设置"));
        assertEquals(2,f.store("battery").writes);
        assertEquals("ok",f.ui.toggle("battery","enabled",true));
        assertEquals(true,f.ui.state("battery").values().get("enabled"));assertEquals(3,f.store("battery").writes);
    }
    @Test public void sameConnectionRefreshAndNewRenderControllerKeepTheUncertaintyBlock(){
        var f=new SettingsUiControllerTest.Fixture();long revision=f.ui.state("battery").revision();
        var original=f.coordinator.session("battery");
        f.store("battery").outcomes.add(false);f.store("battery").outcomes.add(false);
        assertEquals("uncertain",f.ui.reset("battery",revision));
        for(int i=0;i<3;i++){
            f.coordinator.refresh();var recreatedUi=new SettingsUiController(f.coordinator);
            assertSame(original,f.coordinator.session("battery"));assertFalse(recreatedUi.state("battery").writable());
            assertEquals("uncertain",recreatedUi.toggle("battery","enabled",true));
        }
        assertEquals(2,f.store("battery").writes);
        f.source=null;
        assertEquals(-1,f.ui.state("battery").revision());assertFalse(f.ui.state("battery").writable());
        assertEquals("unavailable",f.ui.toggle("battery","enabled",true));
        assertEquals(PreferencePresentation.State.DISCONNECTED,PreferencePresentation.from(f.coordinator.session("battery")).state());
    }
    @Test public void replacementServiceSuppliesFreshAcknowledgedStateAndRejectsOldPendingInput(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("battery").values.putAll(Map.of("weight_enabled",true,"weight",570));
        long oldRevision=f.ui.state("battery").revision();var previous=f.source;
        f.store("battery").outcomes.add(false);f.store("battery").outcomes.add(false);
        assertEquals("uncertain",f.ui.toggle("battery","enabled",true));
        f.source=null;f.ui.state("battery");f.reconnect();
        f.store("battery").values.putAll(Map.of("enabled",true,"weight_enabled",true,"weight",620));
        var replacement=new SettingsUiController(f.coordinator);var state=replacement.state("battery");
        assertTrue(state.writable());assertEquals(620,state.values().get("weight"));assertNotEquals(oldRevision,state.revision());
        assertEquals("conflict",replacement.number("battery",SettingsUiControllerTest.item("battery","weight_enabled"),700,oldRevision));
        assertEquals(2,previous.store("battery").writes);assertEquals(0,f.store("battery").writes);
        assertEquals("ok",replacement.number("battery",SettingsUiControllerTest.item("battery","weight_enabled"),650,state.revision()));
    }
    @Test public void uncertainFeatureDoesNotDisableOrOverwriteTheIndependentIntegrationSwitch(){
        var f=new SettingsUiControllerTest.Fixture();
        f.store("battery").values.put("enabled",true);f.ui.state("battery");
        f.store("battery").outcomes.add(false);f.store("battery").outcomes.add(false);
        assertEquals("uncertain",f.ui.toggle("battery","enabled",false));
        assertEquals("ok",f.ui.toggle("integration","show_entry",false));
        assertEquals(Map.of("show_entry",false),f.ui.state("integration").values());assertTrue(f.ui.state("integration").writable());
        assertEquals(true,f.ui.state("battery").values().get("enabled"));assertFalse(f.ui.state("battery").writable());
        assertEquals(2,f.store("battery").writes);assertEquals(1,f.store("integration").writes);
    }
    @Test public void integrationChangesFromAnotherFrontendOrServiceCannotBeOverwritten(){
        var f=new SettingsUiControllerTest.Fixture();long revision=f.ui.state("integration").revision();
        assertEquals("ok",f.coordinator.write("integration","show_entry",false,revision).status());
        assertEquals("conflict",f.ui.put("integration","show_entry",true,revision));
        assertEquals(false,f.ui.state("integration").values().get("show_entry"));assertEquals(1,f.store("integration").writes);
        long pendingRevision=f.ui.state("integration").revision();var previous=f.source;f.reconnect();
        f.store("integration").values.put("show_entry",true);
        assertEquals("conflict",f.ui.put("integration","show_entry",false,pendingRevision));
        assertEquals(true,f.ui.state("integration").values().get("show_entry"));
        assertEquals(1,previous.store("integration").writes);assertEquals(0,f.store("integration").writes);
    }
}
