package dev.lsposed.fluidcloud;

import java.util.Map;

public record MediaWaveOptions(int intervalMs, int sensitivityPercent, int attackMs, int releaseMs,
                               boolean holdPaused, MediaWaveBands bands) {
    public static final int INTERVAL_MIN_MS = 20;
    public static final int INTERVAL_MAX_MS = 1000;
    public static final int INTERVAL_STEP_MS = 10;
    public static final int SENSITIVITY_MIN_PERCENT = 5;
    public static final int SENSITIVITY_MAX_PERCENT = 500;
    public static final int SENSITIVITY_STEP_PERCENT = 5;
    public static final int ATTACK_MIN_MS = 5;
    public static final int ATTACK_MAX_MS = 1000;
    public static final int ATTACK_STEP_MS = 5;
    public static final int RELEASE_MIN_MS = 10;
    public static final int RELEASE_MAX_MS = 2000;
    public static final int RELEASE_STEP_MS = 5;
    public static final MediaWaveOptions DEFAULTS = new MediaWaveOptions(80, 100, 45, 105, false);

    public MediaWaveOptions(int intervalMs, int sensitivityPercent, int attackMs, int releaseMs, boolean holdPaused) {
        this(intervalMs, sensitivityPercent, attackMs, releaseMs, holdPaused, MediaWaveBands.DEFAULTS);
    }

    public MediaWaveOptions {
        intervalMs = bound(intervalMs, INTERVAL_MIN_MS, INTERVAL_MAX_MS, INTERVAL_STEP_MS);
        sensitivityPercent = bound(sensitivityPercent, SENSITIVITY_MIN_PERCENT, SENSITIVITY_MAX_PERCENT,
                SENSITIVITY_STEP_PERCENT);
        attackMs = bound(attackMs, ATTACK_MIN_MS, ATTACK_MAX_MS, ATTACK_STEP_MS);
        releaseMs = bound(releaseMs, RELEASE_MIN_MS, RELEASE_MAX_MS, RELEASE_STEP_MS);
        bands = bands == null ? MediaWaveBands.DEFAULTS : bands;
    }

    static MediaWaveOptions from(Map<String, ?> values) {
        return new MediaWaveOptions(number(values, "wave_interval_ms", DEFAULTS.intervalMs()),
                number(values, "wave_sensitivity", DEFAULTS.sensitivityPercent()),
                number(values, "wave_attack_ms", DEFAULTS.attackMs()),
                number(values, "wave_release_ms", DEFAULTS.releaseMs()),
                Boolean.TRUE.equals(values.get("wave_hold_paused")), MediaWaveBands.from(values));
    }

    private static int number(Map<String, ?> values, String key, int fallback) {
        return values.get(key) instanceof Integer value ? value : fallback;
    }

    private static int bound(int value, int min, int max, int step) {
        int bounded = Math.max(min, Math.min(max, value));
        return min + Math.round((bounded - min) / (float) step) * step;
    }
}
