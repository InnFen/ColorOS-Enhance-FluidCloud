package dev.howard.colorosenhance.icons;

import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class IconOrderEditorTest {
    @Test public void longClickableRowsCanScrollWithoutTheHostDialogStealingTheirMove(){
        var context=RuntimeEnvironment.getApplication();context.getResources().getDisplayMetrics().heightPixels=2200;
        var host=new InterceptingHost(context);List<String> slots=new ArrayList<>(List.of("clock","battery"));
        for(int i=0;i<30;i++)slots.add("slot_"+i);
        var editor=new IconOrderEditor(context,IconOrder.EMPTY,slots);host.addView(editor,new android.widget.FrameLayout.LayoutParams(-1,700));
        host.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1100,View.MeasureSpec.EXACTLY));host.layout(0,0,800,1100);
        List<android.widget.ScrollView> lists=new ArrayList<>();scrolls(editor,lists);var right=lists.get(1);
        android.graphics.Rect rect=new android.graphics.Rect(0,0,right.getWidth(),right.getHeight());host.offsetDescendantRectToMyCoords(right,rect);
        long now=android.os.SystemClock.uptimeMillis();
        event(host,now,now,android.view.MotionEvent.ACTION_DOWN,rect.centerX(),rect.bottom-8);
        event(host,now,now+70,android.view.MotionEvent.ACTION_MOVE,rect.centerX(),rect.centerY());
        event(host,now,now+120,android.view.MotionEvent.ACTION_MOVE,rect.centerX(),rect.top+8);
        event(host,now,now+200,android.view.MotionEvent.ACTION_UP,rect.centerX(),rect.top+8);
        assertEquals(0,host.stolenMoves);assertTrue(right.getScrollY()>0);
    }
    @Test public void accessibilityUsesTheSameCrossAreaAndWithinAreaReorderAsDragging(){
        var editor=new IconOrderEditor(RuntimeEnvironment.getApplication(),new IconOrder(List.of("clock"),List.of("wifi","battery")),List.of("clock","wifi","battery"));
        View battery=editor.findViewWithTag("battery");action(battery,"移到左侧");
        assertEquals(List.of("battery","clock"),editor.value().left());assertEquals(List.of("wifi"),editor.value().right());
        action(editor.findViewWithTag("battery"),"向后移动");
        assertEquals(List.of("clock","battery"),editor.value().left());
        action(editor.findViewWithTag("clock"),"移到右侧");
        assertEquals(List.of("clock","wifi"),editor.value().right());
    }
    @Test public void cancellingDoesNotMutateTheInputAndResetRestoresBothPhysicalAreas(){
        var saved=new IconOrder(List.of("wifi","battery"),List.of("clock"));
        var editor=new IconOrderEditor(RuntimeEnvironment.getApplication(),saved,List.of("clock","wifi","battery"));
        action(editor.findViewWithTag("wifi"),"移到右侧");assertEquals(List.of("wifi","battery"),saved.left());
        findText(editor,"恢复原厂顺序").performClick();
        assertEquals(List.of("clock"),editor.value().left());assertEquals(List.of("wifi","battery"),editor.value().right());
        assertEquals("",editor.encodedValue());
    }
    @Test public void shortDialogKeepsTheRestoreButtonInsideItsMeasuredBounds(){
        var editor=new IconOrderEditor(RuntimeEnvironment.getApplication(),IconOrder.EMPTY,List.of("clock","wifi","battery"));
        editor.measure(View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.AT_MOST));
        editor.layout(0,0,editor.getMeasuredWidth(),editor.getMeasuredHeight());
        View restore=findText(editor,"恢复原厂顺序");assertTrue(restore.getBottom()<=editor.getHeight());assertTrue(restore.getHeight()>0);
    }
    private void action(View view,String label){
        AccessibilityNodeInfo info=view.createAccessibilityNodeInfo();
        var action=info.getActionList().stream().filter(item->item.getLabel()!=null&&label.contentEquals(item.getLabel())).findFirst().orElseThrow();
        assertTrue(view.performAccessibilityAction(action.getId(),null));
    }
    private android.widget.TextView findText(View view,String label){
        if(view instanceof android.widget.TextView text&&label.contentEquals(text.getText()))return text;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){var found=findText(group.getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
    private void scrolls(View view,List<android.widget.ScrollView> result){
        if(view instanceof android.widget.ScrollView scroll)result.add(scroll);
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)scrolls(group.getChildAt(i),result);
    }
    private void event(View host,long down,long time,int action,float x,float y){
        var event=android.view.MotionEvent.obtain(down,time,action,x,y,0);try{host.dispatchTouchEvent(event);}finally{event.recycle();}
    }
    private static class InterceptingHost extends android.widget.FrameLayout{
        int stolenMoves;InterceptingHost(android.content.Context context){super(context);}
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==android.view.MotionEvent.ACTION_MOVE){stolenMoves++;return true;}return false;}
    }
}
