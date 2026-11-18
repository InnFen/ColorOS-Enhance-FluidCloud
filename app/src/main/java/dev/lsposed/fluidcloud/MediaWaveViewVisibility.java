package dev.lsposed.fluidcloud;

import android.view.View;
import android.widget.ImageView;
import java.util.function.Predicate;

/** The media waveform's content fade does not change whether its capsule is logically presented. */
final class MediaWaveViewVisibility {
    private MediaWaveViewVisibility(){ }
    static boolean eligible(ImageView view,boolean capture,Predicate<View> presentation){
        if(view==null || !view.isAttachedToWindow() || view.getWidth()<=0 || view.getHeight()<=0)return false;
        for(View node=view;node!=null;node=node.getParent() instanceof View parent?parent:null){
            if(node.getVisibility()!=View.VISIBLE)return false;
            boolean capsule=node.getClass().getSimpleName().equals("CapsuleView");
            if((!capture || capsule) && node.getAlpha()<=(capture?0:.01f))return false;
            if(capsule)return presentation.test(view);
        }
        return false;
    }
}
