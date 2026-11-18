package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.media.session.PlaybackState;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class MediaWaveTailTest {
    public static class CapsuleView extends FrameLayout {public CapsuleView(Activity activity){super(activity);}}
    private static final MediaWaveLevels SOUND=new MediaWaveLevels(.2f,.7f,.3f,.5f,.4f,.1f);
    private static final class Fixture implements AutoCloseable {
        final org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup().visible();
        final FrameLayout host=new FrameLayout(activity.get()),root=new FrameLayout(activity.get());
        final CapsuleView capsule=new CapsuleView(activity.get());
        final FrameLayout content=new FrameLayout(activity.get());
        final ImageView wave=new ImageView(activity.get());
        final ArrayList<ImageView> released=new ArrayList<>();
        final MediaWaveTail tail=new MediaWaveTail(root,released::add);
        Fixture(){
            host.addView(root);root.addView(capsule);capsule.addView(content);content.addView(wave);activity.get().setContentView(host);
        }
        public void close(){tail.clear();activity.pause().stop().destroy();}
    }
    @Test public void endedSessionKeepsTheDisplayedFrameAfterAudioEnvelopeHasBeenCleared(){
        try(var f=new Fixture()){
            var envelope=new MediaWaveEnvelope();envelope.sample(SOUND);envelope.step(34);
            var displayed=envelope.snapshot();f.tail.hold(f.wave,displayed);envelope.reset();
            for(int i=0;i<6;i++){assertEquals(displayed.at(i),f.tail.frame(f.wave).at(i),0);assertEquals(0,envelope.at(i),0);}
            assertTrue(f.tail.pending());assertTrue(f.released.isEmpty());
        }
    }
    @Test public void contentFadeAndSmallShrinkKeepTheFrameUntilTheWholeCapsuleDisappears(){
        try(var f=new Fixture()){
            f.tail.hold(f.wave,SOUND);f.content.setAlpha(0);f.capsule.setScaleX(.001f);f.tail.prune();
            assertEquals(SOUND,f.tail.frame(f.wave));assertTrue(f.released.isEmpty());
            f.capsule.setScaleX(0);f.tail.prune();assertFalse(f.tail.pending());assertEquals(java.util.List.of(f.wave),f.released);
        }
    }
    @Test public void nativePreDrawReleasesOnlyAfterTheCapsuleIsActuallyHidden(){
        try(var f=new Fixture()){
            f.tail.hold(f.wave,SOUND);f.root.getViewTreeObserver().dispatchOnPreDraw();assertTrue(f.tail.pending());
            f.capsule.setVisibility(View.INVISIBLE);f.root.getViewTreeObserver().dispatchOnPreDraw();
            assertNull(f.tail.frame(f.wave));assertEquals(1,f.released.size());
            f.root.getViewTreeObserver().dispatchOnPreDraw();assertEquals(1,f.released.size());
        }
    }
    @Test public void detachingTheOutgoingCapsuleReleasesWithoutAnyFurtherFramesOrPolling(){
        try(var f=new Fixture()){
            f.tail.hold(f.wave,SOUND);f.root.removeView(f.capsule);
            assertFalse(f.tail.pending());assertEquals(java.util.List.of(f.wave),f.released);
        }
    }
    @Test public void hiddenStatusBarOutsideTheNativeRootDoesNotEndAnIndependentlyPresentedTail(){
        try(var f=new Fixture()){
            f.tail.hold(f.wave,SOUND);f.host.setVisibility(View.INVISIBLE);f.tail.prune();
            assertEquals(SOUND,f.tail.frame(f.wave));assertTrue(f.released.isEmpty());
        }
    }
    @Test public void duplicateEndEventsCannotReplaceTheFrozenFrameWithZeros(){
        try(var f=new Fixture()){
            f.tail.hold(f.wave,SOUND);f.tail.hold(f.wave,MediaWaveLevels.SILENT);f.tail.prune();
            assertEquals(SOUND,f.tail.frame(f.wave));
        }
    }
    @Test public void newPresentationOrDisablingCanClearTheTailWithoutRemovingNativeViews(){
        try(var f=new Fixture()){
            f.tail.hold(f.wave,SOUND);f.tail.clear();assertFalse(f.tail.pending());assertNull(f.tail.frame(f.wave));
            assertSame(f.content,f.wave.getParent());f.capsule.setVisibility(View.GONE);f.tail.prune();
            assertTrue(f.released.isEmpty());
            f.capsule.setVisibility(View.VISIBLE);f.tail.hold(f.wave,MediaWaveLevels.SILENT);assertEquals(MediaWaveLevels.SILENT,f.tail.frame(f.wave));
        }
    }
    @Test public void unrelatedOrAlreadyHiddenViewsCannotAcquireAnOutgoingWaveform(){
        try(var f=new Fixture()){
            ImageView unrelated=new ImageView(f.activity.get());f.root.addView(unrelated);
            f.tail.hold(unrelated,SOUND);assertFalse(f.tail.pending());
            f.capsule.setVisibility(View.GONE);f.tail.hold(f.wave,SOUND);assertFalse(f.tail.pending());
        }
    }
    @Test public void stoppedNoneAndErrorEndCaptureWhileOrdinaryPauseKeepsItsOwnPolicy(){
        for(int state:new int[]{PlaybackState.STATE_NONE,PlaybackState.STATE_STOPPED,PlaybackState.STATE_ERROR})assertTrue(MediaWaveTail.terminal(state));
        for(int state:new int[]{PlaybackState.STATE_PLAYING,PlaybackState.STATE_PAUSED,PlaybackState.STATE_BUFFERING,PlaybackState.STATE_CONNECTING})assertFalse(MediaWaveTail.terminal(state));
    }
}
