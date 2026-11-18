package dev.howard.colorosenhance.icons;

import android.content.Context;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.DragEvent;
import android.view.View;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;

/** Framework-only editor: uses the caller's Material or COUI dialog theme in both hosts. */
@android.annotation.SuppressLint({"ViewConstructor","SetTextI18n"}) // Programmatic editor with required state; shared Chinese labels also run inside Settings' resource context.
public final class IconOrderEditor extends LinearLayout {
    public static final String HELP="长按图标或拖动柄，在同一区域内排序，也可拖到另一区域。每个区域均按屏幕从左到右排列。确认后保存；隐藏的图标仍可排序。";
    private static final int UP=View.generateViewId(),DOWN=View.generateViewId(),OTHER=View.generateViewId();
    private final List<String> available;
    private final LinearLayout left,right;
    private IconOrder value;
    private boolean restored;
    private final String dragToken=UUID.randomUUID().toString();
    public IconOrderEditor(Context context,IconOrder current,List<String> slots){
        super(context);available=List.copyOf(slots);
        if(StatusIconCatalog.usesColorOsRingerSlot(slots) && !slots.contains("mute"))current=current.ringerSlot();
        value=current.withAvailable(slots);
        setOrientation(VERTICAL);setPadding(dp(16),dp(8),dp(16),dp(4));
        TextView hint=new TextView(context);hint.setText(HELP);hint.setTextSize(14);hint.setPadding(0,0,0,dp(12));addView(hint);
        LinearLayout columns=new LinearLayout(context);columns.setOrientation(HORIZONTAL);
        addView(columns,new LayoutParams(-1,0,1));
        left=column(columns,"左侧",true);right=column(columns,"右侧",false);
        Button reset=new Button(context);reset.setText("恢复原厂顺序");reset.setOnClickListener(v->{value=IconOrder.EMPTY.withAvailable(available);restored=true;render();});
        addView(reset,new LayoutParams(-1,-2));render();
    }
    public IconOrder value(){return value;}
    public String encodedValue(){return restored?"":value.encode();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        // A long-clickable row owns DOWN, so a listener on ScrollView itself never sees it.
        // Reserve the gesture here before COUI's outer dialog scroller can intercept MOVE.
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN&&getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
        try{return super.dispatchTouchEvent(event);}
        finally{if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int desired=Math.min(dp(520),Math.round(getResources().getDisplayMetrics().heightPixels*.62f));
        int limit=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?desired:MeasureSpec.getSize(heightSpec);
        super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(Math.min(desired,limit),MeasureSpec.EXACTLY));
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(LinearLayout columns,String title,boolean toLeft){
        LinearLayout column=new LinearLayout(getContext());column.setOrientation(VERTICAL);
        LayoutParams space=new LayoutParams(0,-1,1);space.setMargins(dp(3),0,dp(3),0);columns.addView(column,space);
        TextView heading=new TextView(getContext());heading.setText(title+" · 从左到右");heading.setTextSize(15);heading.setPadding(dp(8),dp(8),dp(8),dp(8));column.addView(heading);
        ScrollView scroll=new ScrollView(getContext());scroll.setFillViewport(true);column.addView(scroll,new LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(getContext());list.setOrientation(VERTICAL);list.setMinimumHeight(dp(96));scroll.addView(list,new ScrollView.LayoutParams(-1,-2));
        scroll.setOnDragListener((v,event)->{
            boolean ours=event.getLocalState() instanceof Drag drag && drag.token.equals(dragToken);
            if(!ours)return false;
            switch(event.getAction()){
                case DragEvent.ACTION_DRAG_ENTERED -> scroll.setBackgroundColor(0x182080ff);
                case DragEvent.ACTION_DRAG_LOCATION -> {
                    if(event.getY()<dp(48))scroll.smoothScrollBy(0,-dp(20));
                    else if(event.getY()>scroll.getHeight()-dp(48))scroll.smoothScrollBy(0,dp(20));
                }
                case DragEvent.ACTION_DROP -> {
                    Drag drag=(Drag)event.getLocalState();
                    int index=dropIndex(list,event.getY()+scroll.getScrollY(),drag.slot);
                    value=value.move(drag.slot,toLeft,index);restored=false;scroll.setBackgroundColor(Color.TRANSPARENT);render();
                }
                case DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED -> scroll.setBackgroundColor(Color.TRANSPARENT);
                default -> { }
            }
            return true;
        });
        return list;
    }
    private record Drag(String token,String slot){}
    private int dropIndex(LinearLayout list,float y,String moving){
        int index=0;
        for(int i=0;i<list.getChildCount();i++){
            View row=list.getChildAt(i);if(Objects.equals(row.getTag(),moving))continue;
            if(y<row.getTop()+row.getHeight()/2f)break;index++;
        }
        return index;
    }
    private void render(){render(left,value.left(),true);render(right,value.right(),false);}
    private void render(LinearLayout list,List<String> slots,boolean onLeft){
        list.removeAllViews();
        for(String slot:slots){
            TextView row=new TextView(getContext());row.setText("☰  "+StatusIconCatalog.label(slot));row.setTextSize(14);
            row.setTag(slot);row.setPadding(dp(10),dp(14),dp(8),dp(14));row.setMinHeight(dp(54));row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            GradientDrawable background=new GradientDrawable();background.setCornerRadius(dp(12));background.setColor(0x10808080);row.setBackground(background);
            LayoutParams params=new LayoutParams(-1,-2);params.bottomMargin=dp(6);list.addView(row,params);
            row.setOnLongClickListener(v->v.startDragAndDrop(ClipData.newPlainText("icon-order",""),new View.DragShadowBuilder(v),new Drag(dragToken,slot),0));
            row.setFocusable(true);row.setContentDescription(StatusIconCatalog.label(slot)+"，"+(onLeft?"左侧":"右侧")+"，长按拖动排序");
            row.setAccessibilityDelegate(new View.AccessibilityDelegate(){
                @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){
                    super.onInitializeAccessibilityNodeInfo(host,info);
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(UP,"向前移动"));
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(DOWN,"向后移动"));
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(OTHER,onLeft?"移到右侧":"移到左侧"));
                }
                @Override public boolean performAccessibilityAction(View host,int action,android.os.Bundle args){
                    List<String> source=onLeft?value.left():value.right();int index=source.indexOf(slot);
                    if(action==UP || action==DOWN || action==OTHER){
                        value=value.move(slot,action==OTHER?!onLeft:onLeft,action==OTHER?0:index+(action==UP?-1:1));restored=false;render();return true;
                    }
                    return super.performAccessibilityAction(host,action,args);
                }
            });
        }
    }
}
