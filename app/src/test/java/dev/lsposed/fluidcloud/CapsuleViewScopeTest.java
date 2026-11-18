package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class CapsuleViewScopeTest {
    @Test public void pixelInvalidationIsLimitedToTheTrackedNativeSource() {
        Context context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new FrameLayout(context);var parent=new FrameLayout(context);var host=new FrameLayout(context);
        var inner=new FrameLayout(context);var leaf=new View(context);
        root.addView(parent);parent.addView(host);host.addView(inner);inner.addView(leaf);
        var scope=new CapsuleViewScope(host);scope.refresh(null);
        assertTrue(scope.nativeSource(leaf));assertTrue(scope.nativeSource(inner));assertTrue(scope.nativeSource(host));
        assertFalse(scope.nativeSource(parent));assertFalse(scope.nativeSource(root));
        // Another control-center child shares the root, but cannot wake the capsule.
        var unrelated=new View(context);root.addView(unrelated);
        for(int i=0;i<10000;i++)assertFalse(scope.nativeSource(unrelated));
        assertFalse(scope.layout(unrelated,false));assertFalse(scope.sourceProperty(unrelated));
    }
    @Test public void nativeAnimationInvalidationSurvivesReplayButAncestorFeedbackDoesNot() {
        Context context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new FrameLayout(context);var parent=new FrameLayout(context);var host=new FrameLayout(context);
        root.addView(parent);parent.addView(host);var scope=new CapsuleViewScope(host);scope.refresh(null);
        assertTrue(scope.nativeSource(host));assertFalse(scope.nativeSource(parent));
        assertTrue(scope.layout(host,true));assertFalse(scope.layout(parent,true));assertTrue(scope.layout(parent,false));
    }
    @Test public void fakeRendererPropertiesAreScopedAndReplacementRefreshDropsOldAncestors() {
        Context context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new FrameLayout(context);var host=new FrameLayout(context);var shade=new FrameLayout(context);
        root.addView(host);root.addView(shade);var fake=new FakePluginFrame(context);shade.addView(fake);
        var scope=new CapsuleViewScope(host);scope.refresh(shade);
        assertTrue(scope.shadeProperty(fake));assertTrue(scope.shadeProperty(shade));assertTrue(scope.shadeProperty(root));
        var contents=new View(context);fake.addView(contents);assertFalse(scope.shadeProperty(contents));
        var replacement=new FrameLayout(context);var newRoot=new FrameLayout(context);
        root.removeView(host);newRoot.addView(host);newRoot.addView(replacement);scope.refresh(replacement);
        assertFalse(scope.shadeProperty(fake));assertFalse(scope.sourceProperty(root));
        assertTrue(scope.sourceProperty(newRoot));assertSame(replacement,scope.shade());
        scope.refresh(null);assertFalse(scope.shadeProperty(new FakePluginFrame(context)));
    }
    @Test public void openingCardMustRetainSourceAlphaAndLayoutSignalsBeforeTheyReachTheOuterHost() {
        Context context=Robolectric.buildActivity(Activity.class).setup().get();
        var host=new FrameLayout(context);var container=new FrameLayout(context);var capsule=new View(context);
        host.addView(container);container.addView(capsule);var scope=new CapsuleViewScope(host);scope.refresh(null);
        assertTrue(scope.sourceProperty(capsule));assertTrue(scope.nativeSource(capsule));
        assertTrue(scope.layout(capsule,true));
        // A clipped / animated parent need not forward a pixel event to the outer host.
        container.removeView(capsule);scope.refreshSources();assertFalse(scope.sourceProperty(capsule));
        var replacement=new View(context);container.addView(replacement);scope.refreshSources();
        assertTrue(scope.nativeSource(replacement));
    }
    public static class FakePluginFrame extends FrameLayout { public FakePluginFrame(Context c){super(c);} }
}
