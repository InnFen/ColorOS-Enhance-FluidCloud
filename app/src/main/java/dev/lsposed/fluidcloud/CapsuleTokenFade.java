package dev.lsposed.fluidcloud;

import android.util.Log;
import android.view.WindowManager;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exempts the module capsule from same-orientation fixed-launch fades. */
final class CapsuleTokenFade {
    private final Method launch, accept, rotation, rotationChanging, windowConfiguration, configurationRotation;
    private final Field displayContent, fixedApp, attrs, session, internalWindow;
    private final ThreadLocal<Object> sameRotationLaunch = new ThreadLocal<>();

    CapsuleTokenFade(ClassLoader loader) throws ReflectiveOperationException {
        String wm = "com.android.server.wm.";
        Class<?> display = Reflect.type(loader, wm + "DisplayContent");
        Class<?> activity = Reflect.type(loader, wm + "ActivityRecord");
        Class<?> async = Reflect.type(loader, wm + "AsyncRotationController");
        Class<?> window = Reflect.type(loader, wm + "WindowState");
        Class<?> windowSession = Reflect.type(loader, wm + "Session");
        Class<?> configuration = Reflect.type(loader, "android.app.WindowConfiguration");
        launch = Reflect.method(display,"setFixedRotationLaunchingAppUnchecked",void.class,activity,int.class);
        rotation = Reflect.method(display,"getRotation",int.class);
        rotationChanging = Reflect.method(display,"isRotationChanging",boolean.class);
        fixedApp = Reflect.field(display,"mFixedRotationLaunchingApp",activity);
        windowConfiguration = Reflect.method(activity,"getWindowConfiguration",configuration);
        configurationRotation = Reflect.method(configuration,"getRotation",int.class);
        accept = Reflect.method(async,"accept",void.class,window);
        displayContent = Reflect.field(async,"mDisplayContent",display);
        attrs = Reflect.field(window,"mAttrs",WindowManager.LayoutParams.class);
        session = Reflect.field(window,"mSession",windowSession);
        internalWindow = Reflect.field(windowSession,"mCanAddInternalSystemWindow",boolean.class);
    }

    void install(HookEntry module) throws ReflectiveOperationException {
        Method bridge = Reflect.method(accept.getDeclaringClass(),"accept",void.class,Object.class);
        if (!module.deoptimize(bridge)) throw new IllegalStateException("Fixed-rotation window visitor could not be deoptimized");
        var launchHook = module.hook(launch).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            if (!module.config.keepCapsuleDuringLaunch()) return chain.proceed();
            Object display = chain.getThisObject(), previous = sameRotationLaunch.get();
            if (chain.getArg(0) != null && sameRotation((int)chain.getArg(1),(int)Reflect.call(rotation,display)))
                sameRotationLaunch.set(display);
            else sameRotationLaunch.remove();
            try { return chain.proceed(); }
            finally {
                if (previous == null) sameRotationLaunch.remove(); else sameRotationLaunch.set(previous);
            }
        });
        try {
            module.hook(accept).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (!module.config.keepCapsuleDuringLaunch()) return chain.proceed();
                Object window = chain.getArg(0);
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) Reflect.get(attrs,window);
                if (params != null && matches(params.type,params.packageName,params.getTitle())
                        && (boolean) Reflect.get(internalWindow,Reflect.get(session,window))
                        && fixedLaunchWithoutRotation(Reflect.get(displayContent,chain.getThisObject()))) {
                    if (Log.isLoggable("COSPresentation",Log.DEBUG)) module.info("Capsule excluded from same-orientation token fade");
                    return null;
                }
                return chain.proceed();
            });
        } catch (RuntimeException | LinkageError error) { launchHook.unhook(); throw error; }
    }

    static boolean matches(int type, String pkg, CharSequence title) {
        return (type == PresentationWindow.DISPLAY_OVERLAY || type == PresentationWindow.STATUS_BAR_SUB_PANEL)
                && "com.android.systemui".equals(pkg)
                && title != null && PresentationWindow.TITLE.contentEquals(title);
    }

    static boolean sameRotation(int target, int current) { return target >= 0 && target == current; }

    private boolean fixedLaunchWithoutRotation(Object display) {
        if (display == null || (boolean) Reflect.call(rotationChanging,display)) return false;
        if (sameRotationLaunch.get() == display) return true;
        // The native controller may be started on a later handler callback.
        Object activity = Reflect.get(fixedApp,display);
        if (activity == null) return false;
        Object config = Reflect.call(windowConfiguration,activity);
        return sameRotation((int)Reflect.call(configurationRotation,config),(int)Reflect.call(rotation,display));
    }
}
