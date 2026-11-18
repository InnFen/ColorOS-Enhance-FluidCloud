package dev.howard.colorosenhance;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class SettingsCoordinatorTest {
    private final Source source = new Source();
    private final SettingsCoordinator coordinator = new SettingsCoordinator(() -> source, () -> {});

    @Test public void twoFrontendsShareAcknowledgedValuesAndRejectStaleWrite() {
        PreferenceSession a = coordinator.session("battery");
        assertNotNull("Shared configuration owner must exist", a);
        assertSame(a, coordinator.session("battery"));
        long old = coordinator.read("battery").revision();
        assertTrue(a.save("enabled", true));
        assertEquals("conflict", coordinator.write("battery", "weight", 650, old).status());
        long latest = coordinator.read("battery").revision();
        assertEquals("ok", coordinator.write("battery", "weight", 650, latest).status());
        assertEquals(Map.of("enabled",true,"weight",650), source.store("battery").data);
    }

    @Test public void unknownValuesSurviveKnownKeyChangesAndSnapshotsCannotMutateStore() {
        source.store("battery").data.put("future_values", Set.of("x", "y"));
        SettingsCoordinator.State state = coordinator.read("battery");
        assertNotNull(state);
        assertEquals("ok", coordinator.write("battery", "weight", 650, state.revision()).status());
        assertEquals(Set.of("x","y"), source.store("battery").data.get("future_values"));
        assertThrows(UnsupportedOperationException.class, () -> coordinator.read("battery").values().put("enabled",true));
    }

    @Test public void invalidInputsNeverReachPersistence() {
        for (Object[] attempt : List.of(new Object[]{"secret", "pin", "1234"},
                new Object[]{"battery", "pin", 1234}, new Object[]{"battery", "weight", 651},
                new Object[]{"battery", "weight", 1001}, new Object[]{"battery", "enabled", "true"})) {
            assertEquals("invalid", coordinator.write((String)attempt[0], (String)attempt[1], attempt[2], -1).status());
        }
        assertTrue(source.stores.values().stream().allMatch(s -> s.writes == 0));
        assertEquals("invalid", coordinator.reset("sim", -1).status());
    }

    @Test public void failedRollbackRemainsUncertainUntilActualConnectionChanges() {
        PreferenceSession session = coordinator.session("aod");
        assertNotNull(session);
        source.store("aod").failAlways = true;
        long version = coordinator.read("aod").revision();
        assertEquals("uncertain", coordinator.write("aod","enabled",true,version).status());
        coordinator.refresh();
        assertFalse(coordinator.read("aod").writable());
        assertFalse(coordinator.read("aod").values().containsKey("enabled"));
        assertSame(session, coordinator.session("aod"));
        source.identity = new Object(); source.store("aod").failAlways = false;
        assertTrue(coordinator.read("aod").writable());
        assertEquals("conflict", coordinator.write("aod","enabled",true,version).status());
    }

    @Test public void refreshedExternalChangeConflictsWithOldSnapshot() {
        SettingsCoordinator.State old = coordinator.read("cloud");
        assertNotNull(old);
        source.store("cloud").data.put("recents", true);
        coordinator.refresh();
        assertEquals("conflict", coordinator.write("cloud","fullscreen",true,old.revision()).status());
        assertEquals(Boolean.TRUE, coordinator.read("cloud").values().get("recents"));
    }

    @Test public void entryVisibilityDoesNotBecomeAFifthEnabledFeature() {
        assertNotNull(coordinator.session("integration"));
        assertTrue(coordinator.session("integration").flag("show_entry",true));
        long version = coordinator.read("integration").revision();
        assertEquals("ok",coordinator.write("integration","show_entry",false,version).status());
        assertNull(SettingsCatalog.find("integration"));
        assertFalse(coordinator.session("battery").flag("enabled",false));
    }

    private static class Source implements SettingsCoordinator.Source {
        Object identity = new Object(); final Map<String,Store> stores = new HashMap<>();
        public Object identity() { return identity; }
        public PreferenceSession.Store open(String group) { return store(group); }
        Store store(String group) { return stores.computeIfAbsent(group,k -> new Store()); }
    }
    private static class Store implements PreferenceSession.Store {
        final Map<String,Object> data = new HashMap<>(); boolean failAlways; int writes;
        public Map<String,?> read() { return new HashMap<>(data); }
        public boolean replace(Map<String,Object> next) { writes++; data.clear(); data.putAll(next); return !failAlways; }
    }
}
