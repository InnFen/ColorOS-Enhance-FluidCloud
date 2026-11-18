package dev.pixellock.coloros;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Loads Google Sans Flex (OFL) bundled in the module APK, with variable-font settings per use. */
final class Fonts {
    private static final String ASSET = "fonts/GoogleSansFlex.ttf";
    private static final Map<String, Typeface> cache = new HashMap<>();
    private static File extracted;
    private static boolean failedOnce;

    private Fonts() { }

    /** Chunky clock digits. round = 0..100 (ROND axis). */
    static Typeface clock(HookEntry m, Context c, int round) {
        return get(m, c, "'wght' 1000, 'wdth' 110, 'opsz' 144, 'ROND' " + round,
                Typeface.create(Typeface.SANS_SERIF, 900, false));
    }

    static Typeface date(HookEntry m, Context c) {
        return get(m, c, "'wght' 500, 'wdth' 100, 'opsz' 18, 'ROND' 0",
                Typeface.create(Typeface.SANS_SERIF, 500, false));
    }

    private static synchronized Typeface get(HookEntry m, Context c, String variation, Typeface fallback) {
        Typeface cachedFace = cache.get(variation);
        if (cachedFace != null) return cachedFace;
        Typeface face = null;
        // 1) Straight from the module's assets.
        try {
            ApplicationInfo info = m.getModuleApplicationInfo();
            Context module = c.createPackageContext(info.packageName, Context.CONTEXT_IGNORE_SECURITY);
            face = new Typeface.Builder(module.getAssets(), ASSET).setFontVariationSettings(variation).build();
        } catch (Throwable t) {
            if (!failedOnce) m.error("Font from module assets failed, trying file copy", t);
        }
        // 2) Copy the font out of the module APK into SystemUI's cache.
        if (face == null) {
            try {
                File file = extract(m, c);
                if (file != null) face = new Typeface.Builder(file).setFontVariationSettings(variation).build();
            } catch (Throwable t) {
                if (!failedOnce) m.error("Font file copy failed; using system sans-serif", t);
            }
        }
        if (face == null) {
            failedOnce = true;
            face = fallback;
            m.info("Using fallback typeface for " + variation);
        } else {
            m.info("Loaded Google Sans Flex: " + variation);
        }
        cache.put(variation, face);
        return face;
    }

    private static File extract(HookEntry m, Context c) throws Exception {
        if (extracted != null && extracted.isFile()) return extracted;
        String apk = m.getModuleApplicationInfo().sourceDir;
        File out = new File(c.getCacheDir(), "pixellock-GoogleSansFlex.ttf");
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry("assets/" + ASSET);
            if (entry == null) return null;
            if (!out.isFile() || out.length() != entry.getSize()) {
                try (InputStream in = zip.getInputStream(entry); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
            }
        }
        extracted = out;
        return out;
    }

    static synchronized void clear() { cache.clear(); }
}
