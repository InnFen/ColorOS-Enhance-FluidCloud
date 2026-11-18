package dev.howard.colorosenhance;

import org.junit.Test;
import static org.junit.Assert.*;

public class InstallerSettingsContractTest {
    @Test public void acceptsUserSelectedPackageAndClearingSelection() {
        assertTrue(SettingsCoordinator.validValue("installer","target_package","org.example.installer"));
        assertTrue(SettingsCoordinator.validValue("installer","target_package",""));
        assertFalse(SettingsCoordinator.validValue("installer","target_package","bad/package"));
        assertFalse(SettingsCoordinator.validValue("installer","target_package",42));
    }
    @Test public void providesIndependentConfirmationSwitch() {
        assertTrue(SettingsCoordinator.validValue("installer","skip_confirmation",true));
        assertTrue(SettingsCatalog.find("installer").items().stream().anyMatch(i->i.key().equals("skip_confirmation")));
    }
}
