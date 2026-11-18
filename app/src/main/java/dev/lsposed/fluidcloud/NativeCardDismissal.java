package dev.lsposed.fluidcloud;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Keeps outgoing adapter holders available to the OEM's own disappearance scene. */
final class NativeCardDismissal {
    final Method update,detach;
    private final Field data;
    private final Method views;
    private final Handler main;
    private final BooleanSupplier enabled;
    private final WeakHashMap<View,Runnable> pending=new WeakHashMap<>();
    private boolean flushing,closed;
    java.util.function.Consumer<Throwable> failed=error->{};
    java.util.function.Consumer<String> trace=message->{};

    NativeCardDismissal(Class<?> container,Handler main,BooleanSupplier enabled) {
        this(NativeCardList.of(container),main,enabled);
    }
    private NativeCardDismissal(NativeCardList list,Handler main,BooleanSupplier enabled) {
        this(list.data,list.update,list.views,list.detach,main,enabled);
    }
    NativeCardDismissal(Field data,Method update,Method views,Method detach,Handler main,BooleanSupplier enabled) {
        this.data=data;this.update=update;this.views=views;this.detach=detach;this.main=main;this.enabled=enabled;
    }
    boolean defer(View owner) {
        Object next=Reflect.get(data,owner);
        if(closed || flushing || !enabled.getAsBoolean() || !ValueAnimator.areAnimatorsEnabled()
                || !owner.isAttachedToWindow() || !owner.isShown() || owner.getAlpha()<=0
                || !(next instanceof List<?> list) || !list.isEmpty()) {
            forget(owner);return false;
        }
        if(pending.containsKey(owner))return true;
        Object current=Reflect.call(views,owner);
        if(!(current instanceof List<?> holders) || holders.stream().noneMatch(item->item instanceof View view
                && view.isShown() && view.getAlpha()>0))return false;
        WeakReference<View> weak=new WeakReference<>(owner);
        Runnable expiry=()->{View view=weak.get();if(view!=null)flush(view);};
        pending.put(owner,expiry);
        // Normally the native window's detach performs the commit. A bounded,
        // animation-scale-aware deadline prevents a stale card if OEM aborts it.
        main.postDelayed(expiry,Math.round(1500*Math.max(1f,Math.min(10f,ValueAnimator.getDurationScale()))));
        trace.accept("Native card exit: retained outgoing holders="+holders.size());
        return true;
    }
    void detached(View owner){flush(owner);}
    private void forget(View owner){Runnable expiry=pending.remove(owner);if(expiry!=null)main.removeCallbacks(expiry);}
    private void flush(View owner) {
        if(!pending.containsKey(owner))return;
        forget(owner);flushing=true;
        try{Reflect.call(update,owner);}
        catch(RuntimeException|LinkageError error){failed.accept(error);}
        finally{flushing=false;}
    }
    void refresh(){if(!enabled.getAsBoolean())for(View owner:new ArrayList<>(pending.keySet()))if(owner!=null)flush(owner);}
    void close(){closed=true;for(View owner:new ArrayList<>(pending.keySet()))if(owner!=null)flush(owner);}
}
