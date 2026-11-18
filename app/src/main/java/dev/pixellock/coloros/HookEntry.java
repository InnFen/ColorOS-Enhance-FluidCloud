package dev.pixellock.coloros;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * libxposed (API 102) entry. Runs only inside com.android.systemui.
 *
 * Instead of hooking ColorOS-private keyguard classes (whose names change between builds and which we cannot
 * inspect from here), it watches every window SystemUI adds and restyles the lock screen views it recognises
 * by shape, position and text. Every decision is written to the LSPosed log under the tag "PixelLockscreen".
 */
public final class HookEntry extends XposedModule {
    static final String TAG = "PixelLockscreen";
    private static final String SYSTEM_UI = "com.android.systemui";

    private boolean systemUi;
    private boolean installed;
    volatile Config config = Config.from(Map.of());
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private final java.util.Set<WindowScanner> scanners =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        systemUi = !param.isSystemServer() && SYSTEM_UI.equals(param.getProcessName());
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        if (!systemUi || installed || !param.isFirstPackage() || !SYSTEM_UI.equals(param.getPackageName())) return;
        installed = true;
        observePreferences();
        try {
            installWindowHook();
        } catch (Throwable t) {
            error("Could not watch SystemUI windows; module inactive", t);
        }
    }

    private void installWindowHook() throws ReflectiveOperationException {
        Class<?> global = Class.forName("android.view.WindowManagerGlobal");
        int hooked = 0;
        for (Method method : global.getDeclaredMethods()) {
            if (!method.getName().equals("addView")) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length < 2 || !View.class.isAssignableFrom(types[0])
                    || !ViewGroup.LayoutParams.class.isAssignableFrom(types[1])) continue;
            hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    windowAdded((View) chain.getArg(0), (ViewGroup.LayoutParams) chain.getArg(1));
                } catch (Throwable t) {
                    error("Window attach failed", t);
                }
                return result;
            });
            hooked++;
        }
        if (hooked == 0) throw new NoSuchMethodException("WindowManagerGlobal.addView");
        info("Watching SystemUI windows (" + hooked + " addView overloads), config: " + config);
    }

    private void windowAdded(View view, ViewGroup.LayoutParams params) {
        if (!(view instanceof ViewGroup)) return;
        String title = params instanceof WindowManager.LayoutParams
                ? String.valueOf(((WindowManager.LayoutParams) params).getTitle()) : "";
        WindowScanner scanner = new WindowScanner(this, (ViewGroup) view, title);
        synchronized (scanners) { scanners.add(scanner); }
        scanner.start();
        info("Window added: title=" + title + " root=" + view.getClass().getName());
    }

    private void observePreferences() {
        try {
            preferences = getRemotePreferences(Config.GROUP);
            listener = (prefs, key) -> reload();
            preferences.registerOnSharedPreferenceChangeListener(listener);
        } catch (Throwable t) {
            error("Settings unavailable; using defaults", t);
        }
        reload();
    }

    private void reload() {
        try {
            config = Config.from(preferences == null ? Map.of() : preferences.getAll());
        } catch (Throwable t) {
            config = Config.from(Map.of());
            error("Settings unreadable; using defaults", t);
        }
        WindowScanner[] copy;
        synchronized (scanners) { copy = scanners.toArray(new WindowScanner[0]); }
        for (WindowScanner s : copy) s.configChanged();
        info("Config: " + config);
    }

    void info(String message) { log(Log.INFO, TAG, message); }
    void error(String message, Throwable t) { log(Log.ERROR, TAG, message, t); }
}
