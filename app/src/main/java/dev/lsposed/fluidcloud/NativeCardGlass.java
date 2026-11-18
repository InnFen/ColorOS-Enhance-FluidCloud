package dev.lsposed.fluidcloud;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** ColorOS 17 templates draw their own glass, independently of the outer card's blur. */
final class NativeCardGlass {
    private static final String TEMPLATE = "com.oplus.systemui.plugins.shared.template.";
    private final HookEntry module;
    private final Supplier<CloudCorners.Style> currentStyle;
    private final Class<?> card, instant;
    private final Set<ClassLoader> attempted = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> outlineHosts = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> sharedPages = Collections.newSetFromMap(new WeakHashMap<>());
    private final Materials materials = new Materials();
    private final ArrayList<XposedInterface.HookHandle> hooks = new ArrayList<>();
    private boolean closed;
    private NativeTemplateStroke sharedStroke;

    NativeCardGlass(HookEntry module, Supplier<CloudCorners.Style> currentStyle, Class<?> card, Class<?> instant) {
        this.module = module;
        this.currentStyle = currentStyle;
        this.card = card;
        this.instant = instant;
    }

    synchronized void install(ClassLoader loader) throws ReflectiveOperationException {
        installSharedTemplate(loader);
        try { installSharedStroke(loader); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            module.error("Native shared-template stroke unavailable", unavailable);
        }
        Class<?> cardItem = Reflect.type(loader, "com.oplus.systemui.plugins.seedling.card.ui.model.o");
        Class<?> instantState = Reflect.type(loader, "com.oplus.systemui.plugins.seedling.card.ui.model.n");
        watchContent(Reflect.method(card, "h", void.class, cardItem),
                Reflect.method(card, "getContentView", View.class));
        watchContent(Reflect.method(instant, "k", void.class, instantState, boolean.class, boolean.class),
                Reflect.method(instant, "getInstantView", View.class));
    }

    private void installSharedTemplate(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> page = Reflect.type(loader, TEMPLATE + "page.view.PageBackgroundView");
        Class<?> media = Reflect.type(loader, TEMPLATE + "component.media.view.MediaBackGroundView");
        Class<?> light = Reflect.type(loader, TEMPLATE + "component.light.f");
        Class<?> shader = Reflect.type(loader, TEMPLATE + "component.light.g");
        var engine = Reflect.field(light, "e", shader);
        var mode = Reflect.field(shader, "b", int.class);
        var uniforms = Reflect.field(shader, "d", float[].class);
        var modeDirty = Reflect.field(shader, "c", boolean.class);
        var uniformsDirty = Reflect.field(shader, "e", boolean.class);
        installMaterial(light, Reflect.signature(light, void.class, Integer.class, Float.class, Float.class),
                host -> page.isInstance(host) || media.isInstance(host), 1, new Surface() {
                    public Shape read(Drawable drawable) {
                        Object nativeShader = Reflect.get(engine, drawable);
                        return readUniforms((int) Reflect.get(mode, nativeShader),
                                (float[]) Reflect.get(uniforms, nativeShader));
                    }
                    public void write(Drawable drawable, Shape shape) throws IllegalAccessException {
                        Object nativeShader = Reflect.get(engine, drawable);
                        float[] values = (float[]) Reflect.get(uniforms, nativeShader);
                        values[6] = shape.first();
                        values[7] = shape.second();
                        values[8] = shape.third();
                        values[9] = shape.fourth();
                        values[10] = shape.weight();
                        mode.setInt(nativeShader, shape.mode());
                        modeDirty.setBoolean(nativeShader, true);
                        uniformsDirty.setBoolean(nativeShader, true);
                    }
                });
        // The animated light draws through the media view's separately cached outline.
        Class<?> provider = PluginClasses.outline(card);
        Class<?> tuple = Reflect.type(loader, "kotlin.Triple");
        var mask = Reflect.field(provider, "a", android.graphics.Rect.class);
        var profile = tuple.getDeclaredConstructor(Object.class, Object.class, Object.class);
        var constructor = provider.getDeclaredConstructor(android.graphics.Rect.class, tuple);
        Method outline = Reflect.method(provider, "getOutline", void.class, View.class, Outline.class);
        var original = module.getInvoker(outline).setType(XposedInterface.Invoker.Type.ORIGIN);
        hooks.add(module.hook(outline).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            CloudCorners.Style style = currentStyle.get();
            View view = (View) chain.getArg(0);
            if (!style.enabled() || !(media.isInstance(view) || sharedPages.contains(view)) || !inExpandedCard(view))
                return chain.proceed();
            float radius = style.dp() * view.getResources().getDisplayMetrics().density;
            Object corners = profile.newInstance(style.continuous() ? 2 : 0, radius,
                    style.continuous() ? 3f : 0f);
            return original.invoke(constructor.newInstance(Reflect.get(mask, chain.getThisObject()), corners),
                    view, chain.getArg(1));
        }));
        module.deoptimize(Reflect.method(page, "b", void.class));
        module.deoptimize(Reflect.method(Reflect.type(loader, TEMPLATE + "page.view.a"), "draw", void.class, Canvas.class));
        module.info("Native shared-template card glass corners installed");
    }

    private void installSharedStroke(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> updates = Reflect.type(loader, TEMPLATE + "page.entity.s");
        Class<?> page = Reflect.type(loader, TEMPLATE + "page.entity.t");
        Class<?> continuation = Reflect.type(loader, "kotlin.coroutines.Continuation");
        var owner = Reflect.field(updates, "c", page);
        var root = Reflect.field(page, "k", ViewGroup.class);
        Method update = Reflect.method(updates, "a", Object.class, Object.class, continuation);
        sharedStroke = new NativeTemplateStroke(loader);
        hooks.add(module.hook(update).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object result = chain.proceed();
            View view = (View) Reflect.get(root, Reflect.get(owner, chain.getThisObject()));
            sharedPages.add(view);
            refreshSharedStroke(view);
            return result;
        }));
    }

    private void refreshSharedStroke(View view) throws IllegalAccessException {
        if (!inExpandedCard(view)) return;
        Drawable foreground = view.getForeground();
        if (foreground instanceof CardHeaderDrawable wrapper) foreground = wrapper.nativeDrawable;
        if (foreground instanceof GradientDrawable gradient)
            sharedStroke.apply(gradient, currentStyle.get(), view.getResources().getDisplayMetrics().density);
        view.invalidateOutline();
    }

    private static Shape readUniforms(int mode, float[] values) {
        // Native shader u_commonArray keeps its four radii and smoothness after the size and offset.
        return new Shape(mode, values[10], values[6], values[7], values[8], values[9]);
    }

    private void watchContent(Method bind, Method getter) {
        hooks.add(module.hook(bind).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object result = chain.proceed();
            if (Reflect.call(getter, chain.getThisObject()) instanceof View view) captureTemplate(view);
            return result;
        }));
    }

    private void captureTemplate(View view) throws IllegalAccessException {
        // SystemUI can use a Seedling loader distinct from the plugin manager exposed to clients.
        if (sharedPages.contains(view)) refreshSharedStroke(view);
        String name = view.getClass().getName();
        if (name.startsWith("v6.") || name.startsWith("com.oplus.ule.lite.")) {
            installSdk(view.getClass().getClassLoader());
            return;
        }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) captureTemplate(group.getChildAt(i));
    }

    private synchronized void installSdk(ClassLoader sdk) {
        if (closed || !attempted.add(sdk)) return;
        installSdkPart("glass", () -> installSdkGlass(sdk));
        SdkChrome chrome;
        try { chrome = SdkChrome.resolve(sdk); }
        catch (ReflectiveOperationException | LinkageError unavailable) {
            module.error("Seedling stroke and outline interfaces unavailable", unavailable);
            return;
        }
        installSdkPart("stroke", () -> installSdkStroke(chrome));
        installSdkPart("outline", () -> installOutline(chrome));
        installSdkPart("COE", () -> installCoe(sdk, chrome));
    }

    private interface Install { void run() throws ReflectiveOperationException; }

    private void installSdkPart(String part, Install install) {
        int start = hooks.size();
        try {
            install.run();
            module.info("Native Seedling card " + part + " corners installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            while (hooks.size() > start) hooks.removeLast().unhook();
            module.error("Seedling card " + part + " corner interface unavailable", unavailable);
        }
    }

    private record SdkChrome(Class<?> chrome, Class<?> provider, Class<?> stroke, String strokeUpdate, String coeUpdate) {
        static SdkChrome resolve(ClassLoader sdk) throws ReflectiveOperationException {
            try { return resolve(sdk, "g7.i2", "g7.r4", "e7.gk", "i1", "f1"); }
            catch (ReflectiveOperationException | LinkageError differentSdk) {
                try{return resolve(sdk, "g7.f2", "g7.i4", "e7.sj", "h1", "e1");}
                catch(ReflectiveOperationException|LinkageError changed){return discover(sdk);}
            }
        }

        private static SdkChrome discover(ClassLoader sdk)throws ReflectiveOperationException{
            var index=PluginClasses.of(Reflect.type(sdk,"t7.f0"));
            Class<?> chrome=index.unique("g7.",type->PluginClasses.has(type,void.class,FrameLayout.class,View.class));
            Class<?> provider=index.unique("g7.",type->{
                if(!android.view.ViewOutlineProvider.class.isAssignableFrom(type))return false;
                try{type.getDeclaredConstructor(float.class,int.class,Float.class);return true;}
                catch(NoSuchMethodException missing){return false;}
            });
            Method stroke=Reflect.uniqueMethod(chrome,method->!java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    &&method.getParameterCount()==0
                    &&Drawable.class.isAssignableFrom(method.getReturnType())
                    &&PluginClasses.has(method.getReturnType(),void.class,float.class,float.class,int.class));
            return new SdkChrome(chrome,provider,stroke.getReturnType(),null,null);
        }

        private static SdkChrome resolve(ClassLoader sdk, String chrome, String provider, String stroke,
                                         String strokeUpdate, String coeUpdate) throws ReflectiveOperationException {
            Class<?> chromeType = Reflect.type(sdk, chrome);
            Class<?> providerType = Reflect.type(sdk, provider);
            Class<?> strokeType = Reflect.type(sdk, stroke);
            if (!android.view.ViewOutlineProvider.class.isAssignableFrom(providerType)
                    || !Drawable.class.isAssignableFrom(strokeType)) throw new NoSuchMethodException(chrome);
            Reflect.method(chromeType, "g0", void.class, FrameLayout.class, View.class);
            return new SdkChrome(chromeType, providerType, strokeType, strokeUpdate, coeUpdate);
        }
    }

    private void installSdkGlass(ClassLoader sdk) throws ReflectiveOperationException {
        Class<?> glass = Reflect.type(sdk, "t7.f0");
        Class<?> shader = Reflect.type(sdk, "t7.g0");
        var engine = Reflect.method(glass, "b", shader);
        var mode = Reflect.field(shader, "c", int.class);
        var uniforms = Reflect.field(shader, "e", float[].class);
        var setMode = Reflect.method(shader, "x", void.class, int.class);
        var setRadii = Reflect.method(shader, "y", void.class, float.class, float.class, float.class, float.class);
        var setWeight = Reflect.method(shader, "V", void.class, float.class);
        installMaterial(glass, Reflect.method(glass, "f", void.class, int.class, Float.class, Float.class),
                host -> "cardBackground".equals(host.getTag()), 1, new Surface() {
                    public Shape read(Drawable drawable) {
                        Object nativeShader = Reflect.call(engine, drawable);
                        return readUniforms((int) Reflect.get(mode, nativeShader),
                                (float[]) Reflect.get(uniforms, nativeShader));
                    }
                    public void write(Drawable drawable, Shape shape) {
                        Object nativeShader = Reflect.call(engine, drawable);
                        Reflect.call(setMode, nativeShader, shape.mode());
                        Reflect.call(setRadii, nativeShader, shape.first(), shape.second(), shape.third(), shape.fourth());
                        Reflect.call(setWeight, nativeShader, shape.weight());
                    }
                });
        module.deoptimize(Reflect.method(Reflect.type(sdk, "t7.e0"), "c0", void.class));
    }

    private void installSdkStroke(SdkChrome chrome) throws ReflectiveOperationException {
        Class<?> stroke = chrome.stroke();
        var radius = Reflect.field(stroke, "a", float.class);
        var weight = Reflect.field(stroke, "b", float.class);
        var type = Reflect.field(stroke, "c", Integer.class);
        installMaterial(stroke, Reflect.method(stroke, "e", void.class, float.class, float.class, int.class),
                host -> "cardStroke".equals(host.getTag()), 0, new Surface() {
                    public Shape read(Drawable drawable) {
                        Integer nativeType = (Integer) Reflect.get(type, drawable);
                        return nativeType == null ? null : Shape.uniform(nativeType, (float) Reflect.get(weight, drawable),
                                (float) Reflect.get(radius, drawable));
                    }
                    public void write(Drawable drawable, Shape shape) throws IllegalAccessException {
                        type.set(drawable, shape.mode());
                        weight.setFloat(drawable, shape.weight());
                        radius.setFloat(drawable, shape.first());
                    }
                });
        if(chrome.strokeUpdate()!=null)module.deoptimize(Reflect.method(chrome.chrome(),chrome.strokeUpdate(),void.class,int.class));
        else for(Method caller:chrome.chrome().getDeclaredMethods())
            if(java.lang.reflect.Modifier.isPrivate(caller.getModifiers())&&caller.getReturnType()==void.class
                    &&java.util.Arrays.equals(caller.getParameterTypes(),new Class<?>[]{int.class}))module.deoptimize(caller);
    }

    private void installMaterial(Class<?> type, Method configure, Predicate<View> isMaterialHost, int roundMode, Surface surface)
            throws ReflectiveOperationException {
        Method draw = Reflect.method(type, "draw", void.class, Canvas.class);
        hooks.add(module.hook(configure).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            materials.restore((Drawable) chain.getThisObject());
            return chain.proceed();
        }));
        hooks.add(module.hook(draw).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Drawable drawable = (Drawable) chain.getThisObject();
            CloudCorners.Style style = currentStyle.get();
            View host = style.enabled() ? Materials.drawableHost(drawable) : null;
            if (host != null && isMaterialHost.test(host) && inExpandedCard(host) && !drawable.getBounds().isEmpty()) {
                float density = host.getResources().getDisplayMetrics().density;
                var bounds = drawable.getBounds();
                float radius = style.radius(density, bounds.width(), bounds.height());
                materials.apply(drawable, surface, Shape.uniform(style.continuous() ? 2 : roundMode,
                        style.continuous() ? 3f : 0f, radius));
            } else materials.restore(drawable);
            return chain.proceed();
        }));
    }

    private void installOutline(SdkChrome chrome) throws ReflectiveOperationException {
        var outlineHost = Reflect.field(chrome.chrome(), "b", View.class);
        var bind = Reflect.signature(chrome.chrome(), void.class, FrameLayout.class, View.class);
        hooks.add(module.hook(bind).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            outlineHosts.add((View) Reflect.get(outlineHost, chain.getThisObject()));
            return chain.proceed();
        }));
        Class<?> provider = chrome.provider();
        var constructor = provider.getDeclaredConstructor(float.class, int.class, Float.class);
        Method method = Reflect.method(provider, "getOutline", void.class, View.class, Outline.class);
        var original = module.getInvoker(method).setType(XposedInterface.Invoker.Type.ORIGIN);
        hooks.add(module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(
                new XposedInterface.Hooker() {
                    private CloudCorners.Style cached;
                    private Object replacement;
                    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        CloudCorners.Style style = currentStyle.get();
                        View view = (View) chain.getArg(0);
                        if (!style.enabled() || view == null || !inExpandedCard(view)
                                || !isChromeOutline(view))
                            return chain.proceed();
                        if (!style.equals(cached)) {
                            replacement = constructor.newInstance((float) style.dp(), style.continuous() ? 2 : 0,
                                    style.continuous() ? 3f : 0f);
                            cached = style;
                        }
                        return original.invoke(replacement, view, chain.getArg(1));
                    }
                }));
    }

    private boolean isChromeOutline(View view) {
        if ("cardBackground".equals(view.getTag()) || outlineHosts.contains(view)) return true;
        // The first template can already be bound when its SDK hooks are installed.
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++)
                if ("cardBackground".equals(group.getChildAt(i).getTag())) return true;
        return false;
    }

    private void installCoe(ClassLoader sdk, SdkChrome chrome) throws ReflectiveOperationException {
        Class<?> coe = Reflect.type(sdk, "e7.c3");
        var host = Reflect.field(coe, "B", View.class);
        Method configure = Reflect.method(coe, "J0", void.class, float.class, float.class, float.class);
        if(chrome.coeUpdate()!=null)module.deoptimize(Reflect.method(chrome.chrome(),chrome.coeUpdate(),void.class));
        else for(Method caller:chrome.chrome().getDeclaredMethods())
            if(java.lang.reflect.Modifier.isPrivate(caller.getModifiers())&&caller.getReturnType()==void.class
                    &&caller.getParameterCount()==0)module.deoptimize(caller);
        hooks.add(module.hook(configure).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            CloudCorners.Style style = currentStyle.get();
            if (style.enabled() && Reflect.get(host, chain.getThisObject()) instanceof View view
                    && "cardBackground".equals(view.getTag()) && inExpandedCard(view)) {
                float radius = style.dp() * view.getResources().getDisplayMetrics().density;
                return chain.proceed(new Object[]{style.continuous() ? 2f : 0f, radius, style.continuous() ? 3f : 0f});
            }
            return chain.proceed();
        }));
    }

    private boolean inExpandedCard(View view) {
        for (View node = view; node != null; node = node.getParent() instanceof View parent ? parent : null)
            if (card.isInstance(node) || instant.isInstance(node)) return true;
        return false;
    }

    interface Surface {
        Shape read(Drawable drawable);
        void write(Drawable drawable, Shape shape) throws IllegalAccessException;
    }
    record Shape(int mode, float weight, float first, float second, float third, float fourth) {
        static Shape uniform(int mode, float weight, float radius) {
            return new Shape(mode, weight, radius, radius, radius, radius);
        }
    }
    static final class Materials {
        private record Owned(Surface surface, Shape stock, Shape applied) { }
        private final WeakHashMap<Drawable, Owned> shapes = new WeakHashMap<>();

        static View drawableHost(Drawable drawable) {
            Drawable.Callback callback = drawable.getCallback();
            // PageLightDrawable and the camera-header wrapper both act as Drawable callbacks.
            for (int depth = 0; depth < 8 && callback instanceof Drawable wrapper; depth++)
                callback = wrapper.getCallback();
            return callback instanceof View view ? view : null;
        }

        void apply(Drawable drawable, Surface surface, Shape target) throws IllegalAccessException {
            Owned previous = shapes.get(drawable);
            Shape current = surface.read(drawable);
            if (current == null || current.equals(target)) return;
            Shape stock = previous != null && previous.applied().equals(current) ? previous.stock() : current;
            surface.write(drawable, target);
            shapes.put(drawable, new Owned(surface, stock, target));
        }

        void restore(Drawable drawable) throws IllegalAccessException {
            Owned owned = shapes.remove(drawable);
            if (owned != null && owned.applied().equals(owned.surface().read(drawable)))
                owned.surface().write(drawable, owned.stock());
        }
    }

    synchronized void close() {
        closed = true;
        for (var hook : hooks) hook.unhook();
        hooks.clear();
        for (Drawable drawable : new ArrayList<>(materials.shapes.keySet())) {
            if (drawable == null) continue;
            try { materials.restore(drawable); }
            catch (IllegalAccessException | RuntimeException error) {
                module.error("Could not restore native Seedling material", error);
            }
        }
        outlineHosts.clear();
        sharedPages.clear();
        if (sharedStroke != null) {
            try { sharedStroke.restore(); }
            catch (IllegalAccessException | RuntimeException failure) {
                module.error("Could not restore native shared-template stroke", failure);
            }
        }
    }
}
