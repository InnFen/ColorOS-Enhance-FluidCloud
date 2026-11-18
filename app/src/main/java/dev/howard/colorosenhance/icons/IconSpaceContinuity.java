package dev.howard.colorosenhance.icons;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Removes the space a suppressed slot currently occupies, including its native add/remove animation. */
final class IconSpaceContinuity {
    private record Gap(WeakReference<View> far,WeakReference<View> near) {}
    private final List<Gap> previous=new ArrayList<>();
    private final Map<ValueAnimator,Boolean> following=new IdentityHashMap<>();
    private int animatorTag;
    private boolean lookedUp;

    List<IconDrawPlan.Transform> apply(ViewGroup parent,List<IconDrawPlan.Cell> cells,List<Boolean> allocated,
            boolean right,float progress,List<IconDrawPlan.Transform> base,Runnable pixels){
        List<Gap> gaps=new ArrayList<>();View near=null;boolean selected=false;
        for(int i=cells.size()-1;i>=0;i--){
            var cell=cells.get(i);
            if(cell.selected() && !cell.alwaysHidden() && cell.width()>0){selected=true;continue;}
            if(!allocated.get(i) || cell.selected())continue;
            View far=parent.getChildAt(i);
            if(selected)gaps.add(new Gap(new WeakReference<>(far),new WeakReference<>(near)));
            // The selected view may already have been removed, but the adjacent native
            // icons still animate across its old cell. Retain that gap until it closes.
            for(Gap old:previous)if(old.far.get()==far && old.near.get()==near && !selected){gaps.add(old);break;}
            selected=false;near=far;
        }
        List<IconDrawPlan.Transform> result=new ArrayList<>(base);previous.clear();
        float factor=1-Math.max(0,Math.min(1,progress));
        for(Gap gap:gaps){
            View far=gap.far.get(),next=gap.near.get();int index=parent.indexOfChild(far);
            if(index<0)continue;
            int end=next==null?cells.size():parent.indexOfChild(next);if(end<=index)continue;
            float target=0;for(int i=index+1;i<end;i++)if(cells.get(i).selected())target+=cells.get(i).width();
            float actual=right?(next==null?parent.getWidth()-parent.getPaddingEnd():next.getX())-far.getX()-cells.get(index).width()
                    :far.getX()-(next==null?parent.getPaddingEnd():next.getX()+cells.get(end).width());
            actual=Math.max(0,actual);
            if(target>0 || actual>.1f)previous.add(gap);
            float correction=(actual-target)*factor*(right?1:-1);
            for(int i=0;i<=index;i++){
                var old=result.get(i);result.set(i,new IconDrawPlan.Transform(old.dx()+correction,old.alpha()));
            }
        }
        if(!previous.isEmpty())follow(parent,pixels);
        return result;
    }
    @android.annotation.SuppressLint("DiscouragedApi")
    private void follow(ViewGroup parent,Runnable pixels){
        if(!lookedUp){lookedUp=true;animatorTag=parent.getResources().getIdentifier("translation_x_animator_tag","id","com.android.systemui");}
        if(animatorTag==0)return;
        for(int i=0;i<parent.getChildCount();i++){
            if(!(parent.getChildAt(i).getTag(animatorTag) instanceof ValueAnimator animation) || following.containsKey(animation))continue;
            following.put(animation,true);var owner=new WeakReference<>(parent);
            ValueAnimator.AnimatorUpdateListener update=value->{ViewGroup group=owner.get();if(group!=null){group.invalidate();pixels.run();}};
            animation.addUpdateListener(update);
            animation.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(Animator value){
                    animation.removeUpdateListener(update);animation.removeListener(this);following.remove(animation);
                    ViewGroup group=owner.get();if(group!=null){group.invalidate();pixels.run();}
                }
            });
        }
    }
}
