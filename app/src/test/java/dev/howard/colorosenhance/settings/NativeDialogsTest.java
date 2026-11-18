package dev.howard.colorosenhance.settings;
import static org.junit.Assert.*;
import android.app.*;
import android.content.DialogInterface;
import android.view.*;
import android.widget.EditText;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class NativeDialogsTest {
    @Test public void pinRequiresMatchingDigitsAndClearsOnLifecycleExit() {
        SettingsEntranceTest.Host host=new SettingsEntranceTest.Host();
        NativeDialogs dialogs=new NativeDialogs(host.context,new HostReflection(getClass().getClassLoader()),error->fail(error));
        List<String> saved=new ArrayList<>(); dialogs.pin(0,(pin,confirmation)->saved.add(pin));
        Dialog dialog=ShadowDialog.getLatestDialog(); assertNotNull(dialog); assertTrue(dialog.isShowing());
        List<EditText> fields=inputs(dialog.getWindow().getDecorView()); assertEquals(2,fields.size());
        for(EditText field:fields) { assertFalse(field.isSaveEnabled()); assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,field.getImportantForAutofill()); }
        fields.get(0).setText("4826"); fields.get(1).setText("4827");
        ((AlertDialog)dialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick(); assertTrue(saved.isEmpty()); assertTrue(dialog.isShowing());
        dialogs.clear(); assertFalse(dialog.isShowing());
        for(EditText field:fields) assertEquals("",field.getText().toString());
    }
    @Test public void validSubmissionClearsBothFieldsAndReturnsOnlyToSaveCallback() {
        SettingsEntranceTest.Host host=new SettingsEntranceTest.Host();
        NativeDialogs dialogs=new NativeDialogs(host.context,new HostReflection(getClass().getClassLoader()),error->fail(error));
        List<String> saved=new ArrayList<>(); dialogs.pin(1,(pin,confirmation)->saved.add(pin+":"+confirmation));
        AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog(); assertNotNull(dialog);
        List<EditText> fields=inputs(dialog.getWindow().getDecorView()); fields.forEach(field->field.setText("4826"));
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        assertEquals(List.of("4826:4826"),saved); assertFalse(dialog.isShowing());
        for(EditText field:fields) assertEquals("",field.getText().toString());
    }
    @Test public void illustrationUsesNativeDialogWithoutAddingCredentialInputsOrSavingAnything(){
        SettingsEntranceTest.Host host=new SettingsEntranceTest.Host();
        NativeDialogs dialogs=new NativeDialogs(host.context,new HostReflection(getClass().getClassLoader()),error->fail(error));
        Map<String,Object> values=new HashMap<>(Map.of("unbind_status_bar",true,"recents",true));
        dialogs.demo("scenes","cloud_portrait","场景与方向","效果示意",values);
        AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());
        assertTrue(inputs(dialog.getWindow().getDecorView()).isEmpty());
        assertEquals(Map.of("unbind_status_bar",true,"recents",true),values);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();assertFalse(dialog.isShowing());
    }
    @Test public void clearingNativeDemoStopsItImmediatelyAndCancelsPendingAutoplay(){
        SettingsEntranceTest.Host host=new SettingsEntranceTest.Host();
        NativeDialogs dialogs=new NativeDialogs(host.context,new HostReflection(getClass().getClassLoader()),error->fail(error));
        dialogs.demo("camera","cloud_cards","展开卡片","效果示意",Map.of());
        var illustration=(dev.howard.colorosenhance.ui.FeatureDemoView)org.robolectric.util.ReflectionHelpers.getField(dialogs,"activeDemo");
        assertNotNull(illustration);dialogs.clear();
        assertNull(org.robolectric.util.ReflectionHelpers.getField(dialogs,"activeDemo"));
        assertNull(org.robolectric.util.ReflectionHelpers.getField(illustration,"animator"));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNull(org.robolectric.util.ReflectionHelpers.getField(illustration,"animator"));
    }
    static List<EditText> inputs(View view) {
        List<EditText> result=new ArrayList<>(); if(view instanceof EditText edit) result.add(edit);
        if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) result.addAll(inputs(group.getChildAt(i)));
        return result;
    }
}
