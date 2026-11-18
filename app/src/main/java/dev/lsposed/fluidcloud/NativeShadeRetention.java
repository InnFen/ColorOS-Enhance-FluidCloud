package dev.lsposed.fluidcloud;

import android.os.Bundle;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Only the capsule panel handler is filtered; notification/quick-settings state stays native. */
final class NativeShadeRetention {
    private final Method panel;
    private final Map<Object,Bundle> nativeInputs=new WeakHashMap<>();
    NativeShadeRetention(Method panel){this.panel=panel;}
    Bundle filter(Object handler,Bundle original,boolean retain){
        if(original==null)return null;
        if(!retain){nativeInputs.remove(handler);return original;}
        nativeInputs.put(handler,new Bundle(original));
        Bundle copy=new Bundle(original);copy.putFloat("expandFraction",0f);return copy;
    }
    void restore(){
        Map<Object,Bundle> pending=new LinkedHashMap<>(nativeInputs);nativeInputs.clear();
        for(var entry:pending.entrySet())Reflect.call(panel,entry.getKey(),entry.getValue());
    }
}
