package dev.lsposed.fluidcloud;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaWaveEnvelopeTest {
    private static final MediaWaveLevels SOUND=new MediaWaveLevels(.2f,.4f,.6f,.3f,.5f,.1f);
    @Test public void suspendedCaptureKeepsLastHeightsAndNewDataContinuesFromThem(){
        var envelope=new MediaWaveEnvelope();envelope.sample(SOUND);
        for(int i=0;i<40;i++)envelope.step(34);
        assertFalse(envelope.moving());
        for(int i=0;i<6;i++)assertEquals(SOUND.at(i),envelope.at(i),0);
        // No sample and no display frames while the card covers the capsule.
        assertFalse(envelope.sample(SOUND));assertFalse(envelope.moving());
        var next=new MediaWaveLevels(.4f,.2f,.3f,.6f,.1f,.5f);envelope.sample(next);
        for(int i=0;i<6;i++)assertEquals(SOUND.at(i),envelope.at(i),0);
        envelope.step(34);assertTrue(envelope.at(0)>.2f);assertTrue(envelope.at(0)<.4f);
    }
    @Test public void actualPauseFallsSmoothlyToIdleAndThenStopsScheduling(){
        var envelope=new MediaWaveEnvelope();envelope.sample(SOUND);for(int i=0;i<40;i++)envelope.step(34);
        envelope.pause();assertTrue(envelope.moving());envelope.step(34);
        assertTrue(envelope.at(2)>0);assertTrue(envelope.at(2)<.6f);
        for(int i=0;i<40;i++)envelope.step(34);
        assertFalse(envelope.moving());for(int i=0;i<6;i++)assertEquals(0,envelope.at(i),0);
    }
    @Test public void staleFrameTimeCannotJumpAndSessionResetClearsOldLevels(){
        var envelope=new MediaWaveEnvelope();envelope.sample(SOUND);envelope.step(10000);
        assertTrue(envelope.at(2)>0);assertTrue(envelope.at(2)<.6f);
        envelope.reset();assertFalse(envelope.moving());assertEquals(0,envelope.at(2),0);
    }
    @Test public void pauseHoldFreezesTheDisplayedFrameAndSwitchingItOffFallsToZero(){
        var envelope=new MediaWaveEnvelope();envelope.sample(SOUND);envelope.step(34);float height=envelope.at(2);
        envelope.pause(true);assertFalse(envelope.moving());
        for(int i=0;i<40;i++){envelope.pause(true);envelope.step(34);}
        assertEquals(height,envelope.at(2),0);
        envelope.pause(false);assertTrue(envelope.moving());for(int i=0;i<40;i++)envelope.step(34);assertEquals(0,envelope.at(2),0);
        envelope.sample(SOUND);envelope.step(34);assertTrue(envelope.at(2)>0);
    }
    @Test public void responseTimesCanChangeWithoutResettingTheCurrentHeights(){
        var fast=new MediaWaveEnvelope();var slow=new MediaWaveEnvelope();
        fast.configure(new MediaWaveOptions(80,100,20,40,false));slow.configure(new MediaWaveOptions(80,100,200,500,false));
        fast.sample(SOUND);slow.sample(SOUND);fast.step(34);slow.step(34);assertTrue(fast.at(2)>slow.at(2));
        float previous=fast.at(2);fast.configure(MediaWaveOptions.DEFAULTS);assertEquals(previous,fast.at(2),0);
    }
    @Test public void identicalTargetsStillNeedFramesWhenVisibilityInterruptedTheInterpolation(){
        var envelope=new MediaWaveEnvelope();envelope.sample(SOUND);envelope.step(34);
        float interrupted=envelope.at(2);assertFalse(envelope.sample(SOUND));assertTrue(envelope.moving());
        envelope.step(34);assertTrue(envelope.at(2)>interrupted);
    }

    @Test public void playbackResumeDoesNotSpendThePausedTimeOnItsFirstStep() {
        var envelope = new MediaWaveEnvelope();
        envelope.configure(new MediaWaveOptions(80, 100, 5, 105, false));
        envelope.sample(SOUND);
        envelope.step(34);
        envelope.pause(false);
        for (int i = 0; i < 40; i++) envelope.step(34);
        envelope.captureStarting();

        envelope.sample(SOUND);
        assertEquals(MediaWaveLevels.SILENT, envelope.snapshot());
        envelope.step(10_000);
        float first = envelope.at(2);
        assertTrue(first > 0 && first < SOUND.at(2) * .25f);
        envelope.step(34);
        assertTrue(envelope.at(2) > first && envelope.at(2) < SOUND.at(2));
    }

    @Test public void captureSuspensionHoldsTheDisplayedFrameUntilFreshData() {
        var envelope = new MediaWaveEnvelope();
        envelope.sample(SOUND);
        envelope.step(34);
        MediaWaveLevels shown = envelope.snapshot();

        envelope.suspend();
        assertFalse(envelope.step(10_000));
        envelope.captureStarting();
        envelope.sample(MediaWaveLevels.SILENT);
        assertFalse(envelope.moving());
        assertEquals(shown, envelope.snapshot());

        // A second card opening cancels this capture before it reads again.
        envelope.suspend();
        envelope.captureStarting();
        envelope.sample(MediaWaveLevels.SILENT);
        assertFalse(envelope.moving());
        assertEquals(shown, envelope.snapshot());

        var next = new MediaWaveLevels(.4f, .1f, .7f, .1f, .7f, .3f);
        envelope.sample(next);
        assertEquals(shown, envelope.snapshot());
        envelope.step(34);
        for (int i = 0; i < MediaWaveLevels.COUNT; i++) {
            assertTrue(envelope.at(i) > Math.min(shown.at(i), next.at(i)));
            assertTrue(envelope.at(i) < Math.max(shown.at(i), next.at(i)));
        }
    }

    @Test public void quietPlaybackAndARealPauseCanStillSettleAfterSuspension() {
        var envelope = new MediaWaveEnvelope();
        envelope.sample(SOUND);
        envelope.step(34);
        envelope.captureStarting();
        envelope.sample(MediaWaveLevels.SILENT);
        envelope.suspend();
        envelope.sample(MediaWaveLevels.SILENT);
        assertTrue(envelope.moving());
        envelope.step(34);

        envelope.pause(false);
        envelope.suspend();
        envelope.pause(false);
        for (int i = 0; i < 40; i++) envelope.step(34);
        assertEquals(MediaWaveLevels.SILENT, envelope.snapshot());
    }
}
