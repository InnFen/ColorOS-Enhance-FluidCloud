package dev.howard.colorosenhance.settings;
import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.preference.*;
import android.view.KeyEvent;
import android.view.ViewGroup;
import com.coui.appcompat.preference.COUISwitchPreference;
import dev.howard.colorosenhance.SettingsCatalog;
import dev.howard.colorosenhance.bridge.SettingsBridgeClient;
import dev.howard.colorosenhance.ui.WaveBandAxisView;
import dev.lsposed.fluidcloud.MediaWaveBands;
import java.util.*;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class NativeSettingsPageTest {
    @Test public void cloudRootShowsTopicNavigationInsteadOfAllSwitches()throws Exception{
        Fixture f=new Fixture("cloud");f.page.start();f.backend.connected();f.backend.reply("snapshot",config(1,true));
        assertNotNull(f.host.screen.findPreference("enabled"));assertNull(f.host.screen.findPreference("recents"));
        assertNull(f.host.screen.findPreference("keep_paused_music"));
        for(String route:List.of("cloud_media","cloud_capsule","cloud_cards","cloud_scenes")){
            Preference jump=f.host.screen.findPreference("open_"+route);assertNotNull(jump);
            jump.getOnPreferenceClickListener().onPreferenceClick(jump);
            var next=Shadows.shadowOf(f.host.context).getNextStartedActivity();
            assertEquals(route,SettingsRoutes.route(next.getBundleExtra(":settings:show_fragment_args")));
        }
    }
    @Test public void visibilitySubpageKeepsOnlyItsRowsAndWritesToOriginalIconsGroup()throws Exception{
        Fixture f=new Fixture("icons_visibility");f.page.start();f.backend.connected();
        Bundle state=config(7,true);state.getBundle("values").putBoolean("shade_only.clock",true);f.backend.reply("snapshot",state);
        assertNull(f.host.screen.findPreference("enabled"));assertNull(f.host.screen.findPreference("bluetooth_mode"));
        assertNull(f.host.screen.findPreference("edit_order"));assertNull(f.host.screen.findPreference("order_enabled"));
        assertNotNull(f.host.screen.findPreference("parent_status"));
        SwitchPreference clock=f.switchAt("shade_only.clock");assertTrue(clock.isChecked());assertTrue(clock.isEnabled());
        clock.getOnPreferenceChangeListener().onPreferenceChange(clock,false);
        Bundle put=f.backend.find("put").request;assertEquals("icons",put.getString("group"));
        assertEquals("shade_only.clock",put.getString("key"));assertEquals(7,put.getLong("revision"));
        state=config(8,false);state.getBundle("values").putBoolean("shade_only.clock",true);f.backend.reply("put",state);
        assertTrue(clock.isChecked());assertFalse(clock.isEnabled());
    }
    @Test public void sceneInheritanceHidesItsNavigationOnlyAfterSaveAcknowledgment()throws Exception{
        Fixture f=new Fixture("cloud_scenes");f.page.start();f.backend.connected();
        Bundle state=config(7,true);state.getBundle("values").putBoolean("unbind_status_bar",true);f.backend.reply("snapshot",state);
        var target=(com.oplus.settings.widget.preference.SettingsSimpleJumpPreference)f.host.screen.findPreference("open_cloud_landscape");
        assertTrue(target.isVisible());SwitchPreference follow=f.switchAt("landscape_follow_portrait");
        follow.getOnPreferenceChangeListener().onPreferenceChange(follow,true);
        assertFalse(follow.isChecked());assertTrue(target.isVisible());
        Bundle put=f.backend.find("put").request;assertEquals("cloud",put.getString("group"));assertEquals(7,put.getLong("revision"));
        state=config(8,true);state.getBundle("values").putBoolean("unbind_status_bar",true);
        state.getBundle("values").putBoolean("landscape_follow_portrait",true);f.backend.reply("put",state);
        assertTrue(follow.isChecked());assertFalse(target.isVisible());
        assertTrue(((com.oplus.settings.widget.preference.SettingsSimpleJumpPreference)f.host.screen.findPreference("open_cloud_portrait")).isVisible());
    }
    @Test public void childNumberUsesCloudNamespaceAndDisconnectionCancelsTheGesture()throws Exception{
        Fixture f=new Fixture("cloud_media");f.page.start();f.backend.connected();
        Bundle state=config(7,true);state.getBundle("values").putBoolean("realtime_media_wave",true);f.backend.reply("snapshot",state);
        Preference preference=f.host.screen.findPreference("wave_interval_ms");
        android.view.ViewGroup root=(android.view.ViewGroup)preference.getView(null,new android.widget.LinearLayout(f.host.context));f.page.bindNumber(preference,root);
        var line=NativeNumberRowTest.numericLine(root);var bar=(com.coui.appcompat.seekbar.COUISeekBar)line.getChildAt(0);
        bar.start();bar.move(8);f.backend.connected=false;f.backend.listener.onConnectionChanged(false);
        assertFalse(bar.isEnabled());bar.stop();assertTrue(f.backend.pending.isEmpty());
        assertEquals("正在读取状态 · 点按返回总设置",f.host.screen.findPreference("parent_status").getSummary().toString());
    }
    @Test public void iconOrderEditorSavesBothSidesInOneRevisionedNativeWrite()throws Exception{
        Fixture f=new Fixture("icons");f.page.start();f.backend.connected();f.backend.reply("snapshot",config(17,true));
        var edit=f.host.screen.findPreference("edit_order");assertTrue(edit.isEnabled());edit.getOnPreferenceClickListener().onPreferenceClick(edit);
        var dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        Bundle write=f.backend.find("put").request;
        assertEquals("order_layout",write.getString("key"));assertEquals(17,write.getLong("revision"));
        var order=dev.howard.colorosenhance.icons.IconOrder.decode(write.getString("value"));
        assertTrue(order.left().contains("clock"));assertTrue(order.right().contains("battery"));
    }
    @Test public void iconRulesUseNativeDialogAndRevisionedWriteWhilePreservingPage() throws Exception {
        Fixture f=new Fixture("icons");f.page.start();f.backend.connected();
        Bundle config=config(7,true);config.getBundle("values").putBoolean("shade_only.clock",true);f.backend.reply("snapshot",config);
        var screen=f.host.screen;assertNull(screen.findPreference("shade_only.clock"));
        assertNotNull(screen.findPreference("open_icons_visibility"));
        var rule=screen.findPreference("bluetooth_mode");assertTrue(rule.isEnabled());
        rule.getOnPreferenceClickListener().onPreferenceClick(rule);
        var dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        org.robolectric.Shadows.shadowOf(dialog).clickOnItem(2);
        Bundle write=f.backend.find("put").request;
        assertEquals("icons",write.getString("group"));assertEquals("bluetooth_mode",write.getString("key"));
        assertEquals(2,write.getInt("value"));assertEquals(7,write.getLong("revision"));
        config=config(8,false);config.getBundle("values").putInt("bluetooth_mode",2);config.getBundle("values").putBoolean("shade_only.clock",true);f.backend.reply("put",config);
        assertSame(screen,f.host.screen);assertFalse(rule.isEnabled());
        assertTrue(config.getBundle("values").getBoolean("shade_only.clock"));
    }
    @Test public void inheritanceUpdatesNativeRowsAndCategoriesWithoutRecreatingPage() throws Exception {
        Fixture f=new Fixture("cloud_landscape");f.page.start();f.backend.connected();
        Bundle config=config(1,true);config.getBundle("values").putBoolean("unbind_status_bar",true);
        f.backend.reply("snapshot",config);
        var screen=f.host.screen;
        var horizontal=(COUISwitchPreference)screen.findPreference("landscape_fullscreen");
        assertTrue(horizontal.isVisible());
        f.backend.listener.onChanged();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        config=config(2,true);config.getBundle("values").putBoolean("unbind_status_bar",true);
        config.getBundle("values").putBoolean("landscape_follow_portrait",true);f.backend.reply("snapshot",config);
        assertSame(screen,f.host.screen);assertFalse(horizontal.isVisible());
        var category=(com.oplus.settings.widget.preference.SettingsPreferenceCategory)screen.findPreference("coloros.section_横屏 · 进入/退出全屏");
        assertFalse(category.isVisible());
        assertNull(screen.findPreference("fullscreen"));assertNull(screen.findPreference("landscape_follow_portrait"));
    }
    @Test public void installerChoiceIsNativeAndUsesBridgeStringWriteWithRevision() throws Exception {
        Fixture f=new Fixture("installer"); f.page.start();f.backend.connected();
        Bundle config=config(7,true);config.getBundle("values").putString("target_package","first.installer");
        config.putString("target_label","First installer\nfirst.installer");f.backend.reply("snapshot",config);
        Preference row=f.host.screen.findPreference("target_package");assertNotNull(row);
        assertTrue(row.getSummary().toString().contains("first.installer"));
        row.getOnPreferenceClickListener().onPreferenceClick(row);
        Bundle choices=new Bundle();choices.putString("status","ok");choices.putStringArray("packages",new String[]{"second.installer"});
        choices.putStringArray("labels",new String[]{"Second installer\nsecond.installer"});f.backend.reply("installer_choices",choices);
        var dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        org.robolectric.Shadows.shadowOf(dialog).clickOnItem(1);
        Bundle write=f.backend.find("put").request;assertEquals("target_package",write.getString("key"));
        assertEquals("second.installer",write.getString("value"));assertEquals(7,write.getLong("revision"));
        assertTrue(row.getSummary().toString().contains("first.installer"));
    }
    @Test public void installerStartsOffExplainsFrameworkRestartAndWritesOnlyItsGroup() throws Exception {
        Fixture f=new Fixture("installer"); f.page.start(); f.backend.connected();
        Bundle fresh=config(1,false); fresh.putBundle("values",new Bundle()); f.backend.reply("snapshot",fresh);
        SwitchPreference master=f.switchAt("enabled"); assertFalse(master.isChecked()); assertTrue(master.isEnabled());
        Preference help=f.host.screen.findPreference("installer_help"); assertNotNull(help);
        assertTrue(help.getSummary().toString().contains("完整重启手机")); assertFalse(help.isSelectable());
        var screen=f.host.screen;
        master.getOnPreferenceChangeListener().onPreferenceChange(master,true);
        Bundle write=f.backend.find("put").request;
        assertEquals("installer",write.getString("group")); assertEquals("enabled",write.getString("key"));
        assertTrue(write.getBoolean("value")); assertFalse(master.isChecked());
        f.backend.reply("put",config(2,true)); assertTrue(master.isChecked()); assertSame(screen,f.host.screen);
    }
    @Test public void cloudDependenciesUpdateExistingNativePreferencesAfterAcknowledgment() throws Exception {
        Fixture f=new Fixture("cloud_portrait"); f.page.start(); f.backend.connected();
        Bundle state=config(7,true); state.getBundle("values").putBoolean("fullscreen",false);state.getBundle("values").putBoolean("unbind_status_bar",true);
        f.backend.reply("snapshot",state);
        COUISwitchPreference animation=(COUISwitchPreference)f.host.screen.findPreference("fullscreen_animation");
        assertTrue(animation.isVisible());
        PreferenceScreen screen=f.host.screen;

        SwitchPreference keep=f.switchAt("fullscreen");
        keep.getOnPreferenceChangeListener().onPreferenceChange(keep,true);
        Bundle saved=config(8,true); saved.getBundle("values").putBoolean("fullscreen",true);saved.getBundle("values").putBoolean("unbind_status_bar",true);
        f.backend.reply("put",saved);

        assertSame(screen,f.host.screen);
        assertSame(animation,f.host.screen.findPreference("fullscreen_animation"));
        assertFalse(animation.isVisible());
    }
    @Test public void cloudMasterDisablesChildrenWithoutChangingTheirCheckedValues() throws Exception {
        Fixture f=new Fixture("cloud_portrait"); f.page.start(); f.backend.connected();
        Bundle state=config(7,false); state.getBundle("values").putBoolean("other_keep",true);
        f.backend.reply("snapshot",state);

        SwitchPreference other=f.switchAt("other_keep");
        assertTrue(other.isChecked());
        assertFalse(other.isEnabled());
        assertFalse(f.switchAt("fullscreen_animation").isEnabled());
    }

    @Test public void unbindGateKeepsMediaUsableButDisablesSavedSceneChoices()throws Exception{
        Bundle state=config(7,true);state.getBundle("values").putBoolean("fullscreen",true);
        Fixture media=new Fixture("cloud_media");media.page.start();media.backend.connected();media.backend.reply("snapshot",state);
        assertTrue(media.switchAt("keep_paused_music").isEnabled());
        assertTrue(media.switchAt("realtime_media_wave").isEnabled());assertFalse(media.switchAt("realtime_media_wave").isChecked());
        Fixture scenes=new Fixture("cloud_scenes");scenes.page.start();scenes.backend.connected();scenes.backend.reply("snapshot",state);
        assertTrue(scenes.switchAt("unbind_status_bar").isEnabled());
        Fixture portrait=new Fixture("cloud_portrait");portrait.page.start();portrait.backend.connected();portrait.backend.reply("snapshot",state);
        assertFalse(portrait.switchAt("fullscreen").isEnabled());assertTrue(portrait.switchAt("fullscreen").isChecked());assertFalse(portrait.switchAt("shade_keep").isEnabled());
    }

    @Test public void nativeSliderSavesOnlyCompletedGestureWithCapturedRevision()throws Exception{
        Fixture f=new Fixture("battery");f.page.start();f.backend.connected();
        Bundle state=config(7,true);state.getBundle("values").putBoolean("weight_enabled",true);state.getBundle("values").putInt("weight",500);f.backend.reply("snapshot",state);
        Preference preference=f.host.screen.findPreference("weight");
        android.view.ViewGroup root=(android.view.ViewGroup)preference.getView(null,new android.widget.LinearLayout(f.host.context));
        f.page.bindNumber(preference,root);
        android.widget.LinearLayout line=NativeNumberRowTest.numericLine(root);
        var bar=(com.coui.appcompat.seekbar.COUISeekBar)line.getChildAt(0);
        bar.start();bar.move(60);assertTrue(f.backend.pending.isEmpty());bar.stop();
        Bundle write=f.backend.find("put").request;assertEquals("weight",write.getString("key"));assertEquals(700,write.getInt("value"));assertEquals(7,write.getLong("revision"));
        assertEquals("700",((android.widget.TextView)line.getChildAt(1)).getText().toString());assertEquals(60,bar.getProgress());
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertEquals("700",((android.widget.TextView)line.getChildAt(1)).getText().toString());assertEquals(60,bar.getProgress());
        bar.move(40);assertEquals(1,f.backend.pending.size());assertEquals(60,bar.getProgress());
        Bundle saved=config(8,true);saved.getBundle("values").putBoolean("weight_enabled",true);saved.getBundle("values").putInt("weight",700);f.backend.reply("put",saved);
        assertEquals("700",((android.widget.TextView)line.getChildAt(1)).getText().toString());
        bar.start();bar.move(70);f.backend.listener.onChanged();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Bundle external=config(9,true);external.getBundle("values").putBoolean("weight_enabled",true);external.getBundle("values").putInt("weight",400);f.backend.reply("snapshot",external);
        bar.stop();assertTrue(f.backend.pending.isEmpty());assertEquals("400",((android.widget.TextView)line.getChildAt(1)).getText().toString());
    }
    @Test public void spectrumNumericRowHasNoExtraSwitchAndSavesItsIntervalAsAnInteger()throws Exception{
        Fixture f=new Fixture("cloud_media");f.page.start();f.backend.connected();
        Bundle state=config(7,true);state.getBundle("values").putBoolean("realtime_media_wave",true);f.backend.reply("snapshot",state);
        Preference preference=f.host.screen.findPreference("wave_interval_ms");
        assertFalse(preference instanceof SwitchPreference);assertTrue(((com.coui.appcompat.preference.COUIPreference)preference).isVisible());assertTrue(preference.isEnabled());
        assertFalse(f.switchAt("wave_hold_paused").isChecked());
        android.view.ViewGroup root=(android.view.ViewGroup)preference.getView(null,new android.widget.LinearLayout(f.host.context));
        f.page.bindNumber(preference,root);var line=NativeNumberRowTest.numericLine(root);var bar=(com.coui.appcompat.seekbar.COUISeekBar)line.getChildAt(0);
        bar.start();bar.move(98);bar.stop();Bundle request=f.backend.find("put").request;
        assertEquals("wave_interval_ms",request.getString("key"));assertEquals(1000,request.getInt("value"));assertEquals(7,request.getLong("revision"));
    }
    @Test public void spectrumParametersHideWhenTheMediaSwitchIsOffAndRespectTheCloudMaster()throws Exception{
        Fixture f=new Fixture("cloud_media");f.page.start();f.backend.connected();f.backend.reply("snapshot",config(7,true));
        assertFalse(((com.coui.appcompat.preference.COUIPreference)f.host.screen.findPreference("wave_interval_ms")).isVisible());assertFalse(((COUISwitchPreference)f.switchAt("wave_hold_paused")).isVisible());
        assertFalse(((com.oplus.settings.widget.preference.SettingsSimpleJumpPreference)f.host.screen.findPreference(MediaWaveBands.KEY)).isVisible());
        assertFalse(((com.oplus.settings.widget.preference.SettingsSimpleJumpPreference)f.host.screen.findPreference("reset_wave")).isVisible());
        Fixture disabled=new Fixture("cloud_media");disabled.page.start();disabled.backend.connected();Bundle state=config(7,false);
        state.getBundle("values").putBoolean("realtime_media_wave",true);disabled.backend.reply("snapshot",state);
        assertFalse(disabled.host.screen.findPreference("wave_interval_ms").isEnabled());
        assertFalse(disabled.host.screen.findPreference(MediaWaveBands.KEY).isEnabled());
        assertFalse(disabled.host.screen.findPreference("reset_wave").isEnabled());
    }
    @Test public void waveBandEditorRejectsOverlappingEdgesAndSavesOrRestoresInOneWrite() throws Exception {
        Fixture f = new Fixture("cloud_media");
        f.page.start();
        f.backend.connected();
        Bundle initial = config(7, true);
        initial.getBundle("values").putBoolean("realtime_media_wave", true);
        f.backend.reply("snapshot", initial);
        Preference row = f.host.screen.findPreference(MediaWaveBands.KEY);
        assertTrue(row.isEnabled());
        assertEquals(SettingsCatalog.waveBandsSummary(MediaWaveBands.DEFAULTS), row.getSummary().toString());

        row.getOnPreferenceClickListener().onPreferenceClick(row);
        var dialog = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        var fields = NativeDialogsTest.inputs(dialog.getWindow().getDecorView());
        assertEquals(MediaWaveBands.EDGE_COUNT, fields.size());
        var container = (ViewGroup) fields.getFirst().getParent().getParent();
        var axis = (WaveBandAxisView) container.getChildAt(1);
        assertTrue(axis.isEnabled());
        fields.get(1).setText("40");
        assertFalse(axis.isEnabled());
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        assertTrue(f.backend.pending.isEmpty());

        fields.get(1).setText("180");
        assertTrue(axis.isEnabled());
        assertTrue(axis.onKeyDown(KeyEvent.KEYCODE_DPAD_UP,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)));
        assertEquals("41", fields.getFirst().getText().toString());
        assertEquals("180", fields.get(1).getText().toString());
        assertTrue(f.backend.pending.isEmpty());
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
        Bundle request = f.backend.find("put").request;
        assertEquals("cloud", request.getString("group"));
        assertEquals(MediaWaveBands.KEY, request.getString("key"));
        assertEquals("41,180,315,800,2000,5000,16000", request.getString("value"));
        assertEquals(7, request.getLong("revision"));
        assertEquals(SettingsCatalog.waveBandsSummary(MediaWaveBands.DEFAULTS), row.getSummary().toString());

        Bundle saved = config(8, true);
        saved.getBundle("values").putBoolean("realtime_media_wave", true);
        saved.getBundle("values").putString(MediaWaveBands.KEY, request.getString("value"));
        f.backend.reply("put", saved);
        assertEquals(SettingsCatalog.waveBandsSummary(MediaWaveBands.parse(request.getString("value"))), row.getSummary().toString());

        row.getOnPreferenceClickListener().onPreferenceClick(row);
        dialog = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).performClick();
        assertFalse(dialog.isShowing());
        Bundle restore = f.backend.find("put").request;
        assertEquals(MediaWaveBands.KEY, restore.getString("key"));
        assertEquals(MediaWaveBands.DEFAULTS.encode(), restore.getString("value"));
        assertEquals(8, restore.getLong("revision"));
    }
    @Test public void waveResetSendsTheDedicatedCloudCommandAtTheConfirmedRevision() throws Exception {
        Fixture f = new Fixture("cloud_media");
        f.page.start();
        f.backend.connected();
        Bundle state = config(17, true);
        state.getBundle("values").putBoolean("realtime_media_wave", true);
        f.backend.reply("snapshot", state);
        Preference reset = f.host.screen.findPreference("reset_wave");
        assertTrue(reset.isEnabled());
        reset.getOnPreferenceClickListener().onPreferenceClick(reset);
        Bundle request = f.backend.find("reset_wave").request;
        assertEquals("cloud", request.getString("group"));
        assertEquals(17, request.getLong("revision"));
        assertEquals(1, f.backend.pending.size());
    }
    @Test public void rejectedNumericWriteRestoresAcknowledgedValueAfterPendingPreview()throws Exception{
        Fixture f=new Fixture("battery");f.page.start();f.backend.connected();
        Bundle state=config(7,true);state.getBundle("values").putBoolean("weight_enabled",true);state.getBundle("values").putInt("weight",500);f.backend.reply("snapshot",state);
        Preference preference=f.host.screen.findPreference("weight");
        android.view.ViewGroup root=(android.view.ViewGroup)preference.getView(null,new android.widget.LinearLayout(f.host.context));f.page.bindNumber(preference,root);
        var line=NativeNumberRowTest.numericLine(root);var bar=(com.coui.appcompat.seekbar.COUISeekBar)line.getChildAt(0);
        bar.start();bar.move(60);bar.stop();assertEquals("700",((android.widget.TextView)line.getChildAt(1)).getText().toString());
        Bundle conflict=config(8,true);conflict.putString("status","conflict");conflict.getBundle("values").putBoolean("weight_enabled",true);conflict.getBundle("values").putInt("weight",400);f.backend.reply("put",conflict);
        assertEquals("400",((android.widget.TextView)line.getChildAt(1)).getText().toString());assertEquals(30,bar.getProgress());
        bar.move(55);assertEquals("400",((android.widget.TextView)line.getChildAt(1)).getText().toString());assertTrue(f.backend.pending.isEmpty());
    }
    @Test public void connectionAndNumericSummariesAreVisibleInNativeRows() throws Exception {
        Fixture f=new Fixture("battery"); f.page.start(); f.backend.connected(); f.backend.reply("snapshot",config(7,true));
        for(String key:List.of("status","weight")) {
            Preference preference=f.host.screen.findPreference(key);
            android.view.View view=preference.getView(null,new android.widget.LinearLayout(f.host.context));
            android.widget.TextView summary=view.findViewById(android.R.id.summary);
            assertNotNull("Native row must contain a summary: "+key,summary);
            assertEquals(android.view.View.VISIBLE,summary.getVisibility());
            assertFalse(summary.getText().toString().isEmpty());
        }
    }
    @Test public void switchWaitsForAcknowledgmentAndUsesCurrentRevision() throws Exception {
        Fixture f = new Fixture("battery");
        f.page.start();
        assertFalse(f.switchAt("enabled").isEnabled());
        f.backend.connected();
        f.backend.reply("snapshot",config(7,true));
        SwitchPreference master = f.switchAt("enabled");
        assertTrue(master.isChecked()); assertTrue(master.isEnabled()); assertFalse(master.isPersistent());
        assertFalse(master.getOnPreferenceChangeListener().onPreferenceChange(master,false));
        assertTrue(master.isChecked()); assertTrue(master.isEnabled());
        Bundle write = f.backend.find("put").request;
        assertEquals(7,write.getLong("revision")); assertEquals(false,write.getBoolean("value"));
        f.backend.reply("put",config(8,false));
        assertFalse(master.isChecked()); assertTrue(master.isEnabled());
    }
    @Test public void fastSaveKeepsPageAppearanceAndStillRejectsDuplicateWrites() throws Exception {
        Fixture f=new Fixture("battery"); f.page.start(); f.backend.connected(); f.backend.reply("snapshot",config(7,true));
        SwitchPreference master=f.switchAt("enabled"), other=f.switchAt("weight_enabled");
        String notice=f.host.screen.findPreference("status").getSummary().toString();
        master.getOnPreferenceChangeListener().onPreferenceChange(master,false);
        assertTrue("A fast save must not dim unrelated rows",other.isEnabled());
        assertEquals(notice,f.host.screen.findPreference("status").getSummary().toString());
        master.getOnPreferenceChangeListener().onPreferenceChange(master,false);
        assertEquals(1,f.backend.pending.stream().filter(p->"put".equals(p.request.getString("op"))).count());
        f.backend.reply("put",config(8,false));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertFalse(master.isChecked()); assertTrue(other.isEnabled());
        assertEquals(notice,f.host.screen.findPreference("status").getSummary().toString());
    }
    @Test public void slowSaveShowsBusyUntilAcknowledgedAndOldTimerCannotDimNewOperation() throws Exception {
        Fixture f=new Fixture("battery"); f.page.start(); f.backend.connected(); f.backend.reply("snapshot",config(7,true));
        SwitchPreference master=f.switchAt("enabled");
        master.getOnPreferenceChangeListener().onPreferenceChange(master,false);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
        f.backend.reply("put",config(8,false));
        master.getOnPreferenceChangeListener().onPreferenceChange(master,true);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
        assertTrue("The completed write's delayed indicator must not affect a new write",master.isEnabled());
        assertFalse(master.isChecked());
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
        assertFalse(master.isEnabled());
        assertTrue(f.host.screen.findPreference("status").getSummary().toString().contains("保存"));
        f.backend.reply("put",config(9,true));
        assertTrue(master.isEnabled()); assertTrue(master.isChecked());
    }
    @Test public void staleReplyAfterStopCannotMutatePageAndRestartUsesNewConnection() throws Exception {
        Fixture f = new Fixture("battery"); f.page.start(); f.backend.connected();
        Pending pending = f.backend.find("snapshot");
        f.page.stop(); assertTrue(f.backend.closed);
        pending.reply.accept(config(7,true)); assertFalse(f.switchAt("enabled").isEnabled());
        f.page.start(); assertEquals(2,f.backends.size());
        f.backend.connected(); f.backend.reply("snapshot",config(9,false));
        assertTrue(f.switchAt("enabled").isEnabled()); assertFalse(f.switchAt("enabled").isChecked());
    }
    @Test public void returnKeepsConditionalRowsInPlaceButCannotWriteBeforeFreshSnapshot() throws Exception {
        Fixture f=new Fixture("cloud_scenes");f.page.start();f.backend.connected();
        Bundle first=config(17,true);first.getBundle("values").putBoolean("landscape_follow_portrait",true);first.getBundle("values").putBoolean("unbind_status_bar",true);
        f.backend.reply("snapshot",first);
        var landscape=(com.oplus.settings.widget.preference.SettingsSimpleJumpPreference)f.host.screen.findPreference("open_cloud_landscape");
        assertFalse(landscape.isVisible());assertTrue(f.switchAt("landscape_follow_portrait").isChecked());
        f.page.stop();f.page.start();f.backend.connected();
        assertFalse(landscape.isVisible());assertTrue(f.switchAt("landscape_follow_portrait").isChecked());
        assertFalse(f.switchAt("landscape_follow_portrait").isEnabled());
        f.switchAt("landscape_follow_portrait").getOnPreferenceChangeListener().onPreferenceChange(f.switchAt("landscape_follow_portrait"),false);
        assertFalse(f.backend.pending.stream().anyMatch(p->"put".equals(p.request.getString("op"))));
        // A new backend can restart its revision counter; cached revisions are never authoritative.
        Bundle current=config(2,true);current.getBundle("values").putBoolean("landscape_follow_portrait",false);current.getBundle("values").putBoolean("unbind_status_bar",true);
        f.backend.reply("snapshot",current);
        assertTrue(landscape.isVisible());assertTrue(f.switchAt("landscape_follow_portrait").isEnabled());
        assertFalse(f.switchAt("landscape_follow_portrait").isChecked());
    }
    @Test public void returnPreservesLauncherPresentationWithoutAllowingAnOldManagementCommand() throws Exception {
        Fixture f=new Fixture("home");f.page.start();f.backend.connected();
        Bundle first=new Bundle();first.putString("status","ok");first.putBoolean("launcher_visible",false);first.putLong("management_revision",17);
        f.backend.reply("management_status",first);assertFalse(f.switchAt("launcher_visible").isChecked());
        f.page.stop();f.page.start();f.backend.connected();
        assertFalse(f.switchAt("launcher_visible").isChecked());assertFalse(f.switchAt("launcher_visible").isEnabled());
        f.switchAt("launcher_visible").getOnPreferenceChangeListener().onPreferenceChange(f.switchAt("launcher_visible"),true);
        assertFalse(f.backend.pending.stream().anyMatch(p->"launcher_visibility".equals(p.request.getString("op"))));
        Bundle current=new Bundle(first);current.putLong("management_revision",1);
        f.backend.reply("management_status",current);assertTrue(f.switchAt("launcher_visible").isEnabled());
        assertFalse(f.switchAt("launcher_visible").isChecked());
    }
    @Test public void unavailableOrUncertainWriteDoesNotClaimSaved() throws Exception {
        Fixture f = new Fixture("battery"); f.page.start(); f.backend.connected(); f.backend.reply("snapshot",config(7,false));
        SwitchPreference master=f.switchAt("enabled"); master.getOnPreferenceChangeListener().onPreferenceChange(master,true);
        Bundle uncertain=config(8,false); uncertain.putString("status","uncertain"); uncertain.putBoolean("writable",false);
        f.backend.reply("put",uncertain);
        assertFalse(master.isChecked()); assertFalse(master.isEnabled());
        assertTrue(f.host.screen.findPreference("status").getSummary().toString().contains("未确认"));
    }
    @Test public void resetConfirmationCannotOverwriteAChangeFromAnotherFrontend()throws Exception{
        Fixture f=new Fixture("battery");f.page.start();f.backend.connected();f.backend.reply("snapshot",config(7,true));
        Preference reset=f.host.screen.findPreference("reset");reset.getOnPreferenceClickListener().onPreferenceClick(reset);
        var dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        f.backend.listener.onChanged();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        f.backend.reply("snapshot",config(8,false));
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        assertTrue(f.backend.pending.isEmpty());assertFalse(f.switchAt("enabled").isChecked());
        assertTrue(f.host.screen.findPreference("status").getSummary().toString().contains("另一页面"));
        reset.getOnPreferenceClickListener().onPreferenceClick(reset);
        dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        Bundle request=f.backend.find("reset").request;
        assertEquals("battery",request.getString("group"));assertEquals(8,request.getLong("revision"));
    }
    @Test public void homeAndChildStatusDoNotAdvertiseUnconfirmedEnabledValues()throws Exception{
        Bundle uncertain=config(10,true);uncertain.putString("status","uncertain");uncertain.putBoolean("writable",false);
        Fixture home=new Fixture("home");home.page.start();home.backend.connected();home.backend.replyGroup("cloud",uncertain);
        String summary=home.host.screen.findPreference("open_cloud").getSummary().toString();
        assertTrue(summary.startsWith("保存状态未确认"));assertFalse(summary.contains("已开启"));
        Fixture child=new Fixture("cloud_media");child.page.start();child.backend.connected();child.backend.reply("snapshot",uncertain);
        assertEquals("保存状态未确认 · 点按返回总设置",child.host.screen.findPreference("parent_status").getSummary().toString());
        assertFalse(child.switchAt("keep_paused_music").isEnabled());
        child.backend.listener.onChanged();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        child.backend.reply("snapshot",config(11,true));
        assertEquals("增强已开启 · 点按返回总设置",child.host.screen.findPreference("parent_status").getSummary().toString());
    }
    @Test public void homeUsesNativeRoutesAndKeepsEntryToggleSeparate() throws Exception {
        Fixture f=new Fixture("home"); f.page.start(); f.backend.connected();
        assertEquals(Set.of("battery","icons","aod","cloud","control_center","sim","installer","integration"),f.backend.requestedGroups());
        assertNotNull(f.host.screen.findPreference("open_control_center"));
        assertNull(f.host.screen.findPreference("open_memory"));
        Preference sim=f.host.screen.findPreference("open_sim");
        assertTrue(sim.getOnPreferenceClickListener().onPreferenceClick(sim));
        var intent=Shadows.shadowOf(f.host.context).getNextStartedActivity();
        assertEquals("com.android.settings.SubSettings",intent.getComponent().getClassName());
        assertEquals("sim",SettingsRoutes.route(intent.getBundleExtra(":settings:show_fragment_args")));
        assertNotNull(f.host.screen.findPreference("integration.show_entry"));
    }
    @Test public void homeLauncherUsesLocalManagementBridgeAndWaitsForAcknowledgment() throws Exception {
        Fixture f=new Fixture("home"); f.page.start(); f.backend.connected();
        Preference launcher=f.host.screen.findPreference("launcher_visible");
        assertNotNull(launcher); assertFalse(launcher.isEnabled());
        Bundle current=new Bundle(); current.putString("status","ok"); current.putLong("management_revision",0);
        current.putBoolean("launcher_visible",true); current.putBoolean("restarting",false);
        f.backend.reply("management_status",current);
        SwitchPreference toggle=f.switchAt("launcher_visible"); assertTrue(toggle.isEnabled()); assertTrue(toggle.isChecked());
        toggle.getOnPreferenceChangeListener().onPreferenceChange(toggle,false);
        assertFalse(f.backend.find("launcher_visibility").request.getBoolean("visible"));
        assertTrue(toggle.isChecked());
        Bundle saved=new Bundle(current); saved.putLong("management_revision",1); saved.putBoolean("launcher_visible",false);
        f.backend.reply("launcher_visibility",saved); assertFalse(toggle.isChecked());
        assertNotNull(f.host.screen.findPreference("restart_scopes"));
        assertTrue(f.host.screen.findPreference("restart_scopes").isEnabled());
        assertFalse(f.backend.pending.stream().anyMatch(p->"restart_scopes".equals(p.request.getString("op"))));
    }
    @Test public void aboutLinksWorkWithoutFrameworkConnectionAndDelegateNetworkToModule() throws Exception {
        Fixture f=new Fixture("home");f.page.start();
        Preference check=f.host.screen.findPreference("check_updates");
        assertNotNull(check);assertTrue(check.isEnabled());
        check.getOnPreferenceClickListener().onPreferenceClick(check);
        var intent=Shadows.shadowOf(f.host.context).getNextStartedActivity();
        assertEquals(dev.howard.colorosenhance.BuildConfig.PUBLIC_APPLICATION_ID,intent.getComponent().getPackageName());
        assertEquals("dev.howard.colorosenhance.updates.UpdateActivity",intent.getComponent().getClassName());
        Preference repository=f.host.screen.findPreference("github_repository");
        repository.getOnPreferenceClickListener().onPreferenceClick(repository);
        intent=Shadows.shadowOf(f.host.context).getNextStartedActivity();
        assertEquals(android.content.Intent.ACTION_VIEW,intent.getAction());
        assertEquals(dev.howard.colorosenhance.updates.ReleaseInfo.SOURCE_URL,intent.getDataString());
        assertTrue(intent.hasCategory(android.content.Intent.CATEGORY_BROWSABLE));
        assertTrue(f.backend.pending.isEmpty());
    }
    @Test public void managementReadFailureHasRetryFeedbackAndRecoveryClearsIt() throws Exception {
        Fixture f=new Fixture("home"); f.page.start(); f.backend.connected();
        for(String group:List.of("battery","aod","cloud","sim","installer","integration")) f.backend.replyGroup(group,config(7,true));
        Bundle failure=new Bundle(); failure.putString("status","timeout"); f.backend.reply("management_status",failure);
        assertTrue(f.host.screen.findPreference("status").getSummary().toString().contains("超时"));
        assertFalse(f.host.screen.findPreference("restart_scopes").isEnabled());
        assertFalse(f.host.screen.findPreference("restart_scopes").getSummary().toString().contains("正在读取"));
        f.backend.listener.onChanged(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Bundle ready=new Bundle(); ready.putString("status","ok"); ready.putLong("management_revision",2);
        ready.putBoolean("launcher_visible",false); ready.putBoolean("restarting",false); f.backend.reply("management_status",ready);
        assertEquals("设置与独立应用同步",f.host.screen.findPreference("status").getSummary().toString());
        assertTrue(f.host.screen.findPreference("launcher_visible").isEnabled());
    }
    @Test public void nativeRestartRequiresDialogConfirmationAndStaysDisabledWhileRunning() throws Exception {
        Fixture f=new Fixture("home"); f.page.start(); f.backend.connected();
        Bundle state=new Bundle(); state.putString("status","ok"); state.putLong("management_revision",1);
        state.putBoolean("launcher_visible",true); state.putBoolean("restarting",false); f.backend.reply("management_status",state);
        Preference restart=f.host.screen.findPreference("restart_scopes");
        restart.getOnPreferenceClickListener().onPreferenceClick(restart);
        assertFalse(f.backend.pending.stream().anyMatch(p->"restart_scopes".equals(p.request.getString("op"))));
        var dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
        assertFalse(f.backend.pending.stream().anyMatch(p->"restart_scopes".equals(p.request.getString("op"))));
        restart.getOnPreferenceClickListener().onPreferenceClick(restart);
        dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        assertTrue(f.backend.find("restart_scopes").request.getBoolean("confirmed"));
        state.putLong("management_revision",2); state.putBoolean("restarting",true); state.putString("status","accepted");
        f.backend.reply("restart_scopes",state); assertFalse(restart.isEnabled());
    }
    @Test public void homeClearsResolvedReadErrorsAfterObserverRefreshWithoutManualRetry() throws Exception {
        Fixture f=new Fixture("home"); f.page.start(); f.backend.connected();
        Bundle unavailable=new Bundle(); unavailable.putString("status","unavailable");
        for(String group:List.of("battery","aod","cloud","sim","installer","integration")) f.backend.replyGroup(group,unavailable);
        Preference status=f.host.screen.findPreference("status");
        assertTrue(status.getSummary().toString().contains("不可用"));
        assertFalse(f.switchAt("integration.show_entry").isEnabled());
        f.backend.listener.onChanged(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        for(String group:List.of("battery","aod","cloud","sim","installer")) f.backend.replyGroup(group,config(7,true));
        assertTrue("An unresolved integration read must remain visible",status.getSummary().toString().contains("不可用"));
        f.backend.replyGroup("integration",config(7,true));
        assertEquals("设置与独立应用同步",status.getSummary().toString());
        assertTrue(f.switchAt("integration.show_entry").isEnabled());
        assertTrue(f.host.screen.findPreference("open_battery").getSummary().toString().startsWith("已开启"));
    }
    @Test public void staleSuccessfulSnapshotCannotClearAnUnresolvedReadWarning() throws Exception {
        Fixture f=new Fixture("battery"); f.page.start(); f.backend.connected();
        Bundle uncertain=config(10,false); uncertain.putString("status","uncertain"); uncertain.putBoolean("writable",false);
        f.backend.reply("snapshot",uncertain);
        String warning=f.host.screen.findPreference("status").getSummary().toString();
        assertTrue(warning.contains("未确认"));
        f.backend.listener.onChanged(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        f.backend.reply("snapshot",config(9,true));
        assertEquals(warning,f.host.screen.findPreference("status").getSummary().toString());
        assertFalse(f.switchAt("enabled").isEnabled());
        f.backend.listener.onChanged(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        f.backend.reply("snapshot",config(11,true));
        assertEquals("设置与独立应用同步",f.host.screen.findPreference("status").getSummary().toString());
        assertTrue(f.switchAt("enabled").isEnabled());
    }
    @Test public void successfulObserverReadsPreserveUnconfirmedAndConflictWriteNotices() throws Exception {
        for(String status:List.of("uncertain","conflict","timeout")) {
            Fixture f=new Fixture("battery"); f.page.start(); f.backend.connected(); f.backend.reply("snapshot",config(7,false));
            SwitchPreference master=f.switchAt("enabled"); master.getOnPreferenceChangeListener().onPreferenceChange(master,true);
            Bundle result=config(8,false); result.putString("status",status); result.putBoolean("writable",false);
            f.backend.reply("put",result);
            String warning=f.host.screen.findPreference("status").getSummary().toString();
            assertFalse(warning.isEmpty());
            f.backend.listener.onChanged(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            f.backend.reply("snapshot",config(9,true));
            assertTrue(master.isChecked()); assertTrue(master.isEnabled());
            assertEquals("Observer reads must not dismiss a write outcome",warning,f.host.screen.findPreference("status").getSummary().toString());
            f.page.stop();
        }
    }
    @Test public void simStatusKeepsToggleOffUntilControllerConfirmsAndStopsRefreshLoop() throws Exception {
        Fixture f=new Fixture("sim_slots"); f.page.start(); f.backend.connected();
        assertTrue(f.backend.find("sim_status").request.getBoolean("refresh"));
        Bundle status=new Bundle(); status.putString("status","ok");
        Bundle slot=new Bundle(); slot.putBoolean("can_toggle",true); slot.putBoolean("can_clear",true); slot.putString("summary","已保存");
        status.putBundle("slot0",slot); status.putBundle("slot1",new Bundle(slot));
        f.backend.reply("sim_status",status);
        SwitchPreference toggle=f.switchAt("slot0.enabled"); assertTrue(toggle.isEnabled());
        assertFalse(toggle.getOnPreferenceChangeListener().onPreferenceChange(toggle,true)); assertFalse(toggle.isChecked());
        assertTrue("A fast operation on slot 1 must not dim slot 2",f.switchAt("slot1.enabled").isEnabled());
        Bundle accepted=new Bundle(status); accepted.putString("status","accepted"); slot.putBoolean("busy",true); accepted.putBundle("slot0",slot);
        f.backend.reply("sim_toggle",accepted); assertFalse(toggle.isChecked()); assertFalse(toggle.isEnabled());
        f.backend.listener.onChanged(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(f.backend.find("sim_status").request.getBoolean("refresh"));
    }
    static Bundle config(long revision,boolean enabled) {
        Bundle b=new Bundle(); b.putString("status","ok"); b.putLong("revision",revision); b.putBoolean("writable",true);
        Bundle values=new Bundle(); values.putBoolean("enabled",enabled); b.putBundle("values",values); return b;
    }
    static class Fixture {
        final Host host=new Host(); final List<FakeBackend> backends=new ArrayList<>();
        FakeBackend backend; final NativeSettingsPage page;
        Fixture(String route) throws Exception {
            page=new NativeSettingsPage(host,new HostReflection(getClass().getClassLoader(),(context,name)->context.getResources().getIdentifier("preference","layout","android")),route,(context,listener)-> {
                backend=new FakeBackend(listener); backends.add(backend); return backend;
            }); page.create();
        }
        SwitchPreference switchAt(String key) { return (SwitchPreference)host.screen.findPreference(key); }
    }
    public static class Host extends SettingsEntranceTest.Host {
        PreferenceScreen screen;
        public PreferenceManager getPreferenceManager() { return context.getPreferenceManager(); }
        public void setPreferenceScreen(PreferenceScreen value) { screen=value; }
        @Override public PreferenceScreen getPreferenceScreen() { return screen; }
    }
    record Pending(Bundle request,Consumer<Bundle> reply) { }
    static class FakeBackend implements NativeSettingsPage.Backend {
        final SettingsBridgeClient.Listener listener; final List<Pending> pending=new ArrayList<>();
        boolean connected,closed;
        FakeBackend(SettingsBridgeClient.Listener listener) { this.listener=listener; }
        public void connect() { }
        public void close() { closed=true; connected=false; }
        public boolean isConnected() { return connected; }
        void connected() { connected=true; listener.onConnectionChanged(true); }
        public void request(Bundle request,Consumer<Bundle> reply) { pending.add(new Pending(new Bundle(request),reply)); }
        Pending find(String op) { return pending.stream().filter(p->op.equals(p.request.getString("op"))).findFirst().orElseThrow(); }
        void reply(String op,Bundle result) { Pending p=find(op); pending.remove(p); p.reply.accept(result); }
        void replyGroup(String group,Bundle result) { Pending p=pending.stream().filter(item->"snapshot".equals(item.request.getString("op")) && group.equals(item.request.getString("group"))).findFirst().orElseThrow(); pending.remove(p); p.reply.accept(result); }
        Set<String> requestedGroups() { Set<String> groups=new HashSet<>(); for(Pending p:pending) if("snapshot".equals(p.request.getString("op"))) groups.add(p.request.getString("group")); return groups; }
    }
}
