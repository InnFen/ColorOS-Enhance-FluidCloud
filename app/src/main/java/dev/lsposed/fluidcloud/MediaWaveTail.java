package dev.lsposed.fluidcloud;

import android.media.session.PlaybackState;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Frozen pixels of an ended session, released by native visibility/detach, without an audio or frame timer. */
final class MediaWaveTail {
    private final View root;
    private final Consumer<ImageView> release;
    private final Map<ImageView,MediaWaveLevels> held=new IdentityHashMap<>();
    private ViewTreeObserver observer;
    private final ViewTreeObserver.OnPreDrawListener draw=()->{prune();return true;};
    private final View.OnAttachStateChangeListener attach=new View.OnAttachStateChangeListener(){
        public void onViewAttachedToWindow(View view){ }
        public void onViewDetachedFromWindow(View view){finish((ImageView)view);}
    };
    MediaWaveTail(View root,Consumer<ImageView> release){this.root=root;this.release=release;}
    static boolean terminal(int state){return state==PlaybackState.STATE_NONE || state==PlaybackState.STATE_STOPPED || state==PlaybackState.STATE_ERROR;}
    void hold(ImageView view,MediaWaveLevels levels){
        if(finished(view) || held.containsKey(view))return;
        held.put(view,levels);view.addOnAttachStateChangeListener(attach);
        if(observer==null){observer=root.getViewTreeObserver();observer.addOnPreDrawListener(draw);}
    }
    MediaWaveLevels frame(ImageView view){return held.get(view);}
    boolean pending(){return !held.isEmpty();}
    void forget(ImageView view){
        if(held.remove(view)!=null)view.removeOnAttachStateChangeListener(attach);
        if(held.isEmpty() && observer!=null){if(observer.isAlive())observer.removeOnPreDrawListener(draw);observer=null;}
    }
    void clear(){for(ImageView view:new ArrayList<>(held.keySet()))forget(view);}
    void prune(){for(ImageView view:new ArrayList<>(held.keySet()))if(finished(view))finish(view);}
    private void finish(ImageView view){if(held.containsKey(view)){forget(view);release.accept(view);}}
    private boolean finished(ImageView view){
        if(!view.isAttachedToWindow())return true;
        boolean capsule=false;
        for(View node=view;node!=null;node=node.getParent() instanceof View parent?parent:null){
            // Content may fade before the capsule finishes shrinking. Keep the snapshot
            // throughout that geometry animation, without using status-bar window visibility.
            capsule|=node.getClass().getSimpleName().equals("CapsuleView");
            if(capsule && (node.getVisibility()!=View.VISIBLE || node.getAlpha()<=0 || node.getScaleX()<=0 || node.getScaleY()<=0))return true;
            if(node==root || node.getClass().getSimpleName().equals("CapsuleContainerRoot"))return !capsule;
        }
        return true;
    }
}
