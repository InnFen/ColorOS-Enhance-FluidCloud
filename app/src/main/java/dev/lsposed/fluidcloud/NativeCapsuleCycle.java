package dev.lsposed.fluidcloud;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Replaces only a user swipe's empty target with the OEM's ordinary slide transition. */
final class NativeCapsuleCycle {
    final Method hide,slide,show;
    final boolean semanticTransitions;
    java.util.function.Consumer<String> trace=value->{};
    private final Field owner,items;
    NativeCapsuleCycle(NativeCapsuleAppearance.Lookup lookup,boolean legacy)throws ReflectiveOperationException{
        Class<?> helper;
        try{helper=lookup.load(legacy?"a5.h":"com.oplus.systemui.plugins.seedling.capsule.ui.viewmodel.l");}
        catch(ClassNotFoundException missing){if(legacy)throw missing;helper=null;}
        Class<?> model=lookup.load(legacy?"a5.q":"com.oplus.systemui.plugins.seedling.capsule.ui.viewmodel.u");
        if(!legacy && !hasCycle(helper)){
            Class<?> container=lookup.load(NativeCapsuleAppearance.VIEWS+"CapsuleContainer");
            Class<?> action=lookup.load("com.oplus.systemui.plugins.seedling.capsule.ui.model.a").getSuperclass();
            model=Reflect.uniqueField(container,field->Reflect.instance(field)
                    && field.getType().getName().startsWith("com.oplus.systemui.plugins.seedling.capsule.ui.viewmodel.")
                    && PluginClasses.has(field.getType(),void.class,action)).getType();
            var types=new java.util.LinkedHashSet<Class<?>>();
            for(Field field:model.getDeclaredFields())if(Reflect.instance(field)&&hasCycle(field.getType()))types.add(field.getType());
            if(types.size()!=1)throw new NoSuchMethodException("Ambiguous native capsule cycle helper");
            helper=types.iterator().next();
        }
        owner=Reflect.field(helper,"a",model);
        Contract semantic=null;
        if(!legacy)try{semantic=semantic(helper,model,lookup.load("com.oplus.systemui.plugins.seedling.capsule.data.model.ContentState"));}
        catch(ReflectiveOperationException|RuntimeException unavailable){/* Retain validated older contracts when DEX inspection is unavailable. */}
        semanticTransitions=semantic!=null;
        hide=semantic!=null?semantic.hide():Reflect.method(helper,"c",void.class,int.class,int.class,int.class,int.class);
        slide=semantic!=null?semantic.slide():Reflect.method(helper,"a",void.class,int.class,int.class,int.class,int.class);
        show=semantic!=null?semantic.show():Reflect.method(helper,"b",void.class,int.class,int.class,int.class,int.class);
        Field data;
        try{data=semantic!=null?semantic.items():Reflect.field(model,legacy?"l":"m",List.class);}
        catch(NoSuchFieldException changed){data=Reflect.field(model,"l",List.class);}
        items=data;
    }
    private static boolean hasCycle(Class<?> type){
        if(type==null)return false;
        return java.util.Arrays.stream(type.getDeclaredMethods()).filter(NativeCapsuleCycle::cycleMethod).count()==3;
    }
    private record Contract(Method hide,Method slide,Method show,Field items){}
    private static boolean cycleMethod(Method method){
        return !java.lang.reflect.Modifier.isStatic(method.getModifiers())&&method.getReturnType()==void.class
                &&java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{int.class,int.class,int.class,int.class});
    }
    private static Contract semantic(Class<?> helper,Class<?> model,Class<?> content)throws ReflectiveOperationException{
        Field hidden=enumField(content,"HIDE"),normal=enumField(content,"NORMAL");
        Method hide=null,show=null,slide=null;
        for(Method method:helper.getDeclaredMethods()){
            if(!cycleMethod(method))continue;
            var fields=DexFieldUse.fields(method);
            if(fields.contains(hidden)){if(hide!=null)throw new NoSuchMethodException("Ambiguous hide transition");hide=method;}
            else if(fields.contains(normal)){if(show!=null)throw new NoSuchMethodException("Ambiguous show transition");show=method;}
            else{if(slide!=null)throw new NoSuchMethodException("Ambiguous slide transition");slide=method;}
        }
        if(hide==null||show==null||slide==null)throw new NoSuchMethodException("Capsule transition roles unavailable");
        Field items=null;
        for(Field field:DexFieldUse.fields(slide)){
            if(!field.getDeclaringClass().isAssignableFrom(model)||!List.class.isAssignableFrom(field.getType()))continue;
            if(items!=null)throw new NoSuchFieldException("Ambiguous capsule transition list");
            items=field;
        }
        if(items==null)throw new NoSuchFieldException("Capsule transition list unavailable");
        hide.setAccessible(true);show.setAccessible(true);slide.setAccessible(true);
        return new Contract(hide,slide,show,items);
    }
    private static Field enumField(Class<?> content,String name)throws ReflectiveOperationException{
        Field found=null;
        for(Field field:content.getDeclaredFields()){
            if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())||field.getType()!=content)continue;
            field.setAccessible(true);
            if(Reflect.get(field,null) instanceof Enum<?> value&&value.name().equals(name)){
                if(found!=null)throw new NoSuchFieldException("Duplicate content state");
                found=field;
            }
        }
        if(found==null)throw new NoSuchFieldException(name);
        return found;
    }
    void trace(Object helper,String phase,int current,int next,int direction,int gesture,boolean enabled){
        if(!android.util.Log.isLoggable("COSCapsule",android.util.Log.DEBUG))return;
        Object model=Reflect.get(owner,helper);List<?> data=(List<?>)Reflect.get(items,model);
        trace.accept("Capsule cycle "+phase+" size="+(data==null?-1:data.size())+" current="+current+" next="+next
                +" direction="+direction+" gesture="+gesture+" enabled="+enabled);
    }
    static int target(int size,int current,int gesture){
        if(size<1||current<0||current>=size||(gesture!=0&&gesture!=1))return -1;
        return gesture==0?(current+1)%size:Math.floorMod(current-1,size);
    }
    boolean skip(Object helper,int current,int next,int direction,int gesture,boolean enabled){
        if(!enabled)return false;
        Object model=Reflect.get(owner,helper);List<?> data=(List<?>)Reflect.get(items,model);
        if(data==null||next!=data.size())return false;
        int target=target(data.size(),current,gesture);if(target<0)return false;
        Reflect.call(slide,helper,current,target,direction,gesture);return true;
    }
}
