package dev.howard.colorosenhance.migration;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, acknowledged group writes. Failure never reports a partial import as success. */
public final class MigrationTransaction {
    public interface Store {
        Map<String,Object> read(String group);
        boolean replace(String group, Map<String,Object> values);
    }
    public record Outcome(boolean success, boolean rollbackConfirmed) { }
    public static Map<String,Map<String,Object>> capture(Store store) {
        var snapshot = new LinkedHashMap<String,Map<String,Object>>();
        for (String group : MigrationProtocol.GROUPS) snapshot.put(group,Map.copyOf(store.read(group)));
        return Map.copyOf(snapshot);
    }
    public static Outcome apply(Store store, Map<String,Map<String,Object>> expected,
                                Map<String,Map<String,Object>> target) {
        if (!expected.keySet().equals(target.keySet()) || !expected.keySet().equals(new java.util.HashSet<>(MigrationProtocol.GROUPS)))
            return new Outcome(false,true);
        var touched = new java.util.ArrayList<String>();
        try {
            if (!capture(store).equals(expected)) return new Outcome(false,true);
            // Disable each feature while installing its parameters. Enable only
            // after every parameter group has been acknowledged.
            for (String group : MigrationProtocol.GROUPS) {
                var values = new LinkedHashMap<>(target.get(group));
                if (!"integration".equals(group)) values.put("enabled",false);
                else values.put(PackageActivation.READY,false);
                touched.add(group);
                if (!store.replace(group,values) || !store.read(group).equals(values)) throw new IllegalStateException();
            }
            for (String group : MigrationProtocol.GROUPS) {
                if ("integration".equals(group)) continue;
                Map<String,Object> values=target.get(group);
                if (!store.read(group).equals(values) && (!store.replace(group,values) || !store.read(group).equals(values)))
                    throw new IllegalStateException();
            }
            Map<String,Object> integration=target.get("integration");
            if(!store.replace("integration",integration) || !store.read("integration").equals(integration))
                throw new IllegalStateException();
            return new Outcome(true,true);
        } catch (RuntimeException failure) {
            boolean restored=true;
            java.util.Collections.reverse(touched);
            for (String group : touched) {
                try { if (!store.replace(group,expected.get(group)) || !store.read(group).equals(expected.get(group))) restored=false; }
                catch (RuntimeException error) { restored=false; }
            }
            // If rollback cannot be proved, fail closed instead of leaving an
            // uncertain partially enabled destination beside the old package.
            if (!restored) for (String group : MigrationProtocol.GROUPS) {
                try { var safe=new LinkedHashMap<>(store.read(group));
                    safe.put("integration".equals(group)?PackageActivation.READY:"enabled",false);store.replace(group,safe); }
                catch (RuntimeException ignored) { }
            }
            return new Outcome(false,restored);
        }
    }
    private MigrationTransaction() { }
}
