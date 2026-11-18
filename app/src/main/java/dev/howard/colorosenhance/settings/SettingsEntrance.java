package dev.howard.colorosenhance.settings;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
final class SettingsEntrance {
    static final String CATEGORY = dev.howard.colorosenhance.BuildConfig.APPLICATION_ID + ".category";
    private final HostReflection host;
    SettingsEntrance(HostReflection host) { this.host = host; }
    void refresh(Object fragment, boolean show) throws ReflectiveOperationException {
        Object screen = host.call(fragment, "getPreferenceScreen");
        Context context = (Context) host.call(fragment, "getContext");
        if (screen == null || context == null) return;
        Object existing = host.call(screen, "findPreference", CATEGORY);
        if (!show) { if (existing != null) host.call(screen, "removePreference", existing); return; }
        if (existing != null) return;
        Object category = host.create("com.oplus.settings.widget.preference.SettingsPreferenceCategory", context);
        Object entry = host.create("com.oplus.settings.widget.preference.SettingsSimpleJumpPreference", context);
        host.call(category, "setKey", CATEGORY);
        host.call(category, "setOrder", 75);
        host.call(category, "setPersistent", false);
        host.call(entry, "setKey", dev.howard.colorosenhance.BuildConfig.APPLICATION_ID + ".entry");
        host.call(entry, "setTitle", "ColorOS 增强");
        host.call(entry, "setPersistent", false);
        host.listen(entry, "setOnPreferenceClickListener", args -> {
            Intent intent = SettingsRoutes.intent("home");
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        });
        host.call(screen, "addPreference", category);
        try { host.call(category, "addPreference", entry); }
        catch (ReflectiveOperationException failure) { host.call(screen, "removePreference", category); throw failure; }
    }
}
