package dev.lsposed.fluidcloud;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36, manifest=Config.NONE)
public class CapsuleSizeAndOrderTest {
    @Test public void defaultsAndFeatureMasterLeaveBothFeaturesOff() {
        var defaults=CloudConfig.from(Map.of("enabled",true));
        assertTrue(defaults.capsuleSize().original());assertFalse(defaults.selectedCardFirst());
        var disabled=CloudConfig.from(Map.of("capsule_size_enabled",true,"capsule_width_percent",150,
                "capsule_height_percent",120,"selected_card_first",true));
        assertTrue(disabled.capsuleSize().original());assertFalse(disabled.selectedCardFirst());
    }
    @Test public void optionsSurviveOrientationAndWindowLayerCopies() {
        var config=CloudConfig.from(Map.of("enabled",true,"capsule_size_enabled",true,
                "capsule_width_percent",130,"capsule_height_percent",120,"selected_card_first",true));
        assertEquals(new CapsuleSize(true,130,120),config.forOrientation(true).capsuleSize());
        assertEquals(config.extras().size(),config.withCapsuleOnTop(true).capsuleSize());
        assertTrue(config.withCapsuleOnTop(true).selectedCardFirst());
        assertEquals(new CapsuleSize(true,50,200),new CapsuleSize(true,0,999));
    }
    @Test @Config(qualifiers="xxhdpi") public void resizingChangesNativeBoundsWithoutStretchingProviderContent() throws Exception {
        var api=api();var view=new Capsule(RuntimeEnvironment.getApplication());
        measure(api,view,new CapsuleSize(true,120,150),1000);
        assertEquals(240,view.getMeasuredWidth());assertEquals(60,view.getMeasuredHeight());
        view.layout(0,0,240,60);
        assertEquals(100,view.child.getWidth());assertEquals(40,view.child.getHeight());
        assertEquals(70,view.child.getLeft());assertEquals(10,view.child.getTop());
        assertEquals(1f,view.getScaleX(),0);assertEquals(1f,view.child.getScaleX(),0);
    }
    @Test public void resizingAlwaysStartsFromNativeMeasurementAndCanRestoreExactly() throws Exception {
        var api=api();var view=new Capsule(RuntimeEnvironment.getApplication());
        for(int i=0;i<8;i++){measure(api,view,new CapsuleSize(true,150,100),1000);assertEquals(300,view.getMeasuredWidth());}
        measure(api,view,CapsuleSize.OEM,1000);view.layout(0,0,200,40);
        assertEquals(200,view.getMeasuredWidth());assertEquals(40,view.getMeasuredHeight());
        assertEquals(0,view.child.getLeft());assertEquals(0,view.child.getTop());
        api.close();
    }
    @Test public void enlargementRespectsAvailableWidthAndStatusBarHeight() throws Exception {
        var api=api();var view=new Capsule(RuntimeEnvironment.getApplication());
        measure(api,view,new CapsuleSize(true,200,200),220);
        assertEquals(220,view.getMeasuredWidth());
        int id=view.getResources().getIdentifier("status_bar_height","dimen","android");
        int limit=id==0?40:Math.max(40,view.getResources().getDimensionPixelSize(id));
        assertTrue(view.getMeasuredHeight()<=limit);
    }
    @Test public void prioritizationDoesNotMutateNativeSourceAndPreservesOtherRelativeOrder() {
        List<String> original=List.of("stopwatch","media","delivery");
        assertEquals(List.of("media","stopwatch","delivery"),NativeCardOrder.prioritize(original,"media",x->x));
        assertEquals(List.of("stopwatch","media","delivery"),original);
        assertEquals(List.of("delivery","stopwatch","media"),NativeCardOrder.prioritize(original,"delivery",x->x));
    }
    @Test public void unknownBlankOrAmbiguousSelectionRetainsNativeOrder() {
        List<String> source=List.of("a","b","a");
        for(String key:new String[]{null,"","missing","a"})assertSame(source,NativeCardOrder.prioritize(source,key,x->x));
        List<String> unique=List.of("media","media:other");
        assertSame(unique,NativeCardOrder.prioritize(unique,"media",x->x));
        assertEquals(List.of("media:other","media"),NativeCardOrder.prioritize(unique,"media:other",x->x));
    }
    @Test public void launchKeepsVisibleOrderWhenNativeCapsuleHidesOrSwitches() {
        var selection=new NativeCardOrder.Selection();selection.change("media");
        assertTrue(selection.begin(true,"media"));
        selection.change(null);
        assertEquals("media",selection.key(true));
        selection.change("timer");
        assertEquals("media",selection.key(true));
        assertFalse(selection.begin(true,"timer"));
        assertEquals(List.of("media","timer"),NativeCardOrder.prioritize(List.of("timer","media"),selection.key(true),x->x));
        selection.end();
        assertEquals("timer",selection.key(true));
    }
    @Test public void launchFailureAndNextWindowCanReleaseWithoutLosingLatestSelection() {
        var selection=new NativeCardOrder.Selection();selection.change("media");
        assertFalse(selection.begin(false,"media"));assertFalse(selection.pinned());
        assertNull(selection.key(false));
        assertTrue(selection.begin(true,"media"));selection.change("timer");
        assertEquals("media",selection.key(false)); // Finish the visible transition even if disabled meanwhile.
        selection.end();assertNull(selection.key(false));
        assertEquals("timer",selection.key(true));
        assertTrue(selection.begin(true,"timer"));assertEquals("timer",selection.key(true));
    }
    @Test public void launchPinsLastPresentedPriorityEvenIfSelectionChangeWasQueuedBeforeTheIntent() {
        var selection=new NativeCardOrder.Selection();selection.change("media");
        // The native launch callback can update the capsule before BaseViewModel
        // is called; a pending adapter refresh has not changed visible holders.
        selection.change("timer");
        assertTrue(selection.begin(true,"media"));
        assertEquals("media",selection.key(true));
        selection.end();assertEquals("timer",selection.key(true));
    }
    private static NativeCapsuleSize api() throws Exception { return new NativeCapsuleSize(name->{
        if(name.equals(NativeCapsuleAppearance.VIEWS+"CapsuleView"))return Capsule.class;
        throw new ClassNotFoundException(name);
    }); }
    private static void measure(NativeCapsuleSize api,Capsule view,CapsuleSize options,int max) {
        int width=View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.AT_MOST);
        view.forceLayout();view.measure(width,View.MeasureSpec.makeMeasureSpec(40,View.MeasureSpec.EXACTLY));
        api.afterMeasure(view,width,options);
    }
    public static class Capsule extends FrameLayout {
        final View child;
        public Capsule(Context context) { super(context);child=new View(context);addView(child,new LayoutParams(100,40)); }
        @Override public void onMeasure(int w,int h) { super.onMeasure(w,h);setMeasuredDimension(200,40); }
    }
}
