package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class CapsuleClickTopGuardTest {
    ActivityController<Activity> activity;
    FrameLayout bar;
    View capsule;
    CapsuleClickTopGuard guard;
    @Before public void setup(){
        activity=Robolectric.buildActivity(Activity.class).setup().visible();
        bar=new FrameLayout(activity.get());activity.get().setContentView(bar);
        capsule=new View(activity.get());
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(100,40);params.leftMargin=80;params.topMargin=20;
        bar.addView(capsule,params);
        bar.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));
        bar.layout(0,0,400,100);
        guard=new CapsuleClickTopGuard(view->view==capsule);
    }
    @After public void teardown(){activity.pause().stop().destroy();}
    private void event(long down,int action,float x,float y,boolean enabled,boolean blocked)throws Throwable{
        MotionEvent event=MotionEvent.obtain(down,down+10,action,x,y,0);
        try{
            Object result=guard.dispatch(bar,event,enabled,()->{assertEquals(blocked,guard.suppress(event));return Boolean.TRUE;});
            assertEquals(Boolean.TRUE,result);assertFalse(guard.suppress(event));
        }finally{event.recycle();}
    }
    @Test public void capsuleClickIsExcludedEvenWhenOpeningHidesItBeforeUpHelper()throws Throwable{
        event(1,MotionEvent.ACTION_DOWN,100,30,true,true);
        capsule.setVisibility(View.GONE);
        event(1,MotionEvent.ACTION_UP,100,30,true,true);
        event(20,MotionEvent.ACTION_DOWN,30,30,true,false);
        event(20,MotionEvent.ACTION_UP,30,30,true,false);
    }
    @Test public void blankClicksAndBlankDownEndingOverCapsuleRemainNative()throws Throwable{
        event(1,MotionEvent.ACTION_DOWN,30,30,true,false);
        event(1,MotionEvent.ACTION_UP,100,30,true,false);
        event(20,MotionEvent.ACTION_DOWN,200,30,true,false);
        event(20,MotionEvent.ACTION_UP,200,30,true,false);
    }
    @Test public void hiddenTransparentDetachedAndOutsideBoundsDoNotCapture()throws Throwable{
        capsule.setAlpha(0);event(1,MotionEvent.ACTION_DOWN,100,30,true,false);
        capsule.setAlpha(1);capsule.setVisibility(View.INVISIBLE);event(2,MotionEvent.ACTION_DOWN,100,30,true,false);
        capsule.setVisibility(View.VISIBLE);event(3,MotionEvent.ACTION_DOWN,180,30,true,false);
        bar.removeView(capsule);event(4,MotionEvent.ACTION_DOWN,100,30,true,false);
    }
    @Test public void clippingAndCurrentTranslationDefineActualClickableBounds()throws Throwable{
        capsule.setTranslationX(30);event(1,MotionEvent.ACTION_DOWN,85,30,true,false);
        event(2,MotionEvent.ACTION_DOWN,115,30,true,true);
        bar.setClipBounds(new android.graphics.Rect(0,0,150,100));
        event(3,MotionEvent.ACTION_DOWN,170,30,true,false);
    }
    @Test public void cancellationMismatchedStreamAndDisabledSwitchCannotLeakOwnership()throws Throwable{
        event(1,MotionEvent.ACTION_DOWN,100,30,true,true);
        event(1,MotionEvent.ACTION_CANCEL,100,30,true,false);
        event(1,MotionEvent.ACTION_UP,100,30,true,false);
        event(2,MotionEvent.ACTION_DOWN,100,30,true,true);
        event(3,MotionEvent.ACTION_UP,100,30,true,false);
        event(4,MotionEvent.ACTION_DOWN,100,30,true,true);
        event(4,MotionEvent.ACTION_UP,100,30,false,false);
        event(5,MotionEvent.ACTION_DOWN,100,30,false,false);
    }
    @Test public void swipesStillProceedAndOnlyDownUpHelperIsSuppressed()throws Throwable{
        event(1,MotionEvent.ACTION_DOWN,100,30,true,true);
        event(1,MotionEvent.ACTION_MOVE,210,60,true,false);
        event(1,MotionEvent.ACTION_UP,210,60,true,true);
    }
    @Test public void nativeFailureAlwaysClearsScopedSuppression()throws Throwable{
        MotionEvent down=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,100,30,0);
        try{
            try{guard.dispatch(bar,down,true,()->{assertTrue(guard.suppress(down));throw new IllegalStateException("native");});fail();}
            catch(IllegalStateException expected){assertEquals("native",expected.getMessage());}
            assertFalse(guard.suppress(down));
            event(2,MotionEvent.ACTION_DOWN,30,30,true,false);
        }finally{down.recycle();}
    }
}
