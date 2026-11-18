package dev.lsposed.fluidcloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Independent capsule choices; no scene-unbind or camera-card gate is required. */
final class CloudExtras {
    private final HookEntry module;
    private final Method added,attached,removed;
    private final Field host,fakeHost;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Session> sessions=new ArrayList<>();
    CloudExtras(HookEntry module,ClassLoader loader)throws ReflectiveOperationException{
        this.module=module;
        Class<?> owner=Reflect.type(loader,"com.oplus.systemui.statusbar.seeding.CapsulePluginContainerController");
        Class<?> plugin=Reflect.type(loader,"com.android.systemui.plugins.statusbar.SeedlingPlugin");
        added=Reflect.method(owner,"onPluginAdded",void.class,plugin,Context.class);
        attached=Reflect.method(owner,"onViewAttached",void.class);removed=Reflect.method(owner,"onPluginRemove",void.class,plugin);
        host=Reflect.field(owner,"capsulePluginContainer",ViewGroup.class);
        Field fake;
        try{fake=Reflect.field(owner,"capsuleFakeContainer",ViewGroup.class);}catch(NoSuchFieldException missing){fake=null;}
        fakeHost=fake;
    }
    void install(){
        ArrayList<XposedInterface.HookHandle> hooks=new ArrayList<>();
        try{
            for(Method event:new Method[]{added,attached})hooks.add(module.hook(event)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                        Object result=chain.proceed();capture(chain.getThisObject());return result;
                    }));
            hooks.add(module.hook(removed).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                ViewGroup view=(ViewGroup)Reflect.get(host,chain.getThisObject());
                for(Session session:new ArrayList<>(sessions))if(session.belongsTo(view)){session.close();sessions.remove(session);}
                return chain.proceed();
            }));
        }catch(RuntimeException|LinkageError error){for(var hook:hooks)hook.unhook();throw error;}
    }
    private void capture(Object owner){
        if(Looper.myLooper()!=Looper.getMainLooper())return;
        ViewGroup view=(ViewGroup)Reflect.get(host,owner);
        if(module.mediaWaves!=null)module.mediaWaves.context(view.getContext());
        for(int i=0;i<view.getChildCount();i++){
            View root=view.getChildAt(i);
            if(!root.getClass().getName().equals(NativeCapsuleAppearance.VIEWS+"CapsuleContainerRoot"))continue;
            Session existing=null;
            for(Session session:sessions)if(session.loader==root.getClass().getClassLoader()){existing=session;break;}
            if(existing==null){existing=new Session(root);sessions.add(existing);}
            existing.roots.add(root);
            if(existing.wave!=null)existing.wave.root(root);
            View fake=fakeHost==null?null:(View)Reflect.get(fakeHost,owner);
            if(fake!=null){existing.roots.add(fake);if(existing.wave!=null)existing.wave.root(fake);}
            existing.refresh();
        }
    }
    void configurationChanged(){main.post(()->{for(Session session:sessions)session.refresh();});}
    private final class Session {
        final ClassLoader loader;
        final java.util.Set<View> roots=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
        final ArrayList<XposedInterface.HookHandle> hooks=new ArrayList<>();
        NativeCapsuleAppearance appearance;
        NativeCapsuleSize size;
        NativeCardOrder order;
        NativeCardDim dim;
        NativeCardDismissal dismissal;
        NativeMediaWave wave;
        final View root;
        final android.view.ViewTreeObserver.OnGlobalLayoutListener waveLayout=()->{
            if(wave!=null)wave.layoutChanged();
        };
        boolean closing;
        Session(View root){
            this.root=root;loader=root.getClass().getClassLoader();NativeCapsuleAppearance.Lookup lookup=name->Reflect.type(loader,name);
            if(module.mediaWaves!=null)try{
                wave=module.mediaWaves.addPlugin(loader,root);root.getViewTreeObserver().addOnGlobalLayoutListener(waveLayout);
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){module.error("Native media waveform view unavailable; OEM animation retained",error);}
            int start=hooks.size();
            try{
                appearance=new NativeCapsuleAppearance(lookup);
                if(!module.deoptimize(appearance.stateChanged))throw new IllegalStateException("Capsule state caller deoptimization failed");
                for(Method event:new Method[]{appearance.dark,appearance.inflated})hooks.add(module.hook(event)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                            Object result=chain.proceed();safe(()->{
                                boolean hideStroke=!closing&&module.config.hideCapsuleStroke();
                                if(event==appearance.inflated)appearance.corners((View)chain.getThisObject(),
                                        closing?CloudCorners.Style.OEM:module.config.corners().capsule(),hideStroke);
                                appearance.stroke(chain.getThisObject(),hideStroke);
                            });return result;
                        }));
                hooks.add(module.hook(appearance.colorChanged).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();safe(()->appearance.stroke(appearance.colorOwner(chain.getThisObject()),!closing&&module.config.hideCapsuleStroke()));return result;
                }));
                hooks.add(module.hook(appearance.moons).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    boolean requested=(boolean)chain.getArg(0);
                    boolean actual=appearance.moonRequest((View)chain.getThisObject(),requested,!closing&&module.config.hideCapsuleArrows());
                    return actual==requested?chain.proceed():chain.proceed(new Object[]{actual});
                }));
                hooks.add(module.hook(appearance.stateChanged).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();safe(()->appearance.afterState((View)chain.getThisObject(),!closing&&module.config.hideCapsuleArrows()));return result;
                }));
                module.info("Native capsule border/indicator controls installed");
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){rollback(start);appearance=null;module.error("Capsule appearance controls unavailable; native appearance retained",error);}
            start=hooks.size();
            try{
                size=new NativeCapsuleSize(lookup);
                hooks.add(module.hook(size.measure).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();
                    safe(()->size.afterMeasure((View)chain.getThisObject(),(int)chain.getArg(0),
                            closing?CapsuleSize.OEM:module.config.capsuleSize()));
                    return result;
                }));
                module.info("Native capsule size controls installed");
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){rollback(start);size=null;module.error("Capsule sizing unavailable; native bounds retained",error);}
            start=hooks.size();
            try{
                boolean legacy=appearance!=null?appearance.legacy:legacy(lookup);
                NativeCapsuleCycle cycle=new NativeCapsuleCycle(lookup,legacy);
                cycle.trace=module::info;
                for(Method event:new Method[]{cycle.slide,cycle.show})hooks.add(module.hook(event)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                            safe(()->cycle.trace(chain.getThisObject(),event==cycle.slide?"slide":"show",(int)chain.getArg(0),
                                    (int)chain.getArg(1),(int)chain.getArg(2),(int)chain.getArg(3),module.config.skipEmptyCapsule()));
                            return chain.proceed();
                        }));
                hooks.add(module.hook(cycle.hide).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    safe(()->cycle.trace(chain.getThisObject(),"hide",(int)chain.getArg(0),(int)chain.getArg(1),
                            (int)chain.getArg(2),(int)chain.getArg(3),module.config.skipEmptyCapsule()));
                    if(!closing&&module.config.skipEmptyCapsule()){
                        boolean[] skipped={false};safe(()->skipped[0]=cycle.skip(chain.getThisObject(),(int)chain.getArg(0),
                                (int)chain.getArg(1),(int)chain.getArg(2),(int)chain.getArg(3),true));
                        if(skipped[0])return null;
                    }
                    return chain.proceed();
                }));
                module.info("Native capsule empty-target cycle control installed"
                        +(cycle.semanticTransitions?" (DEX state/list references)":" (validated legacy contract)"));
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){rollback(start);module.error("Capsule cycle control unavailable; native swipe retained",error);}
            start=hooks.size();
            try{
                dim=new NativeCardDim(lookup);
                dim.failed=error->module.error("Card backdrop unavailable; native window retained",error);
                hooks.add(module.hook(dim.added).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    if(module.mediaWaves!=null)module.mediaWaves.card(chain.getThisObject(),true);
                    if(order!=null)order.expanded(true);
                    Object result=chain.proceed();safe(()->dim.added(chain.getThisObject(),!closing&&module.config.dimCardBackground(),module.config.extras().dimPercent()));return result;
                }));
                hooks.add(module.hook(dim.removed).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();dim.removed(chain.getThisObject());
                    if(order!=null)order.expanded(false);
                    if(module.mediaWaves!=null)module.mediaWaves.card(chain.getThisObject(),false);return result;
                }));
                module.info("Native card animated backdrop installed");
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){
                rollback(start);dim=null;
                if(module.mediaWaves!=null){module.mediaWaves.removePlugin(wave);wave=null;}
                module.error("Card window controls unavailable; OEM media waveform retained",error);
            }
            start=hooks.size();
            try{
                dismissal=new NativeCardDismissal(lookup.load(NativeCameraCard.VIEWS+"CardContainer"),main,
                        ()->!closing&&module.config.enabled());
                dismissal.failed=error->module.error("Native card exit cleanup failed",error);
                dismissal.trace=module::info;
                hooks.add(module.hook(dismissal.update).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    boolean[] deferred={false};safe(()->deferred[0]=dismissal.defer((View)chain.getThisObject()));
                    return deferred[0]?null:chain.proceed();
                }));
                hooks.add(module.hook(dismissal.detach).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();safe(()->dismissal.detached((View)chain.getThisObject()));return result;
                }));
                module.info("Native card exit holder retention installed");
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){
                rollback(start);if(dismissal!=null)dismissal.close();dismissal=null;
                module.error("Native card exit retention unavailable; native lifecycle retained",error);
            }
            start=hooks.size();
            try{
                if(appearance==null || dim==null)throw new NoSuchMethodException("Native capsule selection/card lifecycle");
                order=new NativeCardOrder(lookup,appearance.legacy,main,()->!closing&&module.config.selectedCardFirst());
                order.failed=error->module.error("Card order unavailable; native ordering retained",error);
                order.trace=module::info;
                hooks.add(module.hook(order.update).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    if(order.deferUpdate((View)chain.getThisObject()))return null;
                    AutoCloseable scope=null;
                    try{scope=order.beforeUpdate((View)chain.getThisObject());}
                    catch(ReflectiveOperationException|RuntimeException|LinkageError error){module.error("Card ordering skipped",error);}
                    try{return chain.proceed();}
                    finally{if(scope!=null)try{scope.close();}catch(Exception error){module.error("Card data restore",error);}}
                }));
                hooks.add(module.hook(appearance.stateChanged).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();safe(()->observeSelection((View)chain.getThisObject()));return result;
                }));
                hooks.add(module.hook(order.launch).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    boolean pinned=false,accepted=false;
                    try{if(NativeCardLaunch.activityRequest(chain.getArgs().toArray()))pinned=order.beforeLaunch(chain.getThisObject());}
                    catch(RuntimeException|LinkageError error){module.error("Card launch order guard",error);}
                    try{Object result=chain.proceed();accepted=Boolean.TRUE.equals(result);return result;}
                    finally{if(pinned)order.afterLaunch(accepted);}
                }));
                order.selection(root);
                module.info("Native selected capsule card priority installed");
            }catch(ReflectiveOperationException|RuntimeException|LinkageError error){rollback(start);if(order!=null)order.close();order=null;module.error("Card priority controls unavailable; native order retained",error);}
        }
        private boolean legacy(NativeCapsuleAppearance.Lookup lookup)throws ClassNotFoundException{
            try{lookup.load("com.oplus.systemui.plugins.seedling.capsule.ui.viewmodel.u");return false;}
            catch(ClassNotFoundException missing){lookup.load("a5.q");return true;}
        }
        void refresh(){
            if(dismissal!=null)safe(dismissal::refresh);
            if(size!=null)for(View root:new ArrayList<>(roots))safe(()->size.refresh(root,closing?CapsuleSize.OEM:module.config.capsuleSize()));
            if(order!=null){safe(()->order.selection(root));order.schedule();}
            if(appearance!=null)safe(()->appearance.restoreDisabled(!closing&&module.config.hideCapsuleStroke(),!closing&&module.config.hideCapsuleArrows()));
            if(appearance!=null)for(View root:new ArrayList<>(roots))safe(()->appearance.refresh(root,
                    !closing&&module.config.hideCapsuleStroke(),!closing&&module.config.hideCapsuleArrows(),
                    closing?CloudCorners.Style.OEM:module.config.corners().capsule()));
            if(dim!=null)safe(()->dim.refresh(!closing&&module.config.dimCardBackground(),module.config.extras().dimPercent()));
        }
        boolean belongsTo(ViewGroup view){for(View root:roots)if(root.getParent()==view)return true;return false;}
        private void observeSelection(View view){
            for(View ancestor=view;ancestor!=null;ancestor=ancestor.getParent() instanceof View parent?parent:null)
                if(ancestor==root){order.selection(view);return;}
        }
        void close(){closing=true;
            if(dismissal!=null)dismissal.close();
            if(order!=null)order.close();if(size!=null)size.close();
            if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnGlobalLayoutListener(waveLayout);
            if(module.mediaWaves!=null)module.mediaWaves.removePlugin(wave);
            if(appearance!=null)safe(appearance::restore);if(dim!=null)safe(()->dim.refresh(false,0));rollback(0);}
        private void rollback(int start){for(int i=hooks.size()-1;i>=start;i--)hooks.remove(i).unhook();}
        private void safe(Runnable action){try{action.run();}catch(RuntimeException|LinkageError error){module.error("Optional capsule control failed; native lifecycle continues",error);}}
    }
}
