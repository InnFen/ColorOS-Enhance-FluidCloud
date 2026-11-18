package dev.howard.colorosenhance.icons;

import android.app.Application;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class IconVisibilityTest {
    private final Map<String,Object> prefs=new HashMap<>();
    private final IconVisibility adapter=new IconVisibility(()->StatusIconConfig.from(prefs));
    @Test public void seamlessRenderingKeepsNativeVisibilityAndInterpolatesPresentationOnly() throws Exception {
        var context=RuntimeEnvironment.getApplication();var home=new LinearLayout(context);
        var left=new FakeIcon(context);var wifi=new FakeIcon(context);var right=new FakeIcon(context);
        home.addView(left);home.addView(wifi);home.addView(right);
        left.layout(0,0,20,20);wifi.layout(20,0,40,20);right.layout(40,0,60,20);
        adapter.group(home,"HOME");adapter.icon(wifi,"wifi",IconReflection.method(FakeIcon.class,"isIconVisible"),IconReflection.method(FakeIcon.class,"isIconBlocked"));
        prefs.putAll(Map.of("enabled",true,"shade_only.wifi",true));adapter.seamless(true);
        assertTrue(adapter.visible(wifi,"wifi",true));assertEquals(20,wifi.getWidth());
        var frame=adapter.begin(home);assertEquals(20,adapter.transform(home,left).dx(),0);assertEquals(0,adapter.transform(home,wifi).alpha(),0);adapter.end(frame);
        adapter.fraction(.5f);frame=adapter.begin(home);assertEquals(10,adapter.transform(home,left).dx(),0);assertEquals(.5f,adapter.transform(home,wifi).alpha(),0);adapter.end(frame);
        wifi.blocked=true;frame=adapter.begin(home);assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(home,left));assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(home,wifi));adapter.end(frame);
        wifi.blocked=false;wifi.state=2;frame=adapter.begin(home);
        assertEquals(10,adapter.transform(home,left).dx(),0);assertEquals(.5f,adapter.transform(home,wifi).alpha(),0);adapter.end(frame);
    }
    @Test public void clockAndBatteryNeverHaveTheirNativeVisibilityRewrittenInSeamlessMode() {
        var context=RuntimeEnvironment.getApplication();var row=new LinearLayout(context);View clock=new View(context),notification=new View(context);
        row.addView(clock);row.addView(notification);clock.layout(0,0,30,20);notification.layout(30,0,50,20);
        prefs.putAll(Map.of("enabled",true,"shade_only.clock",true));adapter.seamless(true);adapter.special(clock,"clock");
        assertEquals(View.VISIBLE,clock.getVisibility());var frame=adapter.begin(row);assertEquals(-30,adapter.transform(row,notification).dx(),0);adapter.end(frame);
        clock.setVisibility(View.GONE);frame=adapter.begin(row);assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,notification));adapter.end(frame);
        assertEquals(View.GONE,clock.getVisibility());
    }
    @Test public void systemHiddenCollectionDisablesBothBluetoothFilteringAndShadePresentation() throws Exception {
        var home=new LinearLayout(RuntimeEnvironment.getApplication());var wifi=new FakeIcon(home.getContext());home.addView(wifi);wifi.layout(0,0,20,20);
        Set<String> hidden=new HashSet<>(Set.of("bluetooth"));adapter.group(home,"HOME",new NativeIconPolicy(List.of(hidden),true));
        adapter.icon(wifi,"bluetooth",IconReflection.method(FakeIcon.class,"isIconVisible"),IconReflection.method(FakeIcon.class,"isIconBlocked"));
        prefs.putAll(Map.of("enabled",true,"bluetooth_mode",2,"shade_only.bluetooth",true));adapter.seamless(true);adapter.bluetooth(new StatusIconConfig.BluetoothState(true,false,false));
        assertTrue(adapter.visible(wifi,"bluetooth",true));var frame=adapter.begin(home);assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(home,wifi));adapter.end(frame);
        hidden.clear();assertFalse(adapter.visible(wifi,"bluetooth",true));
    }
    @Test public void bluetoothPolicyKeepsMaskingTheNativeFadeInHomeAndHeaderEvenWithoutShadeSelection()throws Exception{
        prefs.putAll(Map.of("enabled",true,"bluetooth_mode",2));adapter.seamless(true);
        adapter.bluetooth(new StatusIconConfig.BluetoothState(true,true,false));
        for(String location:List.of("HOME","QS")){
            var row=new LinearLayout(RuntimeEnvironment.getApplication());var bluetooth=new FakeIcon(row.getContext());
            row.addView(bluetooth);bluetooth.layout(0,0,20,20);bluetooth.setAlpha(.8f);
            adapter.group(row,location);adapter.icon(bluetooth,"bluetooth",IconReflection.method(FakeIcon.class,"isIconVisible"),IconReflection.method(FakeIcon.class,"isIconBlocked"));
            var frame=adapter.begin(row);try{assertEquals(0,adapter.transform(row,bluetooth).alpha(),0);}finally{adapter.end(frame);}
            assertEquals(.8f,bluetooth.getAlpha(),0);
            adapter.bluetooth(new StatusIconConfig.BluetoothState(true,true,true));frame=adapter.begin(row);
            try{assertEquals(1,adapter.transform(row,bluetooth).alpha(),0);}finally{adapter.end(frame);}
            adapter.bluetooth(new StatusIconConfig.BluetoothState(true,true,false));
        }
    }
    @Test public void directOemMirrorsUseSameClockFadeAndNeighborOffsetWithoutLeakingDrawScope() {
        var row=new LinearLayout(RuntimeEnvironment.getApplication());
        View clock=new View(row.getContext()),notification=new View(row.getContext());
        row.addView(clock);row.addView(notification);clock.layout(0,0,30,20);notification.layout(30,0,50,20);
        prefs.putAll(Map.of("enabled",true,"shade_only.clock",true));adapter.special(clock,"clock");adapter.seamless(true);
        adapter.fraction(.25f);
        assertEquals(.25f,adapter.mirrored(clock).alpha(),0);
        assertEquals(-22.5f,adapter.mirrored(notification).dx(),0);
        assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,notification));
        adapter.fraction(1);assertEquals(IconDrawPlan.Transform.NONE,adapter.mirrored(notification));
        assertEquals(IconDrawPlan.Transform.NONE,adapter.mirrored(null));
    }
    @Test public void gestureProgressInvalidatesNativeMirrorsWithoutDependingOnSourceWindowDrawing() {
        var mirror=new InvalidatedView(RuntimeEnvironment.getApplication());
        prefs.putAll(Map.of("enabled",true,"shade_only.wifi",true));adapter.mirror(mirror,true);
        int initial=mirror.invalidations;adapter.fraction(.25f);assertEquals(initial+1,mirror.invalidations);
        adapter.fraction(.25f);assertEquals(initial+1,mirror.invalidations);
        adapter.fraction(.5f);assertEquals(initial+2,mirror.invalidations);
        adapter.mirror(mirror,false);adapter.fraction(1);assertEquals(initial+2,mirror.invalidations);
    }
    static class InvalidatedView extends View {
        int invalidations;
        InvalidatedView(android.content.Context context){super(context);}
        @Override public void invalidate(){invalidations++;super.invalidate();}
    }
    @Test public void homeAndHeaderApplySamePresentationDuringTheirNativeCrossfade() throws Exception {
        var home=new LinearLayout(RuntimeEnvironment.getApplication());var header=new LinearLayout(home.getContext());
        prefs.putAll(Map.of("enabled",true,"shade_only.wifi",true));adapter.seamless(true);adapter.fraction(.5f);
        for(var row:List.of(home,header)) {
            var mobile=new FakeIcon(row.getContext());var wifi=new FakeIcon(row.getContext());row.addView(mobile);row.addView(wifi);
            mobile.layout(0,0,20,20);wifi.layout(20,0,40,20);
            adapter.group(row,row==home?"HOME":"QS");adapter.icon(wifi,"wifi",IconReflection.method(FakeIcon.class,"isIconVisible"),IconReflection.method(FakeIcon.class,"isIconBlocked"));
            var frame=adapter.begin(row);assertEquals(10,adapter.transform(row,mobile).dx(),0);assertEquals(.5f,adapter.transform(row,wifi).alpha(),0);adapter.end(frame);
        }
    }
    @Test public void panelBatteryDrawRegistrationNeverOverridesItsNativeVisibilityInFallback() {
        var row=new LinearLayout(RuntimeEnvironment.getApplication());var battery=new View(row.getContext());var icons=new View(row.getContext());row.addView(icons);row.addView(battery);
        prefs.putAll(Map.of("enabled",true,"shade_only.battery",true));adapter.panelBattery(icons,battery);adapter.refresh();
        assertEquals(View.VISIBLE,battery.getVisibility());assertEquals(View.INVISIBLE,adapter.visibility(battery,View.INVISIBLE));
    }
    @Test public void independentPanelBatteryInNonLinearWrappersSharesTheIconTransition() {
        var context=RuntimeEnvironment.getApplication();var row=new FrameLayout(context);
        var icons=new LinearLayout(context);var batteryWrapper=new FrameLayout(context);var battery=new View(context);
        row.addView(icons);row.addView(batteryWrapper);batteryWrapper.addView(battery);
        icons.layout(0,0,100,20);batteryWrapper.layout(100,0,130,20);battery.layout(0,0,30,20);
        prefs.putAll(Map.of("enabled",true,"shade_only.battery",true));adapter.seamless(true);adapter.panelBattery(icons,battery);
        adapter.fraction(.5f);var frame=adapter.begin(row);
        assertEquals(15,adapter.transform(row,icons).dx(),0);assertEquals(.5f,adapter.transform(row,batteryWrapper).alpha(),0);adapter.end(frame);
        adapter.fraction(1);frame=adapter.begin(row);assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,icons));adapter.end(frame);
        assertEquals(View.VISIBLE,battery.getVisibility());
    }
    @Test public void selectedClockFadesNativeHeaderWithoutCompactingOrClippingItsDigits() {
        var row=new FrameLayout(RuntimeEnvironment.getApplication());
        var clock=new View(row.getContext());var mirror=new FrameLayout(row.getContext());var date=new View(row.getContext());
        row.addView(clock);row.addView(mirror);row.addView(date);
        adapter.panelClock(clock);adapter.seamless(true);
        prefs.putAll(Map.of("enabled",true,"shade_only.clock",true));
        for(float fraction:new float[]{0,.2f,.6f,1,.6f,.2f,0}) {
            adapter.fraction(fraction);
            assertEquals(fraction,adapter.transform(row,clock).alpha(),0);
            assertEquals(0,adapter.transform(row,clock).dx(),0);
            assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,mirror));
            assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,date));
        }
        assertFalse(adapter.reveal(clock));assertTrue(adapter.reveal(date));
        prefs.put("shade_only.clock",false);
        assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,mirror));
        assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,clock));
        prefs.put("shade_only.clock",true);prefs.put("enabled",false);
        assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,mirror));
    }
    @Test public void nativeHomeClockMirrorUsesFadeWithoutCuttingDifferentDigitsDuringScaling() {
        var row=new FrameLayout(RuntimeEnvironment.getApplication());var clock=new View(row.getContext());
        adapter.panelClock(null);adapter.seamless(true);prefs.putAll(Map.of("enabled",true,"shade_only.clock",true));
        adapter.fraction(.5f);assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,clock));
        var sourceRow=new LinearLayout(row.getContext());sourceRow.addView(clock);clock.layout(0,0,100,25);
        adapter.special(clock,"clock");assertEquals(.5f,adapter.mirrored(clock).alpha(),0);assertFalse(adapter.reveal(clock));
    }
    @Test public void clockProgressInvalidatesTheParentThatRecordsItsAdditionalAlphaLayer() {
        var parent=new InvalidatedGroup(RuntimeEnvironment.getApplication());var clock=new View(parent.getContext());parent.addView(clock);
        adapter.panelClock(clock);adapter.seamless(true);prefs.putAll(Map.of("enabled",true,"shade_only.clock",true));
        int before=parent.invalidations;adapter.fraction(.3f);assertTrue(parent.invalidations>before);
        before=parent.invalidations;adapter.fraction(1);assertTrue(parent.invalidations>before);
        before=parent.invalidations;adapter.refresh();assertTrue(parent.invalidations>before);
    }
    static class InvalidatedGroup extends FrameLayout {
        int invalidations;
        InvalidatedGroup(android.content.Context context){super(context);}
        @Override public void invalidate(){invalidations++;super.invalidate();}
    }
    public static class FakeIcon extends View {
        boolean blocked;int state;
        FakeIcon(android.content.Context context){super(context);}
        public boolean isIconVisible(){return true;}
        public boolean isIconBlocked(){return blocked;}
        public int getVisibleState(){return state;}
    }
    @Test public void separateHomeAndHeaderInstancesRetainTheirNativeState() {
        prefs.putAll(Map.of("enabled",true,"shade_only.wifi",true,"keyguard_follow",false));
        var context=RuntimeEnvironment.getApplication();
        var home=new LinearLayout(context);var header=new LinearLayout(context);var lock=new LinearLayout(context);
        View h=new View(context),q=new View(context),k=new View(context);home.addView(h);header.addView(q);lock.addView(k);
        adapter.group(home,"HOME");adapter.group(header,"QS");adapter.group(lock,"KEYGUARD");
        assertFalse(adapter.visible(h,"wifi",true));assertTrue(adapter.visible(q,"wifi",true));assertTrue(adapter.visible(k,"wifi",true));
        assertFalse(adapter.visible(q,"wifi",false)); // A missing native icon is not resurrected.
        prefs.put("enabled",false);adapter.refresh();assertTrue(adapter.visible(h,"wifi",true));
    }
    @Test public void clockRestoresLatestNativeVisibilityAndHeaderIsNeverRegisteredImplicitly() {
        var context=RuntimeEnvironment.getApplication();View clock=new View(context),header=new View(context);
        adapter.special(clock,"clock");prefs.putAll(Map.of("enabled",true,"shade_only.clock",true));adapter.refresh();
        assertEquals(View.GONE,clock.getVisibility());assertEquals(View.VISIBLE,header.getVisibility());
        clock.setVisibility(adapter.visibility(clock,View.INVISIBLE));
        prefs.put("enabled",false);adapter.refresh();assertEquals(View.INVISIBLE,clock.getVisibility());
        assertEquals(View.GONE,adapter.visibility(header,View.GONE));
    }
    @Test public void repeatedConfigurationRefreshDoesNotForgetOriginalBatteryVisibility() {
        View battery=new View(RuntimeEnvironment.getApplication());adapter.special(battery,"battery");
        prefs.putAll(Map.of("enabled",true,"shade_only.battery",true));adapter.refresh();adapter.refresh();adapter.refresh();
        assertEquals(View.GONE,battery.getVisibility());prefs.put("shade_only.battery",false);adapter.refresh();
        assertEquals(View.VISIBLE,battery.getVisibility());
        battery.setVisibility(View.GONE);adapter.refresh();assertEquals(View.GONE,battery.getVisibility());
    }
}
