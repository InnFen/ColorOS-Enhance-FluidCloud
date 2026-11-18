package dev.lsposed.fluidcloud;

import android.os.Handler;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.Choreographer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;

/** Keeps native card business/animation, adding only the QS launch layer and completion. */
final class NativeShadeLaunch {
    private static final String PREFIX="com.oplus.systemui.plugins.seedling.common.transition.";
    interface Lookup{Class<?> load(String name)throws ClassNotFoundException;}
    interface Layer{
        Object anchor(View shade);
        void raise(SurfaceControl application,Object anchor);
        default void beginDismiss(SurfaceControl application,Object anchor){}
        default void restore(Object anchor){}
        default void afterDismiss(Object anchor,Runnable committed){committed.run();}
    }
    final Method ready,finished,windowFinished;
    private final Field controller,target,callback;
    private final Method sourceRoot,leash;
    private final Handler main;
    private final Layer layer;
    private final Runnable dismiss;
    private final Consumer<Throwable> failed;
    private Pending pending,replaying;
    Consumer<String> trace=message->{};
    private final Runnable timeout=this::timeout;
    private static final class Pending{
        final View root;final Object anchor;final BooleanSupplier valid;
        Object callback,controller;
        SurfaceControl application;
        boolean completing,released;
        final List<Release> releases=new ArrayList<>();
        final Set<Object> queued=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Object> delivered=Collections.newSetFromMap(new IdentityHashMap<>());
        Pending(View root,Object anchor,BooleanSupplier valid){this.root=root;this.anchor=anchor;this.valid=valid;}
    }
    private record Release(Object key,Runnable action){}
    NativeShadeLaunch(ClassLoader plugin,ClassLoader system,Handler main,Runnable dismiss,Consumer<Throwable> failed)throws ReflectiveOperationException{
        this(name->Reflect.type(plugin,name),main,new SurfaceLayer(system),dismiss,failed);
    }
    NativeShadeLaunch(Lookup lookup,Handler main,Layer layer,Runnable dismiss,Consumer<Throwable> failed)throws ReflectiveOperationException{
        Class<?> animation=lookup.load("com.oplus.wrapper.view.RemoteAnimationTarget");
        Class<?> data=lookup.load(PREFIX+"w"),owner=lookup.load(PREFIX+"r"),source=lookup.load(PREFIX+"e"),end=lookup.load(PREFIX+"c");
        if(!PluginClasses.has(owner,void.class,SurfaceControl.class,boolean.class)){
            Class<?> anchor=lookup.load(NativeCameraCard.VIEWS+"CardView");
            owner=PluginClasses.of(anchor).unique(PREFIX,type->PluginClasses.has(type,void.class,SurfaceControl.class,boolean.class)
                    && java.util.Arrays.stream(type.getDeclaredFields()).anyMatch(field->field.getType()==Handler.class));
            data=Reflect.uniqueField(owner,field->Reflect.instance(field)
                    && field.getType().getName().startsWith(PREFIX)
                    && java.util.Arrays.stream(field.getType().getDeclaredFields()).anyMatch(member->member.getType()==animation)).getType();
        }
        ready=readyMethod(owner,data);finished=Reflect.method(end,"b",void.class);
        windowFinished=Reflect.signature(owner,void.class,SurfaceControl.class,boolean.class);
        controller=Reflect.field(data,"a",source);target=Reflect.field(data,"b",animation);callback=Reflect.field(data,"g",end);
        sourceRoot=Reflect.method(source,"e",ViewGroup.class);leash=Reflect.method(animation,"getLeash",SurfaceControl.class);
        this.main=main;this.layer=layer;this.dismiss=dismiss;this.failed=failed;
    }
    private static Method readyMethod(Class<?> owner,Class<?> data)throws ReflectiveOperationException{
        try{return Reflect.signature(owner,void.class,data);}
        catch(NoSuchMethodException ambiguous){
            Method found=null;
            for(Method method:owner.getDeclaredMethods()){
                if(java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.getReturnType()!=void.class
                        || !java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{data}))continue;
                // The ready callback cancels the launch timeout; the same-signature
                // reset method only clears data. Resolve by that dependency once.
                boolean timeoutOwner=DexFieldUse.fields(method).stream().anyMatch(field->field.getDeclaringClass()==owner
                        && field.getType()==Handler.class && Reflect.instance(field));
                if(timeoutOwner){if(found!=null)throw ambiguous;found=method;}
            }
            if(found==null)throw ambiguous;
            found.setAccessible(true);return found;
        }
    }
    boolean busy(){return pending!=null;}
    boolean begin(View root,View shade,BooleanSupplier valid){
        if(pending!=null)return false;
        Object anchor=shade==null?null:layer.anchor(shade);
        if(root==null || anchor==null || !valid.getAsBoolean())return false;
        pending=new Pending(root,anchor,valid);main.postDelayed(timeout,1500);trace("armed; native intent proceeds without waiting for panel collapse");return true;
    }
    Object ready(Object data){
        Pending current=pending;if(current==null || current.completing || current.callback!=null || data==null)return null;
        Object source=Reflect.get(controller,data);
        if(Reflect.call(sourceRoot,source)!=current.root)return null;
        if(!current.valid.getAsBoolean()){cancel();return null;}
        try{
            SurfaceControl application=(SurfaceControl)Reflect.call(leash,Reflect.get(target,data));
            // Native QS uses this relative layer before the application becomes visible.
            layer.raise(application,current.anchor);current.application=application;current.controller=source;current.callback=Reflect.get(callback,data);
            trace("application layer raised above shade before native animation");
            main.removeCallbacks(timeout);main.postDelayed(timeout,3000);
            return source;
        }catch(RuntimeException|LinkageError error){complete();failed.accept(error);}
        return null;
    }
    boolean finish(Object nativeCallback,Runnable nativeFinished){
        Pending current=pending!=null && pending.callback==nativeCallback?pending:replaying!=null && replaying.callback==nativeCallback?replaying:null;
        if(current==null)return false;
        return deferFinish(current,nativeCallback,nativeFinished);
    }
    boolean finishSurface(SurfaceControl application,Runnable nativeFinished){
        Pending current=pending!=null && pending.application==application?pending:replaying!=null && replaying.application==application?replaying:null;
        if(current==null || application==null)return false;
        trace("intercepting native application leash release");
        return deferFinish(current,application,nativeFinished);
    }
    private boolean deferFinish(Pending current,Object key,Runnable nativeFinished){
        // Window-leash cleanup and the remote animation-finished callback are
        // separate native obligations. Deduplicate each identity, never one by the other.
        if(current.released)return !current.delivered.add(key);
        if(current.queued.add(key))current.releases.add(new Release(key,nativeFinished));
        if(current.completing)return true;
        current.completing=true;main.removeCallbacks(timeout);
        try{
            if(current.valid.getAsBoolean()){
                layer.beginDismiss(current.application,current.anchor);dismiss.run();
            }
            trace("holding native finish until shade dismissal frame commits");
            layer.afterDismiss(current.anchor,()->{
                if(current.released)return;current.released=true;
                trace("shade dismissal committed; releasing native finish");
                Pending previous=replaying;replaying=current;
                try{
                    for(Release release:List.copyOf(current.releases)){
                        if(current.delivered.contains(release.key()))continue;
                        try{release.action().run();}catch(RuntimeException|LinkageError error){failed.accept(error);}
                        finally{current.delivered.add(release.key());}
                    }
                }finally{replaying=previous;if(pending==current)cancel();}
            });
        }catch(RuntimeException|LinkageError error){cancel();failed.accept(error);return false;}
        return true;
    }
    void cancelController(Object source){if(pending!=null && pending.controller==source)complete();}
    void launchFailed(){cancel();}
    void cancel(){
        Pending current=pending;pending=null;main.removeCallbacks(timeout);
        if(current!=null)try{layer.restore(current.anchor);}catch(RuntimeException|LinkageError error){failed.accept(error);}
    }
    private void complete(){
        Pending current=pending;cancel();
        if(current!=null && current.valid.getAsBoolean()){trace("native completion; dismissing panel before animation leash cleanup");dismiss.run();}
    }
    private void trace(String message){if(android.util.Log.isLoggable("COSPresentation",android.util.Log.DEBUG))trace.accept("Native shade launch: "+message);}
    private void timeout(){
        if(pending==null)return;
        try{
            if(!pending.valid.getAsBoolean()){cancel();return;}
            complete();failed.accept(new IllegalStateException("Native card launch callback timed out; panel dismissed without replay"));
        }catch(RuntimeException|LinkageError error){cancel();failed.accept(error);}
    }
    private static final class SurfaceLayer implements Layer{
        private final Method root,surface,relative,absolute,alpha;
        private final Set<SurfaceControl> gated=Collections.newSetFromMap(new IdentityHashMap<>());
        SurfaceLayer(ClassLoader loader)throws ReflectiveOperationException{
            Class<?> type=Reflect.type(loader,"android.view.ViewRootImpl");
            root=Reflect.method(View.class,"getViewRootImpl",type);surface=Reflect.method(type,"getSurfaceControl",SurfaceControl.class);
            relative=Reflect.method(SurfaceControl.Transaction.class,"setRelativeLayer",SurfaceControl.Transaction.class,SurfaceControl.class,SurfaceControl.class,int.class);
            absolute=Reflect.method(SurfaceControl.Transaction.class,"setLayer",SurfaceControl.Transaction.class,SurfaceControl.class,int.class);
            alpha=Reflect.method(SurfaceControl.Transaction.class,"setAlpha",SurfaceControl.Transaction.class,SurfaceControl.class,float.class);
        }
        public Object anchor(View shade){
            if(!shade.isAttachedToWindow())return null;
            Object viewRoot=Reflect.call(root,shade);if(viewRoot==null)return null;
            SurfaceControl control=(SurfaceControl)Reflect.call(surface,viewRoot);return control!=null && control.isValid()?control:null;
        }
        public void raise(SurfaceControl application,Object anchor){
            if(application==null || !application.isValid() || !(anchor instanceof SurfaceControl control) || !control.isValid())throw new IllegalStateException("Launch surfaces unavailable");
            try(var transaction=new SurfaceControl.Transaction()){Reflect.call(relative,transaction,application,control,1);transaction.apply();}
        }
        public void beginDismiss(SurfaceControl application,Object anchor){
            if(application==null || !application.isValid() || !(anchor instanceof SurfaceControl control) || !control.isValid())throw new IllegalStateException("Launch cutover surfaces unavailable");
            gated.add(control);
            // A relative layer inherits the base layer's disappearance. Detach its
            // relative Z and mask the old shade atomically while the full opening
            // app is still in its native animation leash. Never hide both surfaces.
            try(var transaction=new SurfaceControl.Transaction()){
                Reflect.call(absolute,transaction,application,Integer.MAX_VALUE);
                Reflect.call(alpha,transaction,control,0f);transaction.apply();
            }
        }
        public void restore(Object anchor){
            if(!(anchor instanceof SurfaceControl control) || !gated.remove(control) || !control.isValid())return;
            // The native instant collapse has traversed and committed before leash
            // cleanup. Restore the normal QS surface alpha for its next expansion.
            try(var transaction=new SurfaceControl.Transaction()){Reflect.call(alpha,transaction,control,1f);transaction.apply();}
        }
        public void afterDismiss(Object anchor,Runnable committed){
            // Keep the fully drawn opening app in its native animation leash while
            // the instant panel collapse traverses and WindowManager hides its root.
            // The temporary shade gate is restored after cleanup; SDK parents stay intact.
            Handler handler=new Handler(android.os.Looper.getMainLooper());
            java.util.concurrent.atomic.AtomicBoolean delivered=new java.util.concurrent.atomic.AtomicBoolean();
            Runnable release=new Runnable(){public void run(){if(delivered.compareAndSet(false,true)){handler.removeCallbacks(this);committed.run();}}};
            // Screen-off can stop Choreographer and compositor callbacks. Cleanup
            // must still complete once, without retaining WindowManager's leash.
            handler.postDelayed(release,250);
            Choreographer clock=Choreographer.getInstance();
            clock.postFrameCallback(first->clock.postFrameCallback(second->{
                if(delivered.get())return;
                try(var fence=new SurfaceControl.Transaction()){
                    fence.addTransactionCommittedListener(handler::post,release::run);fence.apply();
                }catch(RuntimeException|LinkageError error){release.run();}
            }));
        }
    }
}
