package dev.howard.colorosenhance.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.util.TypedValue;
import dev.howard.colorosenhance.SettingsCatalog;
import java.util.function.Supplier;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;

/** A numeric row inside host Settings; completed drags and discrete actions keep their original revision. */
final class NativeNumberRow {
    record State(int value, boolean writable, long revision) { }
    interface Save { void commit(int value,long revision); }
    private final SettingsCatalog.Item item;
    private final Supplier<State> state;
    private final Save save;
    private final Runnable manual;
    private ViewGroup root;
    private LinearLayout line;
    private View bar;
    private IntSupplier readProgress;
    private IntConsumer writeProgress;
    private TextView number;
    private boolean tracking,binding;
    private long dragRevision;

    NativeNumberRow(SettingsCatalog.Item item,Supplier<State> state,Save save,Runnable manual) {
        this.item=item;this.state=state;this.save=save;this.manual=manual;
    }
    void bind(ViewGroup target,HostReflection host) {
        TextView title=target.findViewById(android.R.id.title);
        TextView summary=target.findViewById(android.R.id.summary);
        if(title==null || !(title.getParent() instanceof ViewGroup textArea))return;
        if(root!=target || line==null || line.getParent()==null) {
            tracking=false;root=target;
            Context context=target.getContext();
            // Keep the host's title, padding and card background. Only the numeric content is added.
            for(int i=textArea.getChildCount()-1;i>=0;i--)
                if(textArea.getChildAt(i).getTag() instanceof NativeNumberRow)textArea.removeViewAt(i);
            line=new LinearLayout(context);line.setTag(this);line.setGravity(Gravity.CENTER_VERTICAL);
            createBar(context,host);bar.setContentDescription(item.title());installDiscreteInput();
            line.addView(bar,new LinearLayout.LayoutParams(0,dp(context,48),1));
            number=new TextView(context);number.setGravity(Gravity.CENTER);
            number.setMinWidth(dp(context,72));number.setMinHeight(dp(context,48));
            TextView style=summary==null ? title : summary;
            number.setTextColor(style.getTextColors());number.setTextSize(TypedValue.COMPLEX_UNIT_PX,style.getTextSize());number.setTypeface(style.getTypeface());
            TypedValue feedback=new TypedValue();
            if(context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,feedback,true) && feedback.resourceId!=0)
                number.setBackground(context.getDrawable(feedback.resourceId));
            number.setContentDescription(item.title()+"，点按输入数值，长按"+SettingsCatalog.RESET_NUMBER_LABEL);
            number.setOnClickListener(view->manual.run());
            number.setOnLongClickListener(view->{
                State current=state.get();
                if(tracking || !current.writable() || view.getParent()!=line || line.getParent()==null)return false;
                commitDiscrete(item.valueDefault(),current);
                return true; // Consume a long press even when already at the default.
            });
            line.addView(number,new LinearLayout.LayoutParams(-2,-2));
            if(textArea instanceof RelativeLayout) {
                RelativeLayout.LayoutParams params=new RelativeLayout.LayoutParams(-1,-2);
                params.addRule(RelativeLayout.BELOW,android.R.id.title);params.topMargin=dp(context,4);
                textArea.addView(line,params);
            } else if(textArea instanceof LinearLayout container && container.getOrientation()==LinearLayout.VERTICAL) {
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(context,4);
                textArea.addView(line,params);
            } else {line=null;bar=null;return;} // Unknown firmware layout retains the manual-input row.
        }
        if(summary!=null)summary.setVisibility(View.GONE);
        update();
    }
    private void createBar(Context context,HostReflection host){
        try {
            // COS17's COUISeekBar is a custom View, not an android.widget.SeekBar.
            Object nativeBar=host.create("com.coui.appcompat.seekbar.COUISeekBar",context);
            View view=(View)nativeBar;
            var getter=HostReflection.method(nativeBar.getClass(),"getProgress");
            var setter=HostReflection.method(nativeBar.getClass(),"setProgress",int.class);
            host.call(nativeBar,"setMax",(item.max()-item.min())/item.step());
            getter.invoke(nativeBar);setter.invoke(nativeBar,0);
            host.listenEvent(nativeBar,"setOnSeekBarChangeListener",(name,args)->{
                switch(name){
                    case "onStartTrackingTouch" -> startTracking();
                    case "onStopTrackingTouch" -> stopTracking();
                    case "onProgressChanged" -> progressChanged((Integer)args[1],(Boolean)args[2]);
                    default -> { }
                }
                return null;
            });
            readProgress=()->{try{return (Integer)getter.invoke(nativeBar);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}};
            writeProgress=value->{try{setter.invoke(nativeBar,value);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}};
            bar=view;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
            SeekBar fallback=new SeekBar(context);fallback.setMax((item.max()-item.min())/item.step());
            readProgress=fallback::getProgress;writeProgress=fallback::setProgress;bar=fallback;
            fallback.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onStartTrackingTouch(SeekBar source){startTracking();}
                public void onProgressChanged(SeekBar source,int progress,boolean fromUser){progressChanged(progress,fromUser);}
                public void onStopTrackingTouch(SeekBar source){stopTracking();}
            });
        }
    }
    private void startTracking(){State current=state.get();tracking=current.writable();dragRevision=current.revision();}
    private void progressChanged(int progress,boolean fromUser){
        if(number==null)return;
        // COUI may emit trailing user-progress frames after touch-up. They are not new writes.
        if(!binding && !tracking){update();return;}
        if(!binding && !fromUser)return;
        display(item.min()+progress*item.step());
    }
    private boolean commitDiscrete(int value,State original){
        if(tracking || !original.writable())return false;
        int target=clamp(value);
        if(target==clamp(original.value()))return false;
        save.commit(target,original.revision());update();return true;
    }
    private void installDiscreteInput(){
        bar.setOnKeyListener((view,key,event)->{
            int direction=switch(key){
                case KeyEvent.KEYCODE_DPAD_RIGHT -> view.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?-1:1;
                case KeyEvent.KEYCODE_DPAD_LEFT -> view.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?1:-1;
                case KeyEvent.KEYCODE_PLUS,KeyEvent.KEYCODE_EQUALS -> 1;
                case KeyEvent.KEYCODE_MINUS -> -1;
                default -> 0;
            };
            if(direction==0 || tracking || !state.get().writable())return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                State original=state.get();
                commitDiscrete(original.value()+direction*item.step(),original);
            }
            return true;
        });
        bar.setAccessibilityDelegate(new ProgressAccessibilityDelegate(bar.getAccessibilityDelegate()));
    }
    /** Preserve host accessibility behavior while making progress actions explicit, unlike trailing COUI frames. */
    private final class ProgressAccessibilityDelegate extends View.AccessibilityDelegate {
        private final View.AccessibilityDelegate original;
        ProgressAccessibilityDelegate(View.AccessibilityDelegate original){this.original=original;}
        @Override public void onInitializeAccessibilityNodeInfo(View view,AccessibilityNodeInfo info){
            if(original==null)super.onInitializeAccessibilityNodeInfo(view,info);else original.onInitializeAccessibilityNodeInfo(view,info);
            State current=state.get();
            info.setClassName(SeekBar.class.getName());
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,item.min(),item.max(),clamp(current.value())));
            if(current.writable()){
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }
        }
        @Override public boolean performAccessibilityAction(View view,int action,Bundle arguments){
            State current=state.get();
            if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()){
                if(arguments==null || !arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE))return false;
                float value=arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);
                return Float.isFinite(value) && commitDiscrete(Math.round(value),current);
            }
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)return commitDiscrete(current.value()+item.step(),current);
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)return commitDiscrete(current.value()-item.step(),current);
            return original==null?super.performAccessibilityAction(view,action,arguments):original.performAccessibilityAction(view,action,arguments);
        }
        @Override public boolean dispatchPopulateAccessibilityEvent(View view,AccessibilityEvent event){return original==null?super.dispatchPopulateAccessibilityEvent(view,event):original.dispatchPopulateAccessibilityEvent(view,event);}
        @Override public void onInitializeAccessibilityEvent(View view,AccessibilityEvent event){if(original==null)super.onInitializeAccessibilityEvent(view,event);else original.onInitializeAccessibilityEvent(view,event);}
        @Override public void onPopulateAccessibilityEvent(View view,AccessibilityEvent event){if(original==null)super.onPopulateAccessibilityEvent(view,event);else original.onPopulateAccessibilityEvent(view,event);}
        @Override public boolean onRequestSendAccessibilityEvent(ViewGroup parent,View child,AccessibilityEvent event){return original==null?super.onRequestSendAccessibilityEvent(parent,child,event):original.onRequestSendAccessibilityEvent(parent,child,event);}
        @Override public void sendAccessibilityEvent(View view,int eventType){if(original==null)super.sendAccessibilityEvent(view,eventType);else original.sendAccessibilityEvent(view,eventType);}
        @Override public void sendAccessibilityEventUnchecked(View view,AccessibilityEvent event){if(original==null)super.sendAccessibilityEventUnchecked(view,event);else original.sendAccessibilityEventUnchecked(view,event);}
        @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(View view){return original==null?super.getAccessibilityNodeProvider(view):original.getAccessibilityNodeProvider(view);}
    }
    private void stopTracking(){
        if(!tracking)return;tracking=false;
        save.commit(item.min()+readProgress.getAsInt()*item.step(),dragRevision);update();
    }
    void update(){
        if(bar==null || line==null || line.getParent()==null)return;
        State current=state.get();
        if(!current.writable())tracking=false;
        bar.setEnabled(current.writable());number.setEnabled(current.writable());
        if(tracking)return;
        binding=true;
        try {writeProgress.accept((clamp(current.value())-item.min())/item.step());display(clamp(current.value()));}
        finally{binding=false;}
    }
    private int clamp(int value){return Math.max(item.min(),Math.min(item.max(),item.min()+Math.round((value-item.min())/(float)item.step())*item.step()));}
    private void display(int value){number.setText(NativeDialogs.format(value,item.divisor())+(item.unit().isEmpty()?"":" "+item.unit()));}
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
}
