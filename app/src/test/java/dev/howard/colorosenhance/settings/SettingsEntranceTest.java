package dev.howard.colorosenhance.settings;
import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import android.preference.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class SettingsEntranceTest {
    @Test public void entranceIsUniqueAndRemovalPreservesNativeRows() throws Exception {
        Host host = new Host();
        Preference original = new Preference(host.getContext()); original.setKey("native"); host.screen.addPreference(original);
        SettingsEntrance entrance = new SettingsEntrance(new HostReflection(getClass().getClassLoader()));
        entrance.refresh(host,true); entrance.refresh(host,true);
        assertEquals(2,host.screen.getPreferenceCount());
        PreferenceGroup category = (PreferenceGroup) host.screen.findPreference(SettingsEntrance.CATEGORY);
        assertNotNull(category); assertTrue(category.getOrder()>48 && category.getOrder()<100);
        assertEquals(1,category.getPreferenceCount());
        Preference entry = category.getPreference(0);
        assertFalse(entry.isPersistent());
        assertTrue(entry.getOnPreferenceClickListener().onPreferenceClick(entry));
        assertEquals("com.android.settings",Shadows.shadowOf(host.context).getNextStartedActivity().getComponent().getPackageName());
        entrance.refresh(host,false);
        assertEquals(1,host.screen.getPreferenceCount()); assertSame(original,host.screen.getPreference(0));
    }
    @Test public void incompleteHostDoesNotRemoveExistingPreferences() throws Exception {
        Host host = new Host(); Preference original = new Preference(host.getContext()); host.screen.addPreference(original);
        ClassLoader unavailable = new ClassLoader(null) {};
        SettingsEntrance entrance = new SettingsEntrance(new HostReflection(unavailable));
        assertThrows(ReflectiveOperationException.class, () -> entrance.refresh(host,true));
        assertEquals(1,host.screen.getPreferenceCount());
    }
    public static class HostActivity extends android.preference.PreferenceActivity { @Override protected void onCreate(android.os.Bundle state) { setTheme(android.R.style.Theme_Material_Light); super.onCreate(state); } }
    public static class Host {
        final HostActivity context = org.robolectric.Robolectric.buildActivity(HostActivity.class).setup().get();
        final PreferenceScreen screen = context.getPreferenceManager().createPreferenceScreen(context);
        public Context getContext() { return context; }
        public PreferenceScreen getPreferenceScreen() { return screen; }
    }
}
