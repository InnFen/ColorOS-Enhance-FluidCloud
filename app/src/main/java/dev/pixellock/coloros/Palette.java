package dev.pixellock.coloros;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * Colors taken from the system dynamic (Monet) palette, i.e. android.R.color.system_accent*_* tokens.
 * ColorOS fills these from its own "system color" setting, so they follow the wallpaper / theme.
 */
final class Palette {
    final boolean night;
    /** Lock screen clock colours (follow light/dark mode). */
    final int clockLight, clockDark, date;
    /** Always-on display colours: the pale tones, which read well on black in either mode. */
    final int aodLight, aodDark, aodDate;
    final int shortcutBg, shortcutIcon, shortcutBgActive, shortcutIconActive;
    final int signature;

    private static volatile int version;
    private static boolean listening;
    /** Called after the system theme (light/dark, dynamic colours) changed, so the clock restyles at once. */
    static volatile Runnable onChange;
    static volatile java.util.function.Consumer<String> onLog;

    /** accent1 tones the shortcut buttons can use, light to dark. */
    static final int[] TONES = {
            android.R.color.system_accent1_10, android.R.color.system_accent1_50,
            android.R.color.system_accent1_100, android.R.color.system_accent1_200,
            android.R.color.system_accent1_300, android.R.color.system_accent1_400,
            android.R.color.system_accent1_500, android.R.color.system_accent1_600,
            android.R.color.system_accent1_700, android.R.color.system_accent1_800,
    };
    static final int[] TONE_NAMES = {10, 50, 100, 200, 300, 400, 500, 600, 700, 800};

    private Palette(Context c, int tone) {
        night = systemNight(c);
        // Clock: two close tones like the Pixel lock screen clock. Dark mode gets the pale end of the dynamic
        // palette, light mode the deep end, and both follow the system when it switches.
        aodLight = color(c, android.R.color.system_accent1_100, 0xFFC8FFF4);
        aodDark = color(c, android.R.color.system_accent1_200, 0xFF9DE0D0);
        aodDate = color(c, android.R.color.system_accent1_50, Color.WHITE);
        if (night) {
            clockLight = aodLight;
            clockDark = aodDark;
            date = aodDate;
        } else {
            clockLight = color(c, android.R.color.system_accent1_600, 0xFF006B5E);
            clockDark = color(c, android.R.color.system_accent1_700, 0xFF005046);
            date = color(c, android.R.color.system_accent1_800, 0xFF00382F);
        }
        // Shortcut buttons: a pale tonal block by default, like the fingerprint button. The icon picks dark or
        // light from the block's own brightness so it always stands out.
        int t = Math.max(0, Math.min(TONES.length - 1, tone));
        int iconDark = color(c, android.R.color.system_accent1_900, 0xFF001A41);
        int iconLight = color(c, android.R.color.system_accent1_10, Color.WHITE);
        shortcutBg = color(c, TONES[t], 0xFFECF0FF);
        shortcutIcon = Color.luminance(shortcutBg) > 0.4f ? iconDark : iconLight;
        // Switched on (e.g. flashlight lit): the opposite end of the scale.
        shortcutBgActive = color(c, t <= 4 ? android.R.color.system_accent1_600 : android.R.color.system_accent1_100,
                0xFF3D5F90);
        shortcutIconActive = Color.luminance(shortcutBgActive) > 0.4f ? iconDark : iconLight;
        signature = clockLight * 31 + shortcutBg * 17 + (night ? 1 : 0);
    }

    /** Color shown while a button is held down: a step toward the switched-on tone. */
    int shortcutPressed(boolean active) {
        int from = active ? shortcutBgActive : shortcutBg;
        int to = active ? shortcutBg : shortcutBgActive;
        float f = 0.3f;
        return Color.argb(255,
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f));
    }

    private static int color(Context c, int id, int fallback) {
        try { return c.getColor(id); } catch (Throwable t) { return fallback; }
    }

    private static Palette cached;
    private static int cachedVersion = -1;
    private static int cachedTone = -1;
    private static String cachedKey;

    /**
     * Light or dark mode from the system-wide configuration. SystemUI's lock screen windows can carry a
     * configuration of their own, so their context isn't trusted for this.
     */
    static boolean systemNight(Context c) {
        try {
            int mode = android.content.res.Resources.getSystem().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            if (mode != Configuration.UI_MODE_NIGHT_UNDEFINED) return mode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) { }
        try {
            android.app.UiModeManager um = c.getSystemService(android.app.UiModeManager.class);
            if (um != null && um.getNightMode() == android.app.UiModeManager.MODE_NIGHT_YES) return true;
            if (um != null && um.getNightMode() == android.app.UiModeManager.MODE_NIGHT_NO) return false;
        } catch (Throwable ignored) { }
        return (c.getApplicationContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Something that changes the colours: light/dark mode and the system's dynamic colour choice. */
    private static String themeKey(Context c) {
        String overlays = null;
        try {
            overlays = android.provider.Settings.Secure.getString(c.getContentResolver(),
                    "theme_customization_overlay_packages");
        } catch (Throwable ignored) { }
        return systemNight(c) + "|" + overlays;
    }

    /** Forget the cached colours (screen turned on, wallpaper or theme changed): the next read is fresh. */
    static void invalidate() { version++; }

    /** Cached palette; rebuilt whenever light/dark mode, the dynamic colours or the configuration change. */
    static synchronized Palette of(Context context, int tone) {
        if (!listening) {
            listening = true;
            try {
                context.getApplicationContext().registerComponentCallbacks(new ComponentCallbacks() {
                    @Override public void onConfigurationChanged(Configuration newConfig) {
                        version++;
                        Runnable r = onChange;
                        if (r != null) r.run();
                    }
                    @Override public void onLowMemory() { }
                });
            } catch (Throwable ignored) {
                listening = false;
            }
        }
        String key = themeKey(context);
        if (cached == null || cachedVersion != version || cachedTone != tone || !listening
                || !key.equals(cachedKey)) {
            // A context built from the current system configuration, so colours and mode are read fresh.
            Context fresh = context;
            try {
                fresh = context.createConfigurationContext(
                        new Configuration(android.content.res.Resources.getSystem().getConfiguration()));
            } catch (Throwable ignored) { }
            Palette old = cached;
            cached = new Palette(fresh, tone);
            cachedVersion = version;
            cachedTone = tone;
            cachedKey = key;
            if (old != null && old.signature != cached.signature && onLog != null) {
                onLog.accept("Palette: " + (cached.night ? "dark" : "light") + " mode, clock " + hex(cached.clockLight)
                        + "/" + hex(cached.clockDark) + ", AOD " + hex(cached.aodLight));
            }
        }
        return cached;
    }

    static String hex(int c) { return String.format("#%08X", c); }
}
