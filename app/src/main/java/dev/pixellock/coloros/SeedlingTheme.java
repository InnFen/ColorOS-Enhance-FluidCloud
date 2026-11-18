package dev.pixellock.coloros;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * Light fluid cloud cards.
 *
 * ColorOS draws fluid cloud (seedling) cards inside SystemUI with the Seedling SDK's ULE engine, which SystemUI
 * loads from SeedlingSdk.apk (shipped by com.oplus.pantanal.ums) through its own DexClassLoader. Each card has an
 * engine object whose "night" flag picks every colour of the card: the ColorOS 15+ fluid cloud style forces it on
 * ("capsuleCoverCameraHole"), so cards stay black with white text even in light mode.
 *
 * When the setting is on, that flag reads "light" while the system is in light mode and the screen is on, for
 * cards shown on the lock screen and in the notification shade. Status bar capsules (they cover the camera hole),
 * AOD cards and the always-on display keep the original dark look. Cards are reloaded when the screen turns on or
 * off or the setting changes; light/dark switches are handled by the SDK's own reload.
 *
 * The engine classes are obfuscated (v6.e2 in the SDK bundled with UMS 17.22.6): every name is checked before it is
 * used, and the log says plainly when this SDK build doesn't match.
 */
final class SeedlingTheme {
    private static final String ENGINE = "v6.e2";
    /** Card places where a light card makes sense (ub$e enum names). */
    private static final Set<String> LIGHT_PLACES =
            Set.of("LockScreen", "Notification", "SecondaryLockScreen", "SecondaryNotification");

    private static HookEntry module;
    private static final Set<ClassLoader> seen = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Class<?>> hooked = new HashSet<>();
    /** Engine -> last answer: bit 0 = ColorOS said dark, bit 1 = we turned it light. */
    private static final Map<Object, Integer> engines = new WeakHashMap<>();
    private static final Map<Object, String> places = new WeakHashMap<>();
    private static final Set<String> logged = new HashSet<>();
    private static volatile boolean screenOn = true;

    private static Method placeInfo;   // e2.g() -> ub$h
    private static Field placeField;   // ub$h.<enum ub$e>
    private static Method cardView;    // e2.r() -> ub (the card view)
    private static Method reload;      // ub.F5(boolean force)

    private SeedlingTheme() { }

    static void install(HookEntry m) {
        module = m;
        try {
            Constructor<?> c = dalvik.system.DexClassLoader.class.getConstructor(
                    String.class, String.class, String.class, ClassLoader.class);
            m.hook(c).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                Object path = chain.getArg(0);
                if (path != null && path.toString().toLowerCase(java.util.Locale.ROOT).contains("seedling")) {
                    try {
                        loaderCreated((ClassLoader) chain.getThisObject(), path.toString());
                    } catch (Throwable t) {
                        m.error("Fluid cloud card hook failed", t);
                    }
                }
                return result;
            });
            m.info("Fluid cloud cards: waiting for the Seedling SDK to load");
        } catch (Throwable t) {
            m.error("Fluid cloud cards: could not watch the Seedling SDK", t);
        }
    }

    private static synchronized void loaderCreated(ClassLoader loader, String path) {
        HookEntry m = module;
        if (m == null || loader == null || !seen.add(loader)) return;
        Class<?> engine;
        try {
            engine = Class.forName(ENGINE, false, loader);
        } catch (Throwable t) {
            m.info("Fluid cloud cards: Seedling SDK at " + path + " has no " + ENGINE + "; light cards unavailable in this build");
            return;
        }
        Method night = method(engine, "t");
        if (night == null || night.getReturnType() != boolean.class) {
            m.info("Fluid cloud cards: " + ENGINE + ".t() isn't the night flag in this SDK build; light cards unavailable");
            return;
        }
        if (method(engine, "D", Context.class) == null) {
            m.info("Fluid cloud cards: " + ENGINE + " has no resolveUiMode(Context); light cards unavailable");
            return;
        }
        findHelpers(engine);
        if (!hooked.add(engine)) return;
        m.hook(night).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            boolean dark = (Boolean) chain.proceed();
            try {
                return answer(chain.getThisObject(), dark);
            } catch (Throwable t) {
                m.error("Fluid cloud card colour failed", t);
                return dark;
            }
        });
        m.info("Fluid cloud cards: hooked " + ENGINE + ".t() from " + path
                + (reload == null ? " (no reload method: changes show on the next card update)" : ""));
    }

    private static void findHelpers(Class<?> engine) {
        Method g = method(engine, "g");
        if (g != null) {
            for (Field f : g.getReturnType().getDeclaredFields()) {
                if (f.getType().isEnum() && enumHas(f.getType(), "LockScreen")) {
                    f.setAccessible(true);
                    placeInfo = g;
                    placeField = f;
                    break;
                }
            }
        }
        Method r = method(engine, "r");
        if (r != null && View.class.isAssignableFrom(r.getReturnType())) {
            Method f5 = null;
            for (Class<?> c = r.getReturnType(); c != null && c != View.class && f5 == null; c = c.getSuperclass()) {
                try { f5 = c.getDeclaredMethod("F5", boolean.class); } catch (NoSuchMethodException ignored) { }
            }
            if (f5 != null) {
                f5.setAccessible(true);
                cardView = r;
                reload = f5;
            }
        }
        if (placeField == null) module.info("Fluid cloud cards: card place unknown in this SDK build; only cards on the lock screen window are lightened");
    }

    /** dark: what ColorOS decided. Returns what the card should use. */
    private static boolean answer(Object engine, boolean dark) {
        HookEntry m = module;
        boolean light = false;
        String place = place(engine);
        if (dark && m != null && m.config.lightCards && screenOn && LIGHT_PLACES.contains(place)
                && !night()) {
            light = true;
        }
        int state = (dark ? 1 : 0) | (light ? 2 : 0);
        Integer before;
        synchronized (engines) { before = engines.put(engine, state); }
        if (before == null || before != state) {
            String key = place + ":" + light;
            boolean first;
            synchronized (logged) { first = logged.add(key); }
            if (first) m.info("Fluid cloud card (" + place + "): " + (light ? "light" : dark ? "dark (stock)" : "light (stock)"));
        }
        return !light && dark;
    }

    private static String place(Object engine) {
        synchronized (places) {
            String p = places.get(engine);
            if (p != null) return p;
        }
        String p = "?";
        try {
            if (placeInfo != null) {
                Object info = placeInfo.invoke(engine);
                Object e = info == null ? null : placeField.get(info);
                if (e instanceof Enum) p = ((Enum<?>) e).name();
            }
        } catch (Throwable ignored) { }
        if (!"?".equals(p)) synchronized (places) { places.put(engine, p); }
        return p;
    }

    private static volatile Context context;

    static void attach(Context c) { if (c != null) context = c.getApplicationContext(); }

    private static boolean night() {
        Context c = context;
        if (c != null) return Palette.systemNight(c);
        return (android.content.res.Resources.getSystem().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    static void screenChanged(boolean on) {
        screenOn = on;
        refresh("screen " + (on ? "on" : "off"));
    }

    static void configChanged() { refresh("settings"); }

    /** Reloads every card whose colours would now come out differently. */
    private static void refresh(String why) {
        HookEntry m = module;
        if (m == null || reload == null) return;
        List<Object> all;
        synchronized (engines) { all = new ArrayList<>(engines.keySet()); }
        int n = 0;
        for (Object engine : all) {
            Integer was;
            synchronized (engines) { was = engines.get(engine); }
            // Only cards ColorOS draws dark can be turned light (and back).
            if (was == null || (was & 1) == 0) continue;
            String place = place(engine);
            boolean want = m.config.lightCards && screenOn && LIGHT_PLACES.contains(place) && !night();
            if (want == ((was & 2) != 0)) continue;
            try {
                Object view = cardView.invoke(engine);
                if (!(view instanceof View)) continue;
                View v = (View) view;
                v.post(() -> {
                    try { reload.invoke(v, true); } catch (Throwable t) { m.error("Fluid cloud card reload failed", t); }
                });
                n++;
            } catch (Throwable t) {
                m.error("Fluid cloud card reload failed", t);
            }
        }
        if (n > 0) m.info("Fluid cloud cards: reloading " + n + " card(s) (" + why + ")");
    }

    private static Method method(Class<?> c, String name, Class<?>... types) {
        try {
            Method m = c.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean enumHas(Class<?> e, String name) {
        for (Object o : e.getEnumConstants()) if (((Enum<?>) o).name().equals(name)) return true;
        return false;
    }
}
