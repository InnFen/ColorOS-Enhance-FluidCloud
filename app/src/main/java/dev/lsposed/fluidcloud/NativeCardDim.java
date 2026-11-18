package dev.lsposed.fluidcloud;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** A background inside the native full-screen card window, below both normal and instant cards. */
final class NativeCardDim implements View.OnAttachStateChangeListener {
    final Method added,removed;
    private final Field root,anchor,mask;
    private final Class<?> cardType,outlineType;
    private final Method cards,instant,currentInstant,expandedInstant;
    private final Set<FrameLayout> windows=Collections.newSetFromMap(new WeakHashMap<>());
    private final WeakHashMap<FrameLayout,WeakReference<State>> states=new WeakHashMap<>();
    java.util.function.Consumer<Throwable> failed=error->{};
    private boolean enabled;
    private int percent;
    NativeCardDim(NativeCapsuleAppearance.Lookup lookup)throws ReflectiveOperationException{
        Class<?> window,view;boolean legacy;
        try{window=lookup.load("com.oplus.systemui.plugins.seedling.card.window.i");view=lookup.load(NativeCameraCard.VIEWS+"e");legacy=false;}
        catch(ClassNotFoundException missing){window=lookup.load("l5.h");view=lookup.load("i5.d");legacy=true;}
        if(!FrameLayout.class.isAssignableFrom(view))throw new NoSuchMethodException("Card root type");
        root=Reflect.field(window,legacy?"f":"g",view);anchor=Reflect.field(view,legacy?"s":"h",Rect.class);
        cardType=lookup.load(NativeCameraCard.VIEWS+"CardView");
        Class<?> list=lookup.load(NativeCameraCard.VIEWS+"CardContainer"),capsule=lookup.load(NativeCameraCard.VIEWS+"InstantContainer");
        cards=Reflect.method(view,"getCardContainer",list);instant=Reflect.method(view,"getInstantContainer",capsule);
        currentInstant=Reflect.method(capsule,"getInstantView",View.class);expandedInstant=Reflect.method(capsule,"l",boolean.class);
        Class<?> outline=lookup.load(legacy?"j6.h":"com.oplus.systemui.plugins.common.util.j");
        outlineType=android.view.ViewOutlineProvider.class.isAssignableFrom(outline)?outline:PluginClasses.outline(cardType);
        Field outlineMask;
        try{outlineMask=Reflect.field(outlineType,legacy?"c":"a",Rect.class);}
        catch(NoSuchFieldException missing){outlineMask=Reflect.uniqueField(outlineType,field->Reflect.instance(field)&&field.getType()==Rect.class);}
        mask=outlineMask;
        added=Reflect.method(window,"a",void.class,boolean.class);removed=Reflect.method(window,"e",void.class);
    }
    void added(Object controller,boolean enabled,int percent){
        FrameLayout view=(FrameLayout)Reflect.get(root,controller);if(view==null)return;
        this.enabled=enabled;this.percent=percent;
        windows.add(view);view.addOnAttachStateChangeListener(this);update(view,enabled,percent);
    }
    void removed(Object controller){FrameLayout view=(FrameLayout)Reflect.get(root,controller);windows.remove(view);if(view!=null)view.removeOnAttachStateChangeListener(this);stop(view);}
    void refresh(boolean enabled,int percent){this.enabled=enabled;this.percent=percent;for(FrameLayout view:new ArrayList<>(windows))update(view,enabled,percent);}
    @Override public void onViewAttachedToWindow(View view){
        try{update((FrameLayout)view,enabled,percent);}catch(RuntimeException|LinkageError error){stop((FrameLayout)view);failed.accept(error);}
    }
    @Override public void onViewDetachedFromWindow(View view){stop((FrameLayout)view);}
    private void update(FrameLayout view,boolean enabled,int percent){
        if(!enabled||percent<=0){stop(view);return;}
        if(!view.isAttachedToWindow())return;
        State state=state(view);
        if(state==null){
            state=new State(view);states.put(view,new WeakReference<>(state));
            try{state.start();}catch(RuntimeException|LinkageError error){state.stop();throw error;}
        }
        state.percent=percent;state.sample();
    }
    private State state(FrameLayout view){var reference=states.get(view);return reference==null?null:reference.get();}
    private void stop(FrameLayout view){State state=state(view);if(state!=null)state.stop();states.remove(view);}
    private final class State implements ViewTreeObserver.OnPreDrawListener,View.OnAttachStateChangeListener {
        private final WeakReference<FrameLayout> owner;
        private final CardBackdropView backdrop;
        private WeakReference<View> outgoingInstant=new WeakReference<>(null);
        private int percent;
        private boolean stopped;
        State(FrameLayout view){owner=new WeakReference<>(view);backdrop=new CardBackdropView(view.getContext());}
        void start(){
            FrameLayout view=owner.get();if(view==null)return;
            view.addView(backdrop,0,new FrameLayout.LayoutParams(-1,-1));
            view.addOnAttachStateChangeListener(this);view.getViewTreeObserver().addOnPreDrawListener(this);
        }
        void sample(){
            FrameLayout view=owner.get();if(stopped||view==null)return;
            try{
                float value=0;
                if(view.getVisibility()==View.VISIBLE){
                    Rect camera=(Rect)Reflect.get(anchor,view);float diameter=Math.max(camera.width(),camera.height());
                    ViewGroup list=(ViewGroup)Reflect.call(cards,view);
                    if(list!=null)for(int i=0;i<list.getChildCount();i++){
                        View card=list.getChildAt(i);if(cardType.isInstance(card))value=Math.max(value,amount(card,view,diameter));
                    }
                    ViewGroup holder=(ViewGroup)Reflect.call(instant,view);
                    if(holder!=null){
                        View current=(View)Reflect.call(currentInstant,holder);
                        if((boolean)Reflect.call(expandedInstant,holder)&&current!=null)outgoingInstant=new WeakReference<>(current);
                        View outgoing=outgoingInstant.get();
                        if(outgoing!=null&&outgoing.getParent()==holder)value=Math.max(value,amount(outgoing,view,diameter));
                    }
                }
                backdrop.amount(value);
            }catch(RuntimeException|LinkageError error){stop();failed.accept(error);}
        }
        private float amount(View card,FrameLayout view,float diameter){
            float alpha=1;
            for(View part=card;part!=view;){
                if(part.getVisibility()!=View.VISIBLE)return 0;alpha*=part.getAlpha();
                if(!(part.getParent() instanceof View parent))return 0;part=parent;
            }
            Rect rect=null;var provider=card.getOutlineProvider();
            if(outlineType.isInstance(provider))rect=(Rect)Reflect.get(mask,provider);
            float width=rect==null?card.getWidth():rect.width();
            return CardDimProgress.alpha(width*Math.abs(card.getScaleX()),card.getWidth(),diameter,alpha,percent);
        }
        @Override public boolean onPreDraw(){sample();return true;}
        @Override public void onViewAttachedToWindow(View view){}
        @Override public void onViewDetachedFromWindow(View view){stop();}
        void stop(){
            if(stopped)return;stopped=true;
            FrameLayout view=owner.get();
            if(view!=null){
                if(state(view)==this)states.remove(view);
                view.removeOnAttachStateChangeListener(this);if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnPreDrawListener(this);
            }
            if(backdrop.getParent() instanceof ViewGroup parent)parent.removeView(backdrop);
        }
    }
}
