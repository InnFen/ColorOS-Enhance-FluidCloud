package dev.howard.colorosenhance.icons;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Repositions the existing HOME views. No reparenting, holder reindexing, or duplicate binders. */
final class HomeIconOrder {
    record Target(float x,boolean visible){}
    private final Supplier<StatusIconConfig> config;
    private final Consumer<Throwable> failure;
    private final Map<ViewGroup,NativeIconPolicy> groups=new WeakHashMap<>();
    private final Map<ViewGroup,State> roots=new WeakHashMap<>();
    private final Map<View,WeakReference<Source>> owned=new WeakHashMap<>();
    private final Method updateStates;
    private final java.util.function.BiFunction<View,String,View> finder;
    private Runnable pixels=()->{};
    HomeIconOrder(Supplier<StatusIconConfig> config,Method updateStates,Consumer<Throwable> failure){
        this(config,updateStates,failure,HomeIconOrder::nativeFind);
    }
    HomeIconOrder(Supplier<StatusIconConfig> config,Method updateStates,Consumer<Throwable> failure,java.util.function.BiFunction<View,String,View> finder){
        this.config=config;this.updateStates=updateStates;this.failure=failure;this.finder=finder;
    }
    void root(ViewGroup root){
        if(roots.containsKey(root))return;State state=new State(root);roots.put(root,state);
        root.addOnAttachStateChangeListener(state);
        if(root.isAttachedToWindow())state.attach();
    }
    void group(ViewGroup group,NativeIconPolicy policy){groups.put(group,policy);for(State state:roots.values()){state.dirty=true;state.failed=false;}}
    void pixels(Runnable changed){pixels=changed;}
    void refresh(){for(State state:new ArrayList<>(roots.values())){state.dirty=true;state.update();}}
    Target target(View view){
        if(!config.get().ordersHome())return null;
        Source source=source(view);if(source==null||!source.icon)return null;
        State state=source.owner.get();if(state==null||!state.active)return null;
        if(state.dirty)state.prepare();
        if(!state.active || !source.eligible)return null;
        IconOrderPlan.Position position=state.plan.get(source.id);
        if(position==null)return new Target(0,false);
        ViewGroup root=state.root.get();
        if(root==null || !(view.getParent() instanceof View parent))return null;
        return new Target(position.left()+position.compactDx()+source.leading-origin(parent,root),true);
    }
    Float offset(View view,float fraction){
        Source source=source(view);if(source==null)return null;
        State state=source.owner.get();if(state==null||!state.active)return null;
        float p=Float.isFinite(fraction)?Math.max(0,Math.min(1,fraction)):0;
        if(!source.icon)return 0f; // Independent OEM clock/battery/notification mirrors use the real compact source position.
        var position=state.plan.get(source.id);View child=source.view.get();ViewGroup root=state.root.get();
        if(position==null || child==null || root==null)return 0f;
        float current=origin(child,root);
        if(!Float.isFinite(current))return 0f;
        // Native X animators can still be moving from the previous order/width. Draw
        // at the single presentation target instead of adding compaction a second time.
        return position.left()+position.compactDx()*(1-p)+source.leading-current;
    }
    Float alpha(View view){
        Source source=source(view);if(source==null||source.notification||!source.eligible)return null;
        State state=source.owner.get();return state==null||!state.active?null:state.plan.containsKey(source.id)?1f:0f;
    }
    Float mirroredOffset(View view,float fraction){
        for(View part=view;part!=null;part=part.getParent() instanceof View parent?parent:null){
            Float offset=offset(part,fraction);if(offset!=null)return offset;
        }
        return null;
    }
    boolean containsOrderedGroup(View branch){
        if(!config.get().ordersHome())return false;
        for(State state:roots.values())if(state.active)for(ViewGroup group:state.nativeGroups)
            if(descendant(group,branch))return true;
        return false;
    }
    private Source source(View view){var reference=owned.get(view);return reference==null?null:reference.get();}
    private static final class Source {
        final WeakReference<View> view;
        WeakReference<View> content;
        final WeakReference<State> owner;
        final int id;
        final String slot;
        final boolean icon,notification;
        final Method visible,blocked;
        boolean eligible,translated;
        float nativeX,lastX,leading,width,lastOffset=Float.NaN;
        Rect nativeClip,lastClip;
        boolean clipped;
        Source(View view,State state,int id,String slot,boolean icon,boolean notification)throws ReflectiveOperationException{
            this.view=new WeakReference<>(view);content=this.view;owner=new WeakReference<>(state);this.id=id;this.slot=slot;this.icon=icon;this.notification=notification;
            visible=icon?IconReflection.method(view.getClass(),"isIconVisible"):null;
            blocked=icon?IconReflection.method(view.getClass(),"isIconBlocked"):null;
        }
        void translate(float x){
            View target=view.get();if(target==null)return;
            if(!translated || Float.compare(target.getTranslationX(),lastX)!=0)nativeX=target.getTranslationX();
            lastX=x;translated=true;if(Float.compare(target.getTranslationX(),x)!=0)target.setTranslationX(x);
        }
        void restore(){
            View target=view.get();if(target!=null&&translated&&Float.compare(target.getTranslationX(),lastX)==0)target.setTranslationX(nativeX);
            translated=false;
            if(target!=null&&clipped&&Objects.equals(target.getClipBounds(),lastClip))target.setClipBounds(nativeClip);
            clipped=false;
        }
        void clip(float width){
            View target=view.get();if(target==null)return;
            Rect current=target.getClipBounds();if(!clipped||!Objects.equals(current,lastClip))nativeClip=current==null?null:new Rect(current);
            Rect clip=new Rect(0,0,Math.max(0,Math.min(target.getWidth(),Math.round(width))),target.getHeight());
            if(nativeClip!=null&&!clip.intersect(nativeClip))clip.setEmpty();
            lastClip=clip;clipped=true;if(!Objects.equals(current,clip))target.setClipBounds(clip);
        }
    }
    private final class State implements View.OnAttachStateChangeListener,ViewTreeObserver.OnPreDrawListener,ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<ViewGroup> root;
        final List<Source> sources=new ArrayList<>();
        final Map<ViewGroup,boolean[]> clips=new WeakHashMap<>();
        final Set<ViewGroup> nativeGroups=Collections.newSetFromMap(new WeakHashMap<>());
        Map<Integer,IconOrderPlan.Position> plan=Map.of();
        List<IconOrderPlan.Cell> lastCells=List.of();
        IconOrderPlan.Bounds lastBounds;
        IconOrder lastOrder;
        boolean active,dirty=true,attached,applying,failed;
        int nextId;
        WeakReference<View> capsule=new WeakReference<>(null);
        WeakReference<View> leftSide=new WeakReference<>(null),rightSide=new WeakReference<>(null);
        boolean searchCapsule=true;
        State(ViewGroup root){this.root=new WeakReference<>(root);}
        void attach(){
            ViewGroup view=root.get();if(view==null||attached)return;attached=true;
            view.getViewTreeObserver().addOnPreDrawListener(this);view.getViewTreeObserver().addOnGlobalLayoutListener(this);dirty=true;
        }
        @Override public void onViewAttachedToWindow(View view){attach();}
        @Override public void onViewDetachedFromWindow(View view){
            attached=false;if(view.getViewTreeObserver().isAlive()){view.getViewTreeObserver().removeOnPreDrawListener(this);view.getViewTreeObserver().removeOnGlobalLayoutListener(this);}release();
        }
        @Override public void onGlobalLayout(){searchCapsule=true;dirty=true;}
        @Override public boolean onPreDraw(){update();return true;}
        void update(){
            if(applying)return;
            ViewGroup view=root.get();if(view==null)return;
            if(!config.get().ordersHome() || failed){if(active)release();return;}
            try{
                boolean entering=!active;
                Map<Integer,IconOrderPlan.Position> previous=plan;prepare();if(!active)return;
                applySpecials();
                if(!previous.equals(plan)){
                    if(android.util.Log.isLoggable("COSIconOrder",android.util.Log.DEBUG)){
                        var positions=new ArrayList<String>();
                        for(Source source:sources){var target=plan.get(source.id);if(target!=null)positions.add(source.slot+"="+Math.round(target.left()+target.compactDx())+(target.onLeft()?"L":"R"));}
                        android.util.Log.d("COSIconOrder","width="+view.getWidth()+" "+positions);
                    }
                    updateNative(entering);
                }
                boolean moved=!previous.equals(plan);
                for(Source source:sources)if(source.icon){
                    Float offset=HomeIconOrder.this.offset(source.view.get(),0);
                    float value=offset==null?0:offset;
                    if(Float.compare(value,source.lastOffset)!=0){source.lastOffset=value;moved=true;}
                }
                if(moved)pixels.run();
            }catch(ReflectiveOperationException|RuntimeException error){failed=true;release();failure.accept(error);}
        }
        void prepare(){
            ViewGroup view=root.get();if(view==null||view.getWidth()<=0)return;
            if(dirty)rebuild(view);
            if(nativeGroups.isEmpty())return;
            for(ViewGroup group:nativeGroups)if(!groups.get(group).known()){release();dirty=false;return;}
            active=true;
            List<IconOrderPlan.Cell> cells=new ArrayList<>();
            for(Source source:sources){
                View child=source.view.get(),content=source.content.get();source.eligible=false;
                if(child==null||content==null||child.getWidth()<=0||child.getVisibility()!=View.VISIBLE||content.getVisibility()!=View.VISIBLE)continue;
                try{
                    if(source.icon){
                        ViewGroup parent=(ViewGroup)child.getParent();NativeIconPolicy policy=groups.get(parent);
                        source.eligible=policy!=null&&policy.allows(source.slot)&&Boolean.TRUE.equals(source.visible.invoke(child))
                                &&!Boolean.TRUE.equals(source.blocked.invoke(child));
                    }else source.eligible=true;
                }catch(ReflectiveOperationException|RuntimeException missing){source.eligible=false;}
                if(!source.eligible||source.notification)continue;
                source.leading=source.icon?child.getPaddingStart():0;
                source.width=child.getWidth()+(source.icon?child.getPaddingStart()+child.getPaddingEnd():0);
                cells.add(new IconOrderPlan.Cell(source.id,source.slot,source.width,config.get().shadeOnly().contains(source.slot)));
            }
            var bounds=bounds(view);if(bounds==null){failed=true;release();failure.accept(new IllegalStateException("Native status bar side containers unavailable"));return;}
            IconOrder order=config.get().order();
            List<String> available=cells.stream().map(IconOrderPlan.Cell::slot).toList();
            if(StatusIconCatalog.usesColorOsRingerSlot(available) && !available.contains("mute"))order=order.ringerSlot();
            if(!cells.equals(lastCells)||!bounds.equals(lastBounds)||!order.equals(lastOrder)){
                plan=IconOrderPlan.create(order,cells,bounds);lastCells=List.copyOf(cells);lastBounds=bounds;lastOrder=order;
            }
            for(Source source:sources)if(source.eligible)relax(source.view.get(),view);
        }
        Map<View,Source> rebuilding;
        void rebuild(ViewGroup view){
            rebuilding=new IdentityHashMap<>();
            for(Source source:sources){View child=source.view.get();if(child!=null)rebuilding.put(child,source);}
            sources.clear();nativeGroups.clear();
            try{
                for(var entry:new ArrayList<>(groups.entrySet())){
                    ViewGroup group=entry.getKey();if(!descendant(group,view))continue;nativeGroups.add(group);
                    for(int i=0;i<group.getChildCount();i++){
                        View child=group.getChildAt(i);Object slot=IconReflection.call(child,"getSlot");
                        if(slot instanceof String name&&StatusIconConfig.validSlot(name))add(child,name,true,false);
                    }
                }
                View clock=find(view,"clock"),battery=find(view,"battery"),notification=find(view,"notification_icon_area");
                leftSide=new WeakReference<>(find(view,"status_bar_start_side_container"));rightSide=new WeakReference<>(find(view,"status_bar_end_side_container"));
                if(clock!=null)add(branch(clock),"clock",false,false).content=new WeakReference<>(clock);
                if(battery!=null)add(branch(battery),"battery",false,false).content=new WeakReference<>(battery);
                if(notification!=null)add(notification,"notifications",false,true);
                capsule=new WeakReference<>(findType(view,"CapsuleView"));searchCapsule=false;
                dirty=false;
            }catch(ReflectiveOperationException error){throw new IllegalStateException("Native icon source",error);}
            finally{
                for(Source removed:rebuilding.values()){
                    View child=removed.view.get();if(child!=null)owned.remove(child);removed.restore();
                }
                rebuilding=null;
            }
        }
        Source add(View child,String slot,boolean icon,boolean notification)throws ReflectiveOperationException{
            Source source=rebuilding==null?null:rebuilding.remove(child);
            if(source==null || !source.slot.equals(slot) || source.icon!=icon || source.notification!=notification){
                if(source!=null)source.restore();
                source=new Source(child,this,nextId++,slot,icon,notification);
            }
            sources.add(source);owned.put(child,new WeakReference<>(source));
            return source;
        }
        void updateNative(boolean atomic)throws ReflectiveOperationException{
            applying=true;
            try{for(ViewGroup group:new ArrayList<>(nativeGroups)){
                java.lang.reflect.Field animation=null;boolean previous=false;
                if(atomic)try{
                    for(Class<?> type=group.getClass();type!=null;type=type.getSuperclass())try{
                        animation=type.getDeclaredField("mIsEnableAnimation");animation.setAccessible(true);break;
                    }catch(NoSuchFieldException absent){ }
                    if(animation!=null){previous=animation.getBoolean(group);animation.setBoolean(group,false);}
                }catch(IllegalAccessException unavailable){animation=null;}
                try{updateStates.invoke(group);}
                finally{if(animation!=null)animation.setBoolean(group,previous);}
            }}finally{applying=false;}
        }
        IconOrderPlan.Bounds bounds(ViewGroup view){
            View left=leftSide.get(),right=rightSide.get();
            if(left==null||right==null)return null;
            float start=origin(left,view)+left.getPaddingLeft(),end=origin(right,view)+right.getWidth()-right.getPaddingRight();
            float gap=4*view.getResources().getDisplayMetrics().density;
            float centerLeft=view.getWidth()/2f-gap,centerRight=view.getWidth()/2f+gap;
            var insets=view.getRootWindowInsets();
            if(insets!=null&&insets.getDisplayCutout()!=null)for(Rect cutout:insets.getDisplayCutout().getBoundingRects())
                if(cutout.top<view.getHeight()&&cutout.centerX()>view.getWidth()/4&&cutout.centerX()<view.getWidth()*3/4){
                    centerLeft=Math.min(centerLeft,cutout.left-gap);centerRight=Math.max(centerRight,cutout.right+gap);
                }
            View cloud=capsule.get();
            if(searchCapsule || (cloud!=null&&!descendant(cloud,view))){cloud=findType(view,"CapsuleView");capsule=new WeakReference<>(cloud);searchCapsule=false;}
            if(cloud!=null&&cloud.getWidth()>0){
                float x=origin(cloud,view);
                if(x+cloud.getWidth()>view.getWidth()/2f-gap&&x<view.getWidth()/2f+gap){
                    centerLeft=Math.min(centerLeft,x-gap);centerRight=Math.max(centerRight,x+cloud.getWidth()+gap);
                }
            }
            return new IconOrderPlan.Bounds(start,Math.max(start,centerLeft),Math.min(end,centerRight),end);
        }
        void applySpecials(){
            ViewGroup view=root.get();if(view==null)return;
            float leftEnd=0,removed=0;boolean anyLeft=false;
            for(Source source:sources){
                View child=source.view.get();if(child==null||source.icon||source.notification)continue;
                var position=plan.get(source.id);
                if(position==null){source.restore();continue;}
                float base=layoutOrigin(child,view);
                source.translate(position.left()+position.compactDx()-base);
            }
            for(Source source:sources){var position=plan.get(source.id);if(position!=null&&position.onLeft()){
                anyLeft=true;leftEnd=Math.max(leftEnd,position.left()+source.width);
                if(config.get().shadeOnly().contains(source.slot))removed+=source.width;
            }}
            for(Source source:sources)if(source.notification){
                View child=source.view.get();if(child==null)continue;
                var bounds=bounds(view);if(bounds==null)continue;
                float x=anyLeft?leftEnd-removed:bounds.leftStart();source.translate(x-layoutOrigin(child,view));
                source.clip(bounds.leftEnd()-x);
            }
        }
        void relax(View child,ViewGroup root){
            if(child==null)return;
            for(View part=child;part.getParent() instanceof ViewGroup parent;part=parent){
                clips.computeIfAbsent(parent,key->new boolean[]{key.getClipChildren(),key.getClipToPadding()});
                if(parent.getClipChildren())parent.setClipChildren(false);if(parent.getClipToPadding())parent.setClipToPadding(false);
                if(parent==root)break;
            }
        }
        void release(){
            boolean wasActive=active;active=false;plan=Map.of();dirty=true;
            lastCells=List.of();lastBounds=null;lastOrder=null;
            for(Source source:sources)source.restore();
            for(var entry:new ArrayList<>(clips.entrySet())){
                if(!entry.getKey().getClipChildren())entry.getKey().setClipChildren(entry.getValue()[0]);
                if(!entry.getKey().getClipToPadding())entry.getKey().setClipToPadding(entry.getValue()[1]);
            }
            clips.clear();if(wasActive){
                // Drop ordered drawing only after native coordinates have been restored
                // in the same main-thread turn, not halfway through a native X animation.
                try{updateNative(true);}catch(ReflectiveOperationException|RuntimeException error){failure.accept(error);}
                for(ViewGroup group:new ArrayList<>(nativeGroups))group.requestLayout();
                pixels.run();
            }
        }
    }
    private static View branch(View child){
        View target=child;
        while(target.getParent() instanceof ViewGroup parent){
            if(parent instanceof android.widget.LinearLayout layout && layout.getOrientation()==android.widget.LinearLayout.HORIZONTAL)return target;
            target=parent;
        }
        return child;
    }
    private static boolean descendant(View child,View root){for(View part=child;part!=null;part=part.getParent() instanceof View parent?parent:null)if(part==root)return true;return false;}
    private static View findType(View view,String simple){
        if(view.getClass().getSimpleName().equals(simple))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findType(group.getChildAt(i),simple);if(found!=null)return found;}
        return null;
    }
    @android.annotation.SuppressLint("DiscouragedApi")
    private static View nativeFind(View root,String name){int id=root.getResources().getIdentifier(name,"id","com.android.systemui");return id==0?null:root.findViewById(id);}
    private View find(View root,String name){
        // Plugin resources have their own 0x7f IDs. Never resolve a SystemUI ID in that subtree.
        View contents=root.findViewWithTag("status_bar_contents");
        return finder.apply(contents==null?root:contents,name);
    }
    private static float origin(View view,View root){
        float x=0;for(View part=view;part!=root;){
            x+=part.getLeft()+part.getTranslationX();if(!(part.getParent() instanceof View parent))return Float.NaN;
            x-=parent.getScrollX();part=parent;
        }return x;
    }
    private static float layoutOrigin(View view,View root){return origin(view,root)-view.getTranslationX();}
}
