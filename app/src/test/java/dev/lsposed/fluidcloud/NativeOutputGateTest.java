package dev.lsposed.fluidcloud;

import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class NativeOutputGateTest {
    @Test public void nativeLogicalAlphaAndOriginalTransitionArePreservedAcrossRepeatedFrames() throws Exception {
        View view=new View(org.robolectric.RuntimeEnvironment.getApplication());view.setAlpha(.6f);transition(view,.8f);
        var gate=new NativeOutputGate();gate.hide(view);gate.hide(view);
        assertEquals(.6f,view.getAlpha(),0);assertEquals(0,transition(view),0);
        gate.restore();assertEquals(.8f,transition(view),0);assertEquals(.6f,view.getAlpha(),0);
    }
    @Test public void nativeChangesWinWhetherDiscoveredBeforeTheNextDrawOrBeforeRelease() throws Exception {
        View view=new View(org.robolectric.RuntimeEnvironment.getApplication());var gate=new NativeOutputGate();
        gate.hide(view);transition(view,.4f);gate.hide(view);gate.restore();assertEquals(.4f,transition(view),0);
        gate.hide(view);transition(view,.7f);gate.restore();assertEquals(.7f,transition(view),0);
    }
    @Test public void sourceAndShadeReleaseIndependentlyAndAnAlreadyHiddenNativeViewStaysHidden() throws Exception {
        var context=org.robolectric.RuntimeEnvironment.getApplication();View source=new View(context),shade=new View(context);
        transition(shade,0);var gate=new NativeOutputGate();gate.hide(source);gate.hide(shade);
        gate.restore(shade);assertEquals(0,transition(source),0);assertEquals(0,transition(shade),0);
        gate.restore();assertEquals(1,transition(source),0);assertEquals(0,transition(shade),0);
    }
    private static void transition(View view,float value) throws Exception {
        Reflect.call(Reflect.method(View.class,"setTransitionAlpha",void.class,float.class),view,value);
    }
    private static float transition(View view) throws Exception {
        return (float)Reflect.call(Reflect.method(View.class,"getTransitionAlpha",float.class),view);
    }
}
