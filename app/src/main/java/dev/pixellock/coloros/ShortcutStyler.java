package dev.pixellock.coloros;

import android.graphics.Rect;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

/**
 * Finds the two lock screen shortcut buttons (bottom-left / bottom-right, e.g. flashlight and camera) and gives
 * them a solid circle in the system accent tone with a dark icon, like the fingerprint button.
 */
final class ShortcutStyler {
    private final HookEntry m;
    private final Restorer restorer = new Restorer();
    private View left, right;
    private MorphKeyDrawable leftBg, rightBg;
    private String lastLog = "";

    ShortcutStyler(HookEntry m) { this.m = m; }

    void apply(List<View> tree, int screenW, int screenH, Palette p, Set<View> exclude) {
        // Pass 1: clickable views. Pass 2 (only for a side still empty): views with a background or an icon,
        // for buttons that react to touch listeners without being "clickable".
        View[] strict = pick(tree, screenW, screenH, exclude, true);
        View[] loose = (strict[0] == null || strict[1] == null) ? pick(tree, screenW, screenH, exclude, false) : strict;
        View newLeft = strict[0] != null ? strict[0] : loose[0];
        View newRight = strict[1] != null ? strict[1] : loose[1];
        // Keep the current buttons through brief misses (animations) as long as they are still attached.
        if (newLeft == null && left != null && left.isAttachedToWindow()) newLeft = left;
        if (newRight == null && right != null && right.isAttachedToWindow()) newRight = right;
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
        return new View[]{newLeft, newRight};
    }

    private MorphKeyDrawable style(View button, MorphKeyDrawable bg, Palette p) {
        boolean active = isActive(button);
        int color = active ? p.shortcutBgActive : p.shortcutBg;
        int pressed = active ? p.shortcutBg : p.shortcutBgActive;
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
        for (ImageView iv : Views.images(button)) restorer.tint(iv, icon);
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
