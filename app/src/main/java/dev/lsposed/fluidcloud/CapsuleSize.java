package dev.lsposed.fluidcloud;

import java.util.Map;

/** Native viewport proportions; provider pixels keep their aspect ratio. */
public record CapsuleSize(boolean enabled, int widthPercent, int heightPercent) {
    static final CapsuleSize OEM = new CapsuleSize(false, 100, 100);
    public CapsuleSize {
        widthPercent = Math.max(50, Math.min(200, widthPercent));
        heightPercent = Math.max(50, Math.min(200, heightPercent));
    }
    static CapsuleSize from(Map<String, ?> values) {
        return new CapsuleSize(Boolean.TRUE.equals(values.get("capsule_size_enabled")),
                values.get("capsule_width_percent") instanceof Integer value ? value : 100,
                values.get("capsule_height_percent") instanceof Integer value ? value : 100);
    }
    boolean original() { return !enabled || widthPercent == 100 && heightPercent == 100; }
    static int dimension(int nativeValue, int percent, int maximum) {
        if (nativeValue <= 0) return nativeValue;
        return Math.max(1, Math.min(Math.max(nativeValue, maximum), Math.round(nativeValue * percent / 100f)));
    }
}
