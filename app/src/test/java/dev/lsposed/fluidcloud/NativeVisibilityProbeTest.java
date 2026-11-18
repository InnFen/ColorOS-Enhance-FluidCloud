package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class NativeVisibilityProbeTest {
    private static final class Fixture {
        final View host;final NativeVisibilityProbe probe;final Collector collector=new Collector();
        final BaseLambda base=new BaseLambda();final SeedLambda seed=new SeedLambda();
        final Fragment fragment=new Fragment();final Extension extension=new Extension();
        final ArrayList<View> changes=new ArrayList<>();
        Fixture(boolean pipeline)throws Exception{
            Activity activity=Robolectric.buildActivity(Activity.class).setup().visible().get();host=new View(activity);activity.setContentView(host);
            collector.$seedlingContainer=host;extension.seedlingContainer=host;extension.statusBarFragment=fragment;
            probe=new NativeVisibilityProbe(name->{
                if(name.endsWith("$isNotificationIconContainerVisible$1"))return BaseLambda.class;
                if(name.endsWith("$isSeedlingContainerVisible$1"))return SeedLambda.class;
                if(name.endsWith("OplusHomeStatusBarViewModelImpl"))return SeedOwner.class;
                if(name.endsWith("HomeStatusBarViewModelImpl"))return BaseOwner.class;
                if(name.endsWith("$bind$1$1$3$1"))return Collector.class;
                if(name.endsWith("StatusBarDisableFlagsVisibilityModel"))return Flags.class;
                if(name.endsWith("pipeline.shared.ui.model.VisibilityModel"))return Model.class;
                if(name.endsWith("CommandQueue"))return Queue.class;
                if(name.endsWith("Continuation"))return Continuation.class;
                if(name.endsWith("CollapsedStatusBarFragmentExImpl"))return Extension.class;
                if(name.endsWith("CollapsedStatusBarFragment"))return Fragment.class;
                if(name.endsWith("StatusBarVisibilityModelEx$Companion"))return Companion.class;
                if(name.endsWith("StatusBarVisibilityModelEx"))return Extra.class;
                if(name.endsWith("StatusBarVisibilityModel"))return BaseModel.class;
                throw new ClassNotFoundException(name);
            },pipeline,changes::add);
        }
        void inputs(int flags,boolean shown,boolean privacy,boolean mini){
            probe.recordDisable(0,flags,0);
            probe.recordBase(base,new Object[]{true,false,new Flags(shown),null});
            probe.recordSeed(seed,new Object[]{null,true,mini,new Flags(shown),privacy,null});
        }
        void emit(int visibility){probe.observeEmission(collector,new Model(visibility));}
        NativeVisibilityProbe.Snapshot snapshot(){return probe.snapshot(host);}
        Extra legacy(boolean shown,boolean forced,boolean mini,boolean privacy)throws Throwable{
            BaseModel input=new BaseModel();Extra original=new Extra(shown);
            Object result=probe.withLegacyBase(fragment,()->{
                if(forced)probe.recordLegacyForced(input);
                return probe.withLegacyExtension(extension,input,()->{probe.observeLegacy(input,mini,privacy,original);return original;});
            });assertSame(original,result);return original;
        }
    }
    @Test public void pipelineOnlyObservesAndDoesNotMutateNativeModelOrHost()throws Exception{
        Fixture f=new Fixture(true);f.inputs(0x00920000,false,false,false);Model model=new Model(4);
        f.probe.observeEmission(f.collector,model);assertEquals(4,model.getVisibility());assertEquals(View.VISIBLE,f.host.getVisibility());
        assertEquals(CloudScene.RECENTS,f.snapshot().scene());assertFalse(f.snapshot().protectedState());assertFalse(f.snapshot().visible());
    }
    @Test public void unknownInputsAndGoneAreProtected()throws Exception{
        Fixture f=new Fixture(true);f.emit(0);assertTrue(f.snapshot().protectedState());
        f.inputs(0,true,false,false);f.emit(8);assertTrue(f.snapshot().protectedState());
    }
    @Test public void otherNeedsAVisiblePredecessor()throws Exception{
        Fixture f=new Fixture(true);f.inputs(0x20000,false,false,false);f.emit(4);assertNull(f.snapshot().scene());
        f.emit(0);f.emit(4);assertEquals(CloudScene.OTHER,f.snapshot().scene());
    }
    @Test public void rawDisableAndFlowRecoveryCannotDropRecentsOwnershipEarly()throws Exception{
        Fixture f=new Fixture(true);f.inputs(0x00920000,false,false,false);f.emit(4);
        f.probe.recordDisable(0,0,0);f.probe.recordBase(f.base,new Object[]{true,false,new Flags(true),null});
        assertEquals(CloudScene.RECENTS,f.snapshot().scene());assertFalse(f.snapshot().protectedState());
        f.probe.recordSeed(f.seed,new Object[]{null,true,false,new Flags(true),false,null});assertEquals(CloudScene.RECENTS,f.snapshot().scene());
        f.emit(0);assertNull(f.snapshot().scene());assertTrue(f.snapshot().visible());
    }
    @Test public void enteringRecentsSurvivesEitherFlowArrivingFirstWithoutAFalseProtectionReset()throws Exception{
        for(boolean baseFirst:new boolean[]{true,false}){
            Fixture f=new Fixture(true);f.inputs(0,true,false,false);f.emit(0);
            f.probe.recordDisable(0,0x00920000,0);
            assertEquals(CloudScene.RECENTS,f.snapshot().scene());assertFalse(f.snapshot().protectedState());
            if(baseFirst)f.probe.recordBase(f.base,new Object[]{true,false,new Flags(false),null});
            else f.probe.recordSeed(f.seed,new Object[]{null,true,false,new Flags(false),false,null});
            f.emit(4);assertEquals(CloudScene.RECENTS,f.snapshot().scene());assertFalse(f.snapshot().protectedState());
            f.inputs(0x00920000,false,false,false);f.emit(4);assertFalse(f.snapshot().protectedState());
        }
    }
    @Test public void aVisibleEmissionBeforeBothFlowsRecoverCannotRevokeTheActiveAnimation()throws Exception{
        Fixture f=new Fixture(true);f.inputs(0x00920000,false,false,false);f.emit(4);
        f.probe.recordDisable(0,0,0);f.probe.recordBase(f.base,new Object[]{true,false,new Flags(true),null});f.emit(0);
        assertEquals(CloudScene.RECENTS,f.snapshot().scene());assertFalse(f.snapshot().protectedState());
        f.probe.recordSeed(f.seed,new Object[]{null,true,false,new Flags(true),false,null});assertNull(f.snapshot().scene());
    }
    @Test public void privacyMiniAndOccupiedKeepNativePriorityEvenWhileAlreadyHidden()throws Exception{
        Fixture f=new Fixture(true);f.inputs(0x00920000,false,false,false);f.emit(4);
        f.inputs(0x00920000,false,true,false);assertTrue(f.snapshot().protectedState());
        f.inputs(0x00920000,false,false,true);assertTrue(f.snapshot().protectedState());
        f.probe.recordBase(f.base,new Object[]{true,true,new Flags(false),null});assertTrue(f.snapshot().protectedState());
    }
    @Test public void otherDisplayCollectorCannotChangeThisHost()throws Exception{
        Fixture f=new Fixture(true);f.inputs(0x00920000,false,false,false);f.collector.$displayId=1;f.emit(4);
        assertEquals(NativeVisibilityProbe.Snapshot.UNKNOWN,f.snapshot());
    }
    @Test public void legacyModelRemainsExactlyNativeWhileRecentsIsObserved()throws Throwable{
        Fixture f=new Fixture(false);f.probe.recordLegacyDisable(f.fragment,0,0x00920000,0);
        assertFalse(f.legacy(false,false,false,false).getShowSeedlingContainer());assertEquals(CloudScene.RECENTS,f.snapshot().scene());assertFalse(f.snapshot().protectedState());
    }
    @Test public void legacyForcedHiddenMiniPrivacyAndPrimaryAreNotOverridden()throws Throwable{
        for(int reason=0;reason<3;reason++){
            Fixture f=new Fixture(false);f.probe.recordLegacyDisable(f.fragment,0,0x00920000,0);
            f.legacy(false,reason==0,reason==1,reason==2);assertTrue(f.snapshot().protectedState());
        }
    }
    @Test public void nativeExceptionCleansLegacyScopeAndStillPropagatesExactlyOnce()throws Throwable{
        Fixture f=new Fixture(false);int[] calls={0};IllegalStateException original=new IllegalStateException("native failure");
        try{f.probe.withLegacyBase(f.fragment,()->{calls[0]++;throw original;});fail();}catch(IllegalStateException caught){assertSame(original,caught);}
        f.probe.observeLegacy(new BaseModel(),false,false,new Extra(false));assertEquals(NativeVisibilityProbe.Snapshot.UNKNOWN,f.snapshot());assertEquals(1,calls[0]);
    }
    public static class BaseOwner{int thisDisplayId;}
    public static class SeedOwner{Object homeStatusBarViewModel=new BaseOwner();}
    public static class BaseLambda{BaseOwner this$0=new BaseOwner();public Object invoke(Object a,Object b,Object c,Object d){return null;}}
    public static class SeedLambda{SeedOwner this$0=new SeedOwner();public Object invoke(Object a,Object b,Object c,Object d,Object e,Object f){return null;}}
    public static class Flags{boolean isClockAllowed,areNotificationIconsAllowed,isSystemInfoAllowed;Flags(boolean shown){isClockAllowed=areNotificationIconsAllowed=isSystemInfoAllowed=shown;}}
    public static class Queue{public void setDisabled(int d,int a,int b){}}
    public interface Continuation{}
    public static class Collector{View $seedlingContainer;int $displayId;public Object emit(Object value,Continuation c){return null;}}
    public static class Model{int value;Model(int value){this.value=value;}public int getVisibility(){return value;}}
    public static class Fragment{public void disable(int display,int a,int b,boolean animate){}public void updateStatusBarVisibilities(boolean animate){}}
    public static class Extension{View seedlingContainer;Fragment statusBarFragment;public boolean updateStatusBarVisibilities(BaseModel input,boolean animate){return false;}}
    public static class BaseModel{public BaseModel createHiddenModel(){return new BaseModel();}public boolean getShowClock(){return false;}public boolean getShowNotificationIcons(){return false;}public boolean getShowSystemInfo(){return false;}public boolean getShowPrimaryOngoingActivityChip(){return false;}}
    public static class Extra{boolean shown;Extra(boolean shown){this.shown=shown;}public boolean getShowSeedlingContainer(){return shown;}}
    public static class Companion{public Extra createModelFromFlags(BaseModel b,boolean a,boolean mini,boolean privacy){return new Extra(false);}}
}
