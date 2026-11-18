package dev.lsposed.fluidcloud;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Shared, one-time discovery of the native presentation list and its adapter commit. */
final class NativeCardList {
    private static final ClassValue<NativeCardList> CACHE=new ClassValue<>() {
        @Override protected NativeCardList computeValue(Class<?> type) {
            try{return new NativeCardList(type);}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Native card list unavailable",error);}
        }
    };
    final Field data;
    final Method update,views,detach;
    static NativeCardList of(Class<?> type){return CACHE.get(type);}
    private NativeCardList(Class<?> type)throws ReflectiveOperationException {
        data=Reflect.uniqueField(type,field->field.getDeclaringClass()==type && Reflect.instance(field)
                && (field.getType()==Object.class || List.class.isAssignableFrom(field.getType())));
        Method found=null;
        for(Method method:type.getDeclaredMethods()) {
            if(Modifier.isStatic(method.getModifiers()) || method.getReturnType()!=void.class
                    || method.getParameterCount()!=0 || method.getName().startsWith("on"))continue;
            if(!DexFieldUse.fields(method).contains(data))continue;
            if(found!=null)throw new NoSuchMethodException("Ambiguous native card data update");
            found=method;
        }
        if(found==null)throw new NoSuchMethodException("Native card data update");
        found.setAccessible(true);update=found;
        views=Reflect.method(type,"getCardViews",List.class);
        detach=Reflect.method(type,"onDetachedFromWindow",void.class);
        if(detach.getDeclaringClass()!=type)throw new NoSuchMethodException("Native card detach override");
    }
}
