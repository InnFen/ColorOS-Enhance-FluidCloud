package dev.howard.colorosenhance.settings;

import android.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.ViewTreeObserver;

/** Adds only actual overlap plus breathing room; preserves the host's inset listener. */
final class NativePageInsets {
    private final ViewGroup list;
    private final View decor;
    private final int left,top,right,bottom;
    private final boolean clip;
    private boolean alignTop,closed,active;
    private final ViewTreeObserver.OnGlobalLayoutListener changed=this::update;
    NativePageInsets(ViewGroup list,View decor,boolean firstOpen){
        this.list=list;this.decor=decor;
        alignTop=firstOpen;
        left=list.getPaddingLeft();top=list.getPaddingTop();right=list.getPaddingRight();bottom=list.getPaddingBottom();clip=list.getClipToPadding();
        list.setClipToPadding(false);resume();update();
    }
    boolean owns(ViewGroup view){return list==view && !closed;}
    void resume(){
        if(closed || active)return;
        // onStart runs before Settings restores its Toolbar in onResume. Measuring
        // here briefly removes the toolbar's padding and shifts RecyclerView's anchor.
        // Keep the old geometry until the next layout sees the restored toolbar.
        active=true;decor.getViewTreeObserver().addOnGlobalLayoutListener(changed);
    }
    // Keep padding and the RecyclerView anchor while another page is on top.
    void pause(){
        active=false;if(decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnGlobalLayoutListener(changed);
    }
    void close(){if(closed)return;pause();closed=true;list.setPadding(left,top,right,bottom);list.setClipToPadding(clip);}
    void update(){
        if(!active || closed)return;
        WindowInsets window=decor.getRootWindowInsets();if(window==null || list.getHeight()==0)return;
        Insets insets=window.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
        int[] at=new int[2],origin=new int[2];list.getLocationOnScreen(at);decor.getLocationOnScreen(origin);
        int safeTop=origin[1]+insets.top;
        View toolbar=toolbar(decor);
        if(toolbar!=null && toolbar.isShown()){int[] pos=new int[2];toolbar.getLocationOnScreen(pos);safeTop=Math.max(safeTop,pos[1]+toolbar.getHeight());}
        int nextTop=top+overlap(safeTop,at[1])+dp(12);
        int nextBottom=bottom+overlap(at[1]+list.getHeight(),origin[1]+decor.getHeight()-insets.bottom)+dp(24);
        int nextLeft=left+overlap(origin[0]+insets.left,at[0]);
        int nextRight=right+overlap(at[0]+list.getWidth(),origin[0]+decor.getWidth()-insets.right);
        if(list.getPaddingTop()!=nextTop || list.getPaddingBottom()!=nextBottom || list.getPaddingLeft()!=nextLeft || list.getPaddingRight()!=nextRight)
            list.setPadding(nextLeft,nextTop,nextRight,nextBottom);
        if(alignTop){
            alignTop=false;
            // RecyclerView preserves its old anchor when padding is added after its first layout.
            // Align only a newly opened page; returning to an existing page keeps its scroll.
            list.post(()->{
                if(closed || !active)return;
                try{HostReflection.method(list.getClass(),"scrollToPosition",int.class).invoke(list,0);}
                catch(ReflectiveOperationException unavailable){ /* Host without RecyclerView keeps native scrolling. */ }
            });
        }
    }
    static int overlap(int boundary,int edge){return Math.max(0,boundary-edge);}
    private int dp(int value){return Math.round(value*list.getResources().getDisplayMetrics().density);}
    private static View toolbar(View view){
        if(!view.isShown())return null;
        if(view.getClass().getSimpleName().endsWith("Toolbar"))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=toolbar(group.getChildAt(i));if(found!=null)return found;}
        return null;
    }
}
