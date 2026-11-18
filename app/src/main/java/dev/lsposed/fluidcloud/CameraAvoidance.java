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
    /** Same meaning as the theme dimen offset_between_status_bar_and_card. */
    public static final String OFFSET_ENABLED = "camera_card_top_offset";
    public static final String OFFSET_KEY = "camera_card_top_offset_dp";
    public static final int DEFAULT_OFFSET_DP = -28;

    /** Chosen card offset in dp, or {@link Integer#MIN_VALUE} to follow the capsule top. */
    static int topOffsetDp(Map<String, ?> values) {
        Object enabled = values == null ? null : values.get(OFFSET_ENABLED);
        if (Boolean.FALSE.equals(enabled)) return Integer.MIN_VALUE;
        Object value = values == null ? null : values.get(OFFSET_KEY);
        int dp = value instanceof Integer number ? number : DEFAULT_OFFSET_DP;
        return Math.max(-80, Math.min(40, dp));
    }

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
        Found found = find(box);
        return found == null ? -1 : Math.max(0, found.top - paddingTop);
    }

    record Found(View leaf, int top) { }

    static Found find(ViewGroup box) {
        if (box == null || box.getWidth() <= 0 || box.getHeight() <= 0) return null;
        View[] leaf = new View[1];
        int best = find(box, box, 0, Integer.MAX_VALUE, leaf);
        return leaf[0] == null ? null : new Found(leaf[0], best);
    }

    private static int find(ViewGroup box, View view, int offset, int best, View[] leaf) {
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) return best;
        if (view instanceof CameraHeaderSurface || view instanceof CameraMaterialSurface) return best;
        int top = view == box ? 0 : offset + view.getTop();
        if (top >= best) return best;
        if (view instanceof ViewGroup group && group.getChildCount() > 0) {
            for (int i = 0; i < group.getChildCount(); i++)
                best = find(box, group.getChildAt(i), top, best, leaf);
            return best;
        }
        if (view == box || view.getWidth() <= 0 || view.getHeight() <= 0) return best;
        boolean backdrop = view.getHeight() >= box.getHeight() * .8f || view.getWidth() >= box.getWidth() * .95f
                && view.getHeight() >= box.getHeight() * .5f;
        if (backdrop) return best;
        leaf[0] = view;
        return top;
    }

    /**
     * Removes native top spacing (top padding and top margins) along the path from a content box
     * down to its first visible element, so content can sit closer than its own layout allows.
     * Every change is remembered and put back when the card leaves the camera geometry.
     */
    static final class Trimmer {
        private record Saved(int nativePadding, int nativeMargin, int appliedPadding, int appliedMargin) { }
        private final java.util.WeakHashMap<View, Saved> saved = new java.util.WeakHashMap<>();
        private final java.util.WeakHashMap<View, java.util.List<java.lang.ref.WeakReference<View>>> paths = new java.util.WeakHashMap<>();

        /** px currently removed inside {@code box}; add it to a measured lead to get the native one. */
        int trimmed(View box) {
            int total = 0;
            for (View view : views(box)) {
                Saved state = saved.get(view);
                if (state == null) continue;
                if (view.getPaddingTop() == state.appliedPadding()) total += state.nativePadding() - state.appliedPadding();
                if (margin(view) == state.appliedMargin()) total += state.nativeMargin() - state.appliedMargin();
            }
            return total;
        }

        /**
         * @param withBoxPadding whether the box's own top padding may be removed
         * @param amount px to remove; 0 restores the native spacing
         */
        void apply(ViewGroup box, boolean withBoxPadding, int amount) {
            // From the top down: the box's padding, then each ancestor's margin and padding, then the leaf's margin.
            java.util.List<View> chain = new java.util.ArrayList<>();
            if (amount > 0) {
                Found found = find(box);
                View view = found == null ? null : found.leaf();
                while (view != null && view != box) {
                    chain.add(0, view);
                    view = view.getParent() instanceof View parent ? parent : null;
                }
                if (view != box) chain.clear();
            }
            java.util.List<View> before = views(box);
            java.util.List<View> touched = new java.util.ArrayList<>();
            int left = Math.max(0, amount);
            if (amount > 0 && withBoxPadding) left = take(box, left, true, false, touched);
            for (int i = 0; i < chain.size(); i++)
                left = take(chain.get(i), left, i < chain.size() - 1, true, touched);
            for (View view : before) if (!touched.contains(view)) restore(view);
            java.util.List<java.lang.ref.WeakReference<View>> refs = new java.util.ArrayList<>();
            for (View view : touched) refs.add(new java.lang.ref.WeakReference<>(view));
            if (refs.isEmpty()) paths.remove(box); else paths.put(box, refs);
        }

        void restore(ViewGroup box) {
            for (View view : views(box)) restore(view);
            paths.remove(box);
        }

        void clear() {
            for (View box : new java.util.ArrayList<>(paths.keySet())) if (box instanceof ViewGroup group) restore(group);
            saved.clear();
        }

        private java.util.List<View> views(View box) {
            java.util.List<View> views = new java.util.ArrayList<>();
            var refs = paths.get(box);
            if (refs != null) for (var ref : refs) { View view = ref.get(); if (view != null) views.add(view); }
            return views;
        }

        /** Takes px from the view's top margin (first) and top padding; returns what is still left. */
        private int take(View view, int left, boolean padding, boolean margin, java.util.List<View> touched) {
            Saved state = saved.get(view);
            int nativePadding = state != null && view.getPaddingTop() == state.appliedPadding() ? state.nativePadding() : view.getPaddingTop();
            int currentMargin = margin(view);
            int nativeMargin = state != null && currentMargin == state.appliedMargin() ? state.nativeMargin() : currentMargin;
            int newMargin = nativeMargin, newPadding = nativePadding;
            if (margin && nativeMargin > 0 && left > 0) { int cut = Math.min(nativeMargin, left); newMargin -= cut; left -= cut; }
            if (padding && nativePadding > 0 && left > 0) { int cut = Math.min(nativePadding, left); newPadding -= cut; left -= cut; }
            if (newMargin == nativeMargin && newPadding == nativePadding) {
                if (state != null) restore(view);
                return left;
            }
            saved.put(view, new Saved(nativePadding, nativeMargin, newPadding, newMargin));
            touched.add(view);
            if (view.getPaddingTop() != newPadding)
                view.setPadding(view.getPaddingLeft(), newPadding, view.getPaddingRight(), view.getPaddingBottom());
            if (currentMargin != newMargin && view.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams params) {
                params.topMargin = newMargin; view.setLayoutParams(params);
            }
            return left;
        }

        private void restore(View view) {
            Saved state = saved.remove(view);
            if (state == null) return;
            if (view.getPaddingTop() == state.appliedPadding() && state.appliedPadding() != state.nativePadding())
                view.setPadding(view.getPaddingLeft(), state.nativePadding(), view.getPaddingRight(), view.getPaddingBottom());
            if (margin(view) == state.appliedMargin() && state.appliedMargin() != state.nativeMargin()
                    && view.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams params) {
                params.topMargin = state.nativeMargin(); view.setLayoutParams(params);
            }
        }

        private static int margin(View view) {
            return view.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams params ? params.topMargin : 0;
        }
    }
}
