package dev.howard.colorosenhance.migration;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class RootMigrationReaderTest {
    private static byte[] serialize(Object value) throws Exception {
        var bytes=new ByteArrayOutputStream();try(var stream=new ObjectOutputStream(bytes)){stream.writeObject(value);}return bytes.toByteArray();
    }
    private static File file(String suffix) throws Exception {
        File root=new File("build/test-migration");assertTrue(root.isDirectory()||root.mkdirs());return File.createTempFile("fixture-",suffix,root);
    }
    private static void row(SQLiteDatabase db,boolean old,String pkg,int user,String group,String key,byte[] data){
        var values=new ContentValues();values.put("module_pkg_name",pkg);values.put("user_id",user);
        values.put(old?"group_name":"`group`",group);values.put(old?"key_name":"`key`",key);values.put("data",data);
        db.insertOrThrow(old?"module_configs":"configs",null,values);
    }
    private static File fixture(boolean old) throws Exception {
        File file=file(".db");
        try(var db=SQLiteDatabase.openOrCreateDatabase(file,null)){
            if(old){
                db.execSQL("CREATE TABLE modules_state(module_pkg_name TEXT,user_id INTEGER,enabled INTEGER)");
                db.execSQL("INSERT INTO modules_state VALUES(?,0,1)",new Object[]{MigrationProtocol.LEGACY});
                db.execSQL("CREATE TABLE module_configs(module_pkg_name TEXT,user_id INTEGER,group_name TEXT,key_name TEXT,data BLOB)");
            }else{
                db.execSQL("CREATE TABLE modules(module_pkg_name TEXT,enabled INTEGER)");
                db.execSQL("INSERT INTO modules VALUES(?,0)",new Object[]{MigrationProtocol.LEGACY});
                db.execSQL("CREATE TABLE configs(module_pkg_name TEXT,user_id INTEGER,`group` TEXT,`key` TEXT,data BLOB)");
            }
            row(db,old,MigrationProtocol.LEGACY,0,"cloud","enabled",serialize(true));
            row(db,old,MigrationProtocol.LEGACY,0,"cloud","wave_interval_ms",serialize(40));
            row(db,old,MigrationProtocol.LEGACY,1,"cloud","wave_sensitivity",serialize(200));
            row(db,old,MigrationProtocol.LEGACY,0,"sim","pin",serialize(new SideEffect()));
            row(db,old,MigrationProtocol.LEGACY,0,"dev.howard.colorosenhance.sim_pending_data_v1","pending.record.0",new byte[]{1,2,3});
            row(db,old,MigrationProtocol.LEGACY,0,"dev.howard.colorosenhance.sim_pending_key_v1","key",new byte[]{4,5,6});
            row(db,old,"com.example.other",0,"cloud","enabled",serialize(new SideEffect()));
        }
        return file;
    }
    @Test public void bothKnownDatabaseSchemasReadOnlyTheOldProductsOrdinaryUserZeroSettings() throws Exception {
        for(boolean old:new boolean[]{true,false}){
            File file=fixture(old);byte[] before=Files.readAllBytes(file.toPath());
            var result=RootMigrationReader.readDatabase(file,true);
            assertEquals(old?RootMigrationReader.LegacyState.ENABLED:RootMigrationReader.LegacyState.DISABLED,result.state());
            assertEquals(true,result.snapshot().groups().get("cloud").get("enabled"));
            assertEquals(40,result.snapshot().groups().get("cloud").get("wave_interval_ms"));
            assertFalse(result.snapshot().groups().get("cloud").containsKey("wave_sensitivity"));
            assertTrue(result.snapshot().pendingPin());
            String payload=MigrationProtocol.encode(result.snapshot());
            assertFalse(result.snapshot().groups().get("sim").containsKey("pin"));assertFalse(payload.contains("pending.record"));assertFalse(payload.contains("pending_key"));
            assertFalse(SideEffect.executed);assertArrayEquals(before,Files.readAllBytes(file.toPath()));
        }
    }
    @Test public void malformedOrUnknownSchemasNeverBecomeAnEmptySuccessfulMigration() throws Exception {
        File file=file(".db");try(var db=SQLiteDatabase.openOrCreateDatabase(file,null)){db.execSQL("CREATE TABLE unexpected(x INTEGER)");}
        assertThrows(IllegalStateException.class,()->RootMigrationReader.readDatabase(file,true));
    }
    @Test public void invalidActivationFlagsCannotBeInterpretedAsDisabled() throws Exception {
        for(boolean old:new boolean[]{true,false})for(Object flag:new Object[]{null,"invalid",2,0.5}){
            File file=fixture(old);
            try(var db=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                db.execSQL("UPDATE "+(old?"modules_state":"modules")+" SET enabled=?",new Object[]{flag});
            }
            assertThrows(IllegalStateException.class,()->RootMigrationReader.readDatabase(file,false));
        }
    }
    @Test public void localPreferencePathsOnlySelectTheTwoFixedFilesUnderTheLegacyPackage() throws Exception {
        File userRoot=file(".root");assertTrue(userRoot.delete());assertTrue(userRoot.mkdir());
        File expected=new File(userRoot.getCanonicalFile(),MigrationProtocol.LEGACY+"/shared_prefs/ui_presentation.xml");
        assertEquals(expected,RootMigrationReader.localPreference(userRoot,"ui_presentation.xml"));
        assertThrows(IllegalArgumentException.class,()->RootMigrationReader.localPreference(userRoot,"../outside.xml"));
        assertThrows(IllegalArgumentException.class,()->RootMigrationReader.localPreference(userRoot,"credential.xml"));
    }
    @Test public void objectGadgetsAndOversizedRecordsAreRejectedBeforeAnyReadObjectCodeRuns() throws Exception {
        SideEffect.executed=false;byte[] gadget=serialize(new SideEffect());
        assertThrows(IllegalArgumentException.class,()->RootMigrationReader.decodeValue(gadget));assertFalse(SideEffect.executed);
        assertThrows(IllegalArgumentException.class,()->RootMigrationReader.decodeValue(new byte[65537]));
        for(Object value:new Object[]{true,false,40,80L,1.2f,"com.example.installer"})assertEquals(value,RootMigrationReader.decodeValue(serialize(value)));
    }
    @Test public void appearanceReaderIgnoresOtherPreferencesAndOnlyReportsStagingPresence() throws Exception {
        File file=file(".xml");Files.writeString(file.toPath(),"<map><int name=\"appearance\" value=\"2\"/><boolean name=\"liquid_navigation\" value=\"false\"/><string name=\"unrelated\">dummy-secret</string><string name=\"pending.record.0\">ciphertext</string></map>",StandardCharsets.UTF_8);
        var appearance=RootMigrationReader.readXml(file,false);assertEquals(2,appearance.get("appearance"));assertEquals(false,appearance.get("liquid_navigation"));assertEquals(2,appearance.size());
        assertEquals(java.util.Map.of("present",true),RootMigrationReader.readXml(file,true));
        Files.writeString(file.toPath(),"<!DOCTYPE map [<!ENTITY x SYSTEM 'file:///not-read'>]><map/>");
        assertThrows(IllegalArgumentException.class,()->RootMigrationReader.readXml(file,false));
    }
    @Test public void activationProbeDoesNotDeserializeOrReturnAnySettings() throws Exception {
        File file=fixture(true);var result=RootMigrationReader.readDatabase(file,false);
        assertEquals(RootMigrationReader.LegacyState.ENABLED,result.state());assertNull(result.snapshot());
    }
    static final class SideEffect implements Serializable {
        static boolean executed;
        private void readObject(java.io.ObjectInputStream input) throws Exception{executed=true;input.defaultReadObject();}
    }
}
