package dev.lsposed.fluidcloud;

import android.graphics.drawable.Drawable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Native ULE material caches its shader size independently of Drawable bounds. */
final class NativeCardMaterial {
    record Size(float width, float height) { }
    record Contract(Field size, Method width, Method height, Method resize) {
        Size read(Drawable drawable) {
            Object pair = Reflect.get(size, drawable);
            return new Size(((Number) Reflect.call(width, pair)).floatValue(),
                    ((Number) Reflect.call(height, pair)).floatValue());
        }
        void write(Drawable drawable, Size value) {
            Reflect.call(resize, drawable, value.width(), value.height(), 0f, 0f);
        }
    }
    private record Owned(Contract contract, Size nativeSize, Size appliedSize) { }
    private final WeakHashMap<Drawable, Owned> sizes = new WeakHashMap<>();
    private final java.util.function.Function<Class<?>, Contract> lookup;
    private static final ClassValue<Contract> CONTRACTS = new ClassValue<>() {
        @Override protected Contract computeValue(Class<?> type) {
            if (!type.getName().equals("t7.f0") || !Drawable.class.isAssignableFrom(type)) return null;
            try { return resolve(type, Reflect.type(type.getClassLoader(), "z9.l")); }
            catch (ReflectiveOperationException | LinkageError error) { return null; }
        }
    };
    NativeCardMaterial() { this(CONTRACTS::get); }
    NativeCardMaterial(java.util.function.Function<Class<?>, Contract> lookup) { this.lookup = lookup; }
    static Contract resolve(Class<?> drawableType, Class<?> pairType) throws ReflectiveOperationException {
        if (!Drawable.class.isAssignableFrom(drawableType)) throw new NoSuchMethodException("Native material type");
        return new Contract(Reflect.field(drawableType, "c", pairType),
                Reflect.method(pairType, "a", Object.class), Reflect.method(pairType, "b", Object.class),
                Reflect.method(drawableType, "d", void.class, float.class, float.class, float.class, float.class));
    }
    void resize(Drawable drawable, int width, int height) {
        if (drawable == null || width <= 0 || height <= 0) return;
        Contract contract = lookup.apply(drawable.getClass());
        if (contract == null) return;
        Size current = contract.read(drawable), target = new Size(width, height);
        if (current.equals(target)) return; // never invalidate a settled native card
        Owned previous = sizes.get(drawable);
        Size nativeSize = previous != null && current.equals(previous.appliedSize()) ? previous.nativeSize() : current;
        contract.write(drawable, target);
        sizes.put(drawable, new Owned(contract, nativeSize, target));
    }
    void restore(Drawable drawable) {
        Owned owned = sizes.remove(drawable);
        if (owned != null && owned.contract().read(drawable).equals(owned.appliedSize()))
            owned.contract().write(drawable, owned.nativeSize());
    }
    void restore() { for (Drawable drawable : new ArrayList<>(sizes.keySet())) if (drawable != null) restore(drawable); }
}
