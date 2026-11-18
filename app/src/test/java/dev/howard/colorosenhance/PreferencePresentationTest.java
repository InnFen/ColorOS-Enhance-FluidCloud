package dev.howard.colorosenhance;

import static org.junit.Assert.*;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class PreferencePresentationTest {
    @Test public void failedWriteAndRollbackExposeUncertaintyAndBlockAnotherWrite() {
        FaultStore store = new FaultStore();
        store.values.put("enabled", true);
        store.values.put("scale", 175);
        PreferenceSession session = new PreferenceSession(store);
        store.results.add(false); store.results.add(false);
        assertFalse(session.save("enabled", false));

        PreferencePresentation shown = PreferencePresentation.from(session);
        assertEquals(PreferencePresentation.State.UNCONFIRMED, shown.state());
        assertTrue(shown.lastAcknowledgedEnabled());
        assertEquals(175, session.number("scale", 100, 75, 500));
        assertFalse(shown.canWrite());
        assertFalse(session.save("scale", 250));
        assertEquals(2, store.writes);
    }

    @Test public void successfulRollbackCanPresentRestoredValuesAndAllowRetry() {
        FaultStore store = new FaultStore();
        PreferenceSession session = new PreferenceSession(store);
        store.results.add(false); store.results.add(true);
        assertFalse(session.save("enabled", true));

        PreferencePresentation shown = PreferencePresentation.from(session);
        assertEquals(PreferencePresentation.State.CONFIRMED, shown.state());
        assertFalse(shown.lastAcknowledgedEnabled());
        assertTrue(shown.canWrite());
        assertFalse(store.values.containsKey("enabled"));
        assertTrue(session.save("enabled", true));
        assertTrue(PreferencePresentation.from(session).lastAcknowledgedEnabled());
    }

    @Test public void newSessionAfterReconnectionUsesFreshAcknowledgedSettings() {
        FaultStore store = new FaultStore();
        PreferenceSession session = new PreferenceSession(store);
        store.results.add(false); store.results.add(false);
        assertFalse(session.reset());
        assertEquals(PreferencePresentation.State.UNCONFIRMED,
                PreferencePresentation.from(session).state());

        store.values.put("enabled", true);
        PreferenceSession reconnected = new PreferenceSession(store);
        PreferencePresentation shown = PreferencePresentation.from(reconnected);
        assertEquals(PreferencePresentation.State.CONFIRMED, shown.state());
        assertTrue(shown.lastAcknowledgedEnabled());
        assertTrue(shown.canWrite());
    }

    @Test public void noServiceHasNoConfirmedFeatureStatusOrWritableControls() {
        PreferencePresentation shown = PreferencePresentation.from(null);
        assertEquals(PreferencePresentation.State.DISCONNECTED, shown.state());
        assertFalse(shown.canWrite());
    }

    private static final class FaultStore implements PreferenceSession.Store {
        final Map<String, Object> values = new HashMap<>();
        final ArrayDeque<Boolean> results = new ArrayDeque<>();
        int writes;
        public Map<String, ?> read() { return values; }
        public boolean replace(Map<String, Object> next) {
            writes++;
            // Mirrors SharedPreferences' local-map mutation even if persistence is unacknowledged.
            values.clear(); values.putAll(next);
            return results.isEmpty() || results.removeFirst();
        }
    }
}
