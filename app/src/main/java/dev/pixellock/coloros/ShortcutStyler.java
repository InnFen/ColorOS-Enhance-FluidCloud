package dev.pixellock.coloros;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds the two lock screen shortcut buttons (bottom-left / bottom-right, e.g. flashlight and camera) and gives
 * them a solid circle in a pale system accent tone, like the fingerprint button, with an icon that turns dark or
 * light to match. Anything inside quick settings / control center containers or a grid of tiles is never touched.
 */
final class ShortcutStyler {
    private final HookEntry m;
    private final Restorer restorer = new Restorer();
    private View left, right;
    private MorphKeyDrawable leftBg, rightBg;
    private String lastLog = "";

    ShortcutStyler(HookEntry m) { this.m = m; }

    /**
     * Words in class or id names of containers that are never the lock screen bottom area: quick settings,
     * control center (including third-party ones like myfc), media players, lists and grids of tiles.
     * ("notification" is not here: on AOSP-based builds the bottom area itself sits inside NotificationPanelView.)
     */
    private static final String[] FOREIGN = {
            "qs", "quicksetting", "controlcenter", "control_center", "controlpanel", "control_panel", "tile",
            "media", "brightness", "myfc", "recyclerview", "viewpager", "gridlayout", "gridview", "listview",
            "volume", "scroll", "picker", "edit", "list", "grid", "pager", "recycler", "adapter", "drawer",
    };

    void apply(List<View> tree, int screenW, int screenH, Palette p, Set<View> exclude) {
        // Pass 1: clickable views. Pass 2 (only for a side still empty): views with a background or an icon,
        // for buttons that react to touch listeners without being "clickable".
        View[] strict = pick(tree, screenW, screenH, exclude, true);
        View[] loose = (strict[0] == null || strict[1] == null) ? pick(tree, screenW, screenH, exclude, false) : strict;
        View newLeft = strict[0] != null ? strict[0] : loose[0];
        View newRight = strict[1] != null ? strict[1] : loose[1];
        // Keep the current buttons through brief misses (animations), but only while they still pass every check.
        if (newLeft == null && left != null && left.isAttachedToWindow() && stillLockscreenButton(left)) newLeft = left;
        if (newRight == null && right != null && right.isAttachedToWindow() && stillLockscreenButton(right)) newRight = right;
        if (newLeft != left || newRight != right) {
            restorer.restoreAll();
            left = newLeft;
            right = newRight;
            leftBg = null;
            rightBg = null;
        }
        if (left != null) leftBg = style(left, leftBg, p);
        if (right != null) rightBg = style(right, rightBg, p);
        String log = "Shortcuts: left=" + (left == null ? "none" : Views.describe(left))
                + " | right=" + (right == null ? "none" : Views.describe(right))
                + " | bg " + Palette.hex(p.shortcutBg) + " icon " + Palette.hex(p.shortcutIcon);
        if (!log.equals(lastLog)) { lastLog = log; m.info(log); }
    }

    private static View[] pick(List<View> tree, int screenW, int screenH, Set<View> exclude, boolean clickableOnly) {
        View newLeft = null, newRight = null;
        float bestLeft = Float.MAX_VALUE, bestRight = Float.MAX_VALUE;
        for (View v : tree) {
            boolean clickable = v.isClickable() || v.isLongClickable();
            if (clickableOnly ? !clickable : !(v.getBackground() != null || v instanceof ImageView)) continue;
            int w = v.getWidth(), h = v.getHeight();
            float min = Views.dp(v, 36), max = Views.dp(v, 120);
            if (w < min || h < min || w > max || h > max) continue;
            float ratio = w / (float) h;
            if (ratio < 0.75f || ratio > 1.33f) continue;
            if (excluded(v, exclude) || hasDigit(v)) continue;
            Rect r = Views.screen(v);
            if (r.centerY() < screenH * 0.78f) continue;
            if (insideForeign(v) || hasLabel(v) || insideScrollable(v)) continue;
            // Prefer the outermost match: skip views inside an already chosen one.
            if (newLeft != null && Views.isAncestor(newLeft, v)) continue;
            if (newRight != null && Views.isAncestor(newRight, v)) continue;
            float distBottom = screenH - r.centerY();
            if (r.centerX() < screenW * 0.3f) {
                float d = r.centerX() + distBottom;
                if (d < bestLeft) { bestLeft = d; newLeft = v; }
            } else if (r.centerX() > screenW * 0.7f) {
                float d = (screenW - r.centerX()) + distBottom;
                if (d < bestRight) { bestRight = d; newRight = v; }
            }
        }
        // Lock screen shortcuts stand alone in their row (the fingerprint button may sit between them). A row of
        // three or more look-alike buttons is a grid of tiles, e.g. a control center, so leave it alone.
        if (newLeft != null && crowdedRow(tree, newLeft)) newLeft = null;
        if (newRight != null && crowdedRow(tree, newRight)) newRight = null;
        return new View[]{newLeft, newRight};
    }

    private static boolean stillLockscreenButton(View v) {
        return !insideForeign(v) && !hasLabel(v) && !insideScrollable(v);
    }

    /** Lock screen shortcuts are bare icons. Control center tiles and picker items carry a text label. */
    private static boolean hasLabel(View v) {
        for (TextView t : Views.texts(v)) {
            if (t.getVisibility() == View.VISIBLE && !Views.text(t).trim().isEmpty()) return true;
        }
        // A label right under the button (sibling in the same cell) gives a tile away too.
        ViewParent parent = v.getParent();
        if (parent instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) parent;
            Rect b = Views.screen(v);
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c == v || !(c instanceof TextView) || c.getVisibility() != View.VISIBLE) continue;
                if (Views.text(c).trim().isEmpty()) continue;
                Rect r = Views.screen(c);
                if (r.top >= b.bottom - b.height() * 0.2f && r.top - b.bottom < b.height() * 0.6f) return true;
            }
        }
        return false;
    }

    /** Lists and pagers (control center editors, pickers) scroll; the lock screen bottom area does not. */
    private static boolean insideScrollable(View v) {
        ViewParent p = v.getParent();
        while (p instanceof View) {
            View g = (View) p;
            if (g.canScrollVertically(1) || g.canScrollVertically(-1)
                    || g.canScrollHorizontally(1) || g.canScrollHorizontally(-1)) return true;
            if (g instanceof android.widget.AdapterView || g instanceof android.widget.ScrollView
                    || g instanceof android.widget.HorizontalScrollView) return true;
            p = g.getParent();
        }
        return false;
    }

    private static boolean insideForeign(View v) {
        View cur = v;
        while (cur != null) {
            String cls = cur.getClass().getName().toLowerCase(Locale.ROOT);
            String id = Views.id(cur).toLowerCase(Locale.ROOT);
            for (String word : FOREIGN) {
                if (cls.contains(word) || id.contains(word)) return true;
            }
            ViewParent parent = cur.getParent();
            cur = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static boolean crowdedRow(List<View> tree, View button) {
        Rect b = Views.screen(button);
        List<View> same = new ArrayList<>();
        for (View v : tree) {
            if (v == button || Views.isAncestor(v, button) || Views.isAncestor(button, v)) continue;
            if (!(v.isClickable() || v.getBackground() != null)) continue;
            if (Math.abs(v.getWidth() - b.width()) > b.width() * 0.25f
                    || Math.abs(v.getHeight() - b.height()) > b.height() * 0.25f) continue;
            Rect r = Views.screen(v);
            if (Math.abs(r.centerY() - b.centerY()) > b.height() * 0.4f) continue;
            boolean nested = false;
            for (View o : same) if (Views.isSelfOrAncestor(o, v) || Views.isAncestor(v, o)) { nested = true; break; }
            if (!nested) same.add(v);
        }
        return same.size() >= 3;
    }

    private MorphKeyDrawable style(View button, MorphKeyDrawable bg, Palette p) {
        boolean active = isActive(button);
        int color = active ? p.shortcutBgActive : p.shortcutBg;
        int pressed = p.shortcutPressed(active);
        int icon = active ? p.shortcutIconActive : p.shortcutIcon;
        if (bg == null) bg = new MorphKeyDrawable(color, pressed, 0.3f);
        bg.setColors(color, pressed);
        restorer.background(button, bg);
        int w = button.getWidth(), h = button.getHeight();
        for (View d : Views.descendants(button)) {
            if (d == button) continue;
            // Drop ColorOS's own translucent circle if it sits on a child.
            if (d.getBackground() != null && d.getWidth() >= w * 0.7f && d.getHeight() >= h * 0.7f) {
                restorer.clearBackground(d);
            }
        }
        for (ImageView iv : Views.images(button)) {
            // A child icon gets tinted through its layer, which ColorOS can't reset. If the button itself is the
            // ImageView (background and icon on one view), tint the drawable and re-check it every scan.
            if (iv == button) restorer.enforceDrawableTint(iv, icon);
            else { restorer.tint(iv, icon); restorer.tintLayer(iv, icon); }
        }
        for (TextView tv : Views.texts(button)) restorer.text(tv, icon, null);
        return bg;
    }

    private static boolean isActive(View v) {
        for (View d : Views.descendants(v)) if (d.isActivated() || d.isSelected()) return true;
        return false;
    }

    private static boolean hasDigit(View v) {
        for (TextView t : Views.texts(v)) {
            String s = Views.text(t).trim();
            if (s.length() == 1 && Character.isDigit(s.charAt(0))) return true;
        }
        return false;
    }

    private static boolean excluded(View v, Set<View> exclude) {
        for (View e : exclude) if (Views.isSelfOrAncestor(e, v)) return true;
        return false;
    }

    void release() {
        restorer.restoreAll();
        left = right = null;
        leftBg = rightBg = null;
        lastLog = "";
    }
}
