package dev.lsposed.fluidcloud;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Presents the native subtree to its animated drawables, without changing View flags. */
final class NativeContentVisibility {
    private final Method read,dispatch;
    private final Set<View> owned=Collections.newSetFromMap(new IdentityHashMap<>());
    NativeContentVisibility(){
        try{
            read=Reflect.method(View.class,"isAggregatedVisible",boolean.class);
            dispatch=Reflect.method(View.class,"dispatchVisibilityAggregated",boolean.class,boolean.class);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Native content visibility contract unavailable",error);}
    }
    void present(ViewGroup host){
        for(View root:owned.toArray(new View[0]))if(root.getParent()!=host){restore(root);owned.remove(root);}
        for(int i=0;i<host.getChildCount();i++){
            View root=host.getChildAt(i);
            if(root.getVisibility()==View.VISIBLE && !(boolean)Reflect.call(read,root)){
                owned.add(root);Reflect.call(dispatch,root,true);
            }
        }
    }
    void restore(){for(View root:owned.toArray(new View[0])){restore(root);owned.remove(root);}}
    private void restore(View root){
        boolean actual=root.isAttachedToWindow() && root.isShown() && root.getWindowVisibility()==View.VISIBLE;
        if(actual!=(boolean)Reflect.call(read,root))Reflect.call(dispatch,root,actual);
    }
}
