package dev.lsposed.fluidcloud;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;

/** Native input and action contracts; never changes SystemUI's actual panel state. */
final class NativeShadeInteraction {
    static final String VIEWS="com.oplus.systemui.plugins.seedling.capsule.ui.view.";
    final Method touch,action,qsChanged;
    private final Class<?> rootType,containerType,clickType;
    private final Field stateSingleton,stateCache,qsBlocked,qsListener,model,key,contentState;
    private Object heldQsListener;
    private final Method count,mini;
    private final Object blank;
    final boolean legacy;
    record Target(ViewGroup container,Object model,String key) { }
    private record Identity(ViewGroup container,Object model,String key) { }
    interface Lookup{Class<?> load(String name)throws ClassNotFoundException;}

    NativeShadeInteraction(ClassLoader loader)throws ReflectiveOperationException{
        this(name->Reflect.type(loader,name));
    }
    NativeShadeInteraction(Lookup lookup)throws ReflectiveOperationException{
        rootType=lookup.load(VIEWS+"CapsuleContainerRoot");
        containerType=lookup.load(VIEWS+"CapsuleContainer");
        Class<?> selected;boolean older;
        try{
            selected=lookup.load("com.oplus.systemui.plugins.seedling.capsule.ui.viewmodel.u");older=false;
            Class<?> event=lookup.load("com.oplus.systemui.plugins.seedling.capsule.ui.model.a").getSuperclass();
            if(!PluginClasses.has(selected,void.class,event)){
                var field=Reflect.uniqueField(containerType,candidate->Reflect.instance(candidate)
                        && candidate.getType().getName().startsWith("com.oplus.systemui.plugins.seedling.capsule.ui.viewmodel.")
                        && PluginClasses.has(candidate.getType(),void.class,event));
                selected=field.getType();
            }
        }
        catch(ClassNotFoundException old){selected=lookup.load("a5.q");older=true;}
        Class<?> modelType=selected;
        legacy=older;
        clickType=lookup.load(legacy?"y4.a":"com.oplus.systemui.plugins.seedling.capsule.ui.model.a");
        Class<?> state=lookup.load(legacy?"com.oplus.systemui.plugins.seedling.state.n":"com.oplus.systemui.plugins.seedling.state.q");
        Class<?> cache=lookup.load(legacy?"g4.i":"com.oplus.systemui.plugins.baseservice.g");
        Class<?> content=lookup.load(legacy?"v4.e":"com.oplus.systemui.plugins.seedling.capsule.data.model.ContentState");
        touch=Reflect.method(rootType,"onTouchEvent",boolean.class,MotionEvent.class);
        Method dispatch;
        try{dispatch=Reflect.method(modelType,legacy?"j":"k",void.class,clickType.getSuperclass());}
        catch(NoSuchMethodException missing){dispatch=Reflect.signature(modelType,void.class,clickType.getSuperclass());}
        action=dispatch;
        stateSingleton=Reflect.field(state,"j",state);stateCache=Reflect.field(state,"c",cache);
        qsBlocked=Reflect.field(cache,"c",boolean.class);
        Field listener=null;Method callback=null;
        for(Field candidate:state.getDeclaredFields()){
            if(Modifier.isStatic(candidate.getModifiers()))continue;
            try{
                Method method=Reflect.method(candidate.getType(),"onQSExpandedChanged",void.class,boolean.class);
                if(listener!=null)throw new NoSuchFieldException("Ambiguous plugin QS presentation listener");
                candidate.setAccessible(true);listener=candidate;callback=method;
            }catch(NoSuchMethodException ignored){/* Not the plugin's own state listener. */}
        }
        if(listener==null)throw new NoSuchFieldException("Missing plugin QS presentation listener");
        qsListener=listener;qsChanged=callback;
        model=Reflect.field(containerType,legacy?"y":"n",modelType);key=Reflect.field(containerType,legacy?"w":"l",String.class);
        contentState=Reflect.field(containerType,legacy?"x":"m",content);blank=Reflect.get(Reflect.field(content,legacy?"HIDE":"d",content),null);
        count=Reflect.method(containerType,"getCapsuleViewNum",int.class);
        mini=Reflect.method(containerType,legacy?"k":"j",boolean.class);
    }
    boolean isClick(Object event){return event!=null && event.getClass()==clickType;}
    private ViewGroup container(View view){
        if(containerType.isInstance(view))return (ViewGroup)view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){
            ViewGroup found=container(group.getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    boolean hasRecoverableItems(ViewGroup host){
        ViewGroup container=container(host);
        // The inspected native blank transition deliberately marks the container GONE;
        // its root still owns the valid list and handles horizontal recovery gestures.
        return container!=null && container.isAttachedToWindow()
                && (container.getVisibility()==View.VISIBLE || Reflect.get(contentState,container)==blank)
                && !(boolean)Reflect.call(mini,container) && (int)Reflect.call(count,container)>0;
    }
    boolean blankRecoverable(ViewGroup host){
        ViewGroup container=container(host);
        return hasRecoverableItems(host) && Reflect.get(contentState,container)==blank;
    }
    Object contentIdentity(ViewGroup host){
        ViewGroup container=container(host);
        if(container==null || !hasRecoverableItems(host) || Reflect.get(contentState,container)==blank)return null;
        String selected=(String)Reflect.get(key,container);Object owner=Reflect.get(model,container);
        return selected==null || selected.isBlank() || owner==null?null:new Identity(container,owner,selected);
    }
    Target target(ViewGroup host,Object owner){
        ViewGroup container=container(host);
        if(container==null || Reflect.get(model,container)!=owner || Reflect.get(contentState,container)==blank
                || (int)Reflect.call(count,container)<=0 || (boolean)Reflect.call(mini,container))return null;
        String selected=(String)Reflect.get(key,container);
        return selected==null || selected.isEmpty()?null:new Target(container,owner,selected);
    }
    boolean valid(ViewGroup host,Target target){
        if(target==null || !target.container().isAttachedToWindow())return false;
        Target current=target(host,target.model());
        return current!=null && current.container()==target.container() && current.key().equals(target.key());
    }
    Gate allowClick(Object root,MotionEvent event,BooleanSupplier actualQs){
        if(!rootType.isInstance(root) || event==null || event.getActionMasked()!=MotionEvent.ACTION_UP)return null;
        Object state=Reflect.get(stateSingleton,null);if(state==null)return null;
        Object cache=Reflect.get(stateCache,state);if(cache==null || !(boolean)Reflect.get(qsBlocked,cache))return null;
        set(qsBlocked,cache,false);
        return ()->set(qsBlocked,cache,actualQs.getAsBoolean());
    }
    boolean filterQs(Object listener,boolean nativeValue,boolean retain){
        Object state=Reflect.get(stateSingleton,null);
        return retain && state!=null && Reflect.get(qsListener,state)==listener?false:nativeValue;
    }
    void presentQs(boolean retain,BooleanSupplier actualQs){
        Object state=Reflect.get(stateSingleton,null);
        Object listener=state==null?null:Reflect.get(qsListener,state);
        if(heldQsListener!=null && (!retain || listener!=heldQsListener)){
            Object previous=heldQsListener;
            Reflect.call(qsChanged,previous,actualQs.getAsBoolean());
            heldQsListener=null;
        }
        if(retain && heldQsListener==null){
            if(listener==null)throw new IllegalStateException("Plugin QS listener unavailable");
            heldQsListener=listener;Reflect.call(qsChanged,listener,false);
        }
    }
    interface Gate {void close();}
    private static void set(Field field,Object owner,boolean value){
        try{field.setBoolean(owner,value);}catch(IllegalAccessException error){throw new IllegalStateException("Native capsule click permission",error);}
    }
}
