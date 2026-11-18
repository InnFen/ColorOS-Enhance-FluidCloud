package dev.howard.colorosenhance;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
public class PreferenceSessionTest {
    @Test public void disableKeepsValuesAndDoesNotChangeAnotherGroup() {
        MemoryStore a = new MemoryStore(), b = new MemoryStore();
        PreferenceSession battery = new PreferenceSession(a), aod = new PreferenceSession(b);
        battery.save("weight", 800); battery.save("enabled", true); battery.save("enabled", false);
        assertEquals(800, battery.number("weight", 570, 100, 1000));
        assertFalse(aod.flag("enabled", false)); assertTrue(b.data.isEmpty());
    }
    @Test public void failedCommitRollsBackMemoryAndDoesNotReportNewState() {
        MemoryStore store = new MemoryStore(); PreferenceSession session = new PreferenceSession(store);
        store.failOnce = true;
        assertFalse(session.save("enabled", true));
        assertFalse(session.flag("enabled", false)); assertFalse(store.data.containsKey("enabled"));
        assertTrue(session.available());
    }
    @Test public void unsuccessfulRollbackDisablesFurtherWrites() {
        MemoryStore store = new MemoryStore(); PreferenceSession session = new PreferenceSession(store);
        store.failAlways = true;
        assertFalse(session.save("enabled", true)); assertFalse(session.available());
        assertFalse(session.save("enabled", true)); assertEquals(2, store.writes);
    }
    @Test public void invalidStoredTypesDoNotEnableFeatures() {
        MemoryStore store = new MemoryStore(); store.data.put("enabled", "true");
        store.data.put("scale", "500"); PreferenceSession session = new PreferenceSession(store);
        assertFalse(session.flag("enabled", false)); assertEquals(100, session.number("scale",100,75,500));
    }
    static final class MemoryStore implements PreferenceSession.Store {
        final Map<String,Object> data = new HashMap<>(); boolean failOnce, failAlways; int writes;
        public Map<String,?> read() { return data; }
        public boolean replace(Map<String,Object> values) {
            writes++; data.clear(); data.putAll(values);
            boolean fail = failOnce || failAlways; failOnce = false; return !fail;
        }
    }
}
