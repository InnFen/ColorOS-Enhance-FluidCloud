package dev.howard.colorosenhance.settings;
import android.content.Intent;
import android.os.Bundle;
import java.util.Set;
import dev.howard.colorosenhance.SettingsCatalog;
public final class SettingsRoutes {
    public static final String FRAGMENT = "com.coui.appcompat.preference.COUIPreferenceFragment";
    public static final String MARKER = dev.howard.colorosenhance.BuildConfig.APPLICATION_ID + ".embedded";
    public static final String ROUTE = dev.howard.colorosenhance.BuildConfig.APPLICATION_ID + ".route";
    private static final Set<String> ROUTES = dev.howard.colorosenhance.SettingsLayout.routes();
    public static Intent intent(String route) {
        if (!ROUTES.contains(route)) throw new IllegalArgumentException("Unknown settings page");
        Bundle args = new Bundle(); args.putBoolean(MARKER,true); args.putString(ROUTE,route);
        return new Intent().setClassName("com.android.settings","com.android.settings.SubSettings")
                .putExtra(":settings:show_fragment",FRAGMENT)
                .putExtra(":settings:show_fragment_args",args)
                .putExtra(":settings:show_fragment_title",title(route));
    }
    public static boolean accepts(String fragment,Intent intent) {
        try {
            return FRAGMENT.equals(fragment) && intent != null && intent.getComponent() != null
                    && "com.android.settings".equals(intent.getComponent().getPackageName())
                    && "com.android.settings.SubSettings".equals(intent.getComponent().getClassName())
                    && FRAGMENT.equals(intent.getStringExtra(":settings:show_fragment"))
                    && route(intent.getBundleExtra(":settings:show_fragment_args")) != null;
        } catch (RuntimeException error) { return false; }
    }
    public static String route(Bundle arguments) {
        try {
            if (arguments == null || !Boolean.TRUE.equals(arguments.get(MARKER))) return null;
            Object route = arguments.get(ROUTE);
            return route instanceof String s && ROUTES.contains(s) ? s : null;
        } catch (RuntimeException error) { return null; }
    }
    public static String title(String route) {
        var page=dev.howard.colorosenhance.SettingsLayout.page(route);
        if(page!=null)return page.title();
        SettingsCatalog.Group group = SettingsCatalog.find(route);
        return group != null ? group.title() : "sim_slots".equals(route) ? "管理卡槽与 PIN" : "ColorOS 增强";
    }
}
