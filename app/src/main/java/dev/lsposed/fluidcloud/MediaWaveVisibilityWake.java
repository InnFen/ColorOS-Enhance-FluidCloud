package dev.lsposed.fluidcloud;

import android.view.View;
import android.view.ViewTreeObserver;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Observes native frames only while capture is waiting for its known waveform to become visible. */
final class MediaWaveVisibilityWake {
    private final Supplier<? extends Iterable<? extends View>> views;
    private final BooleanSupplier waiting,visible;
    private final Runnable changed;
    private final Set<ViewTreeObserver> observers=Collections.newSetFromMap(new IdentityHashMap<>());
    private final ViewTreeObserver.OnPreDrawListener draw=this::onDraw;
    MediaWaveVisibilityWake(Supplier<? extends Iterable<? extends View>> views,BooleanSupplier waiting,BooleanSupplier visible,Runnable changed){
        this.views=views;this.waiting=waiting;this.visible=visible;this.changed=changed;
    }
    void update(boolean enabled){
        if(!enabled){clear();return;}
        Set<ViewTreeObserver> next=Collections.newSetFromMap(new IdentityHashMap<>());
        for(View view:views.get())if(view!=null && view.isAttachedToWindow()){
            ViewTreeObserver observer=view.getViewTreeObserver();if(observer.isAlive())next.add(observer);
        }
        for(ViewTreeObserver observer:observers.toArray(new ViewTreeObserver[0]))if(!next.contains(observer))remove(observer);
        for(ViewTreeObserver observer:next)if(observers.add(observer))observer.addOnPreDrawListener(draw);
    }
    private boolean onDraw(){
        if(!waiting.getAsBoolean()){clear();return true;}
        if(visible.getAsBoolean()){clear();changed.run();}
        return true;
    }
    boolean observing(){return !observers.isEmpty();}
    void clear(){for(ViewTreeObserver observer:observers.toArray(new ViewTreeObserver[0]))remove(observer);}
    private void remove(ViewTreeObserver observer){if(observer.isAlive())observer.removeOnPreDrawListener(draw);observers.remove(observer);}
}
