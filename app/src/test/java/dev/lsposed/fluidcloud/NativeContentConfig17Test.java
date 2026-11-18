package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.RectF;
import android.widget.FrameLayout;
import com.oplus.systemui.plugins.shared.data.model.g;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import kotlin.jvm.functions.Function1;
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
public class NativeContentConfig17Test {
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

    @Test public void discoversFunctionWrapperAndOnlyChangesFullscreen() throws Exception {
        g nativeValue = state(true);
        Wrapper wrapper = new Wrapper(capsule, nativeValue);
        config.update(true);
        assertFalse(wrapper.e.value.k);
        assertTrue(nativeValue.k);
        assertEquals(1, hooks.size());
        for (Field field : g.class.getDeclaredFields()) {
            if (!field.getName().equals("k")) assertEquals(field.getName(), field.get(nativeValue), field.get(wrapper.e.value));
        }
        config.close();
        assertTrue(wrapper.e.value.k);
    }

    @Test public void partialTransformReceivesNativeFullscreenAndPreservesPauseBlockAndOtherUpdates() {
        Wrapper wrapper = new Wrapper(capsule, state(true));
        config.update(true);
        int[] calls = {0};
        wrapper.p(transform(wrapper, input -> {
            calls[0]++;
            assertTrue(input.k);
            return copy(input, true, false, true, 2);
        }));
        assertEquals(1, calls[0]);
        assertFalse(wrapper.e.value.k);
        assertFalse(wrapper.e.value.g);
        assertTrue(wrapper.e.value.f);
        assertEquals(2, wrapper.e.value.A);
        config.update(false);
        assertTrue(wrapper.e.value.k);
        assertFalse(wrapper.e.value.g);
        assertTrue(wrapper.e.value.f);
        assertEquals(2, wrapper.e.value.A);
    }

    @Test public void latestExplicitNativeFullscreenFalseSurvivesDisableAndReentry() {
        Wrapper wrapper = new Wrapper(capsule, state(true));
        config.update(true);
        wrapper.p(transform(wrapper, input -> copy(input, false, input.g, input.f, input.A)));
        config.update(false);
        assertFalse(wrapper.e.value.k);
        config.update(true);
        config.close();
        assertFalse(wrapper.e.value.k);
    }

    @Test public void continuousKeepSpansNativeFullscreenExitsWithoutRebinding() {
        Wrapper wrapper = new Wrapper(capsule, state(false));
        config.update(true);
        wrapper.p(transform(wrapper, input -> copy(input, true, input.g, input.f, input.A)));
        assertFalse(wrapper.e.value.k);
        wrapper.p(transform(wrapper, input -> copy(input, false, input.g, input.f, input.A)));
        assertFalse(wrapper.e.value.k);
        wrapper.p(transform(wrapper, input -> copy(input, true, input.g, input.f, input.A)));
        assertFalse(wrapper.e.value.k);
        config.update(false);
        assertTrue(wrapper.e.value.k);
    }

    @Test public void initialNativeFalseIsNotReplacedByGlobalHiddenState() {
        Wrapper wrapper = new Wrapper(capsule, state(false));
        config.update(true);
        config.close();
        assertFalse(wrapper.e.value.k);
    }

    @Test public void outsideAndDetachedProvidersKeepOriginalTransformAndRestoreNativeValue() {
        Wrapper outside = new Wrapper(host, state(true));
        Wrapper inside = new Wrapper(capsule, state(true));
        Function1<g, g> identity = input -> input;
        config.update(true);
        assertSame(identity, config.filterTransform(outside, identity));
        capsule.removeView(inside.root);
        config.update(true);
        assertTrue(inside.e.value.k);
        assertSame(identity, config.filterTransform(inside, identity));
    }

    @Test public void ambiguousFunctionContractIsIgnoredWithoutProviderMutation() {
        Wrapper wrapper = new AmbiguousWrapper(capsule, state(true));
        config.update(true);
        assertTrue(wrapper.e.value.k);
        assertTrue(hooks.isEmpty());
    }

    @Test public void nativeTransformExceptionIsPropagatedOnce() {
        Wrapper wrapper = new Wrapper(capsule, state(true));
        config.update(true);
        int[] calls = {0};
        IllegalStateException expected = new IllegalStateException("native transform");
        try {
            wrapper.p(transform(wrapper, input -> { calls[0]++; throw expected; }));
            fail("native exception must propagate");
        } catch (IllegalStateException actual) {
            assertSame(expected, actual);
        }
        assertEquals(1, calls[0]);
    }

    @SuppressWarnings("unchecked")
    private Function1<g, g> transform(Wrapper owner, Function1<g, g> nativeTransform) {
        return (Function1<g, g>) config.filterTransform(owner, nativeTransform);
    }
    public class CapsuleView extends FrameLayout { CapsuleView() { super(activity.get()); } }
    public static class Flow {
        g value;
        Flow(g value) { this.value = value; }
        public Object getValue() { return value; }
    }
    public static class Wrapper {
        final FrameLayout root;
        public final Flow e;
        Wrapper(FrameLayout parent, g value) {
            e = new Flow(value);
            root = new FrameLayout(parent.getContext());
            root.addOnAttachStateChangeListener(new NativeContentConfigTest.Listener(this));
            parent.addView(root);
        }
        public void notifyConfigurationChanged(Configuration ignored) { }
        public void p(Function1<g, g> transform) { e.value = transform.invoke(e.value); }
    }
    public static class AmbiguousWrapper extends Wrapper {
        AmbiguousWrapper(FrameLayout parent, g value) { super(parent, value); }
        public void another(Function1<g, g> transform) { throw new AssertionError("ambiguous mutation"); }
    }
    private static g state(boolean fullscreen) {
        return new g(true, true, false, new RectF(1, 2, 3, 4), 18.5f, false, true, false,
                true, false, fullscreen, 77, true, false, 2, 123, true, false, true, false,
                5, true, 6, true, false, new ArrayList<>(java.util.List.of("fixture")),
                1, "template", true, 2, 7, true);
    }
    private static g copy(g old, boolean fullscreen, boolean canStart, boolean blocked, int toState) {
        return g.a(old, false, false, false, null, 0, blocked, canStart, false, false,
                false, fullscreen, 0, false, false, 0, 0, false, false, false, false,
                0, false, 0, false, false, null, toState, false, 0, 0, false,
                ~(32 | 64 | 1024 | 67108864));
    }
}
