package dev.howard.colorosenhance.settings;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Insets;
import android.os.Looper;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class NativePageInsetsTest {
    @Test public void returningKeepsTheSamePaddingAndDoesNotRealignAnExistingList() {
        Fixture f=new Fixture();NativePageInsets insets=new NativePageInsets(f.list,f.decor,false);
        int top=f.list.getPaddingTop(),bottom=f.list.getPaddingBottom();
        assertTrue(top>4);assertTrue(bottom>8);f.list.paddingWrites=0;
        insets.pause();assertEquals(top,f.list.getPaddingTop());assertEquals(bottom,f.list.getPaddingBottom());
        insets.resume();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,f.list.paddingWrites);assertEquals(0,f.list.alignments);assertTrue(insets.owns(f.list));
        insets.close();assertEquals(4,f.list.getPaddingTop());assertEquals(8,f.list.getPaddingBottom());assertTrue(f.list.getClipToPadding());
    }
    @Test public void coveredPageDoesNotReactToTransitionGeometryAndAppliesNewInsetsOnceOnReturn() {
        Fixture f=new Fixture();NativePageInsets insets=new NativePageInsets(f.list,f.decor,false);
        int previous=f.list.getPaddingTop();f.list.paddingWrites=0;insets.pause();
        f.decor.insets=window(70,25);insets.update();
        assertEquals(previous,f.list.getPaddingTop());assertEquals(0,f.list.paddingWrites);
        insets.resume();assertEquals(previous,f.list.getPaddingTop());assertEquals(0,f.list.paddingWrites);
        insets.update();assertTrue(f.list.getPaddingTop()>previous);assertEquals(1,f.list.paddingWrites);
        insets.update();assertEquals(1,f.list.paddingWrites);assertEquals(0,f.list.alignments);insets.close();
    }
    @Test public void initialAlignmentCannotRunAfterThePageIsCovered() {
        Fixture f=new Fixture();NativePageInsets insets=new NativePageInsets(f.list,f.decor,true);
        insets.pause();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,f.list.alignments);
        insets.resume();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,f.list.alignments);
        insets.close();assertFalse(insets.owns(f.list));
    }
    @Test public void genuinelyNewPageStillAlignsItsFirstLayoutOnlyOnce() {
        Fixture f=new Fixture();NativePageInsets insets=new NativePageInsets(f.list,f.decor,true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,f.list.alignments);
        insets.update();insets.pause();insets.resume();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,f.list.alignments);insets.close();
    }
    @Test public void returningDoesNotMeasureTheTemporarilyHiddenToolbarInOnStart() {
        Fixture f=new Fixture();NativePageInsets insets=new NativePageInsets(f.list,f.decor,false);
        int top=f.list.getPaddingTop();f.list.paddingWrites=0;insets.pause();
        f.decor.insets=window(0,16);insets.resume(); // Toolbar is restored later by Activity.onResume.
        assertEquals(top,f.list.getPaddingTop());assertEquals(0,f.list.paddingWrites);
        f.decor.insets=window(24,16);f.decor.getViewTreeObserver().dispatchOnGlobalLayout();
        assertEquals(top,f.list.getPaddingTop());assertEquals(0,f.list.paddingWrites);insets.close();
    }
    static WindowInsets window(int top,int bottom) {
        return new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.of(0,top,0,bottom)).build();
    }
    static class Fixture {
        final Activity owner=Robolectric.buildActivity(Activity.class).setup().get();
        final Decor decor=new Decor(owner);final AnchorList list=new AnchorList(owner);
        Fixture(){
            list.setPadding(2,4,6,8);list.setClipToPadding(true);
            decor.addView(list,new FrameLayout.LayoutParams(-1,-1));owner.setContentView(decor);
            decor.layout(0,0,360,700);list.layout(0,0,360,700);
        }
    }
    static class Decor extends FrameLayout {
        WindowInsets insets=window(24,16);
        Decor(Context context){super(context);}
        @Override public WindowInsets getRootWindowInsets(){return insets;}
    }
    static class AnchorList extends FrameLayout {
        int paddingWrites,alignments;
        AnchorList(Context context){super(context);}
        @Override public void setPadding(int left,int top,int right,int bottom){super.setPadding(left,top,right,bottom);paddingWrites++;}
        public void scrollToPosition(int position){assertEquals(0,position);alignments++;}
    }
}
