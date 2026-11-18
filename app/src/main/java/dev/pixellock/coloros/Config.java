package dev.pixellock.coloros;

import java.util.Map;

/** Immutable snapshot of the module settings, shared by the settings screen and the SystemUI hook. */
public final class Config {
    public static final String GROUP = "config";

    public static final String CLOCK = "clock_enabled";
    public static final String CLOCK_SCALE = "clock_scale";
    public static final String CLOCK_OFFSET = "clock_offset_dp";
    public static final String CLOCK_ROUND = "clock_round";
    public static final String CLOCK_RESERVE = "clock_reserve";
    public static final String CLOCK_DATE = "clock_date";
    public static final String SHORTCUTS = "shortcut_enabled";
    public static final String DUMP = "dump_views";

    public static final int SCALE_MIN = 50, SCALE_MAX = 150, SCALE_DEFAULT = 100;
    public static final int OFFSET_MIN = -200, OFFSET_MAX = 200, OFFSET_DEFAULT = 0;
    public static final int ROUND_MIN = 0, ROUND_MAX = 100, ROUND_DEFAULT = 30;

    public final boolean clock;
    public final int clockScale;
    public final int clockOffsetDp;
    public final int clockRound;
    public final boolean clockReserve;
    public final boolean clockDate;
    public final boolean shortcuts;
    public final boolean dump;

    private Config(Map<String, ?> m) {
        clock = bool(m, CLOCK, true);
        clockScale = clamp(integer(m, CLOCK_SCALE, SCALE_DEFAULT), SCALE_MIN, SCALE_MAX);
        clockOffsetDp = clamp(integer(m, CLOCK_OFFSET, OFFSET_DEFAULT), OFFSET_MIN, OFFSET_MAX);
        clockRound = clamp(integer(m, CLOCK_ROUND, ROUND_DEFAULT), ROUND_MIN, ROUND_MAX);
        clockReserve = bool(m, CLOCK_RESERVE, true);
        clockDate = bool(m, CLOCK_DATE, true);
        shortcuts = bool(m, SHORTCUTS, true);
        dump = bool(m, DUMP, false);
    }

    public static Config from(Map<String, ?> values) {
        return new Config(values == null ? Map.of() : values);
    }

    private static boolean bool(Map<String, ?> m, String key, boolean fallback) {
        Object v = m.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(Map<String, ?> m, String key, int fallback) {
        Object v = m.get(key);
        return v instanceof Integer ? (Integer) v : fallback;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override public String toString() {
        return "clock=" + clock + " scale=" + clockScale + " offsetDp=" + clockOffsetDp + " round=" + clockRound
                + " reserve=" + clockReserve + " date=" + clockDate + " shortcuts=" + shortcuts
                + " dump=" + dump;
    }
}
