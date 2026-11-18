package dev.lsposed.fluidcloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Independent plugin lifecycle; does not require the status-bar unbind switch. */
final class CameraCardExpansion {
    private final HookEntry module;
    private final Method added, attached, removed;
    private final java.lang.reflect.Field host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<ClassLoader> attempted = Collections.newSetFromMap(new WeakHashMap<>());
    private record Installed(ClassLoader loader, NativeCameraCard contract, NativeInstantCameraCard instant,
                             ArrayList<XposedInterface.HookHandle> hooks, NativeCardGlass glass) { }
    private final ArrayList<Installed> contracts = new ArrayList<>();
    private boolean failed;

    CameraCardExpansion(HookEntry module, ClassLoader loader) throws ReflectiveOperationException {
        this.module = module;
        Class<?> controller = Reflect.type(loader, "com.oplus.systemui.statusbar.seeding.CapsulePluginContainerController");
        Class<?> plugin = Reflect.type(loader, "com.android.systemui.plugins.statusbar.SeedlingPlugin");
        added = Reflect.method(controller, "onPluginAdded", void.class, plugin, Context.class);
        attached = Reflect.method(controller, "onViewAttached", void.class);
        removed = Reflect.method(controller, "onPluginRemove", void.class, plugin);
        host = Reflect.field(controller, "capsulePluginContainer", ViewGroup.class);
    }
    void install() {
        ArrayList<XposedInterface.HookHandle> lifecycle = new ArrayList<>();
        try {
            lifecycle.add(module.hook(added).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                // The instantiated views reveal the actual plugin loader, including reloads.
                Object result = chain.proceed(); capture(chain.getThisObject()); return result;
            }));
            lifecycle.add(module.hook(attached).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed(); capture(chain.getThisObject()); return result;
            }));
            lifecycle.add(module.hook(removed).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                ViewGroup container = (ViewGroup) Reflect.get(host, chain.getThisObject());
                for (int i = 0; i < container.getChildCount(); i++) release(container.getChildAt(i).getClass().getClassLoader());
                return chain.proceed();
            }));
        } catch (RuntimeException | LinkageError error) {
            for (var hook : lifecycle) hook.unhook(); throw error;
        }
    }
    private void capture(Object controller) {
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        ViewGroup container = (ViewGroup) Reflect.get(host, controller);
        for (int i = 0; i < container.getChildCount(); i++) {
            View view = container.getChildAt(i);
            if (!view.getClass().getName().equals(NativeShadeInteraction.VIEWS + "CapsuleContainerRoot")) continue;
            ClassLoader loader = view.getClass().getClassLoader();
            if (!attempted.add(loader)) continue;
            ArrayList<XposedInterface.HookHandle> hooks = new ArrayList<>();
            NativeCardGlass glass = null;
            try {
                NativeCameraCard nativeCard = new NativeCameraCard(loader);
                nativeCard.onFailure = this::geometryFailed;
                nativeCard.gapDp = () -> module.cameraGapDp;
                nativeCard.onInfo = module::info;
                NativeCardCorners corners = new NativeCardCorners(() -> module.config.corners().card());
                int cornerHooks = hooks.size();
                try {
                    corners.install(module,loader,nativeCard,view,hooks);
                    module.info("Native card corner profile installed");
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    while(hooks.size()>cornerHooks)hooks.removeLast().unhook();
                    module.error("Card corner profile unavailable; retaining native corners",error);
                }
                NativeInstantCameraCard instant = installInstant(loader, nativeCard, hooks);
                if (!nativeCard.legacy) {
                    try {
                        glass = new NativeCardGlass(module, () -> module.config.corners().card(), nativeCard.cardType,
                                Reflect.type(loader, NativeCameraCard.VIEWS + "InstantContainer"));
                        glass.install(loader);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                        if (glass != null) glass.close();
                        glass = null;
                        module.error("Seedling material loader unavailable; retaining native template geometry", unavailable);
                    }
                }
                if (!module.deoptimize(nativeCard.windowAdd) || !module.deoptimize(nativeCard.rootUpdate)
                        || !module.deoptimize(nativeCard.cardLayout))
                    throw new IllegalStateException("Native card layout callers could not be deoptimized");
                hooks.add(module.hook(nativeCard.layout).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (!failed && Looper.myLooper() == Looper.getMainLooper()) safe(() -> {
                        nativeCard.afterNativeLayout((ViewGroup) chain.getThisObject(), enabled());
                        trace(nativeCard, "nativeLayout", chain.getThisObject());
                    });
                    return result;
                }));
                hooks.add(module.hook(nativeCard.measure).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (!failed && nativeCard.cardType.isInstance(chain.getThisObject())
                            && Looper.myLooper() == Looper.getMainLooper()) safe(() ->
                            nativeCard.beforeMeasure((View) chain.getThisObject(), enabled()));
                    return chain.proceed();
                }));
                hooks.add(module.hook(nativeCard.intrinsicHeight).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    int nativeHeight = (int) chain.proceed();
                    int[] result = {nativeHeight};
                    if (!failed && enabled() && Looper.myLooper() == Looper.getMainLooper())
                        safe(() -> result[0] = nativeCard.intrinsicHeight((View) chain.getThisObject(), nativeHeight, true));
                    return result[0];
                }));
                hooks.add(module.hook(nativeCard.detach).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    safe(() -> nativeCard.detached((ViewGroup) chain.getThisObject()));
                    return chain.proceed();
                }));
                hooks.add(module.hook(nativeCard.cardLayout).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (!failed && nativeCard.cardType.isInstance(chain.getThisObject())
                            && Looper.myLooper() == Looper.getMainLooper()) safe(() ->
                            { nativeCard.afterLayout((View) chain.getThisObject(), enabled());
                                trace(nativeCard, "cardLayout", chain.getThisObject()); });
                    return result;
                }));
                hooks.add(module.hook(nativeCard.centers).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (!failed) safe(() -> trace(nativeCard, "centers=" + result, chain.getThisObject()));
                    return result;
                }));
                hooks.add(module.hook(nativeCard.blurBounds).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    View card = (View) chain.getThisObject();
                    android.graphics.Rect original = (android.graphics.Rect) chain.getArg(0);
                    android.graphics.Rect bounds = new android.graphics.Rect(original);
                    if (!failed) safe(() -> nativeCard.beforeBlurBounds(card, bounds, enabled()));
                    Object result = bounds.equals(original) ? chain.proceed() : chain.proceed(new Object[]{bounds});
                    if (!failed) safe(() -> nativeCard.align(card, enabled()));
                    return result;
                }));
                hooks.add(module.hook(nativeCard.outline).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    View card = (View) chain.getArg(0);
                    boolean adapt = !failed && nativeCard.handles(card) && enabled();
                    android.graphics.Rect original = adapt ? nativeCard.outlineBounds(chain.getThisObject()) : null;
                    if (adapt)
                        safe(() -> nativeCard.beforeOutline(chain.getThisObject(), card, true));
                    Object result;
                    try {
                        result = chain.proceed();
                        if (nativeCard.legacy)
                            corners.legacyOutline(nativeCard,card,(android.graphics.Outline)chain.getArg(1),
                                    chain.getThisObject());
                    }
                    finally { if (adapt) nativeCard.restoreOutlineBounds(chain.getThisObject(), original); }
                    if (!failed && nativeCard.handles(card) && enabled())
                        safe(() -> nativeCard.align(card, true));
                    return result;
                }));
                hooks.add(module.hook(nativeCard.foreground).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (!failed && nativeCard.cardType.isInstance(chain.getThisObject()) && enabled()) safe(() ->
                            nativeCard.beforeDraw((View) chain.getThisObject(), true));
                    // The header is the last child. Native dispatchDraw renders
                    // its own spotlight afterwards, so one highlight spans both.
                    return chain.proceed();
                }));
                hooks.add(module.hook(nativeCard.cardAlpha).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (!failed && nativeCard.cardType.isInstance(chain.getThisObject()) && enabled()) safe(() ->
                            nativeCard.updateSurface((View) chain.getThisObject(), true));
                    return result;
                }));
                Method translate = Reflect.method(View.class, "setTranslationY", void.class, float.class);
                Method scale = Reflect.method(View.class, "setScaleY", void.class, float.class);
                hooks.add(module.hook(translate).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (failed || !nativeCard.handles((View) chain.getThisObject()) || !enabled()) return chain.proceed();
                    float nativeY = (float) chain.getArg(0);
                    float[] y = {nativeY};
                    safe(() -> y[0] = nativeCard.translation((View) chain.getThisObject(), nativeY, true));
                    return y[0] == nativeY ? chain.proceed() : chain.proceed(new Object[]{y[0]});
                }));
                hooks.add(module.hook(scale).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (!failed && nativeCard.handles((View) chain.getThisObject()) && enabled())
                        safe(() -> nativeCard.align((View) chain.getThisObject(), true));
                    return result;
                }));
                contracts.add(new Installed(loader, nativeCard, instant, hooks, glass));
                module.info("Native camera card geometry installed (" + (nativeCard.legacy ? "legacy" : "current") + ")");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                if (glass != null) glass.close();
                for (var hook : hooks) hook.unhook();
                module.error("Camera card contract unavailable; native card behavior retained", error);
            }
        }
    }
    private NativeInstantCameraCard installInstant(ClassLoader loader, NativeCameraCard cards,
            ArrayList<XposedInterface.HookHandle> hooks) {
        ArrayList<XposedInterface.HookHandle> pending = new ArrayList<>();
        try {
            var instant = new NativeInstantCameraCard(name -> Reflect.type(loader, name), cards);
            if (!cards.legacy) {
                Method updater = Reflect.method(instant.type, "u", void.class);
                if (!module.deoptimize(updater)) throw new IllegalStateException("Instant input-region updater could not be deoptimized");
            }
            for (Method method : new Method[]{instant.state, instant.position})
                pending.add(module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (!failed && Looper.myLooper() == Looper.getMainLooper())
                        safe(() -> { ViewGroup container = (ViewGroup) chain.getThisObject();
                            if (method.equals(instant.state)) instant.afterStateChange(container, enabled());
                            else { instant.nativeChanged(container); instant.update(container, enabled()); }
                            trace(cards, "instantState", chain.getThisObject()); });
                    return result;
                }));
            pending.add(module.hook(instant.draw).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (!failed && Looper.myLooper() == Looper.getMainLooper())
                    safe(() -> { instant.update((ViewGroup) chain.getThisObject(), enabled());
                        trace(cards, "instantDraw", chain.getThisObject()); });
                return chain.proceed();
            }));
            pending.add(module.hook(instant.detach).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                safe(() -> instant.detached((ViewGroup) chain.getThisObject()));
                return chain.proceed();
            }));
            pending.add(module.hook(instant.blur).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                View view = (View) chain.getArg(0);
                if (!enabled() || !instant.owns(view)) return chain.proceed();
                android.graphics.Rect bounds = new android.graphics.Rect((android.graphics.Rect) chain.getArg(1));
                safe(() -> cards.beforeBlurBounds(view, bounds, true));
                Object[] args = {view, bounds, chain.getArg(2), chain.getArg(3), chain.getArg(4)};
                Object result = chain.proceed(args);
                safe(() -> cards.align(view, true));
                if (android.util.Log.isLoggable("COSCameraCard", android.util.Log.DEBUG))
                    module.info("CameraCard instantFrame " + cards.describeInstantFrame(view));
                return result;
            }));
            pending.add(module.hook(instant.touchRegion).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                android.graphics.Region original = (android.graphics.Region) chain.proceed();
                if (failed || !enabled() || Looper.myLooper() != Looper.getMainLooper()) return original;
                android.graphics.Region[] result = {original};
                safe(() -> result[0] = instant.inputRegion((ViewGroup) chain.getArg(0), original, true));
                return result[0];
            }));
            if (!cards.legacy) try {
                // Discover the native InstantCardAppearScene by its constructor,
                // not its obfuscated class/method name. Older paths keep their
                // existing layout/blur hooks if this optional contract is absent.
                Class<?> scene = PluginClasses.of(instant.type).unique(
                        "com.oplus.systemui.plugins.seedling.card.animation.", candidate -> {
                            try { candidate.getDeclaredConstructor(instant.type, boolean.class, View.class); return true; }
                            catch (NoSuchMethodException missing) { return false; }
                        });
                var owner = Reflect.uniqueField(scene, field -> Reflect.instance(field) && field.getType() == instant.type);
                Method build = Reflect.uniqueMethod(scene, method -> !java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0 && method.getReturnType().getName()
                        .startsWith("com.oplus.systemui.plugins.seedling.engine.animatorengine."));
                if (!module.deoptimize(build)) throw new IllegalStateException("Instant appear builder could not be deoptimized");
                pending.add(module.hook(build).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (!failed && Looper.myLooper() == Looper.getMainLooper()) safe(() -> {
                        ViewGroup container = (ViewGroup) Reflect.get(owner, chain.getThisObject());
                        instant.beforeAppearScene(container, enabled());
                        trace(cards, "instantBeforeAppear", container);
                    });
                    return chain.proceed();
                }));
                module.info("Native instant appear measurement preparation installed");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                module.info("Native instant appear builder unavailable; retaining existing instant geometry");
            }
            hooks.addAll(pending);
            module.info("Native instant camera card geometry installed");
            return instant;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            for (var hook : pending) hook.unhook();
            module.error("Instant camera card contract unavailable; native instant behavior retained", error);
            return null;
        }
    }
    private boolean enabled() { return !failed && module.config.expandFromCamera(); }
    private void trace(NativeCameraCard contract, String event, Object owner) {
        if (enabled() && android.util.Log.isLoggable("COSCameraCard", android.util.Log.DEBUG))
            module.info("CameraCard " + event + " " + contract.describe(owner));
    }
    private void safe(Runnable action) {
        try { action.run(); }
        catch (RuntimeException | LinkageError error) {
            geometryFailed(error);
        }
    }
    private void geometryFailed(Throwable error) {
            if (!failed) module.error("Camera card geometry failed; restoring native cards", error);
            failed = true;
            for (Installed installed : contracts) try {
                if (installed.instant() != null) installed.instant().restore();
                installed.contract().restore();
            }
            catch (RuntimeException | LinkageError ignored) { }
    }
    private void release(ClassLoader loader) {
        for (Installed installed : new ArrayList<>(contracts)) if (installed.loader() == loader) {
            safe(() -> { if (installed.instant() != null) installed.instant().restore(); installed.contract().restore(); });
            if (installed.glass() != null) installed.glass().close();
            for (var hook : installed.hooks()) hook.unhook();
            contracts.remove(installed);
        }
        attempted.remove(loader);
    }
    void configurationChanged() {
        main.post(() -> { if (!failed) for (Installed installed : contracts)
            safe(() -> {
                if (installed.instant() != null) installed.instant().refresh(enabled());
                installed.contract().refresh(enabled());
            }); });
    }
}
