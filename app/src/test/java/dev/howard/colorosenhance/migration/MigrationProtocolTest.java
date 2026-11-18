package dev.howard.colorosenhance.migration;

import android.content.Context;
import dev.howard.colorosenhance.BuildConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class MigrationProtocolTest {
    static Map<String,Map<String,Object>> groups(){
        var values=new LinkedHashMap<String,Map<String,Object>>();
        for(String group:MigrationProtocol.GROUPS)values.put(group,new LinkedHashMap<>());
        return values;
    }
    static MigrationProtocol.Snapshot sample(){
        var groups=groups();groups.put("cloud",Map.of("enabled",true,"wave_interval_ms",40,"wave_hold_paused",true));
        groups.put("sim",Map.of("enabled",true));groups.put("installer",Map.of("target_package","com.example.installer"));
        return new MigrationProtocol.Snapshot(groups,2,false,false,false);
    }
    @Test public void ordinarySettingsAndAppearanceRoundTripWithoutCredentialFields(){
        var source=sample();String text=MigrationProtocol.encode(source);
        assertEquals(source,MigrationProtocol.decode(text));
        assertFalse(text.contains("iccid"));assertFalse(text.contains("pending.record"));assertFalse(text.contains("aes"));
        assertEquals("io.github.howard_tse.colorosenhance",BuildConfig.APPLICATION_ID);
    }
    @Test public void pendingPinDoesNotSilentlyEnableAnUnmigratedSlot(){
        var source=sample();var pending=new MigrationProtocol.Snapshot(source.groups(),0,true,true,true);
        assertEquals(false,pending.importGroups().get("sim").get("enabled"));
        assertEquals(true,source.importGroups().get("sim").get("enabled"));
    }
    @Test public void pinAndUnknownPreferenceGroupsCannotEnterThePayload(){
        var values=groups();values.put("sim",Map.of("pin","dummy-secret"));
        assertThrows(IllegalArgumentException.class,()->new MigrationProtocol.Snapshot(values,0,true,true,false));
        values.put("sim",Map.of());values.put("staging",Map.of());
        assertThrows(IllegalArgumentException.class,()->new MigrationProtocol.Snapshot(values,0,true,true,false));
    }
    @Test public void wrongIdentitySchemaOrTypesAreRejected(){
        String text=MigrationProtocol.encode(sample());
        assertThrows(IllegalArgumentException.class,()->MigrationProtocol.decode(text.replace(MigrationProtocol.LEGACY,"com.example.other")));
        assertThrows(IllegalArgumentException.class,()->MigrationProtocol.decode(text.replace("\"schema\":1","\"schema\":2")));
        assertThrows(IllegalArgumentException.class,()->MigrationProtocol.decode(text.replace("\"appearance\":2","\"appearance\":\"2\"")));
        assertThrows(IllegalArgumentException.class,()->MigrationProtocol.decode("x".repeat(MigrationProtocol.MAX_BYTES+1)));
    }
    @Test public void copiedSettingsStayInactiveWhileTheOldModuleIsEnabled(){
        var waiting=MigrationController.activation(sample(),false);
        assertEquals(false,waiting.get("integration").get(PackageActivation.READY));
        assertEquals(false,waiting.get("integration").get("show_entry"));
        assertEquals(false,waiting.get("cloud").get("enabled"));
        assertEquals(40,waiting.get("cloud").get("wave_interval_ms"));
        var active=MigrationController.activation(sample(),true);
        assertEquals(true,active.get("integration").get(PackageActivation.READY));
        assertEquals(true,active.get("cloud").get("enabled"));
    }
    @Test public void startupDefaultInstallerMetadataIsNotTreatedAsUserConfiguration(){
        var values=groups();values.put("installer",Map.of("target_package","","schema",1));
        assertFalse(MigrationController.hasUserSettings(values));
        values.put("cloud",Map.of("wave_interval_ms",40));assertTrue(MigrationController.hasUserSettings(values));
    }
    @Test public void activationIsClosedUntilExplicitlyCommittedAndOnReadFailure(){
        var app=org.robolectric.RuntimeEnvironment.getApplication();var prefs=app.getSharedPreferences("gate",Context.MODE_PRIVATE);
        assertFalse(PackageActivation.allowed(()->prefs));
        prefs.edit().putBoolean(PackageActivation.READY,true).commit();assertTrue(PackageActivation.allowed(()->prefs));
        assertFalse(PackageActivation.allowed(()->{throw new IllegalStateException();}));
    }
}
