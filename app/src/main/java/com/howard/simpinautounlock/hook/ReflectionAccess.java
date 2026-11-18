package com.howard.simpinautounlock.hook;

import android.app.Application;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectionAccess {
    private ReflectionAccess() {}

    public static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    public static Application currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            return application instanceof Application ? (Application) application : null;
        } catch (Throwable error) {
            return null;
        }
    }
}
