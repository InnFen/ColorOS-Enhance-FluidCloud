package dev.lsposed.fluidcloud;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class NativeCapsuleSignalsTest {
    @Test public void inheritedFrameworkMethodsNeverBecomeGlobalHooks() {
        Context context=org.robolectric.RuntimeEnvironment.getApplication();
        List<Method> hooked=new ArrayList<>();var signals=new NativeCapsuleSignals((m,l)->hooked.add(m));
        var root=new FrameLayout(context);root.addView(new android.widget.TextView(context));
        signals.discover(root);assertTrue(hooked.isEmpty());
    }
    @Test public void nativeOverridesAreDiscoveredOnceAcrossDescendantReplacement() {
        Context context=org.robolectric.RuntimeEnvironment.getApplication();
        List<Method> hooked=new ArrayList<>();List<Boolean> layouts=new ArrayList<>();
        var signals=new NativeCapsuleSignals((m,l)->{hooked.add(m);layouts.add(l);});
        var root=new FrameLayout(context);root.addView(new PluginView(context));
        signals.discover(root);assertEquals(2,hooked.size());
        assertTrue(layouts.containsAll(List.of(true,false)));
        assertTrue(hooked.stream().allMatch(m->m.getDeclaringClass()==PluginView.class));
        root.removeAllViews();root.addView(new PluginView(context));signals.discover(root);assertEquals(2,hooked.size());
    }
    public static class PluginView extends FrameLayout {
        public PluginView(Context context){super(context);}
        @Override public void requestLayout(){super.requestLayout();}
        @Override public void invalidateDrawable(Drawable drawable){super.invalidateDrawable(drawable);}
    }
}
