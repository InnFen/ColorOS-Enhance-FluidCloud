package dev.lsposed.fluidcloud;

import android.graphics.drawable.GradientDrawable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** The shared page's stock foreground paints its stroke using ColorOS GradientDrawable geometry. */
final class NativeTemplateStroke {
    private record Shape(float radius, int type, float weight, boolean smooth) { }
    private record Owned(Shape stock, Shape applied) { }
    private final Field state, type, weight, extension;
    private final Method smooth, configure;
    private final int continuousType;
    private final WeakHashMap<GradientDrawable, Owned> shapes = new WeakHashMap<>();

    NativeTemplateStroke(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> drawableState = Reflect.type(loader, "android.graphics.drawable.GradientDrawable$GradientState");
        Class<?> ext = Reflect.type(loader, "android.graphics.drawable.IGradientDrawableExt");
        Class<?> rounded = Reflect.type(loader, "com.oplus.view.OplusSmoothRoundedManager");
        state = Reflect.field(GradientDrawable.class, "mGradientState", drawableState);
        extension = Reflect.field(GradientDrawable.class, "mGradientDrawableExt", ext);
        type = Reflect.field(drawableState, "mSmoothType", int.class);
        weight = Reflect.field(drawableState, "mSmoothWeight", float.class);
        smooth = Reflect.method(ext, "getSmoothRoundStyle", boolean.class);
        configure = Reflect.method(ext, "setSmoothRoundStyle", void.class, boolean.class, float.class, int.class);
        continuousType = (int) Reflect.call(Reflect.method(rounded, "getG2CornerType", int.class), null);
    }

    void apply(GradientDrawable drawable, CloudCorners.Style style, float density) throws IllegalAccessException {
        if (drawable.getCornerRadii() != null || drawable.getShape() != GradientDrawable.RECTANGLE) return;
        Owned previous = shapes.get(drawable);
        if (!style.enabled() && previous == null) return;
        Shape current = read(drawable);
        if (!style.enabled()) {
            shapes.remove(drawable);
            if (current.equals(previous.applied())) write(drawable, previous.stock());
            return;
        }
        boolean continuous = style.continuous();
        Shape target = new Shape(style.dp() * density, continuous ? continuousType : -1, continuous ? 3f : 0f, continuous);
        if (target.equals(current)) return;
        Shape stock = previous != null && current.equals(previous.applied()) ? previous.stock() : current;
        write(drawable, target);
        shapes.put(drawable, new Owned(stock, target));
    }

    private Shape read(GradientDrawable drawable) throws IllegalAccessException {
        Object nativeState = Reflect.get(state, drawable);
        return new Shape(drawable.getCornerRadius(), type.getInt(nativeState), weight.getFloat(nativeState),
                (boolean) Reflect.call(smooth, Reflect.get(extension, drawable)));
    }

    private void write(GradientDrawable drawable, Shape value) throws IllegalAccessException {
        Object nativeState = Reflect.get(state, drawable);
        type.setInt(nativeState, value.type());
        weight.setFloat(nativeState, value.weight());
        Reflect.call(configure, Reflect.get(extension, drawable), value.smooth(), value.weight(), value.type());
        drawable.setCornerRadius(value.radius());
    }

    void restore() throws IllegalAccessException {
        for (GradientDrawable drawable : new ArrayList<>(shapes.keySet()))
            if (drawable != null) apply(drawable, CloudCorners.Style.OEM, 1f);
    }
}
