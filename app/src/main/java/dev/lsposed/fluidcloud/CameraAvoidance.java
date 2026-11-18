package dev.lsposed.fluidcloud;

import java.util.Map;

/**
 * Header space kept above card content when cards expand from the camera area.
 *
 * <p>The original module derives this space at runtime: it is the distance from the
 * capsule top (the camera anchor) down to where the OEM would place the card, so the
 * content keeps its native position. There is no fixed dp value. This class scales that
 * whole space linearly: 100% keeps the original geometry, 0% adds no header at all, so
 * the content sits at its own native padding below the card top. Low values can let the
 * content's top band reach the camera's height; the camera itself sits in the middle of
 * the card, where card layouts normally leave room.
 */
public final class CameraAvoidance {
    public static final String KEY = "camera_avoid_percent";
    public static final int DEFAULT_PERCENT = 50;

    private CameraAvoidance() { }

    static int percent(Map<String, ?> values) {
        Object value = values == null ? null : values.get(KEY);
        int percent = value instanceof Integer number ? number : DEFAULT_PERCENT;
        return Math.max(0, Math.min(100, percent));
    }

    /**
     * @param inset original header space in px (capsule top to native card top)
     * @param percent 0..100 share of that space to keep
     * @return applied header space in px, at least 1 (so the card stays adapted) and never above the original
     */
    static int apply(int inset, int percent) {
        if (inset <= 0) return inset;
        int share = Math.max(0, Math.min(100, percent));
        return Math.max(1, Math.round(inset * (share / 100f)));
    }
}
