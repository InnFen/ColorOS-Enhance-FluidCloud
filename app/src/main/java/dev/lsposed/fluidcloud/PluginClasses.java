package dev.lsposed.fluidcloud;

import dalvik.system.DexFile;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/** A one-time, package-scoped search of the plugin's own DEX. No class initialization or hot-path scans. */
final class PluginClasses {
    private static final ClassValue<PluginClasses> INDEX = new ClassValue<>() {
        @Override protected PluginClasses computeValue(Class<?> anchor) {
            try { return new PluginClasses(anchor.getClassLoader()); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Plugin DEX index unavailable", error); }
        }
    };
    private final ClassLoader loader;
    private final List<String> names;
    final List<String> paths;

    static PluginClasses of(Class<?> anchor) { return INDEX.get(anchor); }

    private PluginClasses(ClassLoader loader) throws ReflectiveOperationException {
        this.loader = loader;
        Object pathList = Reflect.get(Reflect.field(loader.getClass(), "pathList", Object.class), loader);
        Object[] elements = (Object[]) Reflect.get(Reflect.field(pathList.getClass(), "dexElements", Object[].class), pathList);
        var classes = new LinkedHashSet<String>();
        var files = new LinkedHashSet<String>();
        for (Object element : elements) {
            Field dexField = Reflect.field(element.getClass(), "dexFile", DexFile.class);
            DexFile dex = (DexFile) Reflect.get(dexField, element);
            if (dex == null) continue;
            files.add(dex.getName());
            var entries = dex.entries();
            while (entries.hasMoreElements()) classes.add(entries.nextElement());
        }
        names = List.copyOf(classes);
        paths = List.copyOf(files);
    }

    Class<?> unique(String prefix, Predicate<Class<?>> matches) throws ReflectiveOperationException {
        Class<?> found = null;
        for (String name : names) {
            if (!name.startsWith(prefix)) continue;
            // Resolution stays within the actual plugin loader, without running constructors or static initializers.
            Class<?> type;
            try {
                type = Reflect.type(loader, name);
                if (!matches.test(type)) continue;
            } catch (ClassNotFoundException | LinkageError unavailable) { continue; }
            if (found != null) throw new NoSuchMethodException("Ambiguous plugin contract in " + prefix);
            found = type;
        }
        if (found == null) throw new NoSuchMethodException("No matching plugin contract in " + prefix);
        return found;
    }

    static boolean has(Class<?> type, Class<?> result, Class<?>... args) {
        for(var method:type.getDeclaredMethods())
            if(!method.isBridge()&&!method.isSynthetic()&&method.getReturnType()==result
                    &&java.util.Arrays.equals(method.getParameterTypes(),args))return true;
        return false;
    }

    static Class<?> outline(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> anchor = Reflect.type(loader, NativeCameraCard.VIEWS + "CardView");
        return outline(anchor);
    }

    static Class<?> outline(Class<?> anchor) throws ReflectiveOperationException {
        return of(anchor).unique("com.oplus.systemui.plugins.common.util.", type -> {
            if (!android.view.ViewOutlineProvider.class.isAssignableFrom(type)) return false;
            try {
                Reflect.uniqueField(type, field -> Reflect.instance(field) && field.getType() == android.graphics.Rect.class);
                type.getDeclaredConstructor(android.graphics.Rect.class,
                        Reflect.type(anchor.getClassLoader(), "kotlin.Triple"));
                return true;
            } catch (ReflectiveOperationException error) { return false; }
        });
    }
}
