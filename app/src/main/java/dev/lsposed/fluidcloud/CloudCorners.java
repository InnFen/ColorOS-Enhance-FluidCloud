package dev.lsposed.fluidcloud;

import android.graphics.Path;
import android.graphics.RectF;
import java.util.Map;

public record CloudCorners(Style capsule, Style card) {
    static final CloudCorners OEM = new CloudCorners(Style.OEM, Style.OEM);

    public record Style(boolean enabled, int dp, boolean continuous) {
        static final Style OEM = new Style(false, 0, false);

        float radius(float density, float width, float height) {
            return Math.min(dp * density, Math.min(width, height) / 2f);
        }
    }

    static CloudCorners from(Map<String, ?> values) {
        return new CloudCorners(style(values, "capsule", 16, 24), style(values, "card", 28, 64));
    }

    private static Style style(Map<String, ?> values, String prefix, int fallback, int max) {
        if (!Boolean.TRUE.equals(values.get(prefix + "_corners_enabled"))) return Style.OEM;
        int dp = values.get(prefix + "_corner_dp") instanceof Integer number ? number : fallback;
        return new Style(true, Math.max(0, Math.min(dp, max)),
                Boolean.TRUE.equals(values.get(prefix + "_continuous_corners")));
    }

    public static void path(Path path, RectF bounds, float radius, boolean continuous) {
        path.rewind();
        if (bounds.isEmpty()) return;
        float r = Math.max(0, Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2f));
        if (!continuous || r == 0) {
            path.addRoundRect(bounds, r, r, Path.Direction.CW);
            return;
        }
        float l = bounds.left, t = bounds.top, w = bounds.right, b = bounds.bottom;
        // Two mirrored cubics per corner. The first two control points stay on
        // the straight edge, giving zero curvature at the edge, and share the
        // same tangent and curvature at the diagonal join.
        float p = .70f * r, q = .38f * r, m = .19f * r;
        path.moveTo(l + r, t);
        path.lineTo(w - r, t);
        path.cubicTo(w - p, t, w - q, t, w - m, t + m);
        path.cubicTo(w, t + q, w, t + p, w, t + r);
        path.lineTo(w, b - r);
        path.cubicTo(w, b - p, w, b - q, w - m, b - m);
        path.cubicTo(w - q, b, w - p, b, w - r, b);
        path.lineTo(l + r, b);
        path.cubicTo(l + p, b, l + q, b, l + m, b - m);
        path.cubicTo(l, b - q, l, b - p, l, b - r);
        path.lineTo(l, t + r);
        path.cubicTo(l, t + p, l, t + q, l + m, t + m);
        path.cubicTo(l + q, t, l + p, t, l + r, t);
        path.close();
    }
}
