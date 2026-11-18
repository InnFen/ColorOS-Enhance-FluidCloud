package dev.howard.colorosenhance.icons;

import android.app.Activity;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class HomeIconOrderTest {
    @Test public void crossSideLayoutPreservesNativeHolderIndicesAndParentsThenRestoresOwnedChanges()throws Exception{
        try(var f=new Fixture()){
            var order=new IconOrder(List.of("wifi","clock"),List.of("battery","mobile"));
            f.prefs.putAll(Map.of("enabled",true,"order_enabled",true,IconOrder.KEY,order.encode()));f.adapter.refresh();
            assertSame(f.wifi,f.icons.getChildAt(0));assertSame(f.mobile,f.icons.getChildAt(1));assertSame(f.icons,f.wifi.getParent());
            assertEquals(20-600,f.adapter.target(f.wifi).x(),0);assertTrue(f.adapter.target(f.wifi).visible());
            assertEquals(30,f.clock.getTranslationX(),0);assertFalse(f.icons.getClipChildren());
            f.prefs.put("order_enabled",false);f.adapter.refresh();
            assertNull(f.adapter.target(f.wifi));assertEquals(0,f.clock.getTranslationX(),0);assertTrue(f.icons.getClipChildren());
            assertTrue(f.icons.updates>0);assertTrue(f.failures.isEmpty());
        }
    }
    @Test public void nativeBlockedOrUnavailableIconsAreNeverRevivedAndShadeOffsetsUseChosenSide()throws Exception{
        try(var f=new Fixture()){
            var order=new IconOrder(List.of("wifi","clock"),List.of("battery","mobile"));
            f.prefs.putAll(Map.of("enabled",true,"order_enabled",true,IconOrder.KEY,order.encode(),"shade_only.wifi",true));f.adapter.refresh();
            // Clock's native collision and seamless code must see its actual compact position.
            assertEquals(0,f.clock.getTranslationX(),0);
            assertEquals(0,f.adapter.offset(f.clock,0),0);assertEquals(0,f.adapter.offset(f.clock,.5f),0);assertEquals(0,f.adapter.offset(f.clock,1),0);
            f.wifi.blocked=true;f.adapter.refresh();assertNull(f.adapter.target(f.wifi));assertEquals(0,f.clock.getTranslationX(),0);
            f.wifi.blocked=false;f.wifi.available=false;f.adapter.refresh();assertNull(f.adapter.target(f.wifi));
        }
    }
    @Test public void nativeSourcePositionsMatchWhatIsDrawnWhileTheCombinedMirrorGraduallyRestoresItsSpacing()throws Exception{
        try(var f=new Fixture()){
            var order=new IconOrder(List.of("clock"),List.of("wifi","mobile","battery"));
            f.prefs.putAll(Map.of("enabled",true,"order_enabled",true,IconOrder.KEY,order.encode(),"shade_only.mobile",true));f.adapter.refresh();
            float closed=f.adapter.target(f.wifi).x();
            assertEquals(0,f.adapter.offset(f.wifi,0),0);assertEquals(-20,f.adapter.offset(f.wifi,.5f),0);assertEquals(-40,f.adapter.offset(f.wifi,1),0);
            assertEquals(900-600,closed,0);
        }
    }
    @Test public void pluginResourcesCannotReplaceClockOrNotificationSourcesWithCollidingIds()throws Exception{
        try(var f=new Fixture()){
            FrameLayout plugin=new FrameLayout(f.activity.get());TextView impostor=new TextView(f.activity.get());impostor.setTag("clock");plugin.addView(impostor);f.root.addView(plugin,0);
            impostor.layout(0,0,200,40);
            f.prefs.putAll(Map.of("enabled",true,"order_enabled",true,IconOrder.KEY,new IconOrder(List.of("wifi","clock"),List.of("mobile","battery")).encode()));f.adapter.refresh();
            assertEquals(30,f.clock.getTranslationX(),0);assertEquals(0,impostor.getTranslationX(),0);
        }
    }
    @Test public void unknownNativePolicyKeepsAllOriginalGeometryAndDoesNotRelaxClipping()throws Exception{
        try(var f=new Fixture()){
            f.prefs.putAll(Map.of("enabled",true,"order_enabled",true,IconOrder.KEY,new IconOrder(List.of("battery"),List.of("clock","wifi","mobile")).encode()));
            f.adapter.group(f.icons,new NativeIconPolicy(List.of(),false));f.adapter.refresh();
            assertNull(f.adapter.target(f.wifi));assertEquals(0,f.clock.getTranslationX(),0);assertEquals(0,f.battery.getTranslationX(),0);
            assertTrue(f.icons.getClipChildren());assertTrue(f.failures.isEmpty());
        }
    }
    @Test public void dynamicNativeSlotsAreDiscoveredAndRemovedWithoutRestoringUnchangedSources()throws Exception{
        try(var f=new Fixture()){
            f.prefs.putAll(Map.of("enabled",true,"order_enabled",true,IconOrder.KEY,new IconOrder(List.of("mute","clock"),List.of("wifi","mobile","battery")).encode()));
            f.adapter.refresh();float original=f.clock.getTranslationX();
            var volume=new NativeIcon(f.activity.get(),"volume");f.icons.addView(volume,0);volume.layout(0,0,24,40);
            f.root.getViewTreeObserver().dispatchOnGlobalLayout();f.root.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(20-600,f.adapter.target(volume).x(),0);assertEquals(original+24,f.clock.getTranslationX(),0);
            assertSame(f.icons,f.wifi.getParent());assertTrue(f.failures.isEmpty());
            f.icons.removeView(volume);f.root.getViewTreeObserver().dispatchOnGlobalLayout();f.root.getViewTreeObserver().dispatchOnPreDraw();
            assertNull(f.adapter.target(volume));assertEquals(original,f.clock.getTranslationX(),0);
        }
    }
    @Test public void nativeAnimationCannotMoveTheOrderedPresentationAwayFromItsTarget()throws Exception{
        try(var f=new Fixture()){
            f.prefs.putAll(Map.of("enabled",true,"order_enabled",true,IconOrder.KEY,new IconOrder(List.of("wifi","clock"),List.of("mobile","battery")).encode()));
            f.adapter.refresh();
            for(float nativeX:new float[]{0,-80,-200,-580}){
                f.wifi.setX(nativeX);
                assertEquals(20,600+f.wifi.getX()+f.adapter.offset(f.wifi,0),.001f);
                f.root.getViewTreeObserver().dispatchOnPreDraw();
            }
        }
    }
    @Test public void orderingSwitchHandsBackNativeGeometryWithoutADeferredAnimation()throws Exception{
        try(var f=new Fixture()){
            f.prefs.putAll(Map.of("enabled",true,"order_enabled",true,"shade_only.mobile",true,IconOrder.KEY,new IconOrder(List.of("wifi","clock"),List.of("mobile","battery")).encode()));
            f.adapter.refresh();assertFalse(f.icons.animationPasses.getLast());assertTrue(f.icons.mIsEnableAnimation);
            assertEquals(20-600,f.wifi.getX(),0);
            f.prefs.put("order_enabled",false);f.adapter.refresh();
            assertEquals(0,f.wifi.getX(),0);assertEquals(30,f.mobile.getX(),0);
            assertFalse(f.icons.animationPasses.getLast());assertTrue(f.icons.mIsEnableAnimation);
            assertNull(f.adapter.offset(f.wifi,0));
        }
    }
    @Test public void hiddenBatteryWrapperCannotCompactAnAlreadyOrderedIconContainerAgain()throws Exception{
        try(var f=new Fixture()){
            var visibility=new IconVisibility(()->StatusIconConfig.from(f.prefs));visibility.ordering(f.adapter);
            visibility.special(f.battery,"battery");visibility.seamless(true);
            f.prefs.putAll(Map.of("enabled",true,"order_enabled",true,"shade_only.battery",true,
                    IconOrder.KEY,new IconOrder(List.of("clock"),List.of("wifi","mobile","battery")).encode()));
            f.adapter.refresh();var frame=visibility.begin(f.rightRow);
            try{assertEquals(0,visibility.transform(f.rightRow,f.icons).dx(),0);assertEquals(0,visibility.transform(f.rightRow,f.battery).alpha(),0);}
            finally{visibility.end(frame);}
        }
    }
    private static final class Fixture implements AutoCloseable {
        final org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup().visible();
        final FrameLayout root=new FrameLayout(activity.get()),contents=new FrameLayout(activity.get()),left=new FrameLayout(activity.get()),right=new FrameLayout(activity.get());
        final LinearLayout leftRow=new LinearLayout(activity.get()),rightRow=new LinearLayout(activity.get());
        final TextView clock=new TextView(activity.get()),battery=new TextView(activity.get());
        final NativeGroup icons=new NativeGroup(activity.get());
        final NativeIcon wifi=new NativeIcon(activity.get(),"wifi"),mobile=new NativeIcon(activity.get(),"mobile");
        final Map<String,Object> prefs=new HashMap<>();final List<Throwable> failures=new ArrayList<>();final HomeIconOrder adapter;
        Fixture()throws Exception{
            left.setTag("status_bar_start_side_container");right.setTag("status_bar_end_side_container");clock.setTag("clock");battery.setTag("battery");
            contents.setTag("status_bar_contents");root.addView(contents);contents.addView(left);contents.addView(right);left.addView(leftRow);leftRow.addView(clock);right.addView(rightRow);rightRow.addView(icons);rightRow.addView(battery);
            icons.addView(wifi);icons.addView(mobile);activity.get().setContentView(root);
            root.layout(0,0,1000,80);contents.layout(0,0,1000,80);left.layout(20,0,450,80);right.layout(550,0,980,80);
            leftRow.layout(0,0,100,80);rightRow.layout(50,0,430,80);clock.layout(0,0,70,40);icons.layout(0,0,300,80);battery.layout(300,0,350,40);
            wifi.layout(0,0,30,40);mobile.layout(0,0,40,40);
            adapter=new HomeIconOrder(()->StatusIconConfig.from(prefs),NativeGroup.class.getMethod("updateStates"),failures::add,(view,name)->view.findViewWithTag(name));
            icons.order=adapter;
            adapter.root(root);adapter.group(icons,new NativeIconPolicy(List.of(),true));
        }
        public void close(){prefs.clear();adapter.refresh();activity.pause().stop().destroy();}
    }
    public static final class NativeGroup extends LinearLayout {
        int updates;public boolean mIsEnableAnimation=true;HomeIconOrder order;final List<Boolean> animationPasses=new ArrayList<>();
        NativeGroup(android.content.Context context){super(context);}
        public void updateStates(){
            updates++;animationPasses.add(mIsEnableAnimation);float cursor=0;
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);var target=order==null?null:order.target(child);
                child.setX(target!=null&&target.visible()?target.x():cursor);
                cursor+=child.getWidth()+child.getPaddingStart()+child.getPaddingEnd();
            }
        }
    }
    public static final class NativeIcon extends View {
        final String slot;boolean available=true,blocked;
        NativeIcon(android.content.Context context,String slot){super(context);this.slot=slot;}
        public String getSlot(){return slot;}public boolean isIconVisible(){return available;}public boolean isIconBlocked(){return blocked;}
    }
}
