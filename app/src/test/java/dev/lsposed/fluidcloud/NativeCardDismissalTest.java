package dev.lsposed.fluidcloud;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class NativeCardDismissalTest {
    @Test public void emptyBusinessStateKeepsOutgoingHoldersUntilNativeWindowDetaches()throws Exception {
        try(Fixture f=new Fixture()) {
            List<String> empty=List.of();f.list.data=empty;
            assertTrue(f.api.defer(f.list));assertSame(empty,f.list.data);
            assertEquals(List.of(f.list.card),f.list.getCardViews());assertEquals(List.of("timer"),f.list.displayed);
            f.list.card.setAlpha(.25f);assertTrue(f.api.defer(f.list));
            f.owner.setContentView(new FrameLayout(f.owner));f.api.detached(f.list);
            assertEquals(1,f.list.commits);assertTrue(f.list.displayed.isEmpty());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertEquals(1,f.list.commits);
        }
    }
    @Test public void newContentCancelsPendingClearWithoutReplayingAStaleRemoval()throws Exception {
        try(Fixture f=new Fixture()) {
            f.list.data=List.of();assertTrue(f.api.defer(f.list));
            f.list.data=List.of("media");assertFalse(f.api.defer(f.list));f.list.i();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
            assertEquals(List.of("media"),f.list.displayed);assertEquals(1,f.list.commits);
        }
    }
    @Test public void abortedNativeTransitionHasABoundedCleanup()throws Exception {
        try(Fixture f=new Fixture()) {
            f.list.data=List.of();assertTrue(f.api.defer(f.list));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1400));assertEquals(0,f.list.commits);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
            assertTrue(f.list.displayed.isEmpty());assertEquals(1,f.list.commits);
        }
    }
    @Test public void disabledHiddenAndAlreadyEmptyListsRetainNativeBehavior()throws Exception {
        try(Fixture f=new Fixture()) {
            f.list.data=List.of();f.enabled=false;assertFalse(f.api.defer(f.list));
            f.enabled=true;f.list.setVisibility(View.GONE);assertFalse(f.api.defer(f.list));
            f.list.setVisibility(View.VISIBLE);f.list.setAlpha(0);assertFalse(f.api.defer(f.list));
            f.list.setAlpha(1);f.list.displayed=List.of();assertFalse(f.api.defer(f.list));
            f.list.displayed=List.of("timer");f.owner.setContentView(new FrameLayout(f.owner));assertFalse(f.api.defer(f.list));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertEquals(0,f.list.commits);
        }
    }
    @Test public void turningOffOrClosingReleasesAnyPendingPresentation()throws Exception {
        try(Fixture f=new Fixture()) {
            f.list.data=List.of();assertTrue(f.api.defer(f.list));f.enabled=false;f.api.refresh();
            assertEquals(1,f.list.commits);assertTrue(f.list.displayed.isEmpty());
            f.enabled=true;f.list.displayed=List.of("timer");assertTrue(f.api.defer(f.list));f.api.close();
            assertEquals(2,f.list.commits);assertFalse(f.api.defer(f.list));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertEquals(2,f.list.commits);
        }
    }
    @Test public void disabledSystemAnimationsAreNotDelayed()throws Exception {
        float previous=ValueAnimator.getDurationScale();
        try(Fixture f=new Fixture()) {
            ReflectionHelpers.setStaticField(ValueAnimator.class,"sDurationScale",0f);f.list.data=List.of();assertFalse(f.api.defer(f.list));
        }finally{ReflectionHelpers.setStaticField(ValueAnimator.class,"sDurationScale",previous);}
    }
    static class Fixture implements AutoCloseable {
        final org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup().visible();
        final Activity owner=activity.get();final Container list=new Container(owner);boolean enabled=true;
        final NativeCardDismissal api;
        Fixture()throws Exception {
            owner.setContentView(list);list.layout(0,0,1000,2000);list.card.layout(0,0,1000,500);
            api=new NativeCardDismissal(Container.class.getField("data"),Container.class.getMethod("i"),
                    Container.class.getMethod("getCardViews"),Container.class.getDeclaredMethod("onDetachedFromWindow"),
                    new Handler(Looper.getMainLooper()),()->enabled);
        }
        @Override public void close(){api.close();activity.pause().stop().destroy();}
    }
    public static class Container extends FrameLayout {
        public Object data=List.of("timer");List<?> displayed=List.of("timer");final View card;int commits;
        Container(Context context){super(context);card=new View(context);addView(card);}
        public List<View> getCardViews(){return displayed.isEmpty()?List.of():List.of(card);}
        public void i(){commits++;displayed=List.copyOf((List<?>)data);}
        @Override public void onDetachedFromWindow(){super.onDetachedFromWindow();}
    }
}
