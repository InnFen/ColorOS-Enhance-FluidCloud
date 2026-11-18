package dev.howard.colorosenhance.installer;

import dev.howard.colorosenhance.PreferenceSession;
import java.util.HashMap;

/** Explicit empty selection distinguishes a fresh install/reset from the old fixed target. */
public final class InstallerPreferences {
    public static final String TARGET_KEY="target_package";
    public static final String LEGACY_TARGET="com.rosan.installer.x.revived";
    public static void initialize(PreferenceSession.Store store) {
        var previous=store.read();
        if(previous.containsKey(TARGET_KEY)) return;
        var next=new HashMap<String,Object>(previous);
        next.put(TARGET_KEY,previous.containsKey("enabled") ? LEGACY_TARGET : "");
        if(!store.replace(next)) throw new IllegalStateException("Installer selection migration unconfirmed");
    }
    public static boolean validPackage(String value) {
        return value!=null && value.length()<=255 && (value.isEmpty()
                || value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"));
    }
    private InstallerPreferences() {}
}
