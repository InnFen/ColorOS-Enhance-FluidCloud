package dev.lsposed.fluidcloud;

import android.view.View;
import android.view.ViewGroup;
import java.util.Map;

/**
 * Space between the camera cutout and the card content when cards expand from the camera area.
 *
 * <p>The original module keeps the card's native content position and fills the whole gap
 * between the capsule top (the camera anchor) and that position with header. Here the
 * content is pulled up instead: its topmost visible element is placed a small, adjustable
 * distance below the camera cutout, and only that much header is kept.
 */
public final class CameraAvoidance {
    public static final String KEY = "camera_content_gap_dp";
    public static final int DEFAULT_DP = 2;
    public static final int MAX_DP = 24;

    private CameraAvoidance() { }

    static int gapDp(Map<String, ?> values) {
        Object value = values == null ? null : values.get(KEY);
        int dp = value instanceof Integer number ? number : DEFAULT_DP;
        return Math.max(0, Math.min(MAX_DP, dp));
    }

    /**
     * Content-box offset from the card top that puts the first visible element at {@code target}.
     *
     * @param target px from the card top to where visible content should start (camera bottom plus gap)
     * @param lead px from the content box top to its first visible element, or -1 when not measured yet
     */
    static int boxTop(int target, int lead) {
        return Math.max(0, target - Math.max(0, lead));
    }

    /**
     * Distance from {@code box}'s top edge to its topmost visible, non-background leaf, or -1.
     * Views that span nearly the whole box are treated as backgrounds and skipped.
     */
    static int lead(ViewGroup box, int paddingTop) {
        if (box == null || box.getWidth() <= 0 || box.getHeight() <= 0) return -1;
        int best = lead(box, box, 0, Integer.MAX_VALUE);
        return best == Integer.MAX_VALUE ? -1 : Math.max(0, best - paddingTop);
    }

    private static int lead(ViewGroup box, View view, int offset, int best) {
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) return best;
        if (view instanceof CameraHeaderSurface || view instanceof CameraMaterialSurface) return best;
        int top = view == box ? 0 : offset + view.getTop();
        if (top >= best) return best;
        if (view instanceof ViewGroup group && group.getChildCount() > 0) {
            for (int i = 0; i < group.getChildCount(); i++)
                best = lead(box, group.getChildAt(i), view == box ? 0 : top, best);
            return best;
        }
        if (view == box || view.getWidth() <= 0 || view.getHeight() <= 0) return best;
        boolean backdrop = view.getHeight() >= box.getHeight() * .8f || view.getWidth() >= box.getWidth() * .95f
                && view.getHeight() >= box.getHeight() * .5f;
        return backdrop ? best : Math.min(best, top);
    }
}
