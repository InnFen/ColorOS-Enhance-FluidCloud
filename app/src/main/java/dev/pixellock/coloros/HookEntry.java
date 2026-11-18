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
 * libxposed (API 102) entry. Runs inside com.android.systemui and the ColorOS always-on display app.
 *
 * Instead of hooking ColorOS-private keyguard classes (whose names change between builds and which we cannot
 * inspect from here), it watches every window SystemUI adds and restyles the lock screen views it recognises
 * by shape, position and text. Every decision is written to the LSPosed log under the tag "PixelLockscreen".
 */
public final class HookEntry extends XposedModule {
    static final String TAG = "PixelLockscreen";
    private static final String SYSTEM_UI = "com.android.systemui";
    /** ColorOS always-on display apps (the name differs between ColorOS / OxygenOS generations). */
    private static final java.util.Set<String> AOD_APPS = java.util.Set.of("com.oplus.aod", "com.coloros.aod");

    private boolean systemUi;
    private boolean aodApp;
    private String process = "";
    private boolean installed;
    volatile Config config = Config.from(Map.of());
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private final java.util.Set<WindowScanner> scanners =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        if (param.isSystemServer()) return;
        process = String.valueOf(param.getProcessName());
        systemUi = SYSTEM_UI.equals(process);
        aodApp = AOD_APPS.contains(process);
    }

    /** True inside the always-on display app, where only the clock is restyled. */
    boolean inAodApp() { return aodApp; }

    @Override public void onPackageReady(PackageReadyParam param) {
        if (installed || !param.isFirstPackage()) return;
        if (!(systemUi && SYSTEM_UI.equals(param.getPackageName()))
                && !(aodApp && process.equals(param.getPackageName()))) return;
        installed = true;
        observePreferences();
        try {
            hookClockClass(android.widget.TextView.class);
            installWindowHook();
        } catch (Throwable t) {
            error("Could not watch windows in " + process + "; module inactive", t);
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
        info("Watching " + process + " windows (" + hooked + " addView overloads), config: " + config);
    }

    private final java.util.Set<Method> clockHooks = new java.util.HashSet<>();

    /**
     * Hooks onDraw / onMeasure of TextView and of the clock's own class chain (in case ColorOS overrides them),
     * so a bound clock view draws the Pixel clock instead of its text. Unbound views run untouched.
     */
    synchronized void hookClockClass(Class<?> cls) {
        for (Class<?> c = cls; c != null && android.widget.TextView.class.isAssignableFrom(c); c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Class<?>[] t = method.getParameterTypes();
                boolean draw = method.getName().equals("onDraw") && t.length == 1 && t[0] == android.graphics.Canvas.class;
                boolean measure = method.getName().equals("onMeasure") && t.length == 2
                        && t[0] == int.class && t[1] == int.class;
                if ((!draw && !measure) || java.lang.reflect.Modifier.isAbstract(method.getModifiers())) continue;
                if (!clockHooks.add(method)) continue;
                try {
                    if (draw) {
                        hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            InPlaceClock.State st = InPlaceClock.get(chain.getThisObject());
                            if (st == null) return chain.proceed();
                            try {
                                InPlaceClock.draw(st, (android.widget.TextView) chain.getThisObject(),
                                        (android.graphics.Canvas) chain.getArg(0));
                                return null;
                            } catch (Throwable e) {
                                error("Clock draw failed", e);
                                return chain.proceed();
                            }
                        });
                    } else {
                        hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            InPlaceClock.State st = InPlaceClock.get(chain.getThisObject());
                            if (st != null) {
                                try {
                                    InPlaceClock.measure(st, (android.widget.TextView) chain.getThisObject(),
                                            (Integer) chain.getArg(0), (Integer) chain.getArg(1));
                                } catch (Throwable e) {
                                    error("Clock measure failed", e);
                                }
                            }
                            return result;
                        });
                    }
                    info("Hooked " + c.getName() + "." + method.getName());
                } catch (Throwable e) {
                    error("Could not hook " + c.getName() + "." + method.getName(), e);
                }
            }
        }
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

    // Cards in other windows (e.g. a live "fluid cloud" card) that the lock screen clock must stay above.
    private final java.util.Map<WindowScanner, java.util.List<View>> floating = new java.util.WeakHashMap<>();
    private volatile boolean anyFloating;

    void setFloatingCards(WindowScanner owner, java.util.List<View> cards) {
        boolean changed;
        synchronized (floating) {
            java.util.List<View> old = cards.isEmpty() ? floating.remove(owner) : floating.put(owner, cards);
            changed = !(old == null ? cards.isEmpty() : old.equals(cards));
            anyFloating = !floating.isEmpty();
        }
        if (!changed) return;
        WindowScanner[] copy;
        synchronized (scanners) { copy = scanners.toArray(new WindowScanner[0]); }
        for (WindowScanner s : copy) if (s != owner) s.floatingCardsChanged();
    }

    boolean hasFloatingCards() { return anyFloating; }

    java.util.List<View> floatingCards() {
        java.util.List<View> out = new java.util.ArrayList<>();
        synchronized (floating) { for (java.util.List<View> l : floating.values()) out.addAll(l); }
        return out;
    }

    void info(String message) { log(Log.INFO, TAG, message); }
    void error(String message, Throwable t) { log(Log.ERROR, TAG, message, t); }
}
