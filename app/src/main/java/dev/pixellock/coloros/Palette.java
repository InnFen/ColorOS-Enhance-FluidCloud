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
    final int clockLight, clockDark, date;
    final int shortcutBg, shortcutIcon, shortcutBgActive, shortcutIconActive;
    final int signature;

    private static volatile int version;
    private static boolean listening;
    /** Called after the system theme (light/dark, dynamic colours) changed, so the clock restyles at once. */
    static volatile Runnable onChange;

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
        night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        // Clock: two close tones like the Pixel lock screen clock. Dark mode gets the pale end of the dynamic
        // palette, light mode the deep end, and both follow the system when it switches.
        if (night) {
            clockLight = color(c, android.R.color.system_accent1_100, 0xFFC8FFF4);
            clockDark = color(c, android.R.color.system_accent1_200, 0xFF9DE0D0);
            date = color(c, android.R.color.system_accent1_50, Color.WHITE);
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

    /** Cached palette; rebuilt whenever the configuration (theme overlay, night mode) changes. */
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
        if (cached == null || cachedVersion != version || cachedTone != tone || !listening) {
            cached = new Palette(context, tone);
            cachedVersion = version;
            cachedTone = tone;
        }
        return cached;
    }

    static String hex(int c) { return String.format("#%08X", c); }
}
