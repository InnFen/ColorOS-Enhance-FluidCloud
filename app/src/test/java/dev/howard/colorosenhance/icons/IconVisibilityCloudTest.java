package dev.howard.colorosenhance.icons;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class IconVisibilityCloudTest {
    private final Map<String,Object> prefs=new HashMap<>(Map.of("enabled",true,"shade_only.network_speed",true));
    private final IconVisibility adapter=new IconVisibility(()->StatusIconConfig.from(prefs));

    @Test public void capsuleWidthChangesNeverUnmaskAnIconWhileItsNativeHideAnimationIsRunning() throws Exception {
        var row=new LinearLayout(RuntimeEnvironment.getApplication());
        var left=new View(row.getContext());var speed=new NativeIcon(row.getContext());var right=new View(row.getContext());
        row.addView(left);row.addView(speed);row.addView(right);
        left.layout(0,0,20,20);speed.layout(20,0,48,20);right.layout(48,0,68,20);
        adapter.group(row,"HOME");register(speed);adapter.seamless(true);
        // ColorOS updates visibleState to 2 before its 333 ms disappearance animation finishes.
        // Moving across the capsule-width threshold can repeat or reverse this transition.
        for(int state:new int[]{0,2,0,2,2,0}) {
            speed.state=state;speed.setAlpha(.65f);
            var frame=adapter.begin(row);
            assertEquals("native state="+state,0,adapter.transform(row,speed).alpha(),0);
            assertEquals(28,adapter.transform(row,left).dx(),0);
            assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,right));adapter.end(frame);
            assertEquals(View.VISIBLE,speed.getVisibility());assertEquals(.65f,speed.getAlpha(),0);assertEquals(state,speed.state);
        }
    }
    @Test public void closingTheShadeKeepsItsFadeEvenIfCapsuleOcclusionChangesAtTheSameTime() throws Exception {
        for(String location:List.of("HOME","QS")) {
            var row=new LinearLayout(RuntimeEnvironment.getApplication());var speed=new NativeIcon(row.getContext());
            row.addView(speed);speed.layout(0,0,28,20);adapter.group(row,location);register(speed);adapter.seamless(true);
            for(float fraction:new float[]{1,.8f,.4f,.1f,0}) {
                adapter.fraction(fraction);speed.state=2;
                var frame=adapter.begin(row);
                assertEquals(location+" fraction="+fraction,fraction,adapter.transform(row,speed).alpha(),0);adapter.end(frame);
            }
        }
    }
    @Test public void systemHiddenOrUnselectedIconsRetainTheirNativeOcclusionAnimation() throws Exception {
        var row=new LinearLayout(RuntimeEnvironment.getApplication());var speed=new NativeIcon(row.getContext());row.addView(speed);speed.layout(0,0,28,20);
        Set<String> hidden=new HashSet<>(Set.of("network_speed"));adapter.group(row,"HOME",new NativeIconPolicy(List.of(hidden),true));
        register(speed);adapter.seamless(true);speed.state=2;
        assertNative(row,speed);hidden.clear();speed.blocked=true;assertNative(row,speed);
        speed.blocked=false;prefs.put("shade_only.network_speed",false);assertNative(row,speed);
        prefs.put("shade_only.network_speed",true);prefs.put("enabled",false);assertNative(row,speed);
    }
    private void register(NativeIcon icon) throws Exception {
        adapter.icon(icon,"network_speed",IconReflection.method(NativeIcon.class,"isIconVisible"),IconReflection.method(NativeIcon.class,"isIconBlocked"));
    }
    private void assertNative(LinearLayout row,View icon) {
        var frame=adapter.begin(row);assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,icon));adapter.end(frame);
    }
    public static class NativeIcon extends View {
        int state;boolean blocked;
        NativeIcon(Context context){super(context);}
        public boolean isIconVisible(){return true;}
        public boolean isIconBlocked(){return blocked;}
        public int getVisibleState(){return state;}
    }
}
