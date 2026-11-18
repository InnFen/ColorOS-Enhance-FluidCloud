package dev.howard.colorosenhance.icons;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class IconVisibilityKeyguardTest {
    private final Map<String,Object> prefs=new HashMap<>(Map.of("enabled",true,"shade_only.network_speed",true));
    private final IconVisibility adapter=new IconVisibility(()->StatusIconConfig.from(prefs));
    private final Context context=RuntimeEnvironment.getApplication();
    private final LinearLayout row=new LinearLayout(context);
    private final NativeIcon speed=new NativeIcon(context),wifi=new NativeIcon(context);
    private void setup(NativeIconPolicy policy) throws Exception {
        row.addView(speed);row.addView(wifi);speed.layout(0,0,28,20);wifi.layout(28,0,48,20);
        adapter.group(row,"KEYGUARD",policy);register(speed,"network_speed");register(wifi,"wifi");adapter.seamless(true);
    }
    private void register(NativeIcon view,String slot) throws Exception {
        adapter.icon(view,slot,IconReflection.method(NativeIcon.class,"isIconVisible"),IconReflection.method(NativeIcon.class,"isIconBlocked"));
    }
    private IconDrawPlan.Transform draw(View child) {
        var frame=adapter.begin(row);try{return adapter.transform(row,child);}finally{adapter.end(frame);}
    }
    @Test public void defaultOnLockscreenSelectionFollowsPanelProgressAndCanRestoreStockImmediately() throws Exception {
        setup(new NativeIconPolicy(List.of(),true));
        for(float progress:new float[]{0,.4f,1,.4f,0}) {adapter.keyguardFraction(progress);assertEquals(progress,draw(speed).alpha(),0);}
        speed.state=2;assertEquals(0,draw(speed).alpha(),0); // Includes the native disappearance animation.
        prefs.put("keyguard_follow",false);assertEquals(IconDrawPlan.Transform.NONE,draw(speed));
        assertEquals(View.VISIBLE,speed.getVisibility());assertEquals(2,speed.state);
    }
    @Test public void systemHiddenIconRemainsHiddenAfterPullDownAndUsesTheLiveUserList() throws Exception {
        Set<String> hidden=new HashSet<>(Set.of("wifi"));setup(new NativeIconPolicy(List.of(hidden),true));
        adapter.keyguardFraction(1);assertEquals(0,draw(wifi).alpha(),0);assertEquals(20,draw(speed).dx(),0);
        hidden.clear();assertEquals(IconDrawPlan.Transform.NONE,draw(wifi));
        assertEquals(View.VISIBLE,wifi.getVisibility());
    }
    @Test public void bluetoothConditionsRemainPermanentAndDoNotAffectKeyguardWhenOptedOut() throws Exception {
        setup(new NativeIconPolicy(List.of(),true));register(wifi,"bluetooth");prefs.put("bluetooth_mode",2);
        adapter.bluetooth(new StatusIconConfig.BluetoothState(true,true,false));adapter.keyguardFraction(1);
        assertEquals(0,draw(wifi).alpha(),0);assertTrue(adapter.visible(wifi,"bluetooth",true));
        prefs.put("keyguard_follow",false);assertEquals(IconDrawPlan.Transform.NONE,draw(wifi));
        prefs.put("keyguard_follow",true);adapter.bluetooth(new StatusIconConfig.BluetoothState(true,true,true));
        assertEquals(IconDrawPlan.Transform.NONE,draw(wifi));
        adapter.bluetooth(StatusIconConfig.BluetoothState.unknown());assertEquals(IconDrawPlan.Transform.NONE,draw(wifi));
    }
    @Test public void unknownAndNonUserNativeExclusionsDoNotGrantExtraLockscreenOwnership() throws Exception {
        setup(new NativeIconPolicy(List.of(Set.of(),Set.of("network_speed")),true));
        assertEquals(IconDrawPlan.Transform.NONE,draw(speed));
        adapter.group(row,"KEYGUARD",new NativeIconPolicy(List.of(),false));assertEquals(IconDrawPlan.Transform.NONE,draw(speed));
    }
    @Test public void specialTopClockIsScopedToKeyguardAndDoesNotTouchTheLargeClock() {
        var top=new LinearLayout(context);View clock=new View(context),carrier=new View(context),largeClock=new View(context);
        top.addView(clock);top.addView(carrier);clock.layout(0,0,30,20);carrier.layout(30,0,80,20);
        prefs.put("shade_only.clock",true);adapter.special(clock,"clock","KEYGUARD");adapter.seamless(true);
        var frame=adapter.begin(top);assertEquals(0,adapter.transform(top,clock).alpha(),0);adapter.end(frame);
        assertEquals(IconDrawPlan.Transform.NONE,adapter.mirrored(largeClock));assertEquals(View.VISIBLE,largeClock.getVisibility());
        prefs.put("keyguard_follow",false);frame=adapter.begin(top);assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(top,clock));adapter.end(frame);
    }
    @Test public void separatelyWrappedLockscreenBatteryRespectsOptOutAndUsesTheSameProgress() {
        var parent=new FrameLayout(context);var icons=new LinearLayout(context);var wrapper=new FrameLayout(context);var battery=new View(context);
        parent.addView(icons);parent.addView(wrapper);wrapper.addView(battery);
        icons.layout(0,0,100,20);wrapper.layout(100,0,130,20);battery.layout(0,0,30,20);
        prefs.put("shade_only.battery",true);adapter.panelBattery(icons,battery,"KEYGUARD");adapter.seamless(true);adapter.keyguardFraction(.5f);
        var frame=adapter.begin(parent);assertEquals(.5f,adapter.transform(parent,wrapper).alpha(),0);adapter.end(frame);
        prefs.put("keyguard_follow",false);frame=adapter.begin(parent);assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(parent,wrapper));adapter.end(frame);
        assertEquals(View.VISIBLE,battery.getVisibility());
    }
    @Test public void masterOffAndFallbackRetainLatestNativeVisibility() throws Exception {
        setup(new NativeIconPolicy(List.of(Set.of("wifi")),true));adapter.seamless(false);
        assertFalse(adapter.visible(wifi,"wifi",true));assertFalse(adapter.visible(speed,"network_speed",true));
        prefs.put("keyguard_follow",false);assertTrue(adapter.visible(wifi,"wifi",true));assertTrue(adapter.visible(speed,"network_speed",true));
        prefs.put("keyguard_follow",true);prefs.put("enabled",false);assertTrue(adapter.visible(wifi,"wifi",true));
        assertEquals(IconDrawPlan.Transform.NONE,draw(speed));
    }
    @Test public void fullHeightLockscreenWindowIsNotTreatedAsAnOpenedNotificationCenter() throws Exception {
        setup(new NativeIconPolicy(List.of(),true));adapter.fraction(1);
        assertEquals(0,draw(speed).alpha(),0);
        adapter.keyguardFraction(.6f);assertEquals(.6f,draw(speed).alpha(),0);
        adapter.fraction(0);assertEquals(.6f,draw(speed).alpha(),0);
        adapter.keyguardFraction(0);assertEquals(0,draw(speed).alpha(),0);
    }
    public static class NativeIcon extends View {
        int state;
        NativeIcon(Context context){super(context);}
        public boolean isIconVisible(){return true;}
        public boolean isIconBlocked(){return false;}
        public int getVisibleState(){return state;}
    }
}
