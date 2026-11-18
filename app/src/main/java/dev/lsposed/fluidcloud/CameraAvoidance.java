package dev.lsposed.fluidcloud;

import java.util.Map;

/**
 * Header space kept above card content when cards expand from the camera area.
 *
 * <p>The original module derives this space at runtime: it is the distance from the
 * capsule top (the camera anchor) down to where the OEM would place the card, so the
 * content keeps its native position. There is no fixed dp value. This class scales the
 * part of that space that lies below the camera cutout: 100% keeps the original
 * geometry, 0% leaves just enough header to cover the camera so content sits right
 * under its lower edge.
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
     * @param cameraClear px from the capsule top to the camera cutout bottom
     * @param percent 0..100 share of the space below the camera to keep
     * @return applied header space in px, never below the camera clearance and never above the original
     */
    static int apply(int inset, int cameraClear, int percent) {
        if (inset <= 0) return inset;
        int floor = Math.max(1, Math.min(inset, cameraClear));
        int share = Math.max(0, Math.min(100, percent));
        return floor + Math.round((inset - floor) * (share / 100f));
    }
}
