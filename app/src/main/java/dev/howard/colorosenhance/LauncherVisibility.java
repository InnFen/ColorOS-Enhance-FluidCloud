package dev.howard.colorosenhance;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** The launcher alias is optional; the INFO activity and embedded Settings remain usable. */
public final class LauncherVisibility {
    private final PackageManager packages;
    private final ComponentName alias;
    public LauncherVisibility(Context context) {
        packages=context.getPackageManager();
        alias=new ComponentName(context.getPackageName(),MainActivity.class.getPackageName()+".Launcher");
    }
    public boolean visible() {
        int state=packages.getComponentEnabledSetting(alias);
        return state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
    public boolean setVisible(boolean visible) {
        try {
            packages.setComponentEnabledSetting(alias,visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            return visible()==visible;
        } catch(RuntimeException failure) { return false; }
    }
}
