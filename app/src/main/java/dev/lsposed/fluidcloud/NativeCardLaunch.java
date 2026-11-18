package dev.lsposed.fluidcloud;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The plugin's own activity launch entry, discovered through the known model base. */
final class NativeCardLaunch {
    private static final Class<?>[] PARAMETERS={Object.class,View.class,List.class,boolean.class,
            boolean.class,PendingIntent.class,Bundle.class};
    static Optional<Method> find(Class<?> model){
        Method result=null;
        for(Class<?> type=model;type!=null && type!=Object.class;type=type.getSuperclass()){
            for(Method method:type.getDeclaredMethods()){
                if(Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
                        || method.getReturnType()!=boolean.class || !Arrays.equals(method.getParameterTypes(),PARAMETERS))continue;
                if(result!=null)return Optional.empty();method.setAccessible(true);result=method;
            }
        }
        return Optional.ofNullable(result);
    }
    static boolean cardContent(View view){
        for(View current=view;current!=null;current=current.getParent() instanceof View parent?parent:null){
            String name=current.getClass().getName();
            if(name.equals("com.oplus.systemui.plugins.seedling.card.ui.view.CardView")
                    || name.equals("com.oplus.systemui.plugins.seedling.card.ui.view.InstantContainer"))return true;
        }
        return false;
    }
    static View animationSource(View view,View capsuleHost){
        if(view==null)return null;
        if(cardContent(view))return view.getRootView();
        // CapsuleLaunchAnimatorController.e() returns the pressed capsule itself,
        // whereas the card controller returns its window root. Preserve that exact
        // identity when matching the native ready callback; a shared status-bar root
        // must not make unrelated views eligible for the shade launch bridge.
        if(capsuleHost==null || !(view instanceof ViewGroup))return null;
        for(View current=view;current!=null;current=current.getParent() instanceof View parent?parent:null)
            if(current==capsuleHost)return view;
        return null;
    }
    static Object[] snapshot(Object[] args){
        Object[] result=args.clone();
        if(args[2] instanceof List<?> list){
            List<Object> intents=new ArrayList<>(list.size());
            for(Object item:list)intents.add(item instanceof Intent intent?new Intent(intent):item);
            result[2]=intents;
        }
        if(args[6] instanceof Bundle bundle)result[6]=new Bundle(bundle);
        return result;
    }
    static boolean activityRequest(Object[] args){
        if(args[2] instanceof List<?> list)for(Object item:list)if(item instanceof Intent)return true;
        if(args[5] instanceof PendingIntent pending && pending.isActivity())return true;
        return args[6] instanceof Bundle bundle && bundle.containsKey("taskId") && bundle.getInt("taskId",-1)>=0;
    }
}
