package dev.pixellock.coloros;

import java.util.Map;

/** Immutable snapshot of the module settings, shared by the settings screen and the SystemUI hook. */
public final class Config {
    public static final String GROUP = "config";

    public static final String CLOCK = "clock_enabled";
    public static final String CLOCK_SCALE = "clock_scale";
    // New key: the clock now sits where ColorOS puts it, so earlier offsets (tuned for the overlay) start over at 0.
    public static final String CLOCK_OFFSET = "clock_offset_v2_dp";
    public static final String CLOCK_ROUND = "clock_round";
    public static final String CLOCK_RESERVE = "clock_reserve";
    public static final String CLOCK_DATE = "clock_date";
    public static final String SHORTCUTS = "shortcut_enabled";
    public static final String SHORTCUT_TONE = "shortcut_tone";
    public static final String CLOCK_TONE = "clock_tone";
    public static final String CLOCK_AVOID = "clock_avoid";
    public static final String AOD = "aod_enabled";
    public static final String CLOCK_OVERLAP = "clock_overlap";
    public static final String CLOCK_GAP = "clock_gap";
    public static final String AOD_TONE = "aod_tone";
    public static final String DUMP = "dump_views";

    public static final int SCALE_MIN = 50, SCALE_MAX = 150, SCALE_DEFAULT = 100;
    public static final int OFFSET_MIN = -200, OFFSET_MAX = 200, OFFSET_DEFAULT = 0;
    public static final int ROUND_MIN = 0, ROUND_MAX = 100, ROUND_DEFAULT = 0;
    /** Index into Palette.TONES (accent1 10, 50, 100 ... 800). 1 = tone 50, the pale fingerprint-button look. */
    /** Digit overlap in % of the text size, cut-out ring in 0.1 % of the text size. */
    public static final int OVERLAP_MIN = 0, OVERLAP_MAX = 30, OVERLAP_DEFAULT = 12;
    public static final int GAP_MIN = 0, GAP_MAX = 60, GAP_DEFAULT = 25;
    public static final int TONE_MIN = 0, TONE_MAX = 9, TONE_DEFAULT = 1;
    /** Clock tone index: 7 = accent1 600, the deep look of earlier versions. */
    public static final int CLOCK_TONE_DEFAULT = 7;
    /** AOD clock tone index: 2 = accent1 100, the pale look of earlier versions. */
    public static final int AOD_TONE_DEFAULT = 2;

    public final boolean clock;
    public final int clockScale;
    public final int clockOffsetDp;
    public final int clockRound;
    public final boolean clockReserve;
    public final int clockOverlap;
    public final int clockGap;
    public final boolean clockDate;
    public final boolean shortcuts;
    public final int shortcutTone;
    public final int clockTone;
    public final boolean clockAvoid;
    public final boolean aod;
    public final int aodTone;
    public final boolean dump;

    private Config(Map<String, ?> m) {
        clock = bool(m, CLOCK, true);
        clockScale = clamp(integer(m, CLOCK_SCALE, SCALE_DEFAULT), SCALE_MIN, SCALE_MAX);
        clockOffsetDp = clamp(integer(m, CLOCK_OFFSET, OFFSET_DEFAULT), OFFSET_MIN, OFFSET_MAX);
        clockRound = clamp(integer(m, CLOCK_ROUND, ROUND_DEFAULT), ROUND_MIN, ROUND_MAX);
        clockReserve = bool(m, CLOCK_RESERVE, true);
        clockOverlap = clamp(integer(m, CLOCK_OVERLAP, OVERLAP_DEFAULT), OVERLAP_MIN, OVERLAP_MAX);
        clockGap = clamp(integer(m, CLOCK_GAP, GAP_DEFAULT), GAP_MIN, GAP_MAX);
        clockDate = bool(m, CLOCK_DATE, true);
        shortcuts = bool(m, SHORTCUTS, true);
        shortcutTone = clamp(integer(m, SHORTCUT_TONE, TONE_DEFAULT), TONE_MIN, TONE_MAX);
        clockTone = clamp(integer(m, CLOCK_TONE, CLOCK_TONE_DEFAULT), TONE_MIN, TONE_MAX);
        clockAvoid = bool(m, CLOCK_AVOID, true);
        aod = bool(m, AOD, true);
        aodTone = clamp(integer(m, AOD_TONE, AOD_TONE_DEFAULT), TONE_MIN, TONE_MAX);
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
                + " overlap=" + clockOverlap + " gap=" + clockGap
                + " reserve=" + clockReserve + " date=" + clockDate + " shortcuts=" + shortcuts
                + " tone=" + shortcutTone + " clockTone=" + clockTone + " avoid=" + clockAvoid + " aod=" + aod + " aodTone=" + aodTone + " dump=" + dump;
    }
}
