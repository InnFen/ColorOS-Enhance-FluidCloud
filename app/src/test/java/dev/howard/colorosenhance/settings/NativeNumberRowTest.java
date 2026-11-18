package dev.howard.colorosenhance.settings;

import android.app.Application;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import com.coui.appcompat.seekbar.COUISeekBar;
import dev.howard.colorosenhance.SettingsCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class NativeNumberRowTest {
    @Test public void nativeCustomViewPreviewsDragCommitsOnceAndNumberOpensManualInput(){
        var item=weight();
        NativeNumberRow.State[] state={new NativeNumberRow.State(500,true,7)};
        List<String> saved=new ArrayList<>();int[] manual={0};
        NativeNumberRow row=new NativeNumberRow(item,()->state[0],(value,revision)->saved.add(value+":"+revision),()->manual[0]++);
        LinearLayout root=nativeRoot();TextView nativeTitle=root.findViewById(android.R.id.title);
        int titleSize=(int)nativeTitle.getTextSize(),padding=root.getPaddingLeft();
        row.bind(root,new HostReflection(getClass().getClassLoader()));
        LinearLayout line=numericLine(root);
        assertSame(nativeTitle,root.findViewById(android.R.id.title));
        assertEquals(titleSize,(int)nativeTitle.getTextSize());assertEquals(padding,root.getPaddingLeft());
        assertEquals(View.GONE,root.findViewById(android.R.id.summary).getVisibility());
        assertTrue(line.getChildAt(0) instanceof COUISeekBar);
        COUISeekBar bar=(COUISeekBar)line.getChildAt(0);TextView number=(TextView)line.getChildAt(1);
        assertEquals("500",number.getText().toString());assertTrue(saved.isEmpty());
        bar.start();bar.move((700-item.min())/item.step());
        assertEquals("700",number.getText().toString());assertTrue(saved.isEmpty());
        state[0]=new NativeNumberRow.State(400,true,8);row.update();
        assertEquals("700",number.getText().toString());
        bar.stop();assertEquals(List.of("700:7"),saved); // The page checks this captured revision.
        assertEquals("400",number.getText().toString());
        number.performClick();assertEquals(1,manual[0]);
        bar.start();bar.move((600-item.min())/item.step());
        state[0]=new NativeNumberRow.State(400,false,9);row.update();bar.stop();
        assertEquals(1,saved.size());assertFalse(bar.isEnabled());assertFalse(number.isEnabled());
    }

    @Test public void unavailableCouiFallsBackToPlatformSlider(){
        ClassLoader missing=new ClassLoader(getClass().getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(name.equals("com.coui.appcompat.seekbar.COUISeekBar"))throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        };
        NativeNumberRow row=new NativeNumberRow(weight(),()->new NativeNumberRow.State(500,true,1),(v,r)->{},()->{});
        LinearLayout root=nativeRoot();row.bind(root,new HostReflection(missing));
        LinearLayout line=numericLine(root);
        assertTrue(line.getChildAt(0) instanceof SeekBar);
    }

    @Test public void longPressResetsOnlyThisValueAndDoesNotOpenManualInput(){
        var item=weight();
        NativeNumberRow.State[] state={new NativeNumberRow.State(700,true,17)};
        List<String> saved=new ArrayList<>();
        int[] manual={0};
        NativeNumberRow row=new NativeNumberRow(item,()->state[0],(value,revision)->{
            saved.add(value+":"+revision);
            state[0]=new NativeNumberRow.State(value,true,revision+1);
        },()->manual[0]++);
        var root=nativeRoot();
        row.bind(root,new HostReflection(getClass().getClassLoader()));
        var line=numericLine(root);
        var number=(TextView)line.getChildAt(1);
        var bar=(COUISeekBar)line.getChildAt(0);
        assertTrue(number.performLongClick());
        assertEquals(List.of(item.valueDefault()+":17"),saved);
        assertEquals(String.valueOf(item.valueDefault()),number.getText().toString());
        assertEquals((item.valueDefault()-item.min())/item.step(),bar.getProgress());
        assertTrue(number.performLongClick());
        assertEquals(1,saved.size());
        assertEquals(0,manual[0]);
        assertTrue(number.performClick());
        assertEquals(1,manual[0]);
        state[0]=new NativeNumberRow.State(700,false,19);
        row.update();
        assertFalse(number.performLongClick());
        assertEquals(1,saved.size());
    }

    @Test public void recycledNativeRowDoesNotReceiveUpdatesFromItsPreviousParameter(){
        LinearLayout root=nativeRoot();var host=new HostReflection(getClass().getClassLoader());
        NativeNumberRow old=new NativeNumberRow(weight(),()->new NativeNumberRow.State(500,true,1),(v,r)->{},()->{});
        NativeNumberRow replacement=new NativeNumberRow(weight(),()->new NativeNumberRow.State(800,true,2),(v,r)->{},()->{});
        old.bind(root,host);LinearLayout detached=numericLine(root);
        replacement.bind(root,host);LinearLayout attached=numericLine(root);
        assertNotSame(detached,attached);assertNull(detached.getParent());
        old.update();assertEquals("800",((TextView)attached.getChildAt(1)).getText().toString());
        replacement.bind(root,host);assertSame(attached,numericLine(root));
    }

    @Test public void trailingCouiFramesCannotOverwriteSavedOrManuallyEnteredValue(){
        NativeNumberRow.State[] state={new NativeNumberRow.State(500,true,7)};List<Integer> saved=new ArrayList<>();
        NativeNumberRow row=new NativeNumberRow(weight(),()->state[0],(value,revision)->{saved.add(value);state[0]=new NativeNumberRow.State(value,true,8);},()->{});
        var root=nativeRoot();row.bind(root,new HostReflection(getClass().getClassLoader()));var line=numericLine(root);
        var bar=(COUISeekBar)line.getChildAt(0);bar.start();bar.move(60);bar.stop();
        bar.move(45);assertEquals(List.of(700),saved);assertEquals(60,bar.getProgress());
        state[0]=new NativeNumberRow.State(570,true,9);row.update();bar.move(52);
        assertEquals(List.of(700),saved);assertEquals("570",((TextView)line.getChildAt(1)).getText().toString());assertEquals(47,bar.getProgress());
    }

    @Test public void accessibilityAdjustmentsCommitOneRevisionedValueWithoutTouchTracking(){
        NativeNumberRow.State[] state={new NativeNumberRow.State(500,true,7)};List<String> saved=new ArrayList<>();
        NativeNumberRow row=new NativeNumberRow(weight(),()->state[0],(value,revision)->{
            saved.add(value+":"+revision);state[0]=new NativeNumberRow.State(value,true,revision+1);
        },()->{});
        var root=nativeRoot();row.bind(root,new HostReflection(getClass().getClassLoader()));
        var bar=(COUISeekBar)numericLine(root).getChildAt(0);
        Bundle arguments=new Bundle();arguments.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,704);
        assertTrue(bar.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),arguments));
        assertEquals(List.of("700:7"),saved);assertEquals(60,bar.getProgress());
        assertTrue(bar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null));
        assertEquals(List.of("700:7","690:8"),saved);
        bar.move(45);bar.setProgress(44); // Neither trailing touch frames nor programmatic progress are discrete actions.
        assertEquals(2,saved.size());assertEquals(59,bar.getProgress());
        state[0]=new NativeNumberRow.State(690,false,9);row.update();
        assertFalse(bar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null));
        assertEquals(2,saved.size());
    }

    @Test public void keyboardAdjustmentsRespectRtlAndCannotInterruptAnActiveDrag(){
        NativeNumberRow.State[] state={new NativeNumberRow.State(500,true,7)};List<String> saved=new ArrayList<>();
        NativeNumberRow row=new NativeNumberRow(weight(),()->state[0],(value,revision)->{
            saved.add(value+":"+revision);state[0]=new NativeNumberRow.State(value,true,revision+1);
        },()->{});
        var root=nativeRoot();row.bind(root,new HostReflection(getClass().getClassLoader()));
        var bar=(COUISeekBar)numericLine(root).getChildAt(0);
        assertTrue(bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertTrue(bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertEquals(List.of("510:7"),saved);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        assertTrue(bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertEquals(List.of("510:7","500:8"),saved);
        bar.start();bar.move(60);
        assertFalse(bar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null));
        assertEquals(2,saved.size());bar.stop();
        assertEquals(List.of("510:7","500:8","700:9"),saved);
    }

    @Test public void platformFallbackAlsoSupportsDiscreteAccessibilityAndRejectsInvalidProgress(){
        ClassLoader missing=new ClassLoader(getClass().getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(name.equals("com.coui.appcompat.seekbar.COUISeekBar"))throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        };
        List<String> saved=new ArrayList<>();
        NativeNumberRow row=new NativeNumberRow(weight(),()->new NativeNumberRow.State(500,true,11),(value,revision)->saved.add(value+":"+revision),()->{});
        var root=nativeRoot();row.bind(root,new HostReflection(missing));
        var bar=(SeekBar)numericLine(root).getChildAt(0);
        assertTrue(bar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null));
        assertEquals(List.of("510:11"),saved);
        Bundle invalid=new Bundle();invalid.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,Float.NaN);
        assertFalse(bar.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),invalid));
        assertEquals(1,saved.size());
    }

    private static LinearLayout nativeRoot(){
        var context=RuntimeEnvironment.getApplication();LinearLayout root=new LinearLayout(context);root.setPadding(29,11,31,13);
        RelativeLayout area=new RelativeLayout(context);root.addView(area);
        TextView title=new TextView(context);title.setId(android.R.id.title);title.setText("系统标题");title.setTextSize(19);area.addView(title);
        TextView summary=new TextView(context);summary.setId(android.R.id.summary);summary.setTextSize(15);area.addView(summary);
        return root;
    }
    static LinearLayout numericLine(ViewGroup root){
        for(int i=0;i<root.getChildCount();i++){
            View child=root.getChildAt(i);
            if(child.getTag() instanceof NativeNumberRow)return (LinearLayout)child;
            if(child instanceof ViewGroup group){LinearLayout result=numericLine(group);if(result!=null)return result;}
        }
        return null;
    }

    private static SettingsCatalog.Item weight(){return SettingsCatalog.find("battery").items().stream().filter(item->item.valueKey()!=null && item.valueKey().equals("weight")).findFirst().orElseThrow();}
}
