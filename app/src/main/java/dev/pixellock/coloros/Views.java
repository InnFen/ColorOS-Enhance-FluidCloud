package dev.pixellock.coloros;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Small view-tree helpers. */
final class Views {
    private Views() { }

    static float dp(View v, float dp) { return dp * v.getResources().getDisplayMetrics().density; }

    static String id(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return "-";
        try { return v.getResources().getResourceEntryName(id); } catch (Throwable t) { return "0x" + Integer.toHexString(id); }
    }

    static Rect screen(View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
    }

    /** Pre-order list of views that are VISIBLE all the way down (alpha may still be 0). */
    static List<View> visibleTree(View root, Set<View> skip) {
        List<View> out = new ArrayList<>();
        collect(root, skip, out);
        return out;
    }

    private static void collect(View v, Set<View> skip, List<View> out) {
        if (v.getVisibility() != View.VISIBLE || skip.contains(v)) return;
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), skip, out);
        }
    }

    static List<View> descendants(View v) {
        List<View> out = new ArrayList<>();
        collectAll(v, out);
        return out;
    }

    private static void collectAll(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectAll(g.getChildAt(i), out);
        }
    }

    static boolean isAncestor(View ancestor, View v) {
        ViewParent p = v.getParent();
        while (p instanceof View) {
            if (p == ancestor) return true;
            p = p.getParent();
        }
        return false;
    }

    static boolean isSelfOrAncestor(View ancestor, View v) { return ancestor == v || isAncestor(ancestor, v); }

    static View commonAncestor(List<View> views) {
        if (views.isEmpty()) return null;
        View candidate = views.get(0);
        while (candidate != null) {
            boolean all = true;
            for (View v : views) if (!isSelfOrAncestor(candidate, v)) { all = false; break; }
            if (all) return candidate;
            ViewParent p = candidate.getParent();
            candidate = p instanceof View ? (View) p : null;
        }
        return null;
    }

    static String text(View v) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            return t == null ? "" : t.toString();
        }
        return "";
    }

    /** All text and content descriptions inside v, lower-cased, for keyword matching. */
    static String labels(View v) {
        StringBuilder sb = new StringBuilder();
        for (View d : descendants(v)) {
            CharSequence cd = d.getContentDescription();
            if (cd != null) sb.append(cd).append(' ');
            if (d instanceof TextView) sb.append(text(d)).append(' ');
        }
        sb.append(id(v));
        return sb.toString().toLowerCase(java.util.Locale.ROOT);
    }

    static List<ImageView> images(View v) {
        List<ImageView> out = new ArrayList<>();
        for (View d : descendants(v)) if (d instanceof ImageView) out.add((ImageView) d);
        return out;
    }

    static List<TextView> texts(View v) {
        List<TextView> out = new ArrayList<>();
        for (View d : descendants(v)) if (d instanceof TextView) out.add((TextView) d);
        return out;
    }

    static String describe(View v) {
        Rect r = screen(v);
        return v.getClass().getName() + " id=" + id(v) + " " + r.toShortString()
                + " " + v.getWidth() + "x" + v.getHeight() + "px";
    }
}
