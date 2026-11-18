package dev.lsposed.fluidcloud;

import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class NativeShadeLaunchTest {
    FrameLayout root,shade;boolean valid;FakeLayer layer;List<String> events;List<Throwable> errors;NativeShadeLaunch bridge;
    @Before public void setup()throws Exception{
        root=new FrameLayout(RuntimeEnvironment.getApplication());shade=new FrameLayout(root.getContext());valid=true;events=new ArrayList<>();errors=new ArrayList<>();layer=new FakeLayer();
        bridge=new NativeShadeLaunch(name->switch(name){
            case "com.oplus.systemui.plugins.seedling.common.transition.r"->Launcher.class;
            case "com.oplus.systemui.plugins.seedling.common.transition.w"->Data.class;
            case "com.oplus.systemui.plugins.seedling.common.transition.e"->Controller.class;
            case "com.oplus.systemui.plugins.seedling.common.transition.c"->Callback.class;
            case "com.oplus.wrapper.view.RemoteAnimationTarget"->Target.class;
            default->throw new ClassNotFoundException(name);
        },new Handler(Looper.getMainLooper()),layer,()->events.add("collapse"),errors::add);
    }
    @Test public void launchesImmediatelyAndRaisesOnlyItsApplicationBeforeNativeAnimation(){
        assertTrue(bridge.begin(root,shade,()->valid));assertTrue(events.isEmpty());
        Data data=new Data(root);assertSame(data.a,bridge.ready(data));assertEquals(List.of("layer"),events);
        assertNull(bridge.ready(data));assertEquals(List.of("layer"),events);
        bridge.finish(new Callback(),()->{});assertEquals(List.of("layer"),events);
        bridge.finish(data.g,()->{});assertEquals(List.of("layer","collapse"),events);assertFalse(bridge.busy());
        idle(4000);assertEquals(2,events.size());assertTrue(errors.isEmpty());
    }
    @Test public void directCapsuleLaunchUsesTheSameLayerAndCommittedDismissalAsACard(){
        var host=new FrameLayout(root.getContext());var capsule=new FrameLayout(root.getContext());
        root.addView(host);host.addView(capsule);layer.defer=true;
        assertTrue(bridge.begin(NativeCardLaunch.animationSource(capsule,host),shade,()->valid));
        assertNull(bridge.ready(new Data(root)));assertTrue(events.isEmpty());
        Data data=new Data(capsule);assertSame(data.a,bridge.ready(data));assertEquals(List.of("layer"),events);
        bridge.finishSurface(data.b.value,()->events.add("native-window-cleanup"));
        bridge.finish(data.g,()->events.add("native-remote-finish"));
        assertEquals(List.of("layer","collapse"),events);assertTrue(bridge.busy());
        layer.completions.removeFirst().run();
        assertEquals(List.of("layer","collapse","native-window-cleanup","native-remote-finish"),events);
        assertFalse(bridge.busy());
    }
    @Test public void unrelatedNativeAnimationCannotAdoptOurPanelOrCompleteOurLaunch(){
        bridge.begin(root,shade,()->valid);Data other=new Data(new FrameLayout(root.getContext()));
        assertNull(bridge.ready(other));bridge.finish(other.g,()->{});assertTrue(events.isEmpty());assertTrue(bridge.busy());
        valid=false;idle(1600);assertFalse(bridge.busy());assertTrue(events.isEmpty());assertTrue(errors.isEmpty());
    }
    @Test public void cancellationAndDuplicateFinishDismissOnceWithoutReplayingAnyLaunch(){
        bridge.begin(root,shade,()->valid);Data data=new Data(root);bridge.ready(data);
        bridge.cancelController(new Controller(root));assertEquals(List.of("layer"),events);
        bridge.cancelController(data.a);bridge.finish(data.g,()->{});idle(4000);
        assertEquals(List.of("layer","collapse"),events);assertFalse(bridge.busy());
    }
    @Test public void layerFailureDismissesThePanelAndStillLetsTheOriginalNativeCallbackRun(){
        bridge.begin(root,shade,()->valid);layer.broken=true;Data data=new Data(root);
        assertNull(bridge.ready(data));assertEquals(List.of("collapse"),events);assertEquals(1,errors.size());assertFalse(bridge.busy());
    }
    @Test public void missingAnchorCanFallBackBeforeStartingAndBusyLaunchIsNotReplaced(){
        layer.anchor=null;assertFalse(bridge.begin(root,shade,()->valid));assertFalse(bridge.busy());
        layer.anchor=new Object();assertTrue(bridge.begin(root,shade,()->valid));assertFalse(bridge.begin(root,shade,()->valid));
        bridge.cancel();idle(4000);assertTrue(events.isEmpty());assertTrue(errors.isEmpty());
    }
    @Test public void absentNativeAnimationTimesOutOnceWithoutLeavingThePanelOverTheApplication(){
        bridge.begin(root,shade,()->valid);idle(1600);assertEquals(List.of("collapse"),events);assertEquals(1,errors.size());
        idle(4000);assertEquals(1,events.size());assertEquals(1,errors.size());
    }
    @Test public void nativeFinishIsRetainedUntilTheDismissalFrameCommitsAndDuplicateFinishDoesNotReplay(){
        bridge.begin(root,shade,()->valid);Data data=new Data(root);bridge.ready(data);layer.defer=true;
        assertTrue(bridge.finish(data.g,()->events.add("native-finish")));
        assertEquals(List.of("layer","collapse"),events);assertTrue(bridge.busy());
        assertTrue(bridge.finish(data.g,()->events.add("duplicate")));assertEquals(1,layer.completions.size());
        Runnable finish=layer.completions.removeFirst();finish.run();finish.run();assertEquals(List.of("layer","collapse","native-finish"),events);assertFalse(bridge.busy());
    }
    @Test public void lockingDuringCompletionStillReleasesTheNativeLeashWithoutRepeatingTheLaunch(){
        bridge.begin(root,shade,()->valid);Data data=new Data(root);bridge.ready(data);layer.defer=true;
        bridge.finish(data.g,()->events.add("native-finish"));valid=false;bridge.cancel();
        layer.completions.removeFirst().run();assertEquals(List.of("layer","collapse","native-finish"),events);assertFalse(bridge.busy());
    }
    @Test public void theEarlierNativeLeashReleaseIsAlsoHeldUntilThePanelDismissalCommits(){
        bridge.begin(root,shade,()->valid);Data data=new Data(root);bridge.ready(data);layer.defer=true;
        assertFalse(bridge.finishSurface(org.robolectric.shadow.api.Shadow.newInstanceOf(SurfaceControl.class),()->events.add("unrelated")));
        assertTrue(bridge.finishSurface(data.b.value,()->events.add("native-window-cleanup")));
        assertEquals(List.of("layer","collapse"),events);assertEquals(1,layer.completions.size());
        layer.completions.removeFirst().run();assertEquals(List.of("layer","collapse","native-window-cleanup"),events);assertFalse(bridge.busy());
    }
    @Test public void windowCleanupAndRemoteFinishAreBothReturnedInsteadOfMistakingOneForADuplicate(){
        bridge.begin(root,shade,()->valid);Data data=new Data(root);bridge.ready(data);layer.defer=true;
        bridge.finishSurface(data.b.value,()->events.add("native-window-cleanup"));
        bridge.finish(data.g,()->events.add("native-remote-finish"));
        bridge.finish(data.g,()->events.add("duplicate"));assertEquals(1,layer.completions.size());
        layer.completions.removeFirst().run();
        assertEquals(List.of("layer","collapse","native-window-cleanup","native-remote-finish"),events);assertFalse(bridge.busy());
    }
    @Test public void nativeCleanupMaySynchronouslyFinishItsRemoteCallbackWithoutRunningItAgain(){
        bridge.begin(root,shade,()->valid);Data data=new Data(root);bridge.ready(data);layer.defer=true;
        bridge.finishSurface(data.b.value,()->{
            events.add("native-window-cleanup");
            if(!bridge.finish(data.g,()->events.add("unused")))events.add("native-remote-finish");
        });
        bridge.finish(data.g,()->events.add("duplicate"));layer.completions.removeFirst().run();
        assertEquals(List.of("layer","collapse","native-window-cleanup","native-remote-finish"),events);assertFalse(bridge.busy());
    }
    @Test public void relativeLayerCutoverPrecedesPanelDismissalAndTheSurfaceIsRestoredAfterBothNativeFinishes(){
        layer.recordCutover=true;bridge.begin(root,shade,()->valid);Data data=new Data(root);bridge.ready(data);layer.defer=true;
        bridge.finishSurface(data.b.value,()->events.add("native-window-cleanup"));bridge.finish(data.g,()->events.add("native-remote-finish"));
        assertEquals(List.of("layer","atomic-cutover","collapse"),events);
        layer.completions.removeFirst().run();
        assertEquals(List.of("layer","atomic-cutover","collapse","native-window-cleanup","native-remote-finish","restore-shade"),events);
    }
    private static void idle(int ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    private final class FakeLayer implements NativeShadeLaunch.Layer{
        Object anchor=new Object();boolean broken,defer,recordCutover;final List<Runnable> completions=new ArrayList<>();
        public Object anchor(View view){assertSame(shade,view);return anchor;}
        public void raise(SurfaceControl application,Object value){assertSame(anchor,value);if(broken)throw new IllegalStateException("invalid leash");events.add("layer");}
        public void afterDismiss(Object anchor,Runnable committed){if(defer)completions.add(committed);else committed.run();}
        public void beginDismiss(SurfaceControl application,Object anchor){if(recordCutover)events.add("atomic-cutover");}
        public void restore(Object anchor){if(recordCutover)events.add("restore-shade");}
    }
    public static class Launcher{public void d(Data data){}public void f(SurfaceControl leash,boolean cancelled){}}
    public static class Controller{final ViewGroup root;Controller(ViewGroup root){this.root=root;}public ViewGroup e(){return root;}}
    public static class Callback{public void b(){}}
    public static class Target{final SurfaceControl value=org.robolectric.shadow.api.Shadow.newInstanceOf(SurfaceControl.class);public SurfaceControl getLeash(){return value;}}
    public static class Data{public final Controller a;public final Target b=new Target();public final Callback g=new Callback();Data(ViewGroup root){a=new Controller(root);}}
}
