package dev.howard.colorosenhance.settings;
import static org.junit.Assert.*;
import android.app.Application;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class SettingsHostPolicyTest {
    @Test public void acceptsOnlyExactHostClassAndMatchingMarkedRoute() {
        var intent=SettingsRoutes.intent("battery"); var args=intent.getBundleExtra(":settings:show_fragment_args");
        assertEquals("battery",SettingsHostPolicy.route(SettingsRoutes.FRAGMENT,intent,args));
        assertNull(SettingsHostPolicy.route("some.Subclass",intent,args));
        assertNull(SettingsHostPolicy.route(SettingsRoutes.FRAGMENT,intent,new Bundle()));
        assertNull(SettingsHostPolicy.route(SettingsRoutes.FRAGMENT,intent,SettingsRoutes.intent("aod").getBundleExtra(":settings:show_fragment_args")));
        assertNull(SettingsHostPolicy.route(SettingsRoutes.FRAGMENT,null,args));
    }
}
