package dev.howard.colorosenhance;

import dev.lsposed.fluidcloud.MediaWaveBands;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Supplier;

/** Application-owned configuration boundary shared by both frontends. */
public final class SettingsCoordinator {
    private static final Set<String> WAVE_PARAMETERS = Set.of(MediaWaveBands.KEY, "wave_interval_ms",
            "wave_sensitivity", "wave_attack_ms", "wave_release_ms", "wave_hold_paused");
    public interface Source { Object identity(); PreferenceSession.Store open(String group); }
    public record State(long revision, Map<String,Object> values, boolean writable) {}
    public record Result(String status, State state) {}
    private final Supplier<Source> source;
    private final Runnable changed;
    private final java.util.function.BooleanSupplier canonicalRinger;
    private final Map<String,PreferenceSession> sessions = new HashMap<>();
    private Object identity;
    public SettingsCoordinator(Supplier<Source> source, Runnable changed) { this(source,changed,()->false); }
    public SettingsCoordinator(Supplier<Source> source,Runnable changed,java.util.function.BooleanSupplier canonicalRinger) {
        this.source=source;this.changed=changed;this.canonicalRinger=canonicalRinger;
    }
    public synchronized PreferenceSession session(String group) {
        if (!knownGroup(group)) return null;
        try {
            Source current = source.get();
            Object next = current == null ? null : current.identity();
            if (identity != next) { sessions.clear(); identity = next; }
            if (next == null) return null;
            PreferenceSession existing = sessions.get(group);
            if (existing != null) return existing;
            PreferenceSession.Store store=current.open(group);
            if ("installer".equals(group)) dev.howard.colorosenhance.installer.InstallerPreferences.initialize(store);
            if ("icons".equals(group) && canonicalRinger.getAsBoolean()) dev.howard.colorosenhance.icons.StatusIconPreferences.initialize(store);
            PreferenceSession created = new PreferenceSession(store,changed,
                    "installer".equals(group) ? Map.of("target_package","") : Map.of());
            sessions.put(group,created); return created;
        } catch (RuntimeException error) { return null; }
    }
    public synchronized State read(String group) {
        PreferenceSession session = session(group);
        if (session == null) return null;
        synchronized (session) { return new State(session.revision(),session.snapshot(),session.available()); }
    }
    public synchronized Result write(String group,String key,Object value,long expectedRevision) {
        if (!validValue(group,key,value)) return new Result("invalid",null);
        PreferenceSession session = session(group);
        if (session == null) return new Result("unavailable",null);
        if ("cloud".equals(group) && MediaWaveBands.KEY.equals(key)) {
            synchronized (session) {
                if (waveEditingDisabled(session, expectedRevision)) return new Result("invalid", read(group));
                String encoded = MediaWaveBands.parse((String) value).encode();
                return result(group, session.saveAtRevision(expectedRevision, key, encoded));
            }
        }
        return result(group,session.saveAtRevision(expectedRevision,key,value));
    }

    public synchronized Result resetWave(long expectedRevision) {
        PreferenceSession session = session("cloud");
        if (session == null) return new Result("unavailable", null);
        synchronized (session) {
            if (waveEditingDisabled(session, expectedRevision)) return new Result("invalid", read("cloud"));
            return result("cloud", session.clearKeysAtRevision(expectedRevision, WAVE_PARAMETERS));
        }
    }

    private static boolean waveEditingDisabled(PreferenceSession session, long expectedRevision) {
        return session.available() && session.revision() == expectedRevision
                && !SettingsCatalog.controlEnabled("cloud", MediaWaveBands.KEY, session.snapshot());
    }
    public synchronized Result reset(String group,long expectedRevision) {
        if (SettingsCatalog.find(group) == null || "sim".equals(group)) return new Result("invalid",null);
        PreferenceSession session = session(group);
        return session == null ? new Result("unavailable",null) : result(group,session.resetAtRevision(expectedRevision));
    }
    public synchronized void refresh() {
        // Reconcile identity even when no page is currently open.
        session("integration");
        for (PreferenceSession session : sessions.values()) session.refresh();
    }
    private Result result(String group,PreferenceSession.Change change) {
        return new Result(switch (change) {
            case SAVED -> "ok"; case CONFLICT -> "conflict"; case FAILED -> "failed"; case UNCONFIRMED -> "uncertain";
        },read(group));
    }
    public static boolean knownGroup(String group) { return "integration".equals(group) || SettingsCatalog.find(group) != null; }
    public static boolean validValue(String group,String key,Object value) {
        if ("integration".equals(group)) return "show_entry".equals(key) && value instanceof Boolean;
        SettingsCatalog.Group catalog = SettingsCatalog.find(group);
        if (catalog == null || key == null) return false;
        if ("icons".equals(group)) return dev.howard.colorosenhance.icons.StatusIconConfig.validValue(key,value);
        if ("enabled".equals(key)) return value instanceof Boolean;
        if ("cloud".equals(group) && MediaWaveBands.KEY.equals(key)) {
            return value instanceof String text && MediaWaveBands.parse(text) != null;
        }
        if ("installer".equals(group) && "target_package".equals(key)) return value instanceof String text
                && dev.howard.colorosenhance.installer.InstallerPreferences.validPackage(text);
        for (SettingsCatalog.Item item : catalog.items()) {
            if (item.adjustable() && item.valueKey().equals(key)) return value instanceof Integer number
                    && number >= item.min() && number <= item.max() && (number-item.min()) % item.step() == 0;
            if (item.key().equals(key) && !item.valueOnly()) return value instanceof Boolean;
        }
        return false;
    }
}
