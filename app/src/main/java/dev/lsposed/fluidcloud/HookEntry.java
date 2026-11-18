package dev.lsposed.fluidcloud;

import android.util.Log;
import android.content.SharedPreferences;
import java.lang.reflect.Method;
import java.util.Map;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Modern libxposed entry. Scope metadata and runtime checks both restrict injection. */
public final class HookEntry extends XposedModule {
    private static final String SYSTEM_UI = "com.android.systemui";
    private boolean allowed;
    private boolean systemServer;
    private boolean initialized;
    private ColorOsAdapter adapter;
    private MediaPauseRetention pausedMusic;
    private CameraCardExpansion cameraCards;
    private CloudExtras extras;
    MediaWaveFeature mediaWaves;
    volatile CloudConfig config = CloudConfig.from(Map.of());
    volatile int cameraAvoidPercent = CameraAvoidance.DEFAULT_PERCENT;
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        if (!dev.howard.colorosenhance.migration.PackageActivation.allowed(() -> getRemotePreferences("integration"))) return;
        systemServer = param.isSystemServer();
        allowed = !systemServer && SYSTEM_UI.equals(param.getProcessName());
    }

    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        if (!systemServer) return;
        try {
            observePreferences();
            new CapsuleTokenFade(param.getClassLoader()).install(this);
            info("Capsule token-fade exclusion installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            error("Capsule token-fade contract unavailable; native transitions retained",failure);
        }
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        if (!allowed || initialized || !param.isFirstPackage() || !SYSTEM_UI.equals(param.getPackageName())) return;
        initialized = true;
        try {
            observePreferences();
            if (config.unbindStatusBar()) {
                adapter = new ColorOsAdapter(this, param.getClassLoader());
                adapter.install();
            } else info("Status-bar scene hooks disabled by unbind switch");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            error("Unsupported SystemUI adapter; native behavior retained", error);
        }
        try { mediaWaves=new MediaWaveFeature(this,param.getClassLoader()); }
        catch(ReflectiveOperationException|RuntimeException|LinkageError error){error("Realtime media waveform unavailable; OEM animation retained",error);}
        try {
            installPausedMusic(param.getClassLoader());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            error("Paused music retention unavailable; native media behavior retained", error);
        }
        try { installCapsuleClickTop(param.getClassLoader()); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            error("Capsule click-top fix unavailable; native click behavior retained", error);
        }
        try {
            cameraCards = new CameraCardExpansion(this, param.getClassLoader());
            cameraCards.install();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            cameraCards = null;
            error("Camera card expansion unavailable; native card behavior retained", error);
        }
        try { extras=new CloudExtras(this,param.getClassLoader());extras.install(); }
        catch(ReflectiveOperationException|RuntimeException|LinkageError error){extras=null;error("Optional capsule controls unavailable; native behavior retained",error);}
    }

    private void installCapsuleClickTop(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> bar = Reflect.type(loader, "com.android.systemui.statusbar.phone.PhoneStatusBarView");
        Class<?> helper = Reflect.type(loader, "com.oplus.systemui.statusbar.util.StatusbarClickHelper");
        Class<?> extension = Reflect.type(loader, "com.oplus.systemui.statusbar.phone.PhoneStatusBarViewExImpl");
        Method dispatch = Reflect.method(bar, "dispatchTouchEvent", boolean.class, android.view.MotionEvent.class);
        Method click = Reflect.method(helper, "scrollToTop", void.class, android.view.MotionEvent.class);
        Method caller = Reflect.method(extension, "handleClick", void.class, android.view.MotionEvent.class);
        if (!deoptimize(caller)) throw new IllegalStateException("Click-top caller could not be deoptimized");
        CapsuleClickTopGuard guard = new CapsuleClickTopGuard();
        var clickHook = hook(click).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            if (config.preventCapsuleClickTop() && guard.suppress((android.view.MotionEvent) chain.getArg(0))) return null;
            return chain.proceed();
        });
        try {
            hook(dispatch).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain ->
                    guard.dispatch((android.view.View) chain.getThisObject(), (android.view.MotionEvent) chain.getArg(0),
                            config.preventCapsuleClickTop(), chain::proceed));
        } catch (RuntimeException | LinkageError error) { clickHook.unhook(); throw error; }
        info("Native capsule click-top exclusion installed");
    }

    private void installPausedMusic(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> controller = Reflect.type(loader,
                "com.oplus.systemui.media.seedling.livealert.MediaLiveAlertController");
        Class<?> media = Reflect.type(loader,
                "com.oplus.systemui.seedlingservice.mediaControl.SeedlingMediaData");
        Method send = Reflect.method(controller, "sendLiveAlertData", boolean.class, String.class);
        MediaPauseRetention retention = new MediaPauseRetention(controller, media,
                MediaPauseRetention::liveRetainableSession,
                error -> error("Paused music refresh unavailable; using native media behavior", error));
        hook(send).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            MediaPauseRetention.SendState retained = null;
            try {
                retention.trace(chain.getThisObject(), "before", (String) chain.getArg(0));
                retained = retention.beforeSend(chain.getThisObject(), (String) chain.getArg(0),
                        config.retainPausedMusic());
            } catch (RuntimeException | LinkageError error) {
                error("Paused music state unavailable; using native media behavior", error);
            }
            try { return chain.proceed(); }
            finally {
                try { retention.afterSend(chain.getThisObject(), retained);
                    retention.trace(chain.getThisObject(), "after", (String) chain.getArg(0)); }
                catch (RuntimeException | LinkageError error) {
                    error("Paused music timeout cleanup unavailable", error);
                }
                if(mediaWaves!=null)try{mediaWaves.nativeMediaChanged(chain.getThisObject());}
                catch(RuntimeException|LinkageError error){error("Media waveform selection unavailable",error);}
            }
        });
        pausedMusic = retention;
        pausedMusic.configurationChanged(config.retainPausedMusic());
        info("Paused music status-bar timeout adapter installed");
    }

    private void observePreferences() {
        preferences = getRemotePreferences(CloudConfig.GROUP);
        preferenceListener = (prefs, key) -> reload();
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        reload();
    }

    private void reload() {
        try {
            Map<String, ?> values = preferences.getAll();
            config = CloudConfig.from(values);
            cameraAvoidPercent = CameraAvoidance.percent(values);
        }
        catch (RuntimeException error) {
            config = CloudConfig.from(Map.of());
            cameraAvoidPercent = CameraAvoidance.DEFAULT_PERCENT;
            error("Configuration unavailable; native scene behavior retained", error);
        }
        if (adapter != null) {
            if (!config.unbindStatusBar()) { adapter.uninstall(); adapter = null; }
            else adapter.configurationChanged();
        }
        if (pausedMusic != null) pausedMusic.configurationChanged(config.retainPausedMusic());
        if (cameraCards != null) cameraCards.configurationChanged();
        if (extras != null) extras.configurationChanged();
        if (mediaWaves != null) mediaWaves.configurationChanged();
    }

    boolean mediaWaveVisible(android.view.View view){
        if(adapter!=null){Boolean presented=adapter.mediaWaveVisibility(view);if(presented!=null)return presented;}
        return view.isShown() && view.getWindowVisibility()==android.view.View.VISIBLE;
    }
    void mediaWavePixels(android.view.View view){if(adapter!=null)adapter.mediaWavePixels(view);}

    void info(String message) { log(Log.INFO, "FluidCloudUnbound", message); }
    void error(String message, Throwable error) { log(Log.ERROR, "FluidCloudUnbound", message, error); }
}
