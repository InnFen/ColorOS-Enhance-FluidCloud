package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class MediaWaveVisibilityWakeTest {
    @Test public void alphaRestoringWithoutLayoutOrDrawableRebuildWakesOnceFromExistingNativeFrame(){
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var root=new FrameLayout(activity.get());var content=new View(activity.get());root.addView(content);activity.get().setContentView(root);
            var waiting=new AtomicBoolean(true);var calls=new AtomicInteger();content.setAlpha(0);
            var wake=new MediaWaveVisibilityWake(()->List.of(root),waiting::get,()->content.getAlpha()>0,calls::incrementAndGet);
            wake.update(true);assertTrue(wake.observing());
            root.getViewTreeObserver().dispatchOnPreDraw();assertEquals(0,calls.get());
            content.setAlpha(1);root.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(1,calls.get());assertFalse(wake.observing());
            root.getViewTreeObserver().dispatchOnPreDraw();assertEquals(1,calls.get());
            wake.clear();
        }finally{activity.pause().stop().destroy();}
    }
    @Test public void explicitStopRemovesObserversWithoutWaitingForAnotherNativeFrame(){
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var root=new FrameLayout(activity.get());activity.get().setContentView(root);var calls=new AtomicInteger();
            var wake=new MediaWaveVisibilityWake(()->List.of(root),()->true,()->true,calls::incrementAndGet);
            wake.update(true);assertTrue(wake.observing());wake.update(false);
            assertFalse(wake.observing());root.getViewTreeObserver().dispatchOnPreDraw();assertEquals(0,calls.get());
        }finally{activity.pause().stop().destroy();}
    }
    @Test public void endedOrUnavailableSourceCannotWakeFromAQueuedPreDraw(){
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var root=new FrameLayout(activity.get());activity.get().setContentView(root);
            var waiting=new AtomicBoolean(true);var calls=new AtomicInteger();
            var wake=new MediaWaveVisibilityWake(()->List.of(root),waiting::get,()->true,calls::incrementAndGet);
            wake.update(true);waiting.set(false);root.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(0,calls.get());assertFalse(wake.observing());
        }finally{activity.pause().stop().destroy();}
    }
    @Test public void severalNativeWindowsCanWakeTheSameSourceAndDuplicateRootsDoNotDuplicateCallbacks(){
        var first=Robolectric.buildActivity(Activity.class).setup().visible();var second=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var a=new FrameLayout(first.get());var b=new FrameLayout(second.get());first.get().setContentView(a);second.get().setContentView(b);
            var visible=new AtomicBoolean(false);var calls=new AtomicInteger();
            var wake=new MediaWaveVisibilityWake(()->List.of(a,b,a),()->true,visible::get,calls::incrementAndGet);
            wake.update(true);a.getViewTreeObserver().dispatchOnPreDraw();assertEquals(0,calls.get());
            visible.set(true);b.getViewTreeObserver().dispatchOnPreDraw();assertEquals(1,calls.get());assertFalse(wake.observing());
            a.getViewTreeObserver().dispatchOnPreDraw();assertEquals(1,calls.get());
        }finally{second.pause().stop().destroy();first.pause().stop().destroy();}
    }
    @Test public void observingVisibilityDoesNotInvalidateOrRequestItsOwnFrames(){
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var root=new CountedView(activity.get());activity.get().setContentView(root);int before=root.invalidations;
            var wake=new MediaWaveVisibilityWake(()->List.of(root),()->true,()->false,()->fail("Not visible"));
            wake.update(true);for(int i=0;i<12;i++)root.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(before,root.invalidations);wake.clear();assertFalse(wake.observing());
        }finally{activity.pause().stop().destroy();}
    }
    @Test public void detachedRootsAreNotObserved(){
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var detached=new View(activity.get());var calls=new AtomicInteger();
            var wake=new MediaWaveVisibilityWake(()->List.of(detached),()->true,()->true,calls::incrementAndGet);
            wake.update(true);assertFalse(wake.observing());assertEquals(0,calls.get());
        }finally{activity.pause().stop().destroy();}
    }
    private static final class CountedView extends View {
        int invalidations;
        CountedView(Activity context){super(context);}
        @Override public void invalidate(){invalidations++;super.invalidate();}
    }
}
