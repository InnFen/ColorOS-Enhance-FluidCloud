package dev.howard.colorosenhance.migration;

import dev.howard.colorosenhance.BuildConfig;
import dev.howard.colorosenhance.SettingsCoordinator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Ordinary settings only. PINs, ICCIDs, keys and remote staging groups never enter this protocol. */
public final class MigrationProtocol {
    public static final String LEGACY = BuildConfig.LEGACY_APPLICATION_ID;
    public static final String CURRENT = BuildConfig.PUBLIC_APPLICATION_ID;
    public static final List<String> GROUPS = List.of("integration", "battery", "icons", "aod", "cloud", "sim", "installer");
    public static final int MAX_BYTES = 131072;
    public static boolean knownKey(String group,String key) {
        if(key==null || key.length()>128)return false;
        if("integration".equals(group))return "show_entry".equals(key);
        if("icons".equals(group))return dev.howard.colorosenhance.icons.IconOrder.KEY.equals(key)
                || dev.howard.colorosenhance.icons.StatusIconConfig.validValue(key,true)
                || dev.howard.colorosenhance.icons.StatusIconConfig.validValue(key,0);
        var catalog=dev.howard.colorosenhance.SettingsCatalog.find(group);
        if(catalog==null)return false;
        if("enabled".equals(key))return true;
        if("cloud".equals(group) && dev.lsposed.fluidcloud.MediaWaveBands.KEY.equals(key))return true;
        if("installer".equals(group) && "target_package".equals(key))return true;
        for(var item:catalog.items())if(item.key().equals(key) || item.adjustable() && item.valueKey().equals(key))return true;
        return false;
    }
    public record Snapshot(Map<String,Map<String,Object>> groups, int appearance, boolean glass,
                           boolean launcherVisible, boolean pendingPin) {
        public Snapshot {
            if (appearance < 0 || appearance > 2 || groups == null || !groups.keySet().equals(new java.util.HashSet<>(GROUPS)))
                throw new IllegalArgumentException("Invalid migration groups");
            var copy = new LinkedHashMap<String,Map<String,Object>>();
            for (String group : GROUPS) {
                Map<String,Object> values = groups.get(group);
                if (values == null || values.size() > 256) throw new IllegalArgumentException("Invalid migration group size");
                for (var entry : values.entrySet()) {
                    Object value = entry.getValue();
                    if (!SettingsCoordinator.validValue(group, entry.getKey(), value)
                            || value instanceof String text && text.length() > 16384)
                        throw new IllegalArgumentException("Unsupported migration value");
                }
                copy.put(group, Map.copyOf(values));
            }
            groups = Map.copyOf(copy);
        }
        public Map<String,Map<String,Object>> importGroups() {
            var result = new LinkedHashMap<String,Map<String,Object>>(groups);
            if (pendingPin) {
                var sim = new LinkedHashMap<String,Object>(result.get("sim")); sim.put("enabled", false);
                result.put("sim", Map.copyOf(sim));
            }
            return Map.copyOf(result);
        }
    }
    public static String encode(Snapshot snapshot) {
        try {
            var root = new JSONObject().put("schema",1).put("source",LEGACY).put("target",CURRENT)
                    .put("appearance",snapshot.appearance()).put("glass",snapshot.glass())
                    .put("launcher",snapshot.launcherVisible()).put("pending_pin",snapshot.pendingPin());
            var groups = new JSONObject();
            for (String group : GROUPS) groups.put(group,new JSONObject(snapshot.groups().get(group)));
            String result = root.put("groups",groups).toString();
            if (result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
                throw new IllegalArgumentException("Migration payload too large");
            return result;
        } catch (org.json.JSONException error) { throw new IllegalArgumentException("Invalid migration snapshot",error); }
    }
    public static Snapshot decode(String text) {
        if (text == null || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Invalid migration payload size");
        try {
            var root = new JSONObject(text);
            if (!Integer.valueOf(1).equals(root.get("schema")) || !LEGACY.equals(root.get("source"))
                    || !CURRENT.equals(root.get("target")) || root.length() != 8)
                throw new IllegalArgumentException("Migration identity mismatch");
            Object appearance = root.get("appearance"), glass = root.get("glass"), launcher = root.get("launcher"), pending = root.get("pending_pin");
            if (!(appearance instanceof Integer theme) || !(glass instanceof Boolean useGlass)
                    || !(launcher instanceof Boolean visible) || !(pending instanceof Boolean hasPending))
                throw new IllegalArgumentException("Invalid migration presentation");
            JSONObject groups = root.getJSONObject("groups");
            if (groups.length() != GROUPS.size()) throw new IllegalArgumentException("Invalid migration groups");
            var values = new LinkedHashMap<String,Map<String,Object>>();
            for (String group : GROUPS) {
                var source = groups.getJSONObject(group); var items = new LinkedHashMap<String,Object>();
                var keys = source.keys(); while (keys.hasNext()) { String key=keys.next();items.put(key,source.get(key)); }
                values.put(group,items);
            }
            return new Snapshot(values,theme,useGlass,visible,hasPending);
        } catch (org.json.JSONException error) { throw new IllegalArgumentException("Invalid migration payload",error); }
    }
    private MigrationProtocol() { }
}
