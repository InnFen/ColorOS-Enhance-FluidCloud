package dev.howard.colorosenhance;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Only acknowledged writes change the settings presented to the user. No credentials belong here. */
public final class PreferenceSession {
    public interface Store {
        Map<String, ?> read();
        boolean replace(Map<String, Object> values);
    }
    private final Store store;
    private static final AtomicLong CLOCK = new AtomicLong();
    private final Runnable changed;
    private final Map<String,Object> resetDefaults;
    private Map<String, Object> committed;
    private boolean available = true;
    private long revision = CLOCK.incrementAndGet();
    public enum Change { SAVED, CONFLICT, FAILED, UNCONFIRMED }
    public PreferenceSession(Store store) {
        this(store, () -> {});
    }
    public PreferenceSession(Store store, Runnable changed) {
        this(store,changed,Map.of());
    }
    public PreferenceSession(Store store, Runnable changed, Map<String,Object> resetDefaults) {
        this.store = store;
        this.changed = changed;
        this.resetDefaults=Map.copyOf(resetDefaults);
        committed = copy(store.read());
    }
    public synchronized boolean available() { return available; }
    public synchronized long revision() { return revision; }
    public synchronized Map<String,Object> snapshot() { return Collections.unmodifiableMap(copy(committed)); }
    public synchronized boolean flag(String key, boolean fallback) {
        Object value = committed.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
    public synchronized int number(String key, int fallback, int min, int max) {
        Object value = committed.get(key);
        return value instanceof Integer ? Math.max(min, Math.min(max, (Integer) value)) : fallback;
    }
    public synchronized boolean save(String key, Object value) {
        Map<String, Object> next = new HashMap<>(committed);
        next.put(key, value);
        return replace(next);
    }
    public synchronized boolean reset() { return replace(resetDefaults); }
    public synchronized Change saveAtRevision(long expected, String key, Object value) {
        if (!available) return Change.UNCONFIRMED;
        if (expected != revision) return Change.CONFLICT;
        return save(key,value) ? Change.SAVED : available ? Change.FAILED : Change.UNCONFIRMED;
    }
    public synchronized Change resetAtRevision(long expected) {
        if (!available) return Change.UNCONFIRMED;
        if (expected != revision) return Change.CONFLICT;
        return reset() ? Change.SAVED : available ? Change.FAILED : Change.UNCONFIRMED;
    }
    public synchronized Change clearKeysAtRevision(long expected, Set<String> keys) {
        if (!available) return Change.UNCONFIRMED;
        if (expected != revision) return Change.CONFLICT;
        Map<String,Object> next = new HashMap<>(committed);
        next.keySet().removeAll(keys);
        return replace(next) ? Change.SAVED : available ? Change.FAILED : Change.UNCONFIRMED;
    }
    public synchronized void refresh() {
        if (!available) return;
        try {
            Map<String,Object> current = copy(store.read());
            if (!committed.equals(current)) { committed = current; signal(); }
        } catch (RuntimeException error) { available = false; signal(); }
    }
    private boolean replace(Map<String, Object> next) {
        if (!available) return false;
        try {
            if (store.replace(next)) { committed = copy(next); signal(); return true; }
        } catch (RuntimeException ignored) { }
        // Android preferences may change their local snapshot even when persistence fails.
        try { if (!store.replace(committed)) available = false; }
        catch (RuntimeException ignored) { available = false; }
        signal();
        return false;
    }
    private void signal() { revision = CLOCK.incrementAndGet(); changed.run(); }
    private static Map<String,Object> copy(Map<String,?> original) {
        Map<String,Object> result = new HashMap<>();
        original.forEach((key,value) -> result.put(key, value instanceof Set<?> set ? Set.copyOf(set) : value));
        return result;
    }
}
