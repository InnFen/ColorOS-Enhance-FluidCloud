package dev.lsposed.fluidcloud;

import java.lang.reflect.Method;

/** The same immediate completion path used by the native status-bar launch controller. */
final class NativePanelDismissal implements Runnable {
    interface Lookup{Class<?> load(String name)throws ClassNotFoundException;}
    private final Object shade,separate;
    private final Method collapse,collapseQs,enabled;
    NativePanelDismissal(Object capsuleController)throws ReflectiveOperationException{
        this(capsuleController,name->Reflect.type(capsuleController.getClass().getClassLoader(),name));
    }
    NativePanelDismissal(Object capsuleController,Lookup lookup)throws ReflectiveOperationException{
        Object lazy=Reflect.get(Reflect.field(capsuleController.getClass(),"centralSurfacesImpl",Object.class),capsuleController);
        Object central=Reflect.call(Reflect.method(lazy.getClass(),"get",Object.class),lazy);
        if(central==null)throw new IllegalStateException("Central surfaces unavailable");
        shade=Reflect.get(Reflect.field(central.getClass(),"mShadeController",Object.class),central);
        if(shade==null)throw new IllegalStateException("Shade controller unavailable");
        collapse=Reflect.method(shade.getClass(),"instantCollapseShade",void.class);
        Class<?> dependency=lookup.load("com.android.systemui.DependencyEx");
        Object provider=Reflect.get(Reflect.field(dependency,"sDependency",dependency),null);
        Class<?> type=lookup.load("com.android.systemui.shade.OplusSeparateNotificationAndQSEx");
        separate=Reflect.call(Reflect.method(dependency,"getDependency",Object.class,Class.class),provider,type);
        if(!type.isInstance(separate))throw new IllegalStateException("Separate shade controller unavailable");
        enabled=Reflect.method(type,"enableSeparateNotificationAndQS",boolean.class);
        collapseQs=Reflect.method(type,"collapseQSPanel",void.class,boolean.class,boolean.class,boolean.class);
    }
    public void run(){
        if((boolean)Reflect.call(enabled,separate))Reflect.call(collapseQs,separate,false,true,false);
        Reflect.call(collapse,shade);
    }
}
