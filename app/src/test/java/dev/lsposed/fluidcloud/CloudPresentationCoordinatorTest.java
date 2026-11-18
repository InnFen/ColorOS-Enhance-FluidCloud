package dev.lsposed.fluidcloud;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class CloudPresentationCoordinatorTest {
    private static final Object HOST=new Object(), CONTENT=new Object();
    private static final CloudConfig ANIMATE=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,"fullscreen_animation",true,"recents_shrink",true));
    private static final CloudConfig KEEP=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,"fullscreen",true,"recents",true,"shade_keep",true));
    private static CloudPresentationCoordinator.Input input(CloudConfig config,boolean full,CloudScene scene,boolean shade,boolean blocked){
        return new CloudPresentationCoordinator.Input(HOST,0,CONTENT,true,config,false,full,scene,shade,blocked,true);
    }
    private static final class Clock implements CloudPresentationCoordinator.Clock {
        long time=1_000_000_000;Runnable pending;double scale=1;
        public double durationScale(){return scale;}
        public long now(){return time;}public void postFrame(Runnable value){assertNull(pending);pending=value;}public void removeFrame(Runnable value){if(pending==value)pending=null;}
        void tick(long ms){time+=ms*1_000_000;Runnable next=pending;pending=null;if(next!=null)next.run();}
        void settle(){for(int i=0;i<240 && pending!=null;i++)tick(8);assertNull("animation must sleep when settled",pending);}
    }
    private static final class Port implements CloudPresentationCoordinator.Port {
        final List<CloudPresentationCoordinator.Owner> requests=new ArrayList<>();
        final List<Runnable> callbacks=new ArrayList<>();final List<String> order=new ArrayList<>();
        final List<CloudPresentationCoordinator.Frame> frames=new ArrayList<>();
        boolean auto=true,broken;int prepares,releases,cancels,failures;
        public void prepare(Object content){prepares++;if(broken)throw new IllegalStateException("provider lost");}
        public void request(CloudPresentationCoordinator.Owner owner,CloudPresentationCoordinator.Ticket ticket,Runnable ready){requests.add(owner);callbacks.add(ready);order.add("request");if(auto)ready.run();}
        public void render(CloudPresentationCoordinator.Frame frame){frames.add(frame);order.add("render");}
        public void cancelTouch(){cancels++;}public void release(){releases++;}public void failure(Throwable error){failures++;}
    }
    @Test public void defaultAndAllOemChoicesNeverAcquireOutput(){
        for(CloudConfig config:List.of(CloudConfig.from(Map.of()),CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,
                "capsule_on_top",false,"capsule_keep_during_launch",false)))){
            Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
            core.update(input(config,false,null,false,false));core.update(input(config,true,null,false,false));
            assertEquals(0,port.prepares);assertTrue(port.requests.isEmpty());assertNull(clock.pending);
        }
    }
    @Test public void eitherCapsuleOptionAcquiresOnlyVisibleUnlockedScenesAndYieldsToOemScenes(){
        for (boolean onTop : new boolean[]{true, false}) {
            CloudConfig config=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,
                    "capsule_on_top",onTop,"capsule_keep_during_launch",!onTop));
            Port port=new Port();var core=new CloudPresentationCoordinator(port,new Clock());
            core.update(input(config,false,null,false,false));
            assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());
            core.update(input(config,false,null,true,false));
            assertEquals(CloudPresentationCoordinator.Owner.NATIVE,port.requests.getLast());
            core.update(input(config,true,null,false,false));
            assertEquals(CloudPresentationCoordinator.Owner.NATIVE,port.requests.getLast());
            core.update(input(config,false,null,false,false));
            int releases=port.releases;
            core.update(input(config,false,null,false,true));
            assertTrue(port.releases>releases);
        }
    }
    @Test public void firstModuleBufferIsStagedBeforeAtomicHandoff(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(ANIMATE,false,null,false,false));
        assertEquals(List.of("render","request","render"),port.order);
        assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());assertNull(clock.pending);
    }
    @Test public void systemAnimationScaleZeroSettlesWithoutReplayingAnIntermediateNativeAnimation(){
        Port port=new Port();Clock clock=new Clock();clock.scale=0;var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(ANIMATE,false,null,false,false));core.update(input(ANIMATE,true,null,false,false));clock.tick(8);
        assertEquals(CloudPresentationCoordinator.Owner.HIDDEN,core.current().owner());assertEquals(0,core.current().fraction(),0);assertNull(clock.pending);
        core.update(input(ANIMATE,false,null,false,false));clock.tick(8);assertEquals(1,core.current().fraction(),0);assertNull(clock.pending);
        assertFalse(port.requests.contains(CloudPresentationCoordinator.Owner.NATIVE));
    }
    @Test public void fullscreenHideAndShowHaveOneOwnerAndNeverYieldNativeMidway(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(ANIMATE,false,null,false,false));core.update(input(ANIMATE,true,null,false,false));
        clock.tick(80);assertTrue(core.current().fraction()>0 && core.current().fraction()<1);clock.settle();
        assertEquals(CloudPresentationCoordinator.Owner.HIDDEN,core.current().owner());assertEquals(0,core.current().fraction(),0);
        core.update(input(ANIMATE,false,null,false,false));assertEquals(0,core.current().fraction(),0);clock.settle();
        assertEquals(1,core.current().fraction(),0);assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());
        assertFalse(port.requests.contains(CloudPresentationCoordinator.Owner.NATIVE));
    }
    @Test public void rapidReversalsResumeTheCurrentGeometryWithoutRestarting(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(ANIMATE,false,null,false,false));
        for(int i=0;i<80;i++){
            double before=core.current().fraction();core.update(input(ANIMATE,(i&1)==0,null,false,false));
            assertEquals(before,core.current().fraction(),.000001);clock.tick(8+i%5*7);
            assertTrue(Double.isFinite(core.current().fraction()));assertTrue(core.current().fraction()>=0 && core.current().fraction()<=1);
        }
        core.update(input(ANIMATE,false,null,false,false));clock.settle();assertEquals(1,core.current().fraction(),0);
    }
    @Test public void recentsAndFullscreenShareTheSameAnimationState(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(ANIMATE,false,null,false,false));core.update(input(ANIMATE,false,CloudScene.RECENTS,false,false));clock.tick(80);
        double before=core.current().fraction();core.update(input(ANIMATE,true,null,false,false));assertEquals(before,core.current().fraction(),0);
        clock.settle();assertEquals(CloudPresentationCoordinator.Owner.HIDDEN,core.current().owner());
    }
    @Test public void enteringAlreadyHiddenDoesNotInventAVisibleFlash(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(ANIMATE,true,null,false,false));
        assertEquals(CloudPresentationCoordinator.Owner.HIDDEN,core.current().owner());assertEquals(0,core.current().fraction(),0);assertNull(clock.pending);
    }
    @Test public void protectionRevokesPendingOwnershipAndOldCallbacks(){
        Port port=new Port();port.auto=false;Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(KEEP,false,null,false,false));Runnable stale=port.callbacks.getLast();var ticket=core.current().ticket();
        core.update(input(KEEP,true,null,false,true));stale.run();
        assertFalse(core.accepts(ticket));assertEquals(CloudPresentationCoordinator.Owner.NATIVE,core.current().owner());assertNull(clock.pending);
    }
    @Test public void contentReplacementRejectsTheOldCompletion(){
        Port port=new Port();port.auto=false;Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(KEEP,false,null,false,false));Runnable stale=port.callbacks.getLast();var old=core.current().ticket();
        Object replacement=new Object();core.update(new CloudPresentationCoordinator.Input(HOST,0,replacement,true,KEEP,false,false,null,false,false,true));
        stale.run();assertFalse(core.accepts(old));assertEquals(CloudPresentationCoordinator.Owner.NATIVE,core.current().owner());
        port.callbacks.getLast().run();assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());assertSame(replacement,core.current().content());
    }
    @Test public void contentReplacementCannotClearTheLastValidOutputBetweenFrames(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(KEEP,true,null,false,false));int releases=port.releases;
        core.update(new CloudPresentationCoordinator.Input(HOST,0,new Object(),true,KEEP,false,true,null,false,false,true));
        assertEquals(releases,port.releases);assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());
        assertEquals(1,core.current().fraction(),0);assertFalse(port.requests.contains(CloudPresentationCoordinator.Owner.NATIVE));
    }
    @Test public void reversalDuringPendingCompositorHandoffNeverClearsOrRestartsTheSurface(){
        Port port=new Port();port.auto=false;Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(ANIMATE,false,null,false,false));Runnable old=port.callbacks.getLast();int releases=port.releases;
        for(int i=0;i<12;i++)core.update(input(ANIMATE,(i&1)==0,null,false,false));
        old.run();port.callbacks.getLast().run();assertEquals(releases,port.releases);
        assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());assertEquals(1,core.current().fraction(),0);
        assertFalse(port.requests.contains(CloudPresentationCoordinator.Owner.NATIVE));
    }
    @Test public void oemRecentsWinsOverFullscreenKeepAndReturnsWithoutCrossedOwners(){
        CloudConfig config=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,"fullscreen",true));
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(config,true,null,false,false));core.update(input(config,true,CloudScene.RECENTS,false,false));
        assertEquals(CloudPresentationCoordinator.Owner.NATIVE,core.current().owner());
        core.update(input(config,true,null,false,false));assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());
    }
    @Test public void shadeKeepAndShadeOemUseTheSameOwnerDecision(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(KEEP,false,null,true,false));assertTrue(core.retainShade());assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());
        core.update(input(ANIMATE,false,null,true,false));assertFalse(core.retainShade());assertEquals(CloudPresentationCoordinator.Owner.NATIVE,core.current().owner());
    }
    @Test public void nativeCardMayReplaceTheCapsuleWithoutReplayingAnExpandedPanelFraction(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(KEEP,false,null,true,false));assertTrue(core.retainShade());int prepares=port.prepares;
        // The native click removes the small capsule while its real expanded card
        // takes over. Replaying the cached fraction=1 here immediately hides it.
        core.update(new CloudPresentationCoordinator.Input(HOST,0,null,false,KEEP,false,false,null,true,false,true));
        assertTrue(core.ownsContent());assertEquals(CloudPresentationCoordinator.Owner.HIDDEN,core.current().owner());
        assertEquals(prepares,port.prepares);assertTrue(core.retainShade());
        core.update(new CloudPresentationCoordinator.Input(HOST,0,null,false,KEEP,false,false,null,true,true,true));assertFalse(core.retainShade());
        core.update(new CloudPresentationCoordinator.Input(HOST,1,null,false,KEEP,false,false,null,true,false,true));assertFalse(core.retainShade());
    }
    @Test public void independentLandscapeConfigurationRevokesOldCoordinateGeneration(){
        CloudConfig config=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,"fullscreen",true));
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(config,true,null,false,false));var old=core.current().ticket();
        core.update(new CloudPresentationCoordinator.Input(HOST,0,CONTENT,true,config.forOrientation(true),true,true,null,false,false,true));
        assertFalse(core.accepts(old));assertEquals(CloudPresentationCoordinator.Owner.NATIVE,core.current().owner());
    }
    @Test public void pixelInvalidationsCoalesceAndStaticContentSleeps(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(KEEP,false,null,false,false));int before=port.prepares;
        for(int i=0;i<25;i++)core.invalidate();clock.tick(8);assertEquals(before+1,port.prepares);assertNull(clock.pending);
    }
    @Test public void failedProviderFallsBackAndNewContentCanRetry(){
        Port port=new Port();port.broken=true;Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(KEEP,false,null,false,false));assertEquals(1,port.failures);assertFalse(core.ownsContent());
        port.broken=false;core.update(input(KEEP,false,null,false,false));assertFalse(core.ownsContent());
        core.update(new CloudPresentationCoordinator.Input(HOST,0,new Object(),true,KEEP,false,false,null,false,false,true));
        assertTrue(core.ownsContent());assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());
    }
    @Test public void closeNeverAllowsLateAnimationOrSurfaceCallbacks(){
        Port port=new Port();port.auto=false;Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(KEEP,false,null,false,false));Runnable callback=port.callbacks.getLast();core.close();callback.run();
        core.update(input(KEEP,false,null,false,false));assertFalse(core.ownsContent());assertEquals(CloudPresentationCoordinator.Owner.NATIVE,core.current().owner());assertNull(clock.pending);
    }
    @Test public void missingContentAndSecondaryDisplaysAlwaysKeepNative(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(new CloudPresentationCoordinator.Input(HOST,1,CONTENT,true,KEEP,false,true,null,false,false,true));
        core.update(new CloudPresentationCoordinator.Input(HOST,0,null,false,KEEP,false,true,null,false,false,true));assertFalse(core.ownsContent());assertEquals(0,port.prepares);
    }
    @Test public void aNativeCardCanReplaceAndRestoreTheCapsuleWithoutReleasingTheOwnedWindow(){
        for(int scene=0;scene<3;scene++){
            Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
            boolean full=scene==0,shade=scene==2;CloudScene request=scene==1?CloudScene.RECENTS:null;
            core.update(input(KEEP,full,request,shade,false));int releases=port.releases;
            core.update(new CloudPresentationCoordinator.Input(HOST,0,null,false,KEEP,false,full,request,shade,false,false));
            assertTrue(core.ownsContent());assertEquals(releases,port.releases);
            assertEquals(CloudPresentationCoordinator.Owner.HIDDEN,core.current().owner());assertNull(clock.pending);
            assertEquals(0,port.frames.getLast().fraction(),0);
            core.update(input(KEEP,full,request,shade,false));
            assertEquals(CloudPresentationCoordinator.Owner.MODULE,core.current().owner());assertEquals(1,core.current().fraction(),0);
            assertFalse(port.requests.contains(CloudPresentationCoordinator.Owner.NATIVE));assertEquals(releases,port.releases);
        }
    }
    @Test public void protectionOrReturningToOemStillReleasesAnEmptyPresentation(){
        Port port=new Port();Clock clock=new Clock();var core=new CloudPresentationCoordinator(port,clock);
        core.update(input(KEEP,true,null,false,false));
        core.update(new CloudPresentationCoordinator.Input(HOST,0,null,false,KEEP,false,true,null,false,false,false));
        int releases=port.releases;
        core.update(new CloudPresentationCoordinator.Input(HOST,0,null,false,KEEP,false,true,null,false,true,false));
        assertFalse(core.ownsContent());assertEquals(CloudPresentationCoordinator.Owner.NATIVE,core.current().owner());assertTrue(port.releases>releases);
        core.update(input(KEEP,true,null,false,false));
        core.update(new CloudPresentationCoordinator.Input(HOST,0,null,false,CloudConfig.from(Map.of()),false,true,null,false,false,false));
        assertFalse(core.ownsContent());assertEquals(CloudPresentationCoordinator.Owner.NATIVE,core.current().owner());
    }
}
