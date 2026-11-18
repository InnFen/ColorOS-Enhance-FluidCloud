package dev.howard.colorosenhance.migration;

import android.database.sqlite.SQLiteDatabase;
import android.os.Process;
import android.util.Base64;
import android.util.Xml;
import dev.howard.colorosenhance.SettingsCoordinator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/** Fixed, read-only root helper. It never reads phone credentials or writes framework state. */
public final class RootMigrationReader {
    static final String DATABASE = "/data/adb/lspd/config/modules_config.db";
    public enum LegacyState { ENABLED, DISABLED, UNKNOWN }
    public record Result(LegacyState state, MigrationProtocol.Snapshot snapshot) { }
    record Schema(String table,String group,String key) { }

    public static void main(String[] args) {
        if (Process.myUid() != 0 || args.length != 2 || !Set.of("read","probe").contains(args[0])
                || !args[1].matches("[a-f0-9]{32}")) { System.exit(10); return; }
        try {
            File file=new File(DATABASE);
            if (!file.getCanonicalPath().equals(DATABASE) || !file.isFile()) throw new IllegalStateException();
            Result result=readDatabase(file,"read".equals(args[0]));
            var envelope=new JSONObject().put("nonce",args[1]).put("state",result.state().name());
            if(result.snapshot()!=null) envelope.put("snapshot",MigrationProtocol.encode(withLocalPresentation(result.snapshot())));
            String encoded=Base64.encodeToString(envelope.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
            System.out.print("COLOROS_MIGRATION_V1 "+encoded+"\n");
        } catch (Throwable failed) {
            // No throwable message, SQL rows, settings or credential data in logs.
            System.exit(11);
        }
    }
    static Result readDatabase(File file,boolean includeSettings) {
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getPath(),null,
                SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {
            db.beginTransactionReadOnly();
            try {
                Schema schema=schema(db); LegacyState state=state(db);
                if(state==LegacyState.UNKNOWN) throw new IllegalStateException("Unknown framework module schema");
                if(!includeSettings) return new Result(state,null);
                var groups=new LinkedHashMap<String,Map<String,Object>>();
                for(String group:MigrationProtocol.GROUPS) {
                    var values=new LinkedHashMap<String,Object>();
                    try(var rows=db.query(schema.table(),new String[]{"`"+schema.key()+"`"},
                            "module_pkg_name=? AND user_id=? AND `"+schema.group()+"`=?",
                            new String[]{MigrationProtocol.LEGACY,"0",group},null,null,null)) {
                        if(rows.getCount()>256) throw new IllegalStateException("Too many configuration rows");
                        while(rows.moveToNext()) {
                            String key=rows.getString(0);
                            if(!MigrationProtocol.knownKey(group,key))continue;
                            // Unknown keys are not fetched or deserialized at all.
                            try(var item=db.query(schema.table(),new String[]{"data"},
                                    "module_pkg_name=? AND user_id=? AND `"+schema.group()+"`=? AND `"+schema.key()+"`=?",
                                    new String[]{MigrationProtocol.LEGACY,"0",group,key},null,null,null)) {
                                if(item.getCount()!=1 || !item.moveToFirst())throw new IllegalStateException("Ambiguous preference row");
                                Object value=decodeValue(item.getBlob(0));
                                if(SettingsCoordinator.validValue(group,key,value))values.put(key,value);
                            }
                        }
                    }
                    groups.put(group,values);
                }
                boolean pending;
                // Only count staging records. Neither ciphertext nor key rows are returned.
                try(var rows=db.rawQuery("SELECT COUNT(*) FROM `"+schema.table()+"` WHERE module_pkg_name=? AND user_id=0 AND `"
                        +schema.group()+"`=? AND `"+schema.key()+"` IN ('pending.record.0','pending.record.1','pending.state.0','pending.state.1')",
                        new String[]{MigrationProtocol.LEGACY,"dev.howard.colorosenhance.sim_pending_data_v1"})) {
                    pending=rows.moveToFirst() && rows.getInt(0)>0;
                }
                return new Result(state,new MigrationProtocol.Snapshot(groups,0,true,true,pending));
            } finally { db.endTransaction(); }
        }
    }
    static Set<String> columns(SQLiteDatabase db,String table) {
        var result=new java.util.HashSet<String>();
        try(var rows=db.rawQuery("PRAGMA table_info(`"+table+"`)",null)) {
            while(rows.moveToNext()) result.add(rows.getString(rows.getColumnIndexOrThrow("name")));
        }
        return result;
    }
    static Schema schema(SQLiteDatabase db) {
        if(columns(db,"module_configs").containsAll(Set.of("module_pkg_name","user_id","group_name","key_name","data")))
            return new Schema("module_configs","group_name","key_name");
        if(columns(db,"configs").containsAll(Set.of("module_pkg_name","user_id","group","key","data")))
            return new Schema("configs","group","key");
        throw new IllegalStateException("Unsupported framework preference schema");
    }
    static LegacyState state(SQLiteDatabase db) {
        if(columns(db,"modules_state").containsAll(Set.of("module_pkg_name","user_id","enabled"))) {
            try(var rows=db.query("modules_state",new String[]{"enabled"},"module_pkg_name=? AND user_id=0",
                    new String[]{MigrationProtocol.LEGACY},null,null,null)) {
                if(rows.getCount()>1) return LegacyState.UNKNOWN;
                if(!rows.moveToFirst()) return LegacyState.DISABLED;
                return rows.getType(0)==android.database.Cursor.FIELD_TYPE_INTEGER?flag(rows.getInt(0)):LegacyState.UNKNOWN;
            }
        }
        if(columns(db,"modules").containsAll(Set.of("module_pkg_name","enabled"))) {
            try(var rows=db.query("modules",new String[]{"enabled"},"module_pkg_name=?",
                    new String[]{MigrationProtocol.LEGACY},null,null,null)) {
                if(rows.getCount()>1) return LegacyState.UNKNOWN;
                if(!rows.moveToFirst()) return LegacyState.DISABLED;
                return rows.getType(0)==android.database.Cursor.FIELD_TYPE_INTEGER?flag(rows.getInt(0)):LegacyState.UNKNOWN;
            }
        }
        return LegacyState.UNKNOWN;
    }
    private static LegacyState flag(int value) {return value==0?LegacyState.DISABLED:value==1?LegacyState.ENABLED:LegacyState.UNKNOWN;}
    static Object decodeValue(byte[] data) {
        if(data==null || data.length>65536) throw new IllegalArgumentException("Invalid preference size");
        try(var input=new ObjectInputStream(new ByteArrayInputStream(data)) {
            @Override protected Class<?> resolveClass(ObjectStreamClass descriptor) throws java.io.IOException,ClassNotFoundException {
                if(!Set.of("java.lang.Boolean","java.lang.Integer","java.lang.Number","java.lang.Long","java.lang.Float","java.lang.Double")
                        .contains(descriptor.getName())) throw new java.io.InvalidClassException("Unsupported preference class");
                return super.resolveClass(descriptor);
            }
            @Override protected Class<?> resolveProxyClass(String[] interfaces) throws java.io.IOException {
                throw new java.io.InvalidClassException("Proxy preferences are not supported");
            }
        }) {
            Object value=input.readUnshared();
            if(!(value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Float
                    || value instanceof Double || value instanceof String)) throw new IllegalArgumentException("Unsupported preference value");
            if(value instanceof String text && text.length()>16384) throw new IllegalArgumentException("Preference string too large");
            return value;
        } catch(java.io.IOException|ClassNotFoundException error) {throw new IllegalArgumentException("Invalid serialized preference",error);}
    }
    private static MigrationProtocol.Snapshot withLocalPresentation(MigrationProtocol.Snapshot source) throws Exception {
        int appearance=0;boolean glass=true;boolean pending=source.pendingPin();
        File ui=localPreference(new File("/data/user/0"),"ui_presentation.xml");
        if(ui.isFile()) {
            Map<String,Object> values=readXml(ui,false);
            if(values.get("appearance") instanceof Integer value && value>=0 && value<=2) appearance=value;
            if(values.get("liquid_navigation") instanceof Boolean value) glass=value;
        }
        File staged=localPreference(new File("/data/user_de/0"),"dev.howard.colorosenhance.sim_pending.xml");
        if(staged.isFile()) pending|=!readXml(staged,true).isEmpty();
        return new MigrationProtocol.Snapshot(source.groups(),appearance,glass,true,pending);
    }
    static File localPreference(File userRoot,String name) throws java.io.IOException {
        if(!Set.of("ui_presentation.xml","dev.howard.colorosenhance.sim_pending.xml").contains(name))
            throw new IllegalArgumentException("Unsupported local preference file");
        File expected=new File(userRoot.getCanonicalFile(),MigrationProtocol.LEGACY+"/shared_prefs/"+name);
        if(!expected.getCanonicalFile().equals(expected))throw new IllegalArgumentException("Redirected local preference path");
        return expected;
    }
    static Map<String,Object> readXml(File file,boolean stagingPresenceOnly) throws Exception {
        if(file.length()>262144) throw new IllegalArgumentException("Preferences XML too large");
        var values=new LinkedHashMap<String,Object>();
        try(var input=new FileInputStream(file)) {
            byte[] xml=input.readNBytes(262145);
            String document=new String(xml,StandardCharsets.UTF_8);
            if(xml.length>262144 || document.contains("<!DOCTYPE") || document.contains("<!ENTITY"))
                throw new IllegalArgumentException("Unsupported preference XML");
            XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
            parser.setInput(new ByteArrayInputStream(xml),StandardCharsets.UTF_8.name());
            while(parser.next()!=XmlPullParser.END_DOCUMENT) if(parser.getEventType()==XmlPullParser.START_TAG) {
                String key=parser.getAttributeValue(null,"name");
                if(key==null) continue;
                if(stagingPresenceOnly) {
                    if(key.matches("pending\\.(?:record|state)\\.[01]")) values.put("present",true);
                } else if("int".equals(parser.getName()) && "appearance".equals(key))
                    values.put(key,Integer.parseInt(parser.getAttributeValue(null,"value")));
                else if("boolean".equals(parser.getName()) && "liquid_navigation".equals(key)) {
                    String value=parser.getAttributeValue(null,"value");
                    if(!Set.of("true","false").contains(value)) throw new IllegalArgumentException("Invalid preference boolean");
                    values.put(key,Boolean.parseBoolean(value));
                }
            }
        }
        return values;
    }
    private RootMigrationReader() { }
}
