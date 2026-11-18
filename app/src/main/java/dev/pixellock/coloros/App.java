package dev.pixellock.coloros;

import android.app.Application;

import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Binds to LSPosed's module service so the settings screen can write remote preferences. */
public final class App extends Application {
    private volatile XposedService service;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    @Override public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService s) { service = s; notifyListeners(); }
            @Override public void onServiceDied(XposedService s) { if (service == s) { service = null; notifyListeners(); } }
        });
    }

    XposedService service() { return service; }

    void observe(Runnable r) { listeners.addIfAbsent(r); }
    void unobserve(Runnable r) { listeners.remove(r); }

    private void notifyListeners() { for (Runnable r : listeners) r.run(); }
}
