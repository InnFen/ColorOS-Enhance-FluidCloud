package dev.howard.colorosenhance.settings;
import android.content.Intent;
import android.os.Bundle;
final class SettingsHostPolicy {
    static String route(String className,Intent intent,Bundle arguments) {
        if(!SettingsRoutes.accepts(className,intent)) return null;
        String route=SettingsRoutes.route(arguments);
        return route!=null && route.equals(SettingsRoutes.route(intent.getBundleExtra(":settings:show_fragment_args"))) ? route : null;
    }
}
