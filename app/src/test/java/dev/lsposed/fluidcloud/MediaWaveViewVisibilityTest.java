package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class MediaWaveViewVisibilityTest {
    public static class CapsuleView extends FrameLayout {public CapsuleView(Activity context){super(context);}}
    private static final class Fixture implements AutoCloseable {
        final org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup().visible();
        final FrameLayout root=new FrameLayout(activity.get()),content=new FrameLayout(activity.get());
        final CapsuleView capsule=new CapsuleView(activity.get());final ImageView wave=new ImageView(activity.get());
        Fixture(){root.addView(capsule);capsule.addView(content);content.addView(wave);activity.get().setContentView(root);wave.layout(0,0,72,72);}
        boolean capture(){return MediaWaveViewVisibility.eligible(wave,true,view->true);}
        boolean render(){return MediaWaveViewVisibility.eligible(wave,false,view->true);}
        public void close(){activity.pause().stop().destroy();}
    }
    @Test public void transientInternalAlphaDoesNotTurnAVisibleCapsuleIntoACaptureStop(){
        try(var f=new Fixture()){
            assertTrue(f.capture());assertTrue(f.render());
            f.content.setAlpha(0);assertTrue(f.capture());assertFalse(f.render());
            f.content.setAlpha(1);f.wave.setAlpha(0f);assertTrue(f.capture());assertFalse(f.render());
            f.wave.setAlpha(1f);assertTrue(f.capture());assertTrue(f.render());
        }
    }
    @Test public void wholeCapsuleAlphaOrVisibilityStillEndsCapture(){
        try(var f=new Fixture()){
            f.capsule.setAlpha(0);assertFalse(f.capture());f.capsule.setAlpha(.001f);assertTrue(f.capture());
            f.capsule.setAlpha(1);f.capsule.setVisibility(View.INVISIBLE);assertFalse(f.capture());
            f.capsule.setVisibility(View.GONE);assertFalse(f.capture());
        }
    }
    @Test public void goneOrInvisibleContentIsNotConfusedWithATransientFade(){
        try(var f=new Fixture()){
            f.wave.setVisibility(View.GONE);assertFalse(f.capture());f.wave.setVisibility(View.VISIBLE);
            f.content.setVisibility(View.INVISIBLE);assertFalse(f.capture());f.content.setVisibility(View.GONE);assertFalse(f.capture());
        }
    }
    @Test public void removedOrReplacedWaveformCannotKeepTheOldSourceCapturing(){
        try(var f=new Fixture()){
            f.content.removeView(f.wave);assertFalse(f.capture());
            f.root.addView(f.wave);f.wave.layout(0,0,72,72);assertFalse(f.capture());
        }
    }
    @Test public void missingSizeOrHiddenPresentationRemainsIneligible(){
        try(var f=new Fixture()){
            f.wave.layout(0,0,0,72);assertFalse(f.capture());f.wave.layout(0,0,72,72);
            assertFalse(MediaWaveViewVisibility.eligible(f.wave,true,view->false));
        }
    }
}
