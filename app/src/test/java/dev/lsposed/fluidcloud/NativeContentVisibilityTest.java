package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class NativeContentVisibilityTest {
    @Test public void modulePresentationResumesTheNativeDrawableWithoutChangingHiddenHostFlags(){
        try(var activity=Robolectric.buildActivity(Activity.class).setup().visible()){
            Context context=activity.get();FrameLayout host=new FrameLayout(context),body=new FrameLayout(context);
            host.addView(body);ImageView image=new ImageView(context);body.addView(image);Animated drawable=new Animated();image.setImageDrawable(drawable);
            activity.get().setContentView(host);host.setVisibility(View.INVISIBLE);
            Reflect.call(method("dispatchVisibilityAggregated",boolean.class),host,false);assertFalse(drawable.isVisible());
            NativeContentVisibility visibility=new NativeContentVisibility();visibility.present(host);
            assertTrue(drawable.isVisible());assertTrue(drawable.isRunning());assertEquals(View.INVISIBLE,host.getVisibility());assertEquals(View.VISIBLE,body.getVisibility());
            visibility.restore();assertFalse(drawable.isVisible());assertFalse(drawable.isRunning());
        }
    }
    @Test public void hiddenPayloadsStayHiddenAndRestorationUsesTheLatestNativeParentVisibility(){
        try(var activity=Robolectric.buildActivity(Activity.class).setup().visible()){
            FrameLayout host=new FrameLayout(activity.get()),body=new FrameLayout(activity.get());View hidden=new View(activity.get());hidden.setVisibility(View.GONE);
            body.addView(hidden);host.addView(body);activity.get().setContentView(host);host.setVisibility(View.INVISIBLE);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            // The fixture's WindowManager leaves this attached window GONE until
            // its synthetic visibility message; model the native visible window.
            Object attach=org.robolectric.util.ReflectionHelpers.getField(body,"mAttachInfo");
            org.robolectric.util.ReflectionHelpers.setField(attach,"mWindowVisibility",View.VISIBLE);
            Reflect.call(method("dispatchVisibilityAggregated",boolean.class),host,false);
            NativeContentVisibility visibility=new NativeContentVisibility();visibility.present(host);assertFalse((boolean)Reflect.call(method("isAggregatedVisible"),hidden));
            host.setVisibility(View.VISIBLE);assertTrue("Native subtree must be attached",body.isAttachedToWindow());
            assertTrue("Native parent must be shown",body.isShown());assertEquals(View.VISIBLE,body.getWindowVisibility());
            visibility.restore();assertTrue((boolean)Reflect.call(method("isAggregatedVisible"),body));
        }
    }
    private static java.lang.reflect.Method method(String name,Class<?>... args){try{return Reflect.method(View.class,name,boolean.class,args);}catch(Exception e){throw new AssertionError(e);}}
    private static final class Animated extends ColorDrawable implements Animatable{
        boolean running;@Override public boolean setVisible(boolean visible,boolean restart){boolean result=super.setVisible(visible,restart);running=visible;return result;}
        public void start(){running=true;}public void stop(){running=false;}public boolean isRunning(){return running;}
    }
}
