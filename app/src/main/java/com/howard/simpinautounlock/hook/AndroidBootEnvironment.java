package com.howard.simpinautounlock.hook;

import android.content.Context;
import android.provider.Settings;
import com.howard.simpinautounlock.core.BootEnvironment;
import java.lang.reflect.Method;

public final class AndroidBootEnvironment implements BootEnvironment {
    private final Context context;

    public AndroidBootEnvironment(Context context) {
        this.context = context;
    }

    @Override
    public int bootCount() {
        return Settings.Global.getInt(
                context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    @Override
    public boolean bootCompleted() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getDeclaredMethod("get", String.class, String.class);
            get.setAccessible(true);
            Object value = get.invoke(null, "sys.boot_completed", "0");
            return "1".equals(value);
        } catch (Throwable error) {
            return true;
        }
    }
}
