package dev.lsposed.fluidcloud;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.lang.reflect.Modifier;
import java.util.function.Predicate;
import java.util.function.BiConsumer;

final class Reflect {
    private Reflect() { }

    static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    static Field field(Class<?> type, String name, Class<?> expected) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                if (!expected.isAssignableFrom(field.getType())) throw new NoSuchFieldException(name + " type");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    // Kotlin can emit methods with identical arguments and different return types.
    static Method method(Class<?> type, String name, Class<?> result, Class<?>... args)
            throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getReturnType() == result
                        && Arrays.equals(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    static Field uniqueField(Class<?> owner, Predicate<Field> matches) throws NoSuchFieldException {
        Field found = null;
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic() || !matches.test(field)) continue;
                if (found != null) throw new NoSuchFieldException("Ambiguous field in " + owner.getName());
                found = field;
            }
        }
        if (found == null) throw new NoSuchFieldException("No matching field in " + owner.getName());
        found.setAccessible(true);
        return found;
    }

    static Method uniqueMethod(Class<?> owner, Predicate<Method> matches) throws NoSuchMethodException {
        Method found = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (method.isBridge() || method.isSynthetic() || !matches.test(method)) continue;
            if (found != null) throw new NoSuchMethodException("Ambiguous method in " + owner.getName());
            found = method;
        }
        if (found == null) throw new NoSuchMethodException("No matching method in " + owner.getName());
        found.setAccessible(true);
        return found;
    }

    static Method signature(Class<?> owner, Class<?> result, Class<?>... args) throws NoSuchMethodException {
        return uniqueMethod(owner, method -> method.getReturnType() == result
                && Arrays.equals(method.getParameterTypes(), args));
    }

    static boolean instance(Field field) { return !Modifier.isStatic(field.getModifiers()); }

    static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException(method.getName(), error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(method.getName(), error);
        }
    }

    static Object get(Field field, Object target) {
        try { return field.get(target); }
        catch (IllegalAccessException error) { throw new IllegalStateException(field.getName(), error); }
    }

    static Object listener(Class<?> type, BiConsumer<Method, Object[]> callback) {
        if (!type.isInterface()) throw new IllegalArgumentException(type.getName());
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "FluidCloudUnbound:" + type.getSimpleName();
                    default -> null;
                };
            }
            callback.accept(method, args == null ? new Object[0] : args);
            Class<?> result = method.getReturnType();
            if (result == boolean.class) return false;
            if (result == int.class) return 0;
            if (result == long.class) return 0L;
            if (result == float.class) return 0f;
            if (result == double.class) return 0d;
            return null;
        });
    }
}
