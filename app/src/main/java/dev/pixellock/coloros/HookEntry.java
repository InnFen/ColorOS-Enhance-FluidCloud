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
            installClockHooks();
            if (systemUi) ShortcutHook.install(this, param.getClassLoader());
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
     * Global hooks that let a bound clock view draw the Pixel clock instead of its own content, whatever kind of
     * view it is: View.draw (everything), TextView.onDraw and ViewGroup.dispatchDraw (when only those are
     * reached), View.measure (room for the two rows). Unbound views run untouched after one identity check.
     */
    private void installClockHooks() {
        try {
            hookDrawMethod(View.class.getDeclaredMethod("draw", android.graphics.Canvas.class), 0);
            hookDrawMethod(android.widget.TextView.class.getDeclaredMethod("onDraw", android.graphics.Canvas.class), 1);
            hookDrawMethod(ViewGroup.class.getDeclaredMethod("dispatchDraw", android.graphics.Canvas.class), 2);
            Method measure = View.class.getDeclaredMethod("measure", int.class, int.class);
            clockHooks.add(measure);
            hook(measure).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                InPlaceClock.State st = InPlaceClock.get(chain.getThisObject());
                if (st != null) {
                    try {
                        InPlaceClock.measure(st, (View) chain.getThisObject(),
                                (Integer) chain.getArg(0), (Integer) chain.getArg(1));
                    } catch (Throwable e) {
                        error("Clock measure failed", e);
                    }
                }
                return result;
            });
            info("Clock hooks ready: View.draw, TextView.onDraw, ViewGroup.dispatchDraw, View.measure");
        } catch (Throwable t) {
            error("Clock hooks failed; the clock can only be overlaid", t);
        }
    }

    /** kind: 0 draw (whole view), 1 onDraw (content), 2 dispatchDraw (children). */
    private void hookDrawMethod(Method method, int kind) {
        if (!clockHooks.add(method)) return;
        hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            InPlaceClock.State st = InPlaceClock.get(chain.getThisObject());
            if (st == null) return chain.proceed();
            try {
                View v = (View) chain.getThisObject();
                if (InPlaceClock.stockNow(st, v)) return chain.proceed();
                android.graphics.Canvas canvas = (android.graphics.Canvas) chain.getArg(0);
                if (kind == 0) InPlaceClock.drawWhole(st, v, canvas);
                else if (kind == 1) InPlaceClock.drawFromOnDraw(st, v, canvas);
                else InPlaceClock.drawFromDispatch(st, v, canvas);
                return null;
            } catch (Throwable e) {
                error("Clock draw failed", e);
                return chain.proceed();
            }
        });
    }

    /**
     * ColorOS's clock class may override draw / onDraw / dispatchDraw without calling the framework version:
     * hook its own overrides too, so a bound clock always draws ours.
     */
    synchronized void hookClockClass(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != View.class && View.class.isAssignableFrom(c)
                && c != ViewGroup.class && c != android.widget.TextView.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Class<?>[] t = method.getParameterTypes();
                if (t.length != 1 || t[0] != android.graphics.Canvas.class) continue;
                if (java.lang.reflect.Modifier.isAbstract(method.getModifiers())) continue;
                int kind;
                switch (method.getName()) {
                    case "draw": kind = 0; break;
                    case "onDraw": kind = 1; break;
                    case "dispatchDraw": kind = 2; break;
                    default: continue;
                }
                if (clockHooks.contains(method)) continue;
                try {
                    hookDrawMethod(method, kind);
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
        registerScreenReceiver(view.getContext());
        WindowScanner scanner = new WindowScanner(this, (ViewGroup) view, title);
        synchronized (scanners) { scanners.add(scanner); }
        scanner.start();
        info("Window added: title=" + title + " root=" + view.getClass().getName());
    }

    private boolean screenReceiver;

    /** Screen on / off in SystemUI: hand the clock back to ColorOS while the system AOD runs, take it again after. */
    private void registerScreenReceiver(android.content.Context context) {
        if (screenReceiver || !systemUi) return;
        screenReceiver = true;
        try {
            android.content.IntentFilter f = new android.content.IntentFilter();
            f.addAction(android.content.Intent.ACTION_SCREEN_ON);
            f.addAction(android.content.Intent.ACTION_SCREEN_OFF);
            context.getApplicationContext().registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, android.content.Intent i) {
                    boolean on = android.content.Intent.ACTION_SCREEN_ON.equals(i.getAction());
                    WindowScanner[] copy;
                    synchronized (scanners) { copy = scanners.toArray(new WindowScanner[0]); }
                    for (WindowScanner s : copy) s.screenChanged(on);
                }
            }, f, android.content.Context.RECEIVER_NOT_EXPORTED);
        } catch (Throwable t) {
            error("Screen on/off receiver failed", t);
        }
        Palette.onLog = this::info;
        // Theme overlays (dynamic colours) and wallpaper colours: re-read the palette shortly after they change.
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable themeChanged = () -> main.postDelayed(() -> {
            Palette.invalidate();
            Runnable r = Palette.onChange;
            if (r != null) r.run();
        }, 600);
        try {
            android.content.IntentFilter f = new android.content.IntentFilter("android.intent.action.OVERLAY_CHANGED");
            f.addDataScheme("package");
            context.getApplicationContext().registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, android.content.Intent i) { themeChanged.run(); }
            }, f, android.content.Context.RECEIVER_NOT_EXPORTED);
        } catch (Throwable t) {
            error("Overlay change receiver failed", t);
        }
        try {
            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(context.getApplicationContext());
            wm.addOnColorsChangedListener((colors, which) -> themeChanged.run(), main);
        } catch (Throwable t) {
            error("Wallpaper colour listener failed", t);
        }
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
        Palette.onChange = () -> {
            WindowScanner[] all;
            synchronized (scanners) { all = scanners.toArray(new WindowScanner[0]); }
            for (WindowScanner s : all) s.themeChanged();
            ShortcutHook.configChanged();
        };
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
        ShortcutHook.configChanged();
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
