package dev.pixellock.coloros;

import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * ColorOS 17 colours the two lock screen shortcut buttons in OplusKeyguardBottomAreaViewBinder.updateButton
 * (one call per button whenever its state changes). Right after that call we give the button our pale accent
 * circle and a matching icon colour, so nothing has to be guessed from the view tree and the control center is
 * never involved. If the class isn't there (other ColorOS builds), the view-tree ShortcutStyler stays in charge.
 */
final class ShortcutHook {
    static final String BINDER = "com.oplus.systemui.keyguard.ui.binder.OplusKeyguardBottomAreaViewBinder";

    /** True once ColorOS called the hooked method: the tree-based styler then stays out of the way. */
    static volatile boolean active;

    private static HookEntry module;
    private static final Restorer restorer = new Restorer();
    private static final Map<ImageView, MorphKeyDrawable> styled = new WeakHashMap<>();
    private static String lastLog = "";

    private ShortcutHook() { }

    static void install(HookEntry m, ClassLoader cl) {
        module = m;
        Class<?> binder;
        try {
            binder = Class.forName(BINDER, false, cl);
        } catch (Throwable t) {
            m.info("Shortcut hook: " + BINDER + " not found, using the view-tree fallback");
            return;
        }
        int hooked = 0;
        for (Method method : binder.getDeclaredMethods()) {
            String name = method.getName();
            if (!name.contains("updateButton") || name.contains("Alpha")) continue;
            Class<?>[] types = method.getParameterTypes();
            int index = -1;
            for (int i = 0; i < types.length; i++) if (ImageView.class.isAssignableFrom(types[i])) { index = i; break; }
            if (index < 0) continue;
            final int arg = index;
            try {
                m.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        style((ImageView) chain.getArg(arg));
                    } catch (Throwable e) {
                        m.error("Shortcut style failed", e);
                    }
                    return result;
                });
                hooked++;
                m.info("Shortcut hook on " + binder.getSimpleName() + "." + name);
            } catch (Throwable t) {
                m.error("Could not hook " + binder.getSimpleName() + "." + name, t);
            }
        }
        if (hooked == 0) m.info("Shortcut hook: no updateButton method in " + BINDER + ", using the view-tree fallback");
    }

    static void style(ImageView iv) {
        HookEntry m = module;
        if (iv == null || m == null) return;
        Config cfg = m.config;
        if (!cfg.shortcuts) { restore(); return; }
        active = true;
        Palette p = Palette.of(iv.getContext(), cfg.shortcutTone, cfg.clockTone, cfg.aodTone);
        boolean on = iv.isActivated() || iv.isSelected();
        int bg = on ? p.shortcutBgActive : p.shortcutBg;
        int icon = on ? p.shortcutIconActive : p.shortcutIcon;
        MorphKeyDrawable d = styled.get(iv);
        if (d == null) {
            d = new MorphKeyDrawable(bg, p.shortcutPressed(on), 0.3f);
            styled.put(iv, d);
        }
        d.setColors(bg, p.shortcutPressed(on));
        restorer.background(iv, d);
        // ColorOS tints the background for its own states; that would paint over our colour.
        if (iv.getBackgroundTintList() != null) iv.setBackgroundTintList(null);
        restorer.tint(iv, icon);
        String log = "Shortcut styled: " + Views.describe(iv) + (on ? " (on)" : "") + " bg " + Palette.hex(bg)
                + " icon " + Palette.hex(icon);
        if (!log.equals(lastLog)) { lastLog = log; m.info(log); }
    }

    /** Settings changed: restyle or undo on each button's own thread. */
    static void configChanged() {
        List<ImageView> views;
        synchronized (styled) { views = new ArrayList<>(styled.keySet()); }
        for (ImageView iv : views) {
            if (iv != null) iv.post(() -> style(iv));
        }
    }

    private static void restore() {
        if (styled.isEmpty()) return;
        restorer.restoreAll();
        styled.clear();
        active = false;
        lastLog = "";
    }

    static boolean isStyled(View v) { return v instanceof ImageView && styled.containsKey(v); }
}
