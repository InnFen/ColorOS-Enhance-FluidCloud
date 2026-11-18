package dev.howard.colorosenhance.installer;

import dev.howard.colorosenhance.PreferenceSession;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InstallerPreferencesTest {
    static class Store implements PreferenceSession.Store {
        Map<String,Object> data=new HashMap<>(); boolean fail;
        public Map<String,?> read(){return Map.copyOf(data);}
        public boolean replace(Map<String,Object> next){if(fail)return false; data=new HashMap<>(next);return true;}
    }
    @Test public void freshConfigurationDoesNotChooseInstallerXEvenAfterEnabling() {
        Store s=new Store(); InstallerPreferences.initialize(s);
        s.data.put("enabled",true); InstallerPreferences.initialize(s);
        assertEquals("",s.data.get("target_package"));
    }
    @Test public void legacyConfigurationKeepsSelectionAndEnabledState() {
        for(boolean enabled:new boolean[]{false,true}) {
            Store s=new Store();s.data.put("enabled",enabled);InstallerPreferences.initialize(s);
            assertEquals("com.rosan.installer.x.revived",s.data.get("target_package"));
            assertEquals(enabled,s.data.get("enabled"));
        }
    }
    @Test public void preservesExplicitChoiceAndUnrelatedKeys() {
        Store s=new Store();s.data.put("target_package","custom.installer");s.data.put("skip_confirmation",true);
        InstallerPreferences.initialize(s);assertEquals("custom.installer",s.data.get("target_package"));
        assertEquals(true,s.data.get("skip_confirmation"));
    }
    @Test public void migrationFailureDoesNotAllowUninitializedWrites() {
        Store s=new Store();s.fail=true;
        assertThrows(IllegalStateException.class,()->InstallerPreferences.initialize(s));assertTrue(s.data.isEmpty());
    }
    @Test public void resetCannotBeMisidentifiedAsLegacyAfterReenabling() {
        Store store=new Store();
        var coordinator=new dev.howard.colorosenhance.SettingsCoordinator(()->new dev.howard.colorosenhance.SettingsCoordinator.Source(){
            public Object identity(){return store;}public PreferenceSession.Store open(String group){return store;}
        },()->{});
        var session=coordinator.session("installer");session.save("enabled",true);session.reset();session.save("enabled",true);
        InstallerPreferences.initialize(store);assertEquals("",store.data.get("target_package"));
    }
}
