package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, manifest = Config.NONE)
public class NativeShadeStateTest {
    ActivityController<Activity> activity;
    FrameLayout root, container;
    FakePluginFrame frame;
    NativeShadeState state;
    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        root = new FrameLayout(activity.get());
        activity.get().setContentView(root);
        container = new FrameLayout(activity.get());
        frame = new FakePluginFrame(activity.get());
        root.addView(container);
        container.addView(frame);
        state = new NativeShadeState(() -> container, method -> { });
    }
    @After public void tearDown() { activity.pause().stop().destroy(); }
    @Test public void nativeFakeKeepsOwnershipUntilItsOwnAnimationEnds() {
        frame.setShouldInvalidate(true);
        assertTrue(state.isActive()); // Does not depend on a panel fraction reaching zero.
        frame.setShouldInvalidate(false);
        assertFalse(state.isActive());
    }
    @Test public void invisibleOrTransparentFakeDoesNotBlockTheIndependentCapsule() {
        frame.setShouldInvalidate(true);
        container.setVisibility(View.INVISIBLE);
        assertFalse(state.isActive());
        container.setVisibility(View.VISIBLE);
        container.setAlpha(0f);
        assertFalse(state.isActive());
        container.setAlpha(0.01f);
        assertTrue(state.isActive());
        root.setAlpha(0f);
        assertFalse(state.isActive());
    }
    @Test public void detachedFakeOrMissingContainerCannotOwnOutput() {
        frame.setShouldInvalidate(true);
        root.removeView(container);
        assertFalse(state.isActive());
        container = null;
        assertFalse(state.isActive());
    }
    public static class FakePluginFrame extends View {
        private boolean shouldInvalidate;
        public FakePluginFrame(Context context) { super(context); }
        public boolean getShouldInvalidate() { return shouldInvalidate; }
        public void setShouldInvalidate(boolean value) { shouldInvalidate = value; }
    }
}
