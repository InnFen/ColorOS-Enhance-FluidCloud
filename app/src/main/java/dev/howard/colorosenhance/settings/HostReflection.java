package dev.howard.colorosenhance.settings;
import android.content.Context;
import android.util.AttributeSet;
import java.lang.reflect.*;

/** All preference and fragment objects belong to the Settings classloader. */
final class HostReflection {
    private final ClassLoader loader;
    interface Layouts { int id(Context context,String name); }
    private final Layouts layouts;
    @android.annotation.SuppressLint("DiscouragedApi") // Resources are owned by the firmware, never linked into the module.
    HostReflection(ClassLoader loader) { this(loader,(context,name)->context.getResources().getIdentifier(name,"layout","com.android.settings")); }
    HostReflection(ClassLoader loader,Layouts layouts) { this.loader = loader; this.layouts=layouts; }
    Object summaryJump(Context context) throws ReflectiveOperationException {
        int layout=layouts.id(context,"jump_preference_simple_layout_summary_vertical");
        if(layout==0) throw new NoSuchFieldException("Native summary layout unavailable");
        Object preference=create("com.oplus.settings.widget.preference.SettingsSimpleJumpPreference",context);
        call(preference,"setLayoutResource",layout); return preference;
    }
    Object numberPreference(Context context) throws ReflectiveOperationException {
        try {
            Object preference=create("com.coui.appcompat.preference.COUIPreference",context);
            int layout=layouts.id(context,"coui_preference");
            if(layout!=0)call(preference,"setLayoutResource",layout);
            return preference;
        } catch (ClassNotFoundException unavailable) {
            return create("androidx.preference.Preference",context);
        }
    }
    Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }
    Object create(String name, Context context) throws ReflectiveOperationException {
        Class<?> cls = type(name);
        try { return cls.getConstructor(Context.class).newInstance(context); }
        catch (NoSuchMethodException missing) {
            try { return cls.getConstructor(Context.class, AttributeSet.class).newInstance(context, null); }
            catch (NoSuchMethodException alsoMissing) {
                return cls.getConstructor(Context.class, AttributeSet.class, int.class).newInstance(context, null, 0);
            }
        }
    }
    Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        return compatible(target.getClass(), name, args).invoke(target, args);
    }
    private static Method compatible(Class<?> cls, String name, Object[] args) throws NoSuchMethodException {
        Method match = null;
        int best = -1;
        for (Method method : cls.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] types = method.getParameterTypes();
            int score = 0;
            boolean fits = true;
            for (int i = 0; i < types.length; i++) {
                if (args[i] == null) { if (types[i].isPrimitive()) fits = false; }
                else if (!boxed(types[i]).isInstance(args[i])) fits = false;
                else score += boxed(types[i]) == args[i].getClass() ? 2 : 1;
            }
            if (fits && score > best) { best = score; match = method; }
        }
        if (match == null) throw new NoSuchMethodException(cls.getName() + "." + name);
        return match;
    }
    static Method method(Class<?> cls, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            try { Method method = current.getDeclaredMethod(name, args); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(cls.getName() + "." + name);
    }
    interface Callback { Object invoke(Object[] args); }
    void listen(Object target, String setter, Callback callback) throws ReflectiveOperationException {
        listenEvent(target,setter,(name,args)->callback.invoke(args));
    }
    interface EventCallback { Object invoke(String name,Object[] args); }
    void listenEvent(Object target, String setter, EventCallback callback) throws ReflectiveOperationException {
        Method method = null;
        for (Method candidate : target.getClass().getMethods())
            if (candidate.getName().equals(setter) && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInterface()) { method = candidate; break; }
        if (method == null) throw new NoSuchMethodException(setter);
        Class<?> contract = method.getParameterTypes()[0];
        Object proxy = Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (self, invoked, args) -> {
            if (invoked.getDeclaringClass() == Object.class) return switch (invoked.getName()) {
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> "ColorOSEnhance listener";
            };
            return callback.invoke(invoked.getName(),args == null ? new Object[0] : args);
        });
        method.invoke(target, proxy);
    }
    private static Class<?> boxed(Class<?> cls) {
        if (!cls.isPrimitive()) return cls;
        if (cls == boolean.class) return Boolean.class;
        if (cls == int.class) return Integer.class;
        if (cls == long.class) return Long.class;
        if (cls == float.class) return Float.class;
        if (cls == double.class) return Double.class;
        return cls;
    }
}
