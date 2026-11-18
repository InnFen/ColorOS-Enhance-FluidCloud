package dev.lsposed.fluidcloud;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaWaveContinuityTest {
    @Test public void pauseRetainsPresentationAndResumeRechecksSameSession(){
        var state=new MediaWaveContinuity();state.source("media");state.playback(true);state.sampled();
        int epoch=state.epoch();state.playback(false);assertTrue(state.custom());assertEquals(epoch,state.epoch());
        state.playback(true);assertTrue(state.custom());assertNotEquals(epoch,state.epoch());
        int resumed=state.epoch();state.playback(true);assertEquals(resumed,state.epoch());
    }
    @Test public void coldPausedMediaUsesIdleBarsWithoutAnAudioMeasurement(){
        var state=new MediaWaveContinuity();state.source("media");state.playback(false);assertTrue(state.custom());
        state.playback(true);assertTrue(state.custom());state.fallback();assertFalse(state.custom());
    }
    @Test public void failedRouteCannotRetryOnLayoutOrCardEventsButRealResumeCanRecover(){
        var state=new MediaWaveContinuity();state.source("media");state.playback(true);state.sampled();
        state.fallback();int failed=state.epoch();
        state.source("media");state.playback(true);assertEquals(failed,state.epoch());assertFalse(state.custom());
        state.playback(false);assertFalse(state.custom());state.playback(true);assertNotEquals(failed,state.epoch());
        assertFalse(state.custom());state.sampled();assertTrue(state.custom());
    }
    @Test public void sourceReplacementAndSessionEndCannotKeepStaleLiveBars(){
        var state=new MediaWaveContinuity();state.source("first");state.playback(true);state.sampled();
        state.source("second");assertFalse(state.custom());state.playback(false);assertTrue(state.custom());
        state.clear();assertFalse(state.custom());state.sampled();assertFalse(state.custom());
    }
    @Test public void coldSessionWithNoneOrStoppedStateStillUsesTheCustomIdlePresentation(){
        var state=new MediaWaveContinuity();state.source("new");state.playback(false);
        assertFalse(state.playback(false,true));assertTrue(state.custom());
        assertFalse(state.playback(false,true));assertTrue(state.custom());
    }
    @Test public void terminalStateAfterActualPlaybackRetiresButResumeAndNewSessionsRecover(){
        var state=new MediaWaveContinuity();state.source("first");state.playback(true);state.sampled();
        assertTrue(state.playback(false,true));int ended=state.epoch();
        assertFalse(state.playback(true,false));assertNotEquals(ended,state.epoch());
        assertTrue(state.custom());assertTrue(state.playback(false,true));
        state.source("new");assertFalse(state.playback(false,true));assertTrue(state.custom());
        state.clear();assertFalse(state.playback(false,true));assertFalse(state.custom());
    }
}
