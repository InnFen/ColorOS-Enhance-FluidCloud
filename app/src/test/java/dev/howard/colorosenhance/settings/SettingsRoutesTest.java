package dev.howard.colorosenhance.settings;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class SettingsRoutesTest {
    @Test public void nativePageRoutesStayInsideSettingsAndCanBeRestored() {
        for (String route : dev.howard.colorosenhance.SettingsLayout.routes()) {
            Intent intent = SettingsRoutes.intent(route);
            assertNotNull("Embedded page must have an explicit native host", intent.getComponent());
            assertEquals("com.android.settings",intent.getComponent().getPackageName());
            assertEquals("com.android.settings.SubSettings",intent.getComponent().getClassName());
            assertTrue(SettingsRoutes.accepts(SettingsRoutes.FRAGMENT,intent));
            assertEquals(route,SettingsRoutes.route(intent.getBundleExtra(":settings:show_fragment_args")));
            assertEquals(SettingsRoutes.title(route),intent.getStringExtra(":settings:show_fragment_title"));
        }
    }
    @Test public void markingDoesNotAllowUnrelatedFragmentsOrHostActivities() {
        Intent page = SettingsRoutes.intent("home");
        assertFalse(SettingsRoutes.accepts("com.android.settings.DevelopmentSettings",page));
        page.setClassName("com.android.settings","com.android.settings.Settings");
        assertFalse(SettingsRoutes.accepts(SettingsRoutes.FRAGMENT,page));
        assertFalse(SettingsRoutes.accepts(SettingsRoutes.FRAGMENT,new Intent()));
    }
    @Test public void arbitraryArgumentsAndUnmarkedNativeFragmentsRemainNative() {
        Bundle invalid = new Bundle(); invalid.putString(SettingsRoutes.MARKER,"true"); invalid.putString(SettingsRoutes.ROUTE,"home");
        assertNull(SettingsRoutes.route(invalid));
        invalid.putBoolean(SettingsRoutes.MARKER,true); invalid.putString(SettingsRoutes.ROUTE,"../../secret");
        assertNull(SettingsRoutes.route(invalid));
        assertNull(SettingsRoutes.route(null));
        assertThrows(IllegalArgumentException.class, () -> SettingsRoutes.intent("unknown"));
        invalid.putString(SettingsRoutes.ROUTE,"memory");
        assertNull(SettingsRoutes.route(invalid));
        assertThrows(IllegalArgumentException.class, () -> SettingsRoutes.intent("memory"));
        invalid.putString(SettingsRoutes.ROUTE,"cloud_custom");
        assertNull(SettingsRoutes.route(invalid));
    }
}
