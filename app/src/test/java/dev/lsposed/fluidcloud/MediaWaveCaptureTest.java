package dev.lsposed.fluidcloud;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaWaveCaptureTest {
    private static final MediaWaveCapture.Request A=new MediaWaveCapture.Request(10352,77,1);
    private static final MediaWaveCapture.Request B=new MediaWaveCapture.Request(10352,88,2);
    private static final class Clock implements MediaWaveCapture.Scheduler {
        final ArrayList<Runnable> tasks=new ArrayList<>();final ArrayList<Long> delays=new ArrayList<>();
        long time;
        public long now(){return time;}
        public void post(Runnable action,long delay){tasks.add(action);delays.add(delay);}
        public void remove(Runnable action){int i=tasks.indexOf(action);if(i>=0){tasks.remove(i);delays.remove(i);}}
        void next(){delays.removeFirst();tasks.removeFirst().run();}
    }
    private static final class Backend implements MediaWaveCapture.Backend {
        boolean safe=true,readable=true,unsafe;int checks,opens,closes,reads,notReady,transientReads,warmupReads;Runnable duringCheck=()->{},duringOpen=()->{};
        final List<String> events=new ArrayList<>();
        public boolean safe(MediaWaveCapture.Request request){checks++;duringCheck.run();return safe;}
        public MediaWaveCapture.Route route(MediaWaveCapture.Request request)throws Exception{
            if(unsafe){checks++;duringCheck.run();return MediaWaveCapture.Route.UNSAFE;}
            return MediaWaveCapture.Backend.super.route(request);
        }
        public MediaWaveCapture.Source open(MediaWaveCapture.Request request)throws MediaWaveCapture.NotReady{
            if(notReady-->0)throw new MediaWaveCapture.NotReady(new RuntimeException());
            opens++;events.add("open"+request.session());duringOpen.run();
            return new MediaWaveCapture.Source(){
                private boolean filling;
                public MediaWaveLevels read()throws Exception{reads++;filling=warmupReads-->0;if(transientReads-->0)throw new MediaWaveCapture.NotReady("temporary");if(!readable)throw new IllegalStateException();return MediaWaveLevels.SILENT;}
                public boolean buffering(){return filling;}
                public void close(){closes++;events.add("close");}
            };
        }
    }
    private static final class Fixture {
        final Clock clock=new Clock();final Backend backend=new Backend();int outputs,fallbacks;
        final MediaWaveCapture capture=new MediaWaveCapture(clock,backend,(ticket,levels)->outputs++,(ticket,reason)->fallbacks++);
    }
    @Test public void audibleTimelineWarmupKeepsOneEffectAndDoesNotPublishAnArtificialPause(){
        var f=new Fixture();f.backend.warmupReads=3;f.capture.request(A);f.clock.next();f.clock.next();f.clock.next();
        assertEquals(0,f.outputs);assertEquals(0,f.fallbacks);assertEquals(1,f.backend.opens);assertEquals(0,f.backend.closes);
        f.clock.next();assertEquals(1,f.outputs);
        f.capture.request(null);while(!f.clock.tasks.isEmpty())f.clock.next();assertEquals(1,f.backend.closes);
    }
    @Test public void aNeverSettlingTimelineCannotLeaveAnUnboundedSamplingLoop(){
        var f=new Fixture();f.backend.warmupReads=100;f.capture.request(A);f.clock.next();
        f.clock.time=3501;f.clock.next();assertEquals(1,f.fallbacks);assertEquals(1,f.backend.closes);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void cancellationDuringWarmupReleasesWithoutPublishingAnOldFrame(){
        var f=new Fixture();f.backend.warmupReads=100;f.capture.request(A);f.clock.next();
        f.capture.request(null);while(!f.clock.tasks.isEmpty())f.clock.next();assertEquals(0,f.outputs);assertEquals(1,f.backend.closes);
    }
    @Test public void unknownOrUnsafeRoutesNeverOpenAnyAudioEffectAndDoNotRetryOnEachVisibilityChange(){
        var f=new Fixture();f.backend.safe=false;f.capture.request(A);while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(0,f.backend.opens);assertEquals(1,f.fallbacks);assertTrue(f.clock.tasks.isEmpty());
        f.capture.request(null);f.clock.next();f.capture.request(A);f.clock.next();
        assertEquals(6,f.backend.checks);assertEquals(0,f.backend.opens);
        f.capture.request(B);f.clock.next();assertEquals(7,f.backend.checks);
    }
    @Test public void nativeTrackBecomingReadyAfterPlaybackCallbackIsRetriedOnce(){
        var f=new Fixture();f.backend.safe=false;f.capture.request(A);f.clock.next();
        assertEquals(180,f.clock.delays.getFirst().longValue());assertEquals(0,f.backend.opens);
        f.backend.safe=true;f.clock.next();assertEquals(1,f.backend.opens);assertEquals(1,f.outputs);
    }
    @Test public void pauseDuringDelayedReadinessCheckCancelsWithoutOpeningAnEffect(){
        var f=new Fixture();f.backend.safe=false;f.capture.request(A);f.clock.next();
        f.capture.request(null);while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(1,f.backend.checks);assertEquals(0,f.backend.opens);
    }
    @Test public void initialNoInitRecoversWithoutRequiringPauseOrAnotherCapsuleAppearance(){
        var f=new Fixture();f.backend.notReady=2;f.capture.request(A);
        f.clock.next();assertEquals(180,f.clock.delays.getFirst().longValue());
        f.clock.next();assertEquals(360,f.clock.delays.getFirst().longValue());
        f.clock.next();assertEquals(3,f.backend.checks);assertEquals(1,f.backend.opens);
        assertEquals(1,f.outputs);assertEquals(0,f.fallbacks);
    }
    @Test public void noInitHasABoundedBudgetAndDoesNotRetryAfterFailureOrDisable(){
        var f=new Fixture();f.backend.notReady=100;f.capture.request(A);
        while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(6,f.backend.checks);assertEquals(1,f.fallbacks);assertEquals(0,f.backend.opens);
        f.capture.request(null);f.clock.next();f.capture.request(A);f.clock.next();assertEquals(6,f.backend.checks);
    }
    @Test public void retryRechecksRouteSoAnOffloadHandoffNeverOpensAnEffect(){
        var f=new Fixture();f.backend.notReady=1;f.capture.request(A);f.clock.next();f.backend.safe=false;
        while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(0,f.backend.opens);assertEquals(1,f.fallbacks);
    }
    @Test public void samplingIsTwelveAndAHalfHzAndHiddenOrPausedStateReleasesWithoutAResidualTimer(){
        var f=new Fixture();f.capture.request(A);f.clock.next();
        assertEquals(1,f.outputs);assertEquals(80,f.clock.delays.getFirst().longValue());
        f.capture.request(null);f.clock.next(); // stale pending sample cannot publish
        while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(1,f.outputs);assertEquals(1,f.backend.closes);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void cancellationDuringTheNativeRouteCheckPreventsCreatingAnEffect(){
        var f=new Fixture();f.backend.duringCheck=()->f.capture.request(null);
        f.capture.request(A);f.clock.next();while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(0,f.backend.opens);assertEquals(0,f.outputs);
    }
    @Test public void cancellationDuringOpenReleasesTheNewEffectWithoutDisplayingStaleAudio(){
        var f=new Fixture();f.backend.duringOpen=()->f.capture.request(null);
        f.capture.request(A);f.clock.next();while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(1,f.backend.opens);assertEquals(1,f.backend.closes);assertEquals(0,f.outputs);
    }
    @Test public void changingMediaAlwaysClosesTheOldEffectBeforeCreatingTheNext(){
        var f=new Fixture();f.capture.request(A);f.clock.next();f.capture.request(B);
        f.clock.next();f.clock.next();
        assertEquals(List.of("open77","close","open88"),f.backend.events);assertEquals(2,f.backend.opens);
    }
    @Test public void readFailureRestoresOemOnceAndNeverLeavesACaptureLoopBehind(){
        var f=new Fixture();f.backend.readable=false;f.capture.request(A);f.clock.next();
        assertEquals(0,f.outputs);assertEquals(1,f.fallbacks);assertEquals(1,f.backend.closes);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void queuedOldRequestsCannotRestartTheEffectAfterTheSwitchWasDisabled(){
        var f=new Fixture();f.capture.request(A);f.capture.request(null);f.clock.next();f.clock.next();
        assertEquals(0,f.backend.opens);assertEquals(0,f.backend.checks);
    }
    @Test public void zeroAndGlobalSessionIdsAreNeverAccepted(){
        for(int session:new int[]{0,-1})try{new MediaWaveCapture.Request(10352,session,0);fail();}
        catch(IllegalArgumentException expected){ }
    }
    @Test public void changingCaptureIntervalKeepsOneSourceAndAppliesToTheNextTick(){
        var f=new Fixture();f.capture.interval(120);f.capture.request(A);f.clock.next();assertEquals(120,f.clock.delays.getFirst().longValue());
        f.capture.interval(40);f.clock.next();assertEquals(40,f.clock.delays.getFirst().longValue());assertEquals(1,f.backend.opens);
        f.capture.interval(Integer.MAX_VALUE);f.clock.next();assertEquals(1000,f.clock.delays.getFirst().longValue());
        f.capture.interval(-1);f.clock.next();assertEquals(20,f.clock.delays.getFirst().longValue());
        f.capture.request(null);while(!f.clock.tasks.isEmpty())f.clock.next();assertEquals(1,f.backend.closes);
    }
    @Test public void explicitlyUnsafeRouteRestoresOemImmediatelyBeforeItsFirstReadOnlyRecheck(){
        var f=new Fixture();f.backend.unsafe=true;f.capture.request(A);f.clock.next();
        assertEquals(0,f.backend.opens);assertEquals(0,f.backend.reads);assertEquals(1,f.backend.checks);assertEquals(1,f.fallbacks);
        assertEquals(1,f.clock.tasks.size());assertEquals(180,f.clock.delays.getFirst().longValue());
    }
    @Test public void unsafeRouteCanBecomeSafeWithoutAnotherRequestOrSwitchChange(){
        var f=new Fixture();f.backend.unsafe=true;long ticket=f.capture.request(A);f.clock.next();
        f.backend.unsafe=false;f.clock.next();
        assertEquals(ticket,f.capture.generation());assertEquals(2,f.backend.checks);assertEquals(1,f.backend.opens);
        assertEquals(1,f.outputs);assertEquals(1,f.fallbacks);
    }
    @Test public void persistentUnsafeRouteHasOnlySixReadOnlyChecksAndNoEffectOrResidualTask(){
        var f=new Fixture();f.backend.unsafe=true;f.capture.request(A);while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(6,f.backend.checks);assertEquals(0,f.backend.opens);assertEquals(0,f.backend.reads);assertEquals(0,f.backend.closes);
        assertEquals(1,f.fallbacks);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void cancellingUnsafeRecoveryPreventsEveryRemainingRouteRecheck(){
        var f=new Fixture();f.backend.unsafe=true;f.capture.request(A);f.clock.next();
        f.capture.request(null);while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(1,f.backend.checks);assertEquals(0,f.backend.opens);assertEquals(1,f.fallbacks);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void unsafeRouteVisibilityChangesNeitherRearmItsBudgetNorClearItsFailedKey(){
        var f=new Fixture();f.backend.unsafe=true;f.capture.request(A);f.clock.next();
        f.capture.request(null);while(!f.clock.tasks.isEmpty())f.clock.next();
        f.capture.request(A);f.clock.next();assertEquals(360,f.clock.delays.getFirst().longValue());
        while(!f.clock.tasks.isEmpty())f.clock.next();assertEquals(6,f.backend.checks);assertEquals(2,f.fallbacks);
        f.capture.request(null);f.clock.next();f.backend.unsafe=false;f.capture.request(A);f.clock.next();
        assertEquals(6,f.backend.checks);assertEquals(0,f.backend.opens);assertEquals(3,f.fallbacks);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void unsafeRouteAndTransientReadsShareOneFiniteRecoveryBudget(){
        var f=new Fixture();f.backend.unsafe=true;f.capture.request(A);f.clock.next();
        f.backend.unsafe=false;f.backend.transientReads=100;f.clock.next();
        assertEquals(360,f.clock.delays.getFirst().longValue());
        while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(6,f.backend.checks);assertEquals(5,f.backend.opens);assertEquals(5,f.backend.closes);
        assertEquals(0,f.outputs);assertEquals(1,f.fallbacks);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void recoveredUnsafeRouteCanRestoreOemAgainIfItsLaterReadFails(){
        var f=new Fixture();f.backend.unsafe=true;f.capture.request(A);f.clock.next();
        f.backend.unsafe=false;f.clock.next();assertEquals(1,f.outputs);assertEquals(1,f.fallbacks);
        f.backend.readable=false;f.clock.next();
        assertEquals(1,f.backend.closes);assertEquals(2,f.fallbacks);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void replacingAnUnsafeRequestCannotOpenItsOldPendingRoute(){
        var f=new Fixture();f.backend.unsafe=true;f.capture.request(A);f.clock.next();
        f.backend.unsafe=false;f.capture.request(B);f.clock.next();f.clock.next();
        assertEquals(2,f.backend.checks);assertEquals(List.of("open88"),f.backend.events);assertEquals(1,f.outputs);
        f.capture.request(null);while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(1,f.backend.closes);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void replacementAudioTrackInTheSameSessionCanRecoverFromAnOldFailedKey(){
        var f=new Fixture();f.backend.notReady=100;f.capture.request(A);while(!f.clock.tasks.isEmpty())f.clock.next();
        f.backend.notReady=0;
        f.capture.request(new MediaWaveCapture.Request(A.uid(),A.session(),A.route(),99));f.clock.next();
        assertEquals(1,f.backend.opens);assertEquals(1,f.outputs);
    }
    @Test public void transientReadsCloseAndRecheckTheRouteBeforeRecoveringWithoutOemFallback(){
        var f=new Fixture();f.backend.transientReads=2;f.capture.request(A);f.clock.next();f.clock.next();f.clock.next();
        assertEquals(3,f.backend.opens);assertEquals(2,f.backend.closes);assertEquals(3,f.backend.checks);
        assertEquals(1,f.outputs);assertEquals(0,f.fallbacks);
    }
    @Test public void repeatedDisabledSourcesHaveOneFiniteBudgetAndNeverKeepAnIdleRetryLoop(){
        var f=new Fixture();f.backend.transientReads=100;f.capture.request(A);while(!f.clock.tasks.isEmpty())f.clock.next();
        assertEquals(6,f.backend.opens);assertEquals(6,f.backend.closes);assertEquals(1,f.fallbacks);assertEquals(0,f.outputs);
        f.capture.request(null);f.clock.next();f.capture.request(A);f.clock.next();
        assertEquals(6,f.backend.opens);assertTrue(f.clock.tasks.isEmpty());
    }
    @Test public void hideDuringTransientRecoveryCancelsThePendingAcquire(){
        var f=new Fixture();f.backend.transientReads=1;f.capture.request(A);f.clock.next();
        f.capture.request(null);while(!f.clock.tasks.isEmpty())f.clock.next();assertEquals(1,f.backend.opens);assertEquals(1,f.backend.closes);
    }
    @Test public void longHealthyPlaybackRearmsRecoveryButShortRepeatedFailuresDoNot(){
        var f=new Fixture();f.backend.notReady=5;f.capture.request(A);
        for(int i=0;i<6;i++)f.clock.next();assertEquals(1,f.outputs);
        f.clock.time=60001;f.clock.next();assertEquals(2,f.outputs);
        f.backend.transientReads=1;f.clock.next();assertEquals(180,f.clock.delays.getFirst().longValue());
        f.clock.next();assertEquals(3,f.outputs);assertEquals(0,f.fallbacks);
    }
}
