package dev.lsposed.fluidcloud;

import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import io.github.libxposed.api.XposedInterface;

/** Read-only provenance adapter. Native models, Flow emissions and animators remain unchanged. */
final class NativeVisibilityProbe {
    record Snapshot(boolean visible, boolean protectedState, CloudScene scene) {
        static final Snapshot UNKNOWN = new Snapshot(false, true, null);
    }
    interface Lookup { Class<?> load(String name) throws ClassNotFoundException; }
    private record Inputs(int first, int second, PipelineVisibilityPolicy.Base base,
                          PipelineVisibilityPolicy.Capsule capsule) { }
    private static final class Observed { int visibility = -1; CloudScene scene; Snapshot snapshot = Snapshot.UNKNOWN; }
    private static final class Update {
        final Object fragment;
        Object extension, input;
        final IdentityHashMap<Object, Boolean> hidden = new IdentityHashMap<>();
        Update(Object fragment) { this.fragment = fragment; }
    }
    private final boolean pipeline;
    private final Consumer<View> changed;
    private final Map<Integer, Inputs> inputs = new HashMap<>();
    private final Map<View, Observed> hosts = new WeakHashMap<>();
    private final Map<Object, Map<Integer, int[]>> flags16 = new WeakHashMap<>();
    private final ThreadLocal<Update> update = new ThreadLocal<>();
    private final Method disable, base, seed, emission, hidden;
    private final Field baseOwner, seedOwner, homeOwner, displayId, hostField, hostDisplay, fragmentField;
    private final Field flagClock, flagNotifications, flagSystem;
    private final Method visibility, showClock, showNotifications, showSystem, primary;
    Consumer<Throwable> failure = error -> { };
    private volatile boolean failed;
    interface NativeCall { Object run() throws Throwable; }
    private void observe(Runnable action) {
        if(failed)return;
        try{action.run();}catch(RuntimeException|LinkageError error){failed=true;try{failure.accept(error);}catch(RuntimeException|LinkageError ignored){}}
    }

    NativeVisibilityProbe(ClassLoader loader, boolean pipeline, Consumer<View> changed) throws ReflectiveOperationException {
        this(name -> Reflect.type(loader, name), pipeline, changed);
    }
    NativeVisibilityProbe(Lookup lookup, boolean pipeline, Consumer<View> changed) throws ReflectiveOperationException {
        this.pipeline = pipeline; this.changed = changed;
        if (pipeline) {
            String home = "com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModelImpl";
            String oplus = "com.oplus.systemui.statusbar.pipeline.shared.ui.viewmodel.OplusHomeStatusBarViewModelImpl";
            Class<?> a = lookup.load(home), b = lookup.load(oplus);
            Class<?> first = lookup.load(home + "$isNotificationIconContainerVisible$1");
            Class<?> second = lookup.load(oplus + "$isSeedlingContainerVisible$1");
            Class<?> collector = lookup.load("com.oplus.systemui.statusbar.pipeline.shared.ui.binder.OplusHomeStatusBarViewBinderImpl$bind$1$1$3$1");
            Class<?> model = lookup.load("com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel");
            Class<?> flags = lookup.load("com.android.systemui.statusbar.pipeline.shared.domain.model.StatusBarDisableFlagsVisibilityModel");
            base = Reflect.method(first,"invoke",Object.class,Object.class,Object.class,Object.class,Object.class);
            seed = Reflect.method(second,"invoke",Object.class,Object.class,Object.class,Object.class,Object.class,Object.class,Object.class);
            disable = Reflect.method(lookup.load("com.android.systemui.statusbar.CommandQueue"),"setDisabled",void.class,int.class,int.class,int.class);
            emission = Reflect.method(collector,"emit",Object.class,Object.class,lookup.load("kotlin.coroutines.Continuation"));
            baseOwner = Reflect.field(first,"this$0",a); seedOwner = Reflect.field(second,"this$0",b);
            homeOwner = Reflect.field(b,"homeStatusBarViewModel",Object.class); displayId = Reflect.field(a,"thisDisplayId",int.class);
            hostField = Reflect.field(collector,"$seedlingContainer",View.class); hostDisplay = Reflect.field(collector,"$displayId",int.class);
            flagClock = Reflect.field(flags,"isClockAllowed",boolean.class);
            flagNotifications = Reflect.field(flags,"areNotificationIconsAllowed",boolean.class);
            flagSystem = Reflect.field(flags,"isSystemInfoAllowed",boolean.class);
            visibility = Reflect.method(model,"getVisibility",int.class);
            hidden = showClock = showNotifications = showSystem = primary = null; fragmentField = null;
        } else {
            Class<?> fragment = lookup.load("com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment");
            Class<?> extension = lookup.load("com.oplus.systemui.statusbar.phone.fragment.CollapsedStatusBarFragmentExImpl");
            Class<?> model = lookup.load("com.android.systemui.statusbar.phone.fragment.StatusBarVisibilityModel");
            Class<?> extra = lookup.load("com.oplus.systemui.statusbar.phone.fragment.StatusBarVisibilityModelEx");
            Class<?> companion = lookup.load("com.oplus.systemui.statusbar.phone.fragment.StatusBarVisibilityModelEx$Companion");
            disable = Reflect.method(fragment,"disable",void.class,int.class,int.class,int.class,boolean.class);
            base = Reflect.method(fragment,"updateStatusBarVisibilities",void.class,boolean.class);
            seed = Reflect.method(extension,"updateStatusBarVisibilities",boolean.class,model,boolean.class);
            emission = Reflect.method(companion,"createModelFromFlags",extra,model,boolean.class,boolean.class,boolean.class);
            hidden = Reflect.method(model,"createHiddenModel",model);
            hostField = Reflect.field(extension,"seedlingContainer",View.class);
            fragmentField = Reflect.field(extension,"statusBarFragment",fragment);
            visibility = Reflect.method(extra,"getShowSeedlingContainer",boolean.class);
            showClock = Reflect.method(model,"getShowClock",boolean.class);
            showNotifications = Reflect.method(model,"getShowNotificationIcons",boolean.class);
            showSystem = Reflect.method(model,"getShowSystemInfo",boolean.class);
            primary = Reflect.method(model,"getShowPrimaryOngoingActivityChip",boolean.class);
            baseOwner = seedOwner = homeOwner = displayId = hostDisplay = null;
            flagClock = flagNotifications = flagSystem = null;
        }
    }
    void install(HookEntry module, java.util.List<XposedInterface.HookHandle> handles) {
        if (!module.deoptimize(pipeline ? emission : base) || !pipeline && !module.deoptimize(seed))
            throw new IllegalStateException("Native visibility probe caller could not be deoptimized");
        handles.add(module.hook(disable).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            observe(()->{
                if (pipeline) recordDisable((int)chain.getArg(0),(int)chain.getArg(1),(int)chain.getArg(2));
                else recordLegacyDisable(chain.getThisObject(),(int)chain.getArg(0),(int)chain.getArg(1),(int)chain.getArg(2));
                changed.accept(null);
            });return chain.proceed();
        }));
        if (pipeline) {
            for (Method method : new Method[]{base,seed}) handles.add(module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        observe(()->{if (method == base) recordBase(chain.getThisObject(),chain.getArgs().toArray());
                            else recordSeed(chain.getThisObject(),chain.getArgs().toArray());changed.accept(null);});return result;
                    }));
            handles.add(module.hook(emission).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                observe(()->observeEmission(chain.getThisObject(),chain.getArg(0)));
                return chain.proceed(); // exactly the native model, exactly once
            }));
        } else {
            handles.add(module.hook(base).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                return withLegacyBase(chain.getThisObject(),chain::proceed);
            }));
            handles.add(module.hook(seed).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                return withLegacyExtension(chain.getThisObject(),chain.getArg(0),chain::proceed);
            }));
            handles.add(module.hook(hidden).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result=chain.proceed();recordLegacyForced(result);return result;
            }));
            handles.add(module.hook(emission).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result=chain.proceed();observe(()->observeLegacy(chain.getArg(0),(boolean)chain.getArg(2),(boolean)chain.getArg(3),result));return result;
            }));
        }
    }
    synchronized void recordLegacyDisable(Object fragment,int display,int first,int second){flags16.computeIfAbsent(fragment,ignored->new HashMap<>()).put(display,new int[]{first,second});}
    Object withLegacyBase(Object fragment,NativeCall original)throws Throwable{
        Update previous=update.get();update.set(new Update(fragment));
        try{return original.run();}finally{if(previous==null)update.remove();else update.set(previous);}
    }
    Object withLegacyExtension(Object extension,Object input,NativeCall original)throws Throwable{
        Update current=update.get();if(current==null || Reflect.get(fragmentField,extension)!=current.fragment)return original.run();
        Object previousExtension=current.extension,previousInput=current.input;current.extension=extension;current.input=input;
        try{return original.run();}finally{current.extension=previousExtension;current.input=previousInput;}
    }
    void recordLegacyForced(Object model){Update current=update.get();if(current!=null)current.hidden.put(model,true);}
    private Inputs at(int display) { return inputs.getOrDefault(display,new Inputs(-1,-1,null,null)); }
    synchronized void recordDisable(int display,int first,int second) {
        Inputs old=at(display);inputs.put(display,new Inputs(first,second,old.base(),old.capsule()));
    }
    private PipelineVisibilityPolicy.Flags flags(Object value) {
        return new PipelineVisibilityPolicy.Flags((boolean)Reflect.get(flagClock,value),
                (boolean)Reflect.get(flagNotifications,value),(boolean)Reflect.get(flagSystem,value));
    }
    synchronized void recordBase(Object receiver,Object[] args) {
        int display=(int)Reflect.get(displayId,Reflect.get(baseOwner,receiver));Inputs old=at(display);
        inputs.put(display,new Inputs(old.first(),old.second(),new PipelineVisibilityPolicy.Base(
                (boolean)args[0],(boolean)args[1],flags(args[2])),old.capsule()));
    }
    synchronized void recordSeed(Object receiver,Object[] args) {
        int display=(int)Reflect.get(displayId,Reflect.get(homeOwner,Reflect.get(seedOwner,receiver)));Inputs old=at(display);
        inputs.put(display,new Inputs(old.first(),old.second(),old.base(),new PipelineVisibilityPolicy.Capsule(
                (boolean)args[1],(boolean)args[2],(boolean)args[4],flags(args[3]))));
    }
    synchronized void observeEmission(Object receiver,Object model) {
        View host=(View)Reflect.get(hostField,receiver);
        if(host==null || host.getDisplay()==null || host.getDisplay().getDisplayId()!=(int)Reflect.get(hostDisplay,receiver))return;
        Observed state=hosts.computeIfAbsent(host,ignored->new Observed());
        classify(state,(int)Reflect.get(hostDisplay,receiver),(int)Reflect.call(visibility,model));
        changed.accept(host);
    }
    private synchronized void classify(Observed state,int display,int visible) {
        Inputs input=at(display);
        var decision=PipelineVisibilityPolicy.classifyTransition(input.first(),input.second(),input.base(),input.capsule(),
                visible,state.visibility,state.scene);
        boolean carrying=state.scene!=null && PipelineVisibilityPolicy.waitingForExit(input.first(),input.second(),input.base(),input.capsule(),visible);
        boolean protectedState=decision.protectedInput() && !carrying;
        if(!protectedState) {
            if(decision.scene()!=null)state.scene=decision.scene();
            else if(PipelineVisibilityPolicy.recovered(input.first(),input.second(),input.base(),input.capsule(),visible))state.scene=null;
        }
        state.visibility=visible;state.snapshot=new Snapshot(visible==View.VISIBLE,protectedState,state.scene);
    }
    synchronized void observeLegacy(Object input,boolean mini,boolean privacy,Object model) {
        Update current=update.get();if(current==null || current.extension==null || input!=current.input)return;
        View host=(View)Reflect.get(hostField,current.extension);if(host==null || host.getDisplay()==null)return;
        Map<Integer,int[]> displays=flags16.get(current.fragment);int[] flags=displays==null?null:displays.get(host.getDisplay().getDisplayId());
        boolean protectedState=flags==null || mini || privacy || current.hidden.containsKey(input) || (boolean)Reflect.call(primary,input);
        boolean shown=(boolean)Reflect.call(visibility,model);
        Observed state=hosts.computeIfAbsent(host,ignored->new Observed());
        if(!protectedState) {
            boolean hidden=!(boolean)Reflect.call(showClock,input) && !(boolean)Reflect.call(showNotifications,input) && !(boolean)Reflect.call(showSystem,input);
            if(!shown && hidden && (flags[0]!=0 || flags[1]!=0))state.scene=flags[0]==0x00920000 && flags[1]==0?CloudScene.RECENTS:CloudScene.OTHER;
            else if(shown && flags[0]==0 && flags[1]==0)state.scene=null;
        }
        state.snapshot=new Snapshot(shown,protectedState,state.scene);changed.accept(host);
    }
    synchronized Snapshot snapshot(View host) {
        Observed value=hosts.get(host);if(value==null)return Snapshot.UNKNOWN;
        if(pipeline && host.getDisplay()!=null)classify(value,host.getDisplay().getDisplayId(),value.visibility);
        return value.snapshot;
    }
    synchronized void release(View host) { hosts.remove(host); }
}
