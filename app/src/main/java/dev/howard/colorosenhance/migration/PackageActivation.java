package dev.howard.colorosenhance.migration;

import android.content.SharedPreferences;
import java.util.function.Supplier;

/** Sampled once per injected process. A fresh package cannot race the old module. */
public final class PackageActivation {
    public static final String READY = "package_identity_ready_v1";
    public static boolean allowed(Supplier<SharedPreferences> integration) {
        try { return integration.get().getBoolean(READY,false); }
        catch (RuntimeException error) { return false; }
    }
    private PackageActivation() { }
}
