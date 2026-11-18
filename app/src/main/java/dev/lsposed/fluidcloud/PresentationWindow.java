package dev.lsposed.fluidcloud;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Executes coordinator commands; never decides scenes or starts native geometry animators. */
final class PresentationWindow {
    static final int STATUS_BAR_SUB_PANEL = 2017;
    static final int DISPLAY_OVERLAY = 2026;
    static final String TITLE = "ColorOSEnhance-Cloud";
    final NativeCapsuleSource source;
    private final NativeOutputGate gate = new NativeOutputGate();
    private final WindowManager manager;
    private final WindowManager.LayoutParams params;
    private final SurfaceView view;
    private final Region rawTouch = new Region(), touch = new Region();
    private final Path touchClip=new Path();
    private final Supplier<RectF> camera;
    private final BooleanSupplier allowed;
    private final Predicate<CloudPresentationCoordinator.Ticket> accepts;
    private final Runnable changed, pixels;
    private final Consumer<Throwable> failure;
    private final Object insets;
    private final Method removeInsets;
    private final ViewTreeObserver sourceObserver;
    private final ViewTreeObserver.OnPreDrawListener sourceDraw, readyDraw;
    private View shade;
    private boolean suppressShade, added, disposed, pending, dirty = true;
    private int busy;
    private long transferEpoch;
    private CloudPresentationCoordinator.Owner rendered = CloudPresentationCoordinator.Owner.NATIVE;
    private CloudPresentationCoordinator.Owner desired = CloudPresentationCoordinator.Owner.NATIVE;
    private CloudPresentationCoordinator.Ticket requestTicket;
    private Runnable ready;
    private CloudPresentationCoordinator.Frame frame;
    private CapsulePresentationGeometry.Geometry geometry;
    FrameSync frameSync = new NativeFrameSync();
    Runnable prepareContent = () -> { };
    Supplier<CloudCorners.Style> corners = () -> CloudCorners.Style.OEM;

    @SuppressLint({"WrongConstant","RtlHardcoded"})
    PresentationWindow(ViewGroup host, Supplier<RectF> camera, BooleanSupplier allowed,
            Predicate<CloudPresentationCoordinator.Ticket> accepts, Runnable changed, Runnable pixels,
            Consumer<Throwable> failure) throws ReflectiveOperationException {
        this(host,camera,allowed,accepts,changed,pixels,failure,false);
    }

    @SuppressLint({"WrongConstant","RtlHardcoded"})
    PresentationWindow(ViewGroup host, Supplier<RectF> camera, BooleanSupplier allowed,
            Predicate<CloudPresentationCoordinator.Ticket> accepts, Runnable changed, Runnable pixels,
            Consumer<Throwable> failure, boolean onTop) throws ReflectiveOperationException {
        source = new NativeCapsuleSource(host,true);source.independentHostPresentation=true;
        this.camera=camera;this.allowed=allowed;this.accepts=accepts;this.changed=changed;this.pixels=pixels;this.failure=failure;
        int type=onTop?DISPLAY_OVERLAY:STATUS_BAR_SUB_PANEL;
        Context context=host.getContext().createWindowContext(host.getDisplay(),type,null);
        manager=context.getSystemService(WindowManager.class);if(manager==null)throw new IllegalStateException("No window manager");
        view=new SurfaceView(context);view.setVisibility(View.INVISIBLE);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        params=new WindowManager.LayoutParams(-1,Math.max(1,host.getRootView().getHeight()),type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setTitle(TITLE);Reflect.call(Reflect.method(WindowManager.LayoutParams.class,"setFitInsetsTypes",void.class,int.class),params,0);
        Class<?> listener=Reflect.type(View.class.getClassLoader(),"android.view.ViewTreeObserver$OnComputeInternalInsetsListener");
        Class<?> info=Reflect.type(View.class.getClassLoader(),"android.view.ViewTreeObserver$InternalInsetsInfo");
        Method mode=Reflect.method(info,"setTouchableInsets",void.class,int.class);Field region=Reflect.field(info,"touchableRegion",Region.class);
        Method add=Reflect.method(ViewTreeObserver.class,"addOnComputeInternalInsetsListener",void.class,listener);
        removeInsets=Reflect.method(ViewTreeObserver.class,"removeOnComputeInternalInsetsListener",void.class,listener);
        insets=Reflect.listener(listener,(method,args)->{
            try {Reflect.call(mode,args[0],3);((Region)Reflect.get(region,args[0])).set(touch);}
            catch(RuntimeException|LinkageError error){fail(error);}
        });
        sourceObserver=host.getViewTreeObserver();
        sourceDraw=()->{
            if(!disposed && rendered==CloudPresentationCoordinator.Owner.MODULE && busy==0 && source.hasPendingPixels())pixels.run();
            return true;
        };
        readyDraw=()->{ try {transferIfReady();}catch(RuntimeException|LinkageError error){fail(error);}return true;};
        sourceObserver.addOnPreDrawListener(sourceDraw);
        view.getViewTreeObserver().addOnPreDrawListener(readyDraw);Reflect.call(add,view.getViewTreeObserver(),insets);
        view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(l!=ol || t!=ot || r!=or || b!=ob){dirty=true;changed.run();}});
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){changed.run();v.post(()->{if(!disposed){transferIfReady();changed.run();}});}
            public void onViewDetachedFromWindow(View v){changed.run();}
        });
        try {manager.addView(view,params);added=true;}catch(RuntimeException|LinkageError error){dispose();throw error;}
    }
    boolean isBusy(){return busy!=0;}
    boolean contentVisible(){return !disposed && rendered==CloudPresentationCoordinator.Owner.MODULE
            && view.getVisibility()==View.VISIBLE && frame!=null && frame.fraction()>.03;}
    void nativeTargets(View shade,boolean suppress){
        if(this.shade!=shade || suppressShade!=suppress)gate.restore(this.shade);
        this.shade=shade;suppressShade=suppress;
        if(rendered!=CloudPresentationCoordinator.Owner.NATIVE)hideNative();
    }
    boolean suppressesNativeOutput(Object target){return !disposed && rendered!=CloudPresentationCoordinator.Owner.NATIVE
            && (target==source.host || suppressShade && (target==shade || target instanceof View child
                && child.getParent()==shade && child.getClass().getSimpleName().equals("FakePluginFrame")));}
    void sourceChanged(boolean layout){dirty|=layout;if(layout)source.structureChanged();if(!disposed)pixels.run();}
    void prepare(){
        if(disposed || !allowed.getAsBoolean())return;
        busy++;
        try {
            if(dirty || source.host.isLayoutRequested()){dirty=false;source.structureChanged();prepareContent.run();}
            int height=source.viewportHeight(view,Math.max(view.getResources().getDisplayMetrics().heightPixels,source.host.getRootView().getHeight()));
            if(height>0 && params.height!=height){params.height=height;manager.updateViewLayout(view,params);}
            source.refresh(view,rawTouch);
            updateGeometry();
        }finally{busy--;}
    }
    void render(CloudPresentationCoordinator.Frame next){
        if(disposed || !accepts.test(next.ticket()))return;
        frame=next;updateGeometry();view.invalidate();
    }
    private void updateGeometry(){
        Region previous=new Region(touch);touch.setEmpty();
        geometry=frame==null?null:frame.pose()==null
                ?CapsulePresentationGeometry.resolve(new RectF(source.touchBounds()),camera.get(),frame.fraction())
                :CapsulePresentationGeometry.resolve(new RectF(source.touchBounds()),source.profile(view,camera.get()),frame.pose());
        if(rendered==CloudPresentationCoordinator.Owner.MODULE && geometry!=null && frame!=null && accepts.test(frame.ticket())){
            Path area=rawTouch.getBoundaryPath();area.transform(geometry.forward());
            RectF bounds=geometry.bounds();CloudCorners.Style style=corners.get();
            CloudCorners.path(touchClip,bounds,cornerRadius(bounds,style),style.continuous());
            Region clip=new Region();clip.setPath(touchClip,new Region(0,0,view.getWidth(),view.getHeight()));
            touch.setPath(area,clip);
        }
        if(!previous.equals(touch)){view.invalidate();var surface=view.getRootSurfaceControl();if(surface!=null)surface.setTouchableRegion(new Region(touch));}
    }
    private float cornerRadius(RectF bounds,CloudCorners.Style style) {
        float max=Math.min(bounds.width(),bounds.height())/2f;
        if(!style.enabled())return max;
        float target=style.radius(view.getResources().getDisplayMetrics().density,bounds.width(),bounds.height());
        return max+(target-max)*geometry.fraction();
    }
    void request(CloudPresentationCoordinator.Owner next,CloudPresentationCoordinator.Ticket ticket,Runnable committed){
        if(disposed)return;
        requestTicket=ticket;desired=next;ready=committed;
        if(next!=CloudPresentationCoordinator.Owner.MODULE && !source.hasHandedOffTouch())source.cancelTouch();
        transferIfReady();complete();
    }
    private void transferIfReady(){
        if(disposed || pending || rendered==desired || !allowed.getAsBoolean())return;
        // Collapsed and expanded module output share the same native gate. Switching
        // between them needs only the staged module buffer, never a second handoff.
        if(rendered!=CloudPresentationCoordinator.Owner.NATIVE && desired!=CloudPresentationCoordinator.Owner.NATIVE){
            rendered=desired;if(rendered==CloudPresentationCoordinator.Owner.HIDDEN){source.clearMorph();source.restoreVisibility();}
            updateGeometry();updateVisibility();view.invalidate();complete();return;
        }
        if(!frameSync.ready(source.host,view))return;
        if(desired==CloudPresentationCoordinator.Owner.MODULE){prepare();if(!source.hasContent())return;}
        long epoch=++transferEpoch;pending=true;busy++;
        // A deferred transaction takes the newest staged command at its first
        // mutation. Both roots then use that same command, including on reversal.
        CloudPresentationCoordinator.Owner[] target={null};
        Runnable select=()->{if(target[0]==null)target[0]=desired;};
        try {frameSync.transfer(source.host,shade,view,()->{
            if(disposed || epoch!=transferEpoch)return;select.run();
            if(target[0]==CloudPresentationCoordinator.Owner.NATIVE)gate.restore();else hideNative();
        },()->{
            if(disposed || epoch!=transferEpoch)return;select.run();
            rendered=target[0];view.setVisibility(View.VISIBLE);updateGeometry();view.invalidate();
        },()->{
            if(disposed || epoch!=transferEpoch)return;
            try {pending=false;if(!allowed.getAsBoolean()){release();return;}transferIfReady();updateVisibility();
                if(!pending && rendered!=CloudPresentationCoordinator.Owner.MODULE){source.clearMorph();source.restoreVisibility();}complete();}
            catch(RuntimeException|LinkageError error){fail(error);}
        });}finally{busy--;}
    }
    private void hideNative(){gate.hide(source.host);if(suppressShade)gate.hide(shade);}
    private void complete(){
        if(ready==null || pending || rendered!=desired || !accepts.test(requestTicket))return;
        Runnable callback=ready;ready=null;callback.run();
    }
    private void updateVisibility(){
        boolean gesture=source.hasHandedOffTouch() && allowed.getAsBoolean();
        view.setVisibility(rendered==CloudPresentationCoordinator.Owner.MODULE || pending || gesture?View.VISIBLE:View.INVISIBLE);
    }
    void cancelTouch(){source.cancelTouch();}
    void release(){
        if(disposed)return;
        transferEpoch++;pending=false;ready=null;rendered=desired=CloudPresentationCoordinator.Owner.NATIVE;
        frame=null;geometry=null;touch.setEmpty();rawTouch.setEmpty();
        Throwable error=null;
        try{source.cancelTouch();}catch(RuntimeException|LinkageError failure){error=failure;}
        try{source.clearMorph();}catch(RuntimeException|LinkageError failure){error=combine(error,failure);}
        try{source.restoreVisibility();}catch(RuntimeException|LinkageError failure){error=combine(error,failure);}
        try{gate.restore();}catch(RuntimeException|LinkageError failure){error=combine(error,failure);}
        try{view.setVisibility(View.INVISIBLE);view.requestLayout();var surface=view.getRootSurfaceControl();if(surface!=null)surface.setTouchableRegion(new Region());}
        catch(RuntimeException|LinkageError failure){error=combine(error,failure);}
        rethrow(error);
    }
    private void fail(Throwable error){
        try{release();}catch(RuntimeException|LinkageError cleanup){if(cleanup!=error)error.addSuppressed(cleanup);}
        try{failure.accept(error);}catch(RuntimeException|LinkageError ignored){}
    }
    void dispose(){
        if(disposed)return;
        Throwable error=null;
        try{release();}catch(RuntimeException|LinkageError failure){error=failure;}
        disposed=true;
        try{if(sourceObserver.isAlive())sourceObserver.removeOnPreDrawListener(sourceDraw);}
        catch(RuntimeException|LinkageError failure){error=combine(error,failure);}
        try{ViewTreeObserver observer=view.getViewTreeObserver();if(observer.isAlive()){observer.removeOnPreDrawListener(readyDraw);Reflect.call(removeInsets,observer,insets);}}
        catch(RuntimeException|LinkageError failure){error=combine(error,failure);}
        try{if(added){added=false;manager.removeViewImmediate(view);}}
        catch(RuntimeException|LinkageError failure){error=combine(error,failure);}
        rethrow(error);
    }
    private static Throwable combine(Throwable first,Throwable next){if(first==null)return next;if(first!=next)first.addSuppressed(next);return first;}
    private static void rethrow(Throwable error){if(error instanceof RuntimeException runtime)throw runtime;if(error instanceof LinkageError linkage)throw linkage;}
    private final class SurfaceView extends View {
        private final Paint black=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path animationClip=new Path();
        SurfaceView(Context context){super(context);black.setColor(Color.BLACK);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(disposed || rendered!=CloudPresentationCoordinator.Owner.MODULE || frame==null || !accepts.test(frame.ticket()) || geometry==null)return;
            int save=canvas.save();busy++;
            try {
                source.presentVisibility();
                boolean morph=frame.pose()==null?geometry.fraction()<.999f:!frame.pose().equals(CapsuleMotion.FULL);
                if(morph){
                    int clipSave=canvas.save();
                    RectF bounds=geometry.bounds();
                    CloudCorners.Style style=corners.get();float radius=cornerRadius(bounds,style);
                    CloudCorners.path(animationClip,bounds,radius,style.continuous());
                    canvas.clipPath(animationClip);
                    boolean nativeParts=frame.pose()!=null && source.drawMorph(canvas,view,geometry,style.enabled()?radius:-1f);
                    if(!nativeParts){
                        canvas.drawPath(animationClip,black);
                        canvas.saveLayerAlpha(bounds,Math.round(255*geometry.contentAlpha()));
                        canvas.concat(geometry.contentForward());source.draw(canvas);
                    }
                    canvas.restoreToCount(clipSave);
                    if(nativeParts)source.drawMorphDecorations(canvas,view,geometry);
                }else{
                    source.clearMorph();canvas.concat(geometry.contentForward());source.draw(canvas);
                }
                // Animated provider drawables still advance their native clock when
                // the status-bar root is hidden, but that root no longer requests
                // buffers. Only their live output needs another module draw; static
                // content and ended animations do not create a permanent frame loop.
                if(source.hasRunningAnimation())postInvalidateOnAnimation();
            }catch(RuntimeException|LinkageError error){fail(error);}finally{busy--;canvas.restoreToCount(save);}
        }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event){
            if(disposed)return false;
            try {
                if(!allowed.getAsBoolean()){release();return false;}
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN && (rendered!=CloudPresentationCoordinator.Owner.MODULE || geometry==null || frame==null || !accepts.test(frame.ticket()) || !touch.contains((int)event.getX(),(int)event.getY())))return false;
                MotionEvent copy=MotionEvent.obtain(event);
                try {if(geometry!=null)copy.transform(geometry.inverse());return source.dispatchTouchEvent(copy);}
                finally{copy.recycle();updateVisibility();changed.run();}
            }catch(RuntimeException|LinkageError error){fail(error);return false;}
        }
    }
}
