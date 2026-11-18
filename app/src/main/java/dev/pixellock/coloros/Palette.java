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

    private Palette(Context c) {
        night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        // Clock: two close light tones, like the Pixel lock screen clock.
        clockLight = color(c, android.R.color.system_accent1_100, 0xFFC8FFF4);
        clockDark = color(c, android.R.color.system_accent1_200, 0xFF9DE0D0);
        date = color(c, android.R.color.system_accent1_50, Color.WHITE);
        // Shortcut buttons: same tonal block style as the fingerprint button.
        shortcutBg = color(c, android.R.color.system_accent1_100, 0xFFD8E2FF);
        shortcutIcon = color(c, android.R.color.system_accent1_900, 0xFF001A41);
        shortcutBgActive = color(c, android.R.color.system_accent1_600, 0xFF3D5F90);
        shortcutIconActive = color(c, android.R.color.system_accent1_0, Color.WHITE);
        signature = clockLight * 31 + shortcutBg * 17 + (night ? 1 : 0);
    }

    private static int color(Context c, int id, int fallback) {
        try { return c.getColor(id); } catch (Throwable t) { return fallback; }
    }

    private static Palette cached;
    private static int cachedVersion = -1;

    /** Cached palette; rebuilt whenever the configuration (theme overlay, night mode) changes. */
    static synchronized Palette of(Context context) {
        if (!listening) {
            listening = true;
            try {
                context.getApplicationContext().registerComponentCallbacks(new ComponentCallbacks() {
                    @Override public void onConfigurationChanged(Configuration newConfig) { version++; }
                    @Override public void onLowMemory() { }
                });
            } catch (Throwable ignored) {
                listening = false;
            }
        }
        if (cached == null || cachedVersion != version || !listening) {
            cached = new Palette(context);
            cachedVersion = version;
        }
        return cached;
    }

    static String hex(int c) { return String.format("#%08X", c); }
}
