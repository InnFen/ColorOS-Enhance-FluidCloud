package dev.lsposed.fluidcloud;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.SurfaceControl;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Choreographer;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.github.libxposed.api.XposedInterface;

/** Firmware/lifecycle bridge. The presentation coordinator alone selects visible output. */
final class ColorOsAdapter {
    private static final String ROOT="com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleContainerRoot";
    private final HookEntry module;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<XposedInterface.HookHandle> hooks=new ArrayList<>();
    private final Field hostField,fakeHostField,managerField,windowField,stateField;
    private final Method attach,added,detach,removed,connected,handoff,addState,removeState;
    private final Method qsExpanded,keyguardShowing,statusBarState,panel,notificationFraction,qsFraction;
    private final List<Method> stateSetters=new ArrayList<>();
    private final Class<?> listenerType;
    private final boolean pipeline;
    private final boolean capsuleOnTop;
    private final NativeVisibilityProbe visibility;
    private Session session;
    private Object pendingController;
    private boolean disabled;

    ColorOsAdapter(HookEntry module,ClassLoader loader) throws ReflectiveOperationException {
        this.module=module;
        capsuleOnTop=module.config.extras().capsuleOnTop();
        Class<?> controller=Reflect.type(loader,"com.oplus.systemui.statusbar.seeding.CapsulePluginContainerController");
        Class<?> manager=Reflect.type(loader,"com.oplus.systemui.statusbar.seeding.SeedlingPluginManager");
        Class<?> plugin=Reflect.type(loader,"com.android.systemui.plugins.statusbar.SeedlingPlugin");
        Class<?> state=Reflect.type(loader,"com.oplus.systemui.statusbar.SysUiStateControllerImpl");
        listenerType=Reflect.type(loader,"com.android.systemui.plugins.statusbar.SysUiStateController$ISysUiStateListener");
        hostField=Reflect.field(controller,"capsulePluginContainer",ViewGroup.class);
        fakeHostField=Reflect.field(controller,"capsuleFakeContainer",ViewGroup.class);
        managerField=Reflect.field(controller,"seedlingPluginManager",manager);
        Field window;boolean newer;
        try{window=Reflect.field(manager,"statusBarWindowStateController",Object.class);newer=false;}
        catch(NoSuchFieldException missing){window=Reflect.field(manager,"statusBarWindowStateRepository",
                Reflect.type(loader,"com.android.systemui.statusbar.window.data.repository.StatusBarWindowStatePerDisplayRepository"));newer=true;}
        windowField=window;pipeline=newer;stateField=Reflect.field(manager,"sysUiStateController",Object.class);
        attach=Reflect.method(controller,"onViewAttached",void.class);detach=Reflect.method(controller,"onViewDetached",void.class);
        added=Reflect.method(controller,"onPluginAdded",void.class,plugin,Context.class);removed=Reflect.method(controller,"onPluginRemove",void.class,plugin);
        connected=pipeline?Reflect.method(manager,"onPluginConnectedImpl",void.class,plugin,Context.class):null;
        handoff=Reflect.method(controller,"onDispatchTouchEvent",void.class,MotionEvent.class);
        addState=Reflect.method(state,"addListener",void.class,listenerType);removeState=Reflect.method(state,"removeListener",void.class,listenerType);
        qsExpanded=Reflect.method(state,"getQsExpanded",boolean.class);keyguardShowing=Reflect.method(state,"isKeyguardShowing",boolean.class);
        statusBarState=Reflect.method(state,"getStatusBarState",int.class);panel=Reflect.method(state,"getPanelExpandFraction",float.class);
        notificationFraction=Reflect.method(state,"getSeparateNtExpandFraction",float.class);qsFraction=Reflect.method(state,"getSeparateQSExpandFraction",float.class);
        for(String name:new String[]{"setPanelExpandFraction","setSeparateNtExpandFraction","setSeparateQSExpandFraction"})
            stateSetters.add(Reflect.method(state,name,void.class,float.class));
        stateSetters.add(Reflect.method(state,"setQsExpanded",void.class,boolean.class));
        visibility=new NativeVisibilityProbe(loader,pipeline,host->{
            Session current=session;if(current==null || host!=null && current.host!=host)return;
            if(Looper.myLooper()==Looper.getMainLooper() && host==current.host)current.refreshNow();else current.signal();
        });
        visibility.failure=error->{if(Looper.myLooper()==Looper.getMainLooper())disable(error);else main.post(()->disable(error));};
    }
    void install(){
        try{
            visibility.install(module,hooks);
            after(attach,chain->capture(chain.getThisObject()));after(added,chain->capture(chain.getThisObject()));
            if(connected!=null)after(connected,chain->{Object pending=pendingController;if(pending!=null && Reflect.get(managerField,pending)==chain.getThisObject())capture(pending);});
            for(Method setter:stateSetters)after(setter,chain->{Session current=session;if(current!=null && current.state==chain.getThisObject())current.shadeProgressChanged();});
            before(detach,chain->release(chain.getThisObject()));before(removed,chain->release(chain.getThisObject()));
            hooks.add(module.hook(handoff).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Session current=session;
                if(current!=null && current.controller==chain.getThisObject() && current.window!=null)
                    return current.window.source.observeNativeHandoff((MotionEvent)chain.getArg(0),chain::proceed);
                return chain.proceed();
            }));
            module.info("Unified cloud presentation installed; native visibility is observation-only");
        }catch(RuntimeException|LinkageError error){uninstall();throw error;}
    }
    private interface Observation{void run(XposedInterface.Chain chain);}
    private void after(Method method,Observation observer){
        hooks.add(module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            Object result=chain.proceed();try{if(!disabled)observer.run(chain);}catch(RuntimeException|LinkageError error){disable(error);}return result;
        }));
    }
    private void before(Method method,Observation observer){
        hooks.add(module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            try{if(!disabled)observer.run(chain);}catch(RuntimeException|LinkageError error){disable(error);}return chain.proceed();
        }));
    }
    private void capture(Object controller){
        if(disabled || Looper.myLooper()!=Looper.getMainLooper())return;
        ViewGroup host=(ViewGroup)Reflect.get(hostField,controller);
        if(!host.isAttachedToWindow() || host.getDisplay()==null || host.getDisplay().getDisplayId()!=Display.DEFAULT_DISPLAY || root(host)==null)return;
        if(session!=null && session.host==host){session.signal();return;}
        if(session!=null){Session previous=session;session=null;previous.close();}
        Object manager=Reflect.get(managerField,controller);
        if(Reflect.get(windowField,manager)==null){pendingController=controller;return;}
        pendingController=null;
        try{Session next=new Session(controller,host,manager);session=next;next.start();module.info("Unified presentation captured on display "+host.getDisplay().getDisplayId());}
        catch(ReflectiveOperationException|RuntimeException|LinkageError error){disable(error);}
    }
    private static View root(ViewGroup host){for(int i=0;i<host.getChildCount();i++)if(ROOT.equals(host.getChildAt(i).getClass().getName()))return host.getChildAt(i);return null;}
    private void release(Object controller){if(pendingController==controller)pendingController=null;if(session!=null && session.controller==controller){Session previous=session;session=null;previous.close();}}
    private void disable(Throwable error){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->disable(error));return;}
        if(disabled)return;disabled=true;module.error("Unified presentation unavailable; restoring native output",error);
        Session previous=session;session=null;if(previous!=null)previous.close();
    }
    void configurationChanged(){if(session!=null)session.signal();}
    Boolean mediaWaveVisibility(View view){
        if(session==null || !under(view,session.host))return null;
        if(session.coordinator.ownsContent())return session.window!=null && session.window.contentVisible();
        return null;
    }
    void mediaWavePixels(View view){
        if(session!=null && session.window!=null && under(view,session.host))session.window.sourceChanged(false);
    }
    private static boolean under(View view,View root){
        for(View node=view;node!=null;node=node.getParent() instanceof View parent?parent:null)if(node==root)return true;
        return false;
    }
    void uninstall(){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::uninstall);return;}
        disabled=true;pendingController=null;Session previous=session;session=null;
        try{if(previous!=null)previous.close();}finally{unhook(hooks);}
    }
    private void unhook(ArrayList<XposedInterface.HookHandle> handles){
        for(var hook:handles)try{hook.unhook();}catch(RuntimeException|LinkageError error){module.error("Presentation hook removal",error);}handles.clear();
    }

    private final class Session implements CloudPresentationCoordinator.Port {
        final Object controller,state;
        final ViewGroup host;
        final Context context;
        final NativeWindowState nativeWindow;
        final CapsuleViewScope scope;
        final NativeShadeState shade;
        final ArrayList<Runnable> cleanup=new ArrayList<>();
        final ArrayList<XposedInterface.HookHandle> pluginHooks=new ArrayList<>();
        final Set<Method> contentMethods=new HashSet<>(),shadeMethods=new HashSet<>();
        final NativeCapsuleSignals sourceSignals;
        final FrameBoolean environment=new FrameBoolean();
        final DisplayManager displays;
        final PowerManager power;
        final KeyguardManager keyguard;
        final ShadeOpenRequest shadeOpen,cardLaunch;
        final CloudPresentationCoordinator coordinator;
        final PresentationClock clock=new PresentationClock();
        final Object stateListener;
        final Runnable refresh=this::runRefresh;
        NativeShadeInteraction interaction;
        NativeShadeRetention retention;
        NativeShadeLaunch shadeLaunch;
        NativeContentConfig content;
        PresentationWindow window;
        Object lastIdentity;
        String lastDiagnostic;
        Boolean lastShade;
        RectF cameraAnchor;
        int cameraRotation=-1;
        boolean cameraDirty=true;
        boolean closed,queued,contentActive,refreshing;

        Session(Object controller,ViewGroup host,Object manager)throws ReflectiveOperationException{
            this.controller=controller;this.host=host;context=host.getContext();
            state=Reflect.get(stateField,manager);Object source=Reflect.get(windowField,manager);ClassLoader loader=manager.getClass().getClassLoader();
            nativeWindow=pipeline?NativeWindowState.repository(source,
                    Reflect.type(loader,"com.android.systemui.statusbar.window.data.repository.StatusBarWindowStatePerDisplayRepositoryImpl"),
                    Reflect.type(loader,"com.android.systemui.statusbar.CommandQueue$Callbacks"),Reflect.type(loader,"com.android.systemui.statusbar.CommandQueue"),
                    Reflect.type(loader,"kotlinx.coroutines.flow.StateFlow")):NativeWindowState.legacy(source,source.getClass(),
                    Reflect.type(loader,"com.android.systemui.statusbar.window.StatusBarWindowStateListener"));
            scope=new CapsuleViewScope(host);scope.refresh((ViewGroup)Reflect.get(fakeHostField,controller));
            sourceSignals=new NativeCapsuleSignals((method,layout)->pluginHooks.add(module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                        Object result=chain.proceed();try{if(!closed && scope.nativeSource(chain.getThisObject()) && (window==null || !window.isBusy())){
                            if(window!=null)window.sourceChanged(layout);if(layout)signal();else coordinatorInvalidate();
                        }}catch(RuntimeException|LinkageError error){windowFailure(error);}return result;
                    })));
            shade=new NativeShadeState(()->(ViewGroup)Reflect.get(fakeHostField,controller),this::hookShade);
            displays=context.getSystemService(DisplayManager.class);power=context.getSystemService(PowerManager.class);keyguard=context.getSystemService(KeyguardManager.class);
            if(state==null || displays==null || power==null || keyguard==null)throw new IllegalStateException("Missing presentation services");
            var nativeCurve=new android.view.animation.PathInterpolator(.3f,0,.1f,1);
            coordinator=new CloudPresentationCoordinator(this,clock,value->nativeCurve.getInterpolation((float)value));
            shadeOpen=new ShadeOpenRequest(main,this::shadeActive,this::collapsePanel,error->module.error("Cloud panel-open request cancelled",error));
            cardLaunch=new ShadeOpenRequest(main,this::shadeActive,this::collapsePanel,error->module.error("Cloud card launch cancelled",error));
            stateListener=Reflect.listener(listenerType,(method,args)->{
                if(!method.getName().equals("onPanelExpandFractionChange")){environment.invalidate();signal();}
            });
        }
        void coordinatorInvalidate(){if(coordinator!=null)coordinator.invalidate();}
        void start()throws ReflectiveOperationException{
            cleanup.add(nativeWindow::close);nativeWindow.start(host.getDisplay().getDisplayId(),this::signal);
            cleanup.add(()->Reflect.call(removeState,state,stateListener));Reflect.call(addState,state,stateListener);
            DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
                public void onDisplayAdded(int id){environment.invalidate();signal();}
                public void onDisplayRemoved(int id){environment.invalidate();signal();}
                public void onDisplayChanged(int id){cameraDirty=true;environment.invalidate();signal();}
            };
            displays.registerDisplayListener(displayListener,main);cleanup.add(()->displays.unregisterDisplayListener(displayListener));
            BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){cameraDirty=true;environment.invalidate();refreshNow();}};
            IntentFilter filter=new IntentFilter();for(String action:new String[]{Intent.ACTION_SCREEN_OFF,Intent.ACTION_SCREEN_ON,Intent.ACTION_USER_PRESENT,Intent.ACTION_CONFIGURATION_CHANGED})filter.addAction(action);
            context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);cleanup.add(()->context.unregisterReceiver(receiver));
            View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
                public void onViewAttachedToWindow(View view){environment.invalidate();signal();}
                public void onViewDetachedFromWindow(View view){ColorOsAdapter.this.release(controller);}
            };
            host.addOnAttachStateChangeListener(attachment);cleanup.add(()->host.removeOnAttachStateChangeListener(attachment));
            View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->{cameraDirty=true;environment.invalidate();signal();};
            host.addOnLayoutChangeListener(layout);cleanup.add(()->host.removeOnLayoutChangeListener(layout));
            installPluginContracts(root(host).getClass().getClassLoader());sourceSignals.discover(host);signal();
        }
        void installPluginContracts(ClassLoader loader)throws ReflectiveOperationException{
            interaction=new NativeShadeInteraction(loader);
            int launchHooksStart=pluginHooks.size();
            try{
                shadeLaunch=new NativeShadeLaunch(loader,controller.getClass().getClassLoader(),main,new NativePanelDismissal(controller),
                        error->module.error("Native shade launch fallback",error));
                shadeLaunch.trace=module::info;
                pluginHooks.add(module.hook(shadeLaunch.windowFinished).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    try{
                        Object owner=chain.getThisObject();Method finish=shadeLaunch.windowFinished;
                        SurfaceControl application=(SurfaceControl)chain.getArg(0);boolean cancelled=(boolean)chain.getArg(1);
                        if(shadeLaunch.finishSurface(application,()->Reflect.call(finish,owner,application,cancelled)))return null;
                    }catch(RuntimeException|LinkageError error){module.error("Shade window launch completion",error);collapsePanel();}
                    return chain.proceed();
                }));
                pluginHooks.add(module.hook(shadeLaunch.ready).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object data=chain.getArg(0);
                    try{
                        Object source=shadeLaunch.ready(data);
                        if(source!=null){
                            Method cancel=Reflect.method(source.getClass(),"a",void.class);
                            if(shadeMethods.add(cancel))pluginHooks.add(module.hook(cancel).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(event->{
                                try{shadeLaunch.cancelController(event.getThisObject());}catch(RuntimeException|LinkageError error){module.error("Shade launch cancel",error);}return event.proceed();
                            }));
                        }
                    }catch(ReflectiveOperationException|RuntimeException|LinkageError error){module.error("Shade launch layer unavailable",error);shadeLaunch.cancel();collapsePanel();}
                    return chain.proceed();
                }));
                pluginHooks.add(module.hook(shadeLaunch.finished).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    try{
                        Object callback=chain.getThisObject();Method finish=shadeLaunch.finished;
                        if(shadeLaunch.finish(callback,()->Reflect.call(finish,callback)))return null;
                    }catch(RuntimeException|LinkageError error){module.error("Shade launch completion",error);collapsePanel();}
                    return chain.proceed();
                }));
                module.info("Native card-over-shade launch layer installed");
            }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
                while(pluginHooks.size()>launchHooksStart)try{pluginHooks.removeLast().unhook();}catch(RuntimeException|LinkageError cleanup){module.error("Shade launch hook cleanup",cleanup);}
                shadeLaunch=null;module.info("Native shade launch layer unavailable; retaining panel-dismiss fallback");
                if(android.util.Log.isLoggable("COSPresentation",android.util.Log.DEBUG))module.error("Native shade launch contract",unavailable);
            }
            var launch=NativeCardLaunch.find(interaction.action.getDeclaringClass());
            if(launch.isPresent()){
                Method method=launch.get();
                pluginHooks.add(module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object[] nativeArguments=chain.getArgs().toArray();
                    View launchSource=null;
                    if(retainShade() && shadeActive() && NativeCardLaunch.activityRequest(nativeArguments))
                        launchSource=NativeCardLaunch.animationSource((View)chain.getArg(1),host);
                    if(launchSource!=null){
                        if(shadeLaunch!=null){
                            if(shadeLaunch.busy())return true;
                            boolean began=false;
                            try{began=shadeLaunch.begin(launchSource,scope.shade(),()->session==this && allowed() && retainShade());}
                            catch(RuntimeException|LinkageError error){shadeLaunch.cancel();module.error("Shade launch preparation unavailable",error);}
                            if(began){
                                try{Object result=chain.proceed();if(!Boolean.TRUE.equals(result))shadeLaunch.launchFailed();return result;}
                                catch(Throwable error){shadeLaunch.launchFailed();throw error;}
                            }
                        }
                        Object owner=chain.getThisObject();Object[] arguments=NativeCardLaunch.snapshot(nativeArguments);
                        cardLaunch.submit(()->Reflect.call(method,owner,arguments),()->session==this && allowed() && retainShade());
                        return true;
                    }
                    return chain.proceed();
                }));
            }else module.info("Native card launch contract unavailable; preserving native launch");
            pluginHooks.add(module.hook(interaction.qsChanged).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                boolean original=(boolean)chain.getArg(0),presented=original;
                try{presented=interaction.filterQs(chain.getThisObject(),original,retainShade());}
                catch(RuntimeException|LinkageError error){windowFailure(error);}
                return original==presented?chain.proceed():chain.proceed(new Object[]{presented});
            }));
            Class<?> change=Reflect.type(loader,"com.oplus.systemui.plugins.seedling.plugin.process.StateChangeHandler");
            Method panelChange=Reflect.method(change,"onPanelExpandFractionChange",void.class,Bundle.class);
            Method seamless=Reflect.method(change,"onSeamlessExpandStart",void.class,Bundle.class);
            retention=new NativeShadeRetention(panelChange);
            pluginHooks.add(module.hook(panelChange).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Bundle original=(Bundle)chain.getArg(0),adjusted=original;
                try{adjusted=retention.filter(chain.getThisObject(),original,retainShade());}catch(RuntimeException|LinkageError error){windowFailure(error);}
                return adjusted==original?chain.proceed():chain.proceed(new Object[]{adjusted});
            }));
            pluginHooks.add(module.hook(seamless).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->retainShade()?null:chain.proceed()));
            pluginHooks.add(module.hook(interaction.touch).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                NativeShadeInteraction.Gate gate=null;
                try{if(forwardingShade())gate=interaction.allowClick(chain.getThisObject(),(MotionEvent)chain.getArg(0),()->!retainShade() && (boolean)Reflect.call(qsExpanded,state));}
                catch(RuntimeException|LinkageError error){windowFailure(error);}
                try{return chain.proceed();}finally{if(gate!=null)try{gate.close();}catch(RuntimeException|LinkageError error){windowFailure(error);}}
            }));
            pluginHooks.add(module.hook(interaction.action).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                if(android.util.Log.isLoggable("COSPresentation",android.util.Log.DEBUG) && interaction.isClick(chain.getArg(0)))module.info("Presentation native click forwarding="+forwardingShade()+" retain="+retainShade());
                if(forwardingShade() && sceneConfig().collapseBeforeOpen() && interaction.isClick(chain.getArg(0))){
                    NativeShadeInteraction.Target target=interaction.target(host,chain.getThisObject());Object event=chain.getArg(0);
                    if(target!=null){shadeOpen.submit(()->Reflect.call(interaction.action,target.model(),event),
                            ()->session==this && retainShade() && sceneConfig().collapseBeforeOpen() && interaction.valid(host,target));return null;}
                }return chain.proceed();
            }));
        }
        void hookShade(Method setter){
            if(!shadeMethods.add(setter))return;
            pluginHooks.add(module.hook(setter).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Object result=chain.proceed();try{if(!closed)signal();}catch(RuntimeException|LinkageError error){windowFailure(error);}return result;
            }));
            try{
                Method active=Reflect.method(setter.getDeclaringClass(),"a",boolean.class);
                pluginHooks.add(module.hook(active).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->
                        window!=null && window.suppressesNativeOutput(chain.getThisObject())?false:chain.proceed()));
            }catch(NoSuchMethodException missing){/* Older fake frames have no redundant onDraw loop. */}
        }
        void hookContent(Method method){
            if(!contentMethods.add(method))return;
            boolean bundle=method.getParameterTypes()[0]==Bundle.class;
            pluginHooks.add(module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Object original=chain.getArg(0),adjusted=original;
                try{if(content!=null)adjusted=bundle?content.filter(chain.getThisObject(),(Bundle)original):content.filterTransform(chain.getThisObject(),original);}
                catch(RuntimeException|LinkageError error){windowFailure(error);}
                Object result=adjusted==original?chain.proceed():chain.proceed(new Object[]{adjusted});
                if(window!=null && !window.isBusy())window.sourceChanged(true);return result;
            }));
        }
        CloudConfig sceneConfig(){return module.config.forOrientation(landscape());}
        boolean landscape(){return host.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;}
        boolean allowed(){return !closed && !disabled && host.isAttachedToWindow() && root(host)!=null && environment.get(()->{
            Display display=host.getDisplay();return display!=null && display.getDisplayId()==Display.DEFAULT_DISPLAY && display.getState()==Display.STATE_ON && power.isInteractive()
                    && !keyguard.isKeyguardLocked() && !(boolean)Reflect.call(keyguardShowing,state) && (int)Reflect.call(statusBarState,state)==0;
        });}
        boolean shadeActive(){
            float a=(float)Reflect.call(panel,state),b=(float)Reflect.call(notificationFraction,state),c=(float)Reflect.call(qsFraction,state);
            return (boolean)Reflect.call(qsExpanded,state) || !Float.isFinite(a) || !Float.isFinite(b) || !Float.isFinite(c) || a>0 || b>0 || c>0 || shade.isActive();
        }
        void shadeProgressChanged(){
            if(Looper.myLooper()!=Looper.getMainLooper()){signal();return;}
            try{boolean next=shadeActive();if(lastShade==null || next!=lastShade)refreshNow();}
            catch(RuntimeException|LinkageError error){windowFailure(error);}
        }
        boolean retainShade(){return Looper.myLooper()==Looper.getMainLooper() && allowed() && coordinator.retainShade();}
        boolean forwardingShade(){return window!=null && window.source.isForwardingTouch() && retainShade() && shadeActive();}
        @android.annotation.SuppressLint("WrongConstant")
        void collapsePanel(){
            Object service=context.getSystemService("statusbar");if(service==null)throw new IllegalStateException("No status bar service");
            try{Reflect.call(Reflect.method(service.getClass(),"collapsePanels",void.class),service);}catch(ReflectiveOperationException error){throw new IllegalStateException("Panel collapse unavailable",error);}
        }
        RectF camera(){
            Display display=host.getDisplay();if(display==null)return null;
            int rotation=display.getRotation();if(!cameraDirty && rotation==cameraRotation)return cameraAnchor;
            cameraDirty=false;cameraRotation=rotation;cameraAnchor=null;
            var cutout=display.getCutout();if(cutout==null)return null;
            int inset=Math.max(Math.max(cutout.getSafeInsetTop(),cutout.getSafeInsetBottom()),Math.max(cutout.getSafeInsetLeft(),cutout.getSafeInsetRight()));
            WindowInsets windowInsets=host.getRootWindowInsets();if(windowInsets!=null)inset=Math.max(inset,windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top);
            int max=inset*2;
            for(Rect bounds:cutout.getBoundingRects())if(!bounds.isEmpty() && bounds.width()<=max && bounds.height()<=max){cameraAnchor=new RectF(bounds);break;}
            return cameraAnchor;
        }
        void runRefresh(){
            queued=false;if(closed)return;if(refreshing){signal();return;}refreshing=true;
            try{
                CloudConfig effective=sceneConfig().withCapsuleOnTop(capsuleOnTop);scope.refresh((ViewGroup)Reflect.get(fakeHostField,controller));sourceSignals.discover(host);
                NativeVisibilityProbe.Snapshot facts=visibility.snapshot(host);Object identity=interaction.contentIdentity(host);
                if(identity==null && interaction.blankRecoverable(host))identity=lastIdentity;else lastIdentity=identity;
                int windowState=nativeWindow.value();boolean fullscreen=windowState==1 || windowState==2;
                boolean protectedState=!allowed() || facts.protectedState() || windowState<0;
                CloudScene top=facts.scene()!=null?facts.scene():fullscreen?CloudScene.FULLSCREEN:null;
                if(top!=null && effective.mode(top)==CloudSceneMode.ANIMATE && camera()==null)protectedState=true;
                boolean shadeExpanded=shadeActive();lastShade=shadeExpanded;
                coordinator.update(new CloudPresentationCoordinator.Input(host,host.getDisplay()==null?-1:host.getDisplay().getDisplayId(),identity,identity!=null,
                        effective,landscape(),fullscreen,facts.scene(),shadeExpanded,protectedState,facts.visible()));
                if(android.util.Log.isLoggable("COSPresentation",android.util.Log.DEBUG)){
                    var output=coordinator.current();
                    String diagnostic="Presentation state visible="+facts.visible()+" protected="+protectedState+" scene="+facts.scene()+" content="+(identity!=null)+" shade="+shadeExpanded+" retain="+retainShade()+" owned="+coordinator.ownsContent()+" output="+output.owner()+" fraction="+Math.round(output.fraction()*1000)/1000d;
                    if(!diagnostic.equals(lastDiagnostic)){lastDiagnostic=diagnostic;module.info(diagnostic+(window==null?"":" source="+window.source.debugState()));}
                }
                if(window!=null){window.nativeTargets(scope.shade(),retainShade());setContentActive(coordinator.ownsContent());}
                interaction.presentQs(retainShade(),()->(boolean)Reflect.call(qsExpanded,state));
                if(!effective.collapseBeforeOpen())shadeOpen.cancel();if(!retainShade()){cardLaunch.cancel();if(shadeLaunch!=null)shadeLaunch.cancel();if(retention!=null)retention.restore();}
            }catch(RuntimeException|LinkageError error){windowFailure(error);}finally{refreshing=false;}
        }
        @Override public void prepare(Object identity){
            try{
                if(window==null){
                    content=new NativeContentConfig(host,()->nativeWindow.value()!=0,this::hookContent);
                    if(capsuleOnTop)try {
                        window=new PresentationWindow(host,this::camera,this::allowed,coordinator::accepts,
                                this::signal,coordinator::invalidate,this::windowFailure,true);
                    }catch(RuntimeException|LinkageError error){
                        module.error("Elevated capsule window unavailable; retaining the normal layer",error);
                    }
                    if(window==null)window=new PresentationWindow(host,this::camera,this::allowed,coordinator::accepts,
                            this::signal,coordinator::invalidate,this::windowFailure,false);
                    window.corners=()->module.config.corners().capsule();
                    window.prepareContent=()->{if(content!=null)content.update(coordinator.ownsContent());scope.refreshSources();sourceSignals.discover(host);};
                    window.source.recoverBlank=()->retainShade() && interaction.blankRecoverable(host);
                    window.source.retainRecovery=()->retainShade() && interaction.hasRecoverableItems(host);
                }
                setContentActive(true);window.nativeTargets(scope.shade(),retainShade());window.prepare();
            }catch(ReflectiveOperationException error){throw new IllegalStateException("Content rendering capability unavailable",error);}
        }
        @Override public void request(CloudPresentationCoordinator.Owner owner,CloudPresentationCoordinator.Ticket ticket,Runnable ready){if(window==null){if(owner==CloudPresentationCoordinator.Owner.NATIVE)ready.run();}else window.request(owner,ticket,ready);}
        Boolean mediaOutputVisible;
        @Override public void render(CloudPresentationCoordinator.Frame frame){
            if(window!=null)window.render(frame);
            boolean visible=window!=null && window.contentVisible();
            if(mediaOutputVisible==null || mediaOutputVisible!=visible){
                mediaOutputVisible=visible;if(module.mediaWaves!=null)module.mediaWaves.changed();
            }
        }
        @Override public void cancelTouch(){if(window!=null)window.cancelTouch();}
        void setContentActive(boolean active){if(content!=null && contentActive!=active){contentActive=active;content.update(active);}}
        @Override public void release(){if(window!=null)window.release();setContentActive(false);}
        @Override public void failure(Throwable error){
            cardLaunch.cancel();
            if(shadeLaunch!=null)shadeLaunch.cancel();
            module.error("Presentation session failed; restoring native content",error);
            try{stopWindow();}finally{
                try{if(interaction!=null)interaction.presentQs(false,()->(boolean)Reflect.call(qsExpanded,state));}
                finally{if(retention!=null)retention.restore();}
            }
        }
        void windowFailure(Throwable error){coordinator.unavailable(error);}
        void stopWindow(){PresentationWindow previous=window;NativeContentConfig previousContent=content;window=null;content=null;contentActive=false;
            try{if(previous!=null)previous.dispose();}finally{if(previousContent!=null)previousContent.close();}}
        void signal(){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::signal);return;}if(closed || queued)return;queued=true;main.post(refresh);}
        void refreshNow(){if(Looper.myLooper()!=Looper.getMainLooper()){signal();return;}if(closed)return;environment.invalidate();if(refreshing || window!=null && window.isBusy()){signal();return;}main.removeCallbacks(refresh);runRefresh();}
        void close(){
            if(closed)return;closed=true;main.removeCallbacks(refresh);shadeOpen.cancel();cardLaunch.cancel();if(shadeLaunch!=null)shadeLaunch.cancel();
            try{coordinator.close();}catch(RuntimeException|LinkageError error){module.error("Presentation close",error);}
            try{stopWindow();}catch(RuntimeException|LinkageError error){module.error("Presentation window close",error);}
            try{if(retention!=null)retention.restore();}catch(RuntimeException|LinkageError error){module.error("Panel input restore",error);}
            try{if(interaction!=null)interaction.presentQs(false,()->(boolean)Reflect.call(qsExpanded,state));}catch(RuntimeException|LinkageError error){module.error("Card QS presentation restore",error);}
            unhook(pluginHooks);visibility.release(host);
            for(int i=cleanup.size()-1;i>=0;i--)try{cleanup.get(i).run();}catch(RuntimeException|LinkageError error){module.error("Presentation lifecycle close",error);}cleanup.clear();
        }
    }
    private static final class PresentationClock implements CloudPresentationCoordinator.Clock {
        private final Choreographer choreographer=Choreographer.getInstance();private Runnable pending;
        private final Choreographer.FrameCallback callback=time->{Runnable next=pending;pending=null;if(next!=null)next.run();};
        public long now(){return System.nanoTime();}
        public double durationScale(){return android.animation.ValueAnimator.getDurationScale();}
        public void postFrame(Runnable frame){pending=frame;choreographer.postFrameCallback(callback);}
        public void removeFrame(Runnable frame){if(pending==frame){pending=null;choreographer.removeFrameCallback(callback);}}
    }
}
