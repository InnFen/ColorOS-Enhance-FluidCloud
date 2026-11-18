package dev.lsposed.fluidcloud;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RenderNode;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Animatable;
import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.Optional;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;

/** The existing native host is retained; this object never owns its lifecycle. */
final class NativeCapsuleSource {
    final ViewGroup host;
    private final boolean independentPlacement;
    boolean independentHostPresentation;
    private final Matrix toWindow = new Matrix();
    private final Matrix toHost = new Matrix();
    private final Region touchable = new Region();
    private final Region recovery = new Region();
    private int recoveryWidth,recoveryHeight;
    BooleanSupplier recoverBlank = () -> false;
    BooleanSupplier retainRecovery = () -> false;
    private final RectF drawBounds = new RectF();
    private final List<Path> ancestorClips = new ArrayList<>();
    private boolean content;
    private MotionEvent lastTouch;
    private int touchDispatchDepth;
    private boolean handedOff, cancelPending;
    private final List<View> capsules=new ArrayList<>();
    private final List<View> animationViews=new ArrayList<>();
    private final List<View> animationRoots=new ArrayList<>();
    private final NativeContentVisibility presentationVisibility=new NativeContentVisibility();
    private boolean animationsDirty=true;
    private final Map<Class<?>,Optional<Anchor>> anchors=new HashMap<>();
    private final Map<Resources,Dimensions> dimensions=new WeakHashMap<>();
    private final Map<View,Skin> skins=new WeakHashMap<>();
    private final Map<Class<?>,List<Method>> moons=new HashMap<>();
    private record Anchor(Method method,Field first,Field second) { }
    private record Dimensions(int density,float extra,float blur) { }

    NativeCapsuleSource(ViewGroup host) { this(host, false); }
    NativeCapsuleSource(ViewGroup host, boolean independentPlacement) {
        this.host = host;
        this.independentPlacement = independentPlacement;
    }
    boolean hasPendingPixels() { return host.isLayoutRequested() || host.isDirty() || dirty(host); }
    private boolean dirty(View view) {
        if (view.getVisibility() != View.VISIBLE && !(view == host && independentHostPresentation)) return false;
        if (view.isLayoutRequested()) return true;
        if (view.isDirty()) return true;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            if (dirty(group.getChildAt(i))) return true;
        return false;
    }
    String debugState() {
        StringBuilder out = new StringBuilder("content=").append(content).append(" touch=")
                .append(lastTouch == null ? -1 : lastTouch.getActionMasked());
        describe(host, out, 0, new int[]{40}); return out.toString();
    }
    private static void describe(View view, StringBuilder out, int depth, int[] left) {
        if (depth > 7 || left[0]-- <= 0) return;
        out.append(' ').append(view.getClass().getSimpleName()).append('[').append(view.getVisibility())
                .append('/').append(view.getAlpha()).append(' ').append(view.getWidth()).append('x').append(view.getHeight())
                .append(" s=").append(view.getScaleX()).append('/').append(view.getScaleY()).append(']');
        if(view instanceof ImageView image && image.getDrawable()!=null){
            Drawable drawable=image.getDrawable();out.append(" drawable=").append(drawable.getClass().getSimpleName());
            if(drawable instanceof Animatable animation)out.append(" running=").append(animation.isRunning());
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) describe(group.getChildAt(i),out,depth+1,left);
    }

    void refresh(View window, Region region) {
        content = false;
        capsules.clear();
        touchable.setEmpty();
        ancestorClips.clear();
        float opacity = 1f;
        if (host.getWidth() <= 0 || host.getHeight() <= 0) {
            recovery.setEmpty();
            region.setEmpty();
            return;
        }
        // A hidden ViewRoot may defer this traversal. Keep the native host's constraints
        // and bounds, while allowing its children and their layout callbacks to advance.
        if (host.isLayoutRequested()) {
            animationsDirty=true;
            int l = host.getLeft(), t = host.getTop(), r = host.getRight(), b = host.getBottom();
            host.measure(View.MeasureSpec.makeMeasureSpec(r - l, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(b - t, View.MeasureSpec.EXACTLY));
            host.layout(l, t, r, b);
        }
        toWindow.set(matrixInWindow(host, window));
        if (!toWindow.invert(toHost)) {
            recovery.setEmpty();
            region.setEmpty();
            return;
        }
        Region clip = new Region(0, 0, window.getWidth(), window.getHeight());
        for (View v = host; v != null; v = parent(v)) {
            if (!(v == host && independentHostPresentation)) {
                if (v.getVisibility() != View.VISIBLE) opacity = 0;
                opacity *= v.getAlpha();
            }
            if (parent(v) instanceof ViewGroup group && group.getClipChildren()) {
                ancestorClip(v, window, new RectF(0, 0, v.getWidth(), v.getHeight()), clip);
            }
            Rect explicit = v.getClipBounds();
            if (explicit != null) ancestorClip(v, window, new RectF(explicit), clip);
            // host.draw already applies its own dispatchDraw padding clip.
            if (v != host && v instanceof ViewGroup group && clipsPadding(group)) {
                ancestorClip(v, window, paddingBounds(v), clip);
            }
            if (independentPlacement) break; // Bar ancestors do not own this presentation surface.
        }
        drawBounds.set(clip.getBounds());
        if (opacity > 0 && !clip.isEmpty()) collect(host, window, clip, 1f);
        if(animationsDirty || !animationRoots.equals(capsules)){
            animationRoots.clear();animationRoots.addAll(capsules);animationViews.clear();
            for(View capsule:capsules)collectAnimations(capsule);
            animationsDirty=false;
        }
        boolean retain=opacity>0 && !clip.isEmpty() && retainRecovery.getAsBoolean();
        boolean sameSize=recoveryWidth==window.getWidth() && recoveryHeight==window.getHeight();
        if(!touchable.isEmpty()){
            if(retain && sameSize)recovery.op(touchable,Region.Op.UNION);
            else recovery.set(touchable);
            recoveryWidth=window.getWidth();recoveryHeight=window.getHeight();
        }else if(opacity>0 && !clip.isEmpty() && recoverBlank.getAsBoolean()
                && recoveryWidth==window.getWidth() && recoveryHeight==window.getHeight()){
            // The native list still has items, but its selected slot is blank. Keep only
            // the last capsule's bounds as a swipe entry; draw remains the native blank slot.
            touchable.set(recovery);touchable.op(clip,Region.Op.INTERSECT);
        }else if(!retain || !sameSize)recovery.setEmpty();
        content = !touchable.isEmpty();
        region.set(touchable);
    }

    private void ancestorClip(View view, View window, RectF bounds, Region clip) {
        Path path = mappedPath(view, window, bounds);
        ancestorClips.add(path);
        Region area = new Region();
        area.setPath(path, clip);
        clip.set(area);
    }

    private void collect(View view, View window, Region clip, float alpha) {
        if (!(view == host && independentHostPresentation)) {
            if (view.getVisibility() != View.VISIBLE) return;
            alpha *= view.getAlpha();
        }
        if (alpha <= 0) return;
        RectF bounds = new RectF(0, 0, view.getWidth(), view.getHeight());
        Region childClip = new Region(clip);
        if (parent(view) instanceof ViewGroup group && group.getClipChildren()) {
            intersect(view, window, bounds, childClip);
        }
        Rect explicit = view.getClipBounds();
        if (explicit != null) intersect(view, window, new RectF(explicit), childClip);
        if (childClip.isEmpty()) return;
        if (isCapsule(view)) {
            Region area = new Region(childClip);
            intersect(view, window, bounds, area);
            touchable.op(area, Region.Op.UNION);
            if(!area.isEmpty())capsules.add(view);
            // All descendant regions can only shrink childClip. Once it is covered,
            // walking text/Lottie/template children cannot add any touchable pixels.
            if (area.equals(childClip)) return;
        }
        if (view instanceof ViewGroup group) {
            if (clipsPadding(group)) intersect(view, window, paddingBounds(view), childClip);
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), window, childClip, alpha);
            }
        }
    }

    private boolean isCapsule(View view) {
        if (view.getClass().getSimpleName().equals("CapsuleView")) return true;
        if (view.getId() == View.NO_ID) return false;
        try {
            return view.getResources().getResourceEntryName(view.getId()).equals("mimi_view");
        } catch (android.content.res.Resources.NotFoundException ignored) {
            return false;
        }
    }

    private Matrix matrixInWindow(View view, View window) {
        Matrix result = new Matrix();
        if (independentPlacement) {
            int[] sourceOrigin = windowOrigin(host);
            int[] destinationOrigin = windowOrigin(window);
            result.preTranslate(sourceOrigin[0] - destinationOrigin[0], sourceOrigin[1] - destinationOrigin[1]);
            List<View> path = new ArrayList<>();
            for (View current = view; current != null; current = parent(current)) path.add(current);
            boolean nativeContent = false;
            for (int i = path.size() - 1; i >= 0; i--) {
                View current = path.get(i);
                View p = parent(current);
                result.preTranslate(current.getLeft() - (p == null ? 0 : p.getScrollX()),
                        current.getTop() - (p == null ? 0 : p.getScrollY()));
                nativeContent |= current == host;
                if (nativeContent && !(current == host && independentHostPresentation)) result.preConcat(current.getMatrix());
            }
            return result;
        }
        view.transformMatrixToGlobal(result);
        window.transformMatrixToLocal(result);
        return result;
    }

    private static int[] windowOrigin(View view) {
        int[] screen = new int[2], local = new int[2];
        View root = view.getRootView();
        root.getLocationOnScreen(screen);
        root.getLocationInWindow(local);
        return new int[]{screen[0] - local[0], screen[1] - local[1]};
    }

    private static RectF paddingBounds(View view) {
        return new RectF(view.getPaddingLeft(), view.getPaddingTop(),
                view.getWidth() - view.getPaddingRight(), view.getHeight() - view.getPaddingBottom());
    }

    private static boolean clipsPadding(ViewGroup view) {
        return view.getClipToPadding() && (view.getPaddingLeft() != 0 || view.getPaddingTop() != 0
                || view.getPaddingRight() != 0 || view.getPaddingBottom() != 0);
    }

    private Path mappedPath(View view, View window, RectF bounds) {
        Path path = new Path();
        path.addRect(bounds, Path.Direction.CW);
        path.transform(matrixInWindow(view, window));
        return path;
    }

    private void intersect(View view, View window, RectF bounds, Region clip) {
        Region area = new Region();
        area.setPath(mappedPath(view, window, bounds), clip);
        clip.set(area);
    }

    private static View parent(View view) {
        return view.getParent() instanceof View v ? v : null;
    }

    boolean hasContent() { return content; }
    void presentVisibility(){if(independentHostPresentation)presentationVisibility.present(host);}
    void restoreVisibility(){presentationVisibility.restore();}
    void structureChanged(){animationsDirty=true;}
    boolean hasRunningAnimation(){
        if(!content)return false;
        for(View view:animationViews){
            if(!view.isAttachedToWindow() || view.getVisibility()!=View.VISIBLE || view.getAlpha()<=0)continue;
            if(running(view.getBackground()) || running(view.getForeground())
                    || view instanceof ImageView image && running(image.getDrawable()))return true;
        }
        return false;
    }
    private void collectAnimations(View view){
        if(view.getVisibility()!=View.VISIBLE || view.getAlpha()<=0)return;
        animationViews.add(view);
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)collectAnimations(group.getChildAt(i));
    }
    private static boolean running(Drawable drawable){return drawable instanceof Animatable animation && animation.isRunning();}
    Rect touchBounds() { return touchable.getBounds(); }
    @android.annotation.SuppressLint("DiscouragedApi")
    CapsulePresentationGeometry.Profile profile(View window,RectF fallback){
        if(capsules.isEmpty())return fallbackProfile(fallback);
        View capsule=capsules.getFirst();View container=parent(capsule);
        Optional<Anchor> shape=container==null?Optional.empty():anchors.computeIfAbsent(container.getClass(),NativeCapsuleSource::anchor);
        RectF dot=null;
        if(shape.isPresent()){
            Anchor contract=shape.get();Object pair=Reflect.call(contract.method(),container,capsule.getMeasuredHeight());
            Object a=Reflect.get(contract.first(),pair),b=Reflect.get(contract.second(),pair);
            if(a instanceof Number top && b instanceof Number bottom){
                float height=bottom.floatValue()-top.floatValue();
                if(height>0 && height<=capsule.getMeasuredHeight()*2){
                    dot=new RectF((container.getWidth()-height)/2,top.floatValue(),(container.getWidth()+height)/2,bottom.floatValue());
                    matrixInWindow(container,window).mapRect(dot);
                }
            }
        }
        if(dot==null)return fallbackProfile(fallback);
        Resources resources=capsule.getResources();int density=resources.getDisplayMetrics().densityDpi;
        Dimensions values=dimensions.get(resources);
        if(values==null || values.density()!=density){
            int id=resources.getIdentifier("capsule_appear_extra_expansion_width","dimen","com.oplus.systemui.plugins");
            values=new Dimensions(density,id==0?0:resources.getDimensionPixelSize(id),9*resources.getDisplayMetrics().density);
            dimensions.put(resources,values);
        }
        return new CapsulePresentationGeometry.Profile(dot,values.extra(),values.blur());
    }
    private CapsulePresentationGeometry.Profile fallbackProfile(RectF camera){
        Rect bounds=touchBounds();if(bounds.isEmpty())return null;
        if(camera==null || camera.isEmpty())camera=new RectF(bounds);
        float size=Math.min(bounds.width(),bounds.height());
        return new CapsulePresentationGeometry.Profile(new RectF(camera.centerX()-size/2,bounds.exactCenterY()-size/2,
                camera.centerX()+size/2,bounds.exactCenterY()+size/2),0,0);
    }
    private static Optional<Anchor> anchor(Class<?> type){
        Anchor found=null;
        for(Method method:type.getDeclaredMethods()){
            if(Modifier.isStatic(method.getModifiers()) || method.getParameterCount()!=1 || method.getParameterTypes()[0]!=int.class)continue;
            try{
                Field first=Reflect.field(method.getReturnType(),"first",Object.class),second=Reflect.field(method.getReturnType(),"second",Object.class);
                if(found!=null)return Optional.empty();method.setAccessible(true);found=new Anchor(method,first,second);
            }catch(NoSuchFieldException ignored){/* Not the native top/bottom pair factory. */}
        }
        return Optional.ofNullable(found);
    }
    boolean drawMorph(Canvas canvas,View window,CapsulePresentationGeometry.Geometry geometry,float radius){
        if(capsules.isEmpty())return false;
        for(View capsule:capsules)if(!(capsule instanceof ViewGroup group) || group.getChildCount()==0)return false;
        skins.keySet().retainAll(capsules);
        for(View capsule:capsules){
            Skin skin=skins.computeIfAbsent(capsule,Skin::new);skin.refresh(capsule);
            if(skin.originalBackground!=null && skin.background==null || skin.originalForeground!=null && skin.foreground==null)return false;
        }
        for(View capsule:capsules){
            Skin skin=skins.get(capsule);
            skin.drawShape(canvas,capsule,geometry.bounds(),false,radius);
            ViewGroup group=(ViewGroup)capsule;
            int save=canvas.save();
            try{
                canvas.saveLayerAlpha(geometry.bounds(),Math.round(255*geometry.contentAlpha()*capsule.getAlpha()));
                canvas.concat(geometry.contentForward());
                for(int i=0;i<group.getChildCount();i++){
                    View child=group.getChildAt(i);if(child.getVisibility()!=View.VISIBLE || child.getAlpha()<=0)continue;
                    int childSave=canvas.save();
                    try{
                        canvas.concat(matrixInWindow(child,window));
                        if(child.getAlpha()<1)canvas.saveLayerAlpha(0,0,child.getWidth(),child.getHeight(),Math.round(255*child.getAlpha()));
                        if(canvas.isHardwareAccelerated() && geometry.blurRadius()>0){
                            RenderNode node=skin.nodes.computeIfAbsent(i,slot->new RenderNode("ColorOSEnhance-CapsuleContent"));
                            if(child.getWidth()>0 && child.getHeight()>0){
                                node.setPosition(0,0,child.getWidth(),child.getHeight());
                                Canvas recording=node.beginRecording();try{child.draw(recording);}finally{node.endRecording();}
                            }
                            if(node.hasDisplayList()){node.setRenderEffect(skin.blur(geometry.blurRadius()));canvas.drawRenderNode(node);}
                        }else child.draw(canvas);
                    }finally{canvas.restoreToCount(childSave);}
                }
            }finally{canvas.restoreToCount(save);}
            skin.drawShape(canvas,capsule,geometry.bounds(),true,radius);
        }
        return true;
    }
    void drawMorphDecorations(Canvas canvas,View window,CapsulePresentationGeometry.Geometry geometry){
        if(!capsules.isEmpty())drawMoons(canvas,window,capsules.getFirst(),geometry);
    }
    private void drawMoons(Canvas canvas,View window,View capsule,CapsulePresentationGeometry.Geometry geometry){
        View parent=parent(capsule);if(parent==null)return;
        List<Method> methods=moons.computeIfAbsent(parent.getClass(),type->{
            List<Method> result=new ArrayList<>();
            for(String name:new String[]{"getLeftMoon","getRightMoon"})try{result.add(Reflect.method(type,name,android.widget.ImageView.class));}
            catch(NoSuchMethodException ignored){/* A single payload has no side decorations. */}
            return result;
        });
        for(Method method:methods){
            View moon=(View)Reflect.call(method,parent);
            if(moon==null || moon.getVisibility()!=View.VISIBLE || geometry.moonAlpha()<=0)continue;
            int save=canvas.save();
            try{
                canvas.translate(method.getName().equals("getLeftMoon")?geometry.moonShift():-geometry.moonShift(),0);
                canvas.concat(matrixInWindow(moon,window));
                canvas.saveLayerAlpha(0,0,moon.getWidth(),moon.getHeight(),Math.round(255*moon.getAlpha()*geometry.moonAlpha()));moon.draw(canvas);
            }finally{canvas.restoreToCount(save);}
        }
    }
    void clearMorph(){for(Skin skin:skins.values())for(RenderNode node:skin.nodes.values())node.discardDisplayList();skins.clear();}
    private static final class Skin{
        Drawable originalBackground,originalForeground,background,foreground;
        final Map<Integer,RenderNode> nodes=new HashMap<>();
        final Map<Integer,RenderEffect> effects=new HashMap<>();
        Skin(View view){refresh(view);}
        void refresh(View view){
            if(originalBackground!=view.getBackground()){originalBackground=view.getBackground();background=copy(originalBackground,view.getResources());}
            if(originalForeground!=view.getForeground()){originalForeground=view.getForeground();foreground=copy(originalForeground,view.getResources());}
        }
        private static Drawable copy(Drawable original,Resources resources){return original==null || original.getConstantState()==null?null:original.getConstantState().newDrawable(resources).mutate();}
        RenderEffect blur(float radius){int key=Math.max(1,Math.round(radius*2));return effects.computeIfAbsent(key,k->RenderEffect.createBlurEffect(k/2f,k/2f,Shader.TileMode.CLAMP));}
        void drawShape(Canvas canvas,View view,RectF bounds,boolean front,float radius){
            Drawable drawable=front?foreground:background,original=front?originalForeground:originalBackground;
            if(drawable==null)return;
            drawable.setState(view.getDrawableState());drawable.setLevel(original.getLevel());drawable.setAlpha(Math.round(original.getAlpha()*view.getAlpha()));
            if(radius>=0 && drawable instanceof android.graphics.drawable.GradientDrawable shape && shape.getCornerRadius()!=radius)
                shape.setCornerRadius(radius);
            drawable.setBounds(Math.round(bounds.left),Math.round(bounds.top),Math.round(bounds.right),Math.round(bounds.bottom));drawable.draw(canvas);
        }
    }
    int viewportHeight(View window, int screenHeight) {
        if (host.getWidth() <= 0 || host.getHeight() <= 0 || !host.isAttachedToWindow()) return Math.max(1, screenHeight);
        RectF bounds = new RectF(0, 0, host.getWidth(), host.getHeight());
        matrixInWindow(host, window).mapRect(bounds);
        int quantum = Math.max(1, Math.round(host.getResources().getDisplayMetrics().density * 16));
        return viewportHeight(bounds, screenHeight, quantum);
    }
    static int viewportHeight(RectF bounds, int screenHeight, int quantum) {
        int full = Math.max(1, screenHeight);
        if (bounds == null || bounds.isEmpty() || !Float.isFinite(bounds.left) || !Float.isFinite(bounds.top)
                || !Float.isFinite(bounds.right) || !Float.isFinite(bounds.bottom) || bounds.bottom <= 0 || quantum <= 0) return full;
        if (bounds.bottom >= full) return full;
        // Reserve one band for native shadows/rebound, and quantize to avoid relayout on each animation frame.
        long bands = (long) Math.ceil((bounds.bottom + quantum) / quantum);
        return (int) Math.max(1, Math.min(full, bands * quantum));
    }

    void draw(Canvas canvas) {
        if (!content || !host.isAttachedToWindow()) return;
        // prepare() and this window's draw can straddle a native animation callback.
        // View.draw() does not apply its own visibility/alpha; do not replay a restored
        // full-size capsule using opacity cached before its hide animation completed.
        float opacity = 1f;
        for (View v = host; v != null; v = parent(v)) {
            if (!(v == host && independentHostPresentation)) {
                if (v.getVisibility() != View.VISIBLE) return;
                opacity *= v.getAlpha();
            }
            if (independentPlacement) break;
        }
        if (opacity <= 0f) return;
        int save = canvas.save();
        try {
            canvas.clipRect(drawBounds);
            for (Path clip : ancestorClips) canvas.clipPath(clip);
            if (opacity < 1f) canvas.saveLayerAlpha(drawBounds, Math.round(opacity * 255));
            canvas.concat(toWindow);
            host.draw(canvas);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    boolean dispatchTouchEvent(MotionEvent event) {
        if (android.util.Log.isLoggable("COSMirror", android.util.Log.DEBUG))
            android.util.Log.d("COSMirror", "touch " + event.getActionMasked() + " " + debugState());
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelTouch();
            if (!touchable.contains((int) event.getX(), (int) event.getY())) return false;
        } else if (lastTouch == null) {
            return false;
        }
        MotionEvent copy = MotionEvent.obtain(event);
        try {
            copy.transform(toHost);
            if (lastTouch != null) lastTouch.recycle();
            lastTouch = MotionEvent.obtain(copy);
            boolean handled;
            touchDispatchDepth++;
            try { handled = host.dispatchTouchEvent(copy); }
            finally {
                touchDispatchDepth--;
                if(touchDispatchDepth==0 && cancelPending){
                    cancelPending=false;
                    if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)clearTouch();else cancelTouch();
                }
            }
            if (!handled && action == MotionEvent.ACTION_DOWN
                    || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                clearTouch();
            }
            return handled;
        } finally {
            copy.recycle();
        }
    }
    boolean isForwardingTouch(){return touchDispatchDepth>0;}
    void clearRecovery(){recovery.setEmpty();}

    void cancelTouch() {
        handedOff = false;
        if(touchDispatchDepth>0){cancelPending=true;return;}
        if (lastTouch == null) return;
        MotionEvent cancel = lastTouch;
        lastTouch = null;
        try {
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            host.dispatchTouchEvent(cancel);
        } finally {
            cancel.recycle();
        }
    }

    private void clearTouch() {
        handedOff = false;
        if (lastTouch != null) lastTouch.recycle();
        lastTouch = null;
    }

    void markNativeHandoff() {
        if (touchDispatchDepth > 0 && lastTouch != null) handedOff = true;
    }

    interface NativeDispatch { Object call() throws Throwable; }
    Object observeNativeHandoff(MotionEvent event, NativeDispatch dispatch) throws Throwable {
        // The native handler can synchronously expand the shade and suspend our window.
        // Record ownership while still in the forwarded input stack, before that callback.
        if (event != null && (event.getActionMasked() == MotionEvent.ACTION_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_MOVE)) markNativeHandoff();
        return dispatch.call();
    }

    boolean hasHandedOffTouch() { return handedOff && lastTouch != null; }
}
