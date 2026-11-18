package dev.howard.colorosenhance.settings;

import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** UI integration is confined to the Settings main process. */
public final class SettingsModule extends XposedModule {
    private boolean allowed,initialized;
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private SettingsHostAdapter adapter;
    private final Set<String> warned=ConcurrentHashMap.newKeySet();
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        if (!dev.howard.colorosenhance.migration.PackageActivation.allowed(() -> getRemotePreferences("integration"))) return;
        allowed=!param.isSystemServer() && "com.android.settings".equals(param.getProcessName());
    }
    @Override public void onPackageReady(PackageReadyParam param) {
        if(!allowed || initialized || !param.isFirstPackage() || !"com.android.settings".equals(param.getPackageName())) return;
        initialized=true;
        try {
            preferences=getRemotePreferences("integration");
            adapter=new SettingsHostAdapter(this,param.getClassLoader());
            adapter.install();
            listener=(prefs,key)->reload();
            preferences.registerOnSharedPreferenceChangeListener(listener);
            reload();
        } catch(Throwable failure) { warn("系统设置入口初始化失败",failure); }
    }
    private void reload() {
        boolean show=true;
        try { show=preferences.getBoolean("show_entry",true); }
        catch(RuntimeException failure) { warn("入口配置读取失败",failure); }
        adapter.visibility(show);
    }
    void warn(String operation,Throwable failure) {
        // Exception messages from remote UI input must never become diagnostics.
        if(warned.add(operation)) log(Log.WARN,"ColorOSEnhance",operation+" ("+failure.getClass().getSimpleName()+")");
    }
}
