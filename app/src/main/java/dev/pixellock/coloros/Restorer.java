package dev.pixellock.coloros;

import android.content.res.ColorStateList;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

/** Remembers what we changed on ColorOS views so a switch in settings can put it back without a reboot. */
final class Restorer {
    private static final class Saved {
        boolean bgSaved; Drawable bg;
        boolean filterSaved; ColorFilter filter; ColorStateList tint;
        boolean textSaved; ColorStateList textColors; Typeface typeface;
        boolean visSaved; int visibility;
    }

    private final Map<View, Saved> saved = new WeakHashMap<>();

    private Saved of(View v) {
        Saved s = saved.get(v);
        if (s == null) { s = new Saved(); saved.put(v, s); }
        return s;
    }

    void background(View v, Drawable ours) {
        if (v.getBackground() == ours) return;
        Saved s = of(v);
        if (!s.bgSaved || !(v.getBackground() instanceof MorphKeyDrawable)) {
            s.bg = v.getBackground();
            s.bgSaved = true;
        }
        v.setBackground(ours);
    }

    void clearBackground(View v) {
        if (v.getBackground() == null) return;
        Saved s = of(v);
        if (!s.bgSaved) { s.bg = v.getBackground(); s.bgSaved = true; }
        v.setBackground(null);
    }

    void tint(ImageView v, int color) {
        ColorFilter cf = v.getColorFilter();
        if (cf instanceof Tagged && ((Tagged) cf).color == color) return;
        Saved s = of(v);
        if (!s.filterSaved) { s.filter = cf; s.tint = v.getImageTintList(); s.filterSaved = true; }
        v.setImageTintList(null);
        v.setColorFilter(new Tagged(color));
    }

    void text(TextView v, int color, Typeface face) {
        boolean colorOk = v.getCurrentTextColor() == color && v.getTextColors().isStateful() == false;
        boolean faceOk = face == null || v.getTypeface() == face;
        if (colorOk && faceOk) return;
        Saved s = of(v);
        if (!s.textSaved) { s.textColors = v.getTextColors(); s.typeface = v.getTypeface(); s.textSaved = true; }
        if (!colorOk) v.setTextColor(color);
        if (!faceOk) v.setTypeface(face);
    }

    void hide(View v) {
        if (v.getVisibility() == View.GONE) return;
        Saved s = of(v);
        if (!s.visSaved) { s.visibility = v.getVisibility(); s.visSaved = true; }
        v.setVisibility(View.GONE);
    }

    void restoreAll() {
        for (Map.Entry<View, Saved> e : saved.entrySet()) {
            View v = e.getKey();
            Saved s = e.getValue();
            if (v == null) continue;
            if (s.bgSaved) v.setBackground(s.bg);
            if (s.filterSaved && v instanceof ImageView) {
                ((ImageView) v).setColorFilter(s.filter);
                ((ImageView) v).setImageTintList(s.tint);
            }
            if (s.textSaved && v instanceof TextView) {
                if (s.textColors != null) ((TextView) v).setTextColor(s.textColors);
                ((TextView) v).setTypeface(s.typeface);
            }
            if (s.visSaved) v.setVisibility(s.visibility);
        }
        saved.clear();
    }

    boolean isEmpty() { return saved.isEmpty(); }

    /** Our own tint, recognisable so we don't re-apply it every scan. */
    static final class Tagged extends PorterDuffColorFilter {
        final int color;
        Tagged(int color) { super(color, PorterDuff.Mode.SRC_IN); this.color = color; }
    }
}
