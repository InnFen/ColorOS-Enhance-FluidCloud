package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
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
public class NativeContentConfigTest {
    ActivityController<Activity> activity;
    FrameLayout host;
    CapsuleView capsule;
    NativeContentConfig config;
    Set<Method> hooks;

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        host = new FrameLayout(activity.get());
        activity.get().setContentView(host);
        capsule = new CapsuleView();
        host.addView(capsule);
        hooks = new HashSet<>();
        config = new NativeContentConfig(host, () -> true, hooks::add);
    }
    @After public void tearDown() { config.close(); activity.pause().stop().destroy(); }

    @Test public void newCapsuleAlreadyInFullscreenResumesThroughNativeConfiguration() {
        Wrapper wrapper = new Wrapper(capsule);
        config.update(true);
        assertFalse(wrapper.fullscreen);
        assertEquals(1, hooks.size());
        assertTrue(wrapper.canStart);
    }
    @Test public void partialConfigurationPreservesExplicitPauseAndTransitionBlocks() {
        Wrapper wrapper = new Wrapper(capsule);
        config.update(true);
        Bundle nativeConfig = new Bundle();
        nativeConfig.putBoolean("isFullScreen", true);
        nativeConfig.putBoolean("canStartAnimation", false);
        nativeConfig.putBoolean("blockViewUpdate", true);
        nativeConfig.putInt("toState", 2);
        Bundle filtered = config.filter(wrapper, nativeConfig);
        wrapper.changed(filtered);
        assertTrue(nativeConfig.getBoolean("isFullScreen"));
        assertFalse(wrapper.fullscreen);
        assertFalse(wrapper.canStart);
        assertTrue(filtered.getBoolean("blockViewUpdate"));
        assertEquals(2, filtered.getInt("toState"));
        Bundle other = new Bundle();
        other.putFloat("capsuleMiddleBlankWidth", 18f);
        assertSame(other, config.filter(wrapper, other));
    }
    @Test public void shadeOrLockRestoresLastNativeValueAndReentryResumesOnce() {
        Wrapper wrapper = new Wrapper(capsule);
        config.update(true);
        config.update(false);
        assertTrue(wrapper.fullscreen);
        Bundle visible = new Bundle();
        visible.putBoolean("isFullScreen", false);
        wrapper.changed(config.filter(wrapper, visible));
        config.update(true);
        config.update(false);
        assertFalse(wrapper.fullscreen);
    }
    @Test public void continuousKeepOnLegacyBundlePreservesLatestNativeValue() {
        Wrapper wrapper = new Wrapper(capsule);
        config.update(true);
        assertTrue(config.hasActiveOverride());

        Bundle visible = new Bundle();
        visible.putBoolean("isFullScreen", false);
        wrapper.changed(config.filter(wrapper, visible));
        assertFalse(wrapper.fullscreen);
        assertTrue(config.hasActiveOverride());

        Bundle nextHide = new Bundle();
        nextHide.putBoolean("isFullScreen", true);
        wrapper.changed(config.filter(wrapper, nextHide));
        assertFalse(wrapper.fullscreen);
        config.update(false);
        assertTrue(wrapper.fullscreen);
    }
    @Test public void outsideCapsuleAndDetachedProvidersAreNeverOverridden() {
        Wrapper outside = new Wrapper(host);
        Wrapper inside = new Wrapper(capsule);
        config.update(true);
        assertTrue(outside.fullscreen);
        capsule.removeView(inside.root);
        config.update(true);
        assertTrue(inside.fullscreen);
        Bundle b = new Bundle();
        b.putBoolean("isFullScreen", true);
        assertSame(b, config.filter(outside, b));
        assertSame(b, config.filter(inside, b));
    }
    @Test public void closeRestoresNativeStateAndDoesNotRestartOtherAnimations() {
        Wrapper wrapper = new Wrapper(capsule);
        wrapper.canStart = false;
        config.update(true);
        config.close();
        assertTrue(wrapper.fullscreen);
        assertFalse(wrapper.canStart);
    }
    @Test public void ambiguousBundleContractIsRejectedWithoutInvokingEitherMethod() {
        Ambiguous wrapper = new Ambiguous(capsule);
        config.update(true);
        assertTrue(wrapper.fullscreen);
        assertTrue(hooks.isEmpty());
    }
    @Test public void subclassWithBubbleContainerStillFindsItsAttachedTemplateRoot() {
        BubbleWrapper wrapper = new BubbleWrapper(capsule);
        config.update(true);
        assertFalse(wrapper.fullscreen);
        assertEquals(1, hooks.size());
    }
    public class CapsuleView extends FrameLayout {
        CapsuleView() { super(activity.get()); }
    }
    public static class Wrapper {
        final FrameLayout root;
        boolean fullscreen = true, canStart = true;
        Wrapper(FrameLayout parent) {
            root = new FrameLayout(parent.getContext());
            root.addOnAttachStateChangeListener(new Listener(this));
            parent.addView(root);
        }
        public void notifyConfigurationChanged(Configuration ignored) { }
        public void changed(Bundle b) {
            fullscreen = b.getBoolean("isFullScreen", fullscreen);
            canStart = b.getBoolean("canStartAnimation", canStart);
        }
    }
    public static class Ambiguous extends Wrapper {
        Ambiguous(FrameLayout parent) { super(parent); }
        public void another(Bundle b) { throw new AssertionError("ambiguous mutation"); }
    }
    public static class BubbleWrapper extends Wrapper {
        final FrameLayout bubble;
        BubbleWrapper(FrameLayout parent) {
            super(parent);
            bubble = new FrameLayout(parent.getContext());
        }
    }
    static class Listener implements View.OnAttachStateChangeListener {
        final Object owner;
        Listener(Object owner) { this.owner = owner; }
        public void onViewAttachedToWindow(View v) { }
        public void onViewDetachedFromWindow(View v) { }
    }
}
