package dev.lsposed.fluidcloud;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import org.json.JSONObject;

/** Only exact OEM voice-print assets inside a capsule; never app icons or card controls. */
final class NativeMediaWave implements AutoCloseable {
    private final MediaWaveFeature feature;
    private final HookEntry module;
    private final Method json, image, completed, draw, pause;
    private final Field lottie;
    private final Set<View> roots=Collections.newSetFromMap(new WeakHashMap<>());
    private final MediaWaveTail tail;
    private final MediaWaveVisibilityWake visibilityWake;
    private final Method composition,cacheGet;
    private final Object cache;
    private final ArrayList<XposedInterface.HookHandle> hooks=new ArrayList<>();
    private XposedInterface.HookHandle drawHook;
    private final WeakHashMap<ImageView,String> models=new WeakHashMap<>();
    private final WeakHashMap<Resources,Map<String,MediaWaveStyle>> assets=new WeakHashMap<>();
    private final IdentityHashMap<Drawable,Binding> active=new IdentityHashMap<>();
    private final WeakHashMap<Drawable,Boolean> traced=new WeakHashMap<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean enabled,bootstrapDirty=true;
    NativeMediaWave(HookEntry module,View root,ClassLoader loader,MediaWaveFeature feature)throws ReflectiveOperationException{
        this.module=module;this.feature=feature;
        roots.add(root);
        tail=new MediaWaveTail(root,this::restoreView);
        visibilityWake=new MediaWaveVisibilityWake(this::observedViews,feature::awaitingVisibility,this::canWake,feature::changed);
        Class<?> view=Reflect.type(loader,"com.oplus.systemui.plugins.shared.template.component.view.CustomLottieView");
        if(!ImageView.class.isAssignableFrom(view))throw new NoSuchMethodException("Unknown voice-print view");
        Class<?> base=view.getSuperclass();Method found=null;Field field=null;
        for(Method method:base.getDeclaredMethods()){
            if(!method.isBridge() && !method.isSynthetic() && method.getReturnType()==void.class
                    && java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{String.class,String.class})){
                if(found!=null)throw new NoSuchMethodException("Ambiguous Lottie JSON loader");
                found=method;
            }
        }
        for(Field candidate:base.getDeclaredFields())if(Drawable.class.isAssignableFrom(candidate.getType()) && Animatable.class.isAssignableFrom(candidate.getType())){
            if(field!=null)throw new NoSuchFieldException("Ambiguous Lottie drawable");field=candidate;
        }
        if(found==null || field==null)throw new NoSuchMethodException("Unsupported Lottie contract");
        json=found;json.setAccessible(true);lottie=field;lottie.setAccessible(true);
        image=Reflect.method(view,"setImageDrawable",void.class,Drawable.class);
        draw=Reflect.method(lottie.getType(),"draw",void.class,Canvas.class);
        pause=Reflect.method(lottie.getType(),lottie.getType().getName().equals("com.airbnb.lottie.b")?"j":"pauseAnimation",void.class);
        if(draw.getDeclaringClass().getName().startsWith("android."))throw new NoSuchMethodException("Framework draw hooks are forbidden");
        composition=base.getMethod("getComposition");
        Method loaded;
        try{loaded=Reflect.method(base,"setComposition",void.class,composition.getReturnType());}
        catch(NoSuchMethodException missing){loaded=null;}
        completed=loaded;
        Object foundCache=null;Method foundGet=null;
        try{
            Class<?> type;
            if(base.getName().equals("com.airbnb.lottie.a")){
                type=PluginClasses.of(view).unique("com.oplus.systemui.plugins.r8.",candidate->{
                    if(!PluginClasses.has(candidate,composition.getReturnType(),String.class))return false;
                    try{
                        Reflect.uniqueField(candidate,f->java.lang.reflect.Modifier.isStatic(f.getModifiers())&&f.getType()==candidate);
                        return true;
                    }catch(NoSuchFieldException missing){return false;}
                });
            }else type=Reflect.type(loader,"com.airbnb.lottie.model.LottieCompositionCache");
            if(base.getName().equals("com.airbnb.lottie.a"))foundCache=Reflect.get(Reflect.uniqueField(type,
                    f->java.lang.reflect.Modifier.isStatic(f.getModifiers())&&f.getType()==type),null);
            else foundCache=Reflect.call(Reflect.method(type,"getInstance",type),null);
            foundGet=Reflect.signature(type,composition.getReturnType(),String.class);
        }catch(ReflectiveOperationException missing){/* Future model loads remain observable without the cache. */}
        cache=foundCache;cacheGet=foundGet;
    }
    void install(){
        try{
            hooks.add(module.hook(json).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Object result=chain.proceed();
                if(chain.getThisObject() instanceof ImageView view){
                    boolean previous=models.containsKey(view);
                    String value=(String)chain.getArg(0);
                    if(value!=null && value.length()<=262144 && assetStyle(view,value)!=null){models.put(view,value);rememberRoot(view);}
                    else models.remove(view);
                    // The same ImageView may receive a new composition without replacing its
                    // Drawable. Keep ownership until the new model has been identified.
                    if(enabled && (previous || models.containsKey(view)))feature.changed();
                }
                return result;
            }));
            hooks.add(module.hook(image).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Object result=chain.proceed();
                if(chain.getThisObject() instanceof ImageView view)loaded(view);
                return result;
            }));
            if(completed!=null)hooks.add(module.hook(completed).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Object result=chain.proceed();
                if(chain.getThisObject() instanceof ImageView view)loaded(view);
                return result;
            }));
        }catch(RuntimeException|LinkageError failure){close();throw failure;}
    }
    void enabled(boolean value){
        if(enabled==value)return;enabled=value;
        if(value){
            bootstrapDirty=true;
            drawHook=module.hook(draw).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Drawable drawable=(Drawable)chain.getThisObject();
                Binding held=active.get(drawable);
                ImageView target=drawable.getCallback() instanceof ImageView view?view:null;
                if(feature.rendering() || held!=null && tail.frame(held.view)!=null || target!=null && tail.frame(target)!=null)try{
                    Binding binding=held;
                    if(binding!=null && !matches(binding.view,drawable)){
                        restoreView(binding.view,false);binding=null;
                    }
                    // Native rebinds can draw before the posted state refresh. Bind in this
                    // very draw to prevent one frame of the original decorative animation.
                    if(binding==null && target!=null && (feature.rendering() || tail.frame(target)!=null) && capsule(target))binding=bind(target,drawable);
                    if(binding!=null && capsule(binding.view)){
                        // Native media refreshes may restart the decorative clock.
                        // Stop only this specific waveform, never a capsule geometry animator.
                        if(drawable instanceof Animatable animation && animation.isRunning())Reflect.call(pause,drawable);
                        binding.draw((Canvas)chain.getArg(0),drawable);
                        // A paused custom renderer must be able to resume too. Previously
                        // only OEM drawing could signal that its alpha had become visible.
                        if(feature.awaitingVisibility() && eligible(binding.view))feature.changed();
                        return null;
                    }
                }catch(RuntimeException|LinkageError error){feature.renderFailure();}
                if(feature.rendering() && android.util.Log.isLoggable("COSMediaWave",android.util.Log.DEBUG)
                        && drawable.getCallback() instanceof ImageView view && assetStyle(view,models.get(view))!=null
                        && !traced.containsKey(drawable)){
                    traced.put(drawable,true);StringBuilder path=new StringBuilder();
                    for(View node=view;node!=null && path.length()<500;node=node.getParent() instanceof View parent?parent:null)
                        path.append(node.getClass().getSimpleName()).append('/');
                    module.info("Media waveform native draw during continuity: "+path);
                }
                Object result=chain.proceed();
                if(feature.awaitingVisibleWave() && drawable.getCallback() instanceof ImageView view && assetStyle(view,models.get(view))!=null)feature.changed();
                return result;
            });
        }else{
            visibilityWake.clear();
            try{restore();}finally{if(drawHook!=null){drawHook.unhook();drawHook=null;}}
        }
    }
    ArrayList<ImageView> visible(){
        return visible(false);
    }
    ArrayList<ImageView> captureVisible(){
        return visible(true);
    }
    private ArrayList<ImageView> visible(boolean capture){
        ArrayList<ImageView> result=new ArrayList<>();
        if(!enabled)return result;
        if(bootstrapDirty){bootstrapDirty=false;for(View value:new ArrayList<>(roots))bootstrap(value);}
        for(var entry:new ArrayList<>(models.entrySet())){
            ImageView view=entry.getKey();
            if(view!=null && MediaWaveViewVisibility.eligible(view,capture,feature::visible) && assetStyle(view,entry.getValue())!=null)result.add(view);
        }
        return result;
    }
    void layoutChanged(){bootstrapDirty=true;tail.prune();feature.changed();}
    void root(View view){if(view!=null){roots.add(view);bootstrapDirty=true;if(enabled)feature.changed();}}
    void awaitVisibility(boolean waiting){visibilityWake.update(enabled && waiting && !models.isEmpty());}
    private ArrayList<View> observedViews(){
        var result=new ArrayList<View>(roots);result.addAll(models.keySet());return result;
    }
    private boolean canWake(){
        try{
            for(var entry:new ArrayList<>(models.entrySet()))
                if(eligible(entry.getKey()) && assetStyle(entry.getKey(),entry.getValue())!=null)return true;
        }catch(RuntimeException|LinkageError error){feature.renderFailure();}
        return false;
    }
    private void rememberRoot(View view){
        for(View node=view;node!=null;node=node.getParent() instanceof View parent?parent:null)
            if(node.getClass().getSimpleName().equals("CapsuleContainerRoot")){roots.add(node);break;}
    }
    private void loaded(ImageView view){
        bootstrapDirty=true;
        if(!models.containsKey(view))identify(view);
        if(models.containsKey(view)){rememberRoot(view);if(enabled)feature.changed();}
    }
    private void bootstrap(View node){
        if(node instanceof ImageView view)identify(view);
        if(node instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)bootstrap(group.getChildAt(i));
    }
    private void identify(ImageView view){
        if(!models.containsKey(view) && lottie.getDeclaringClass().isInstance(view) && cache!=null && cacheGet!=null){
            // A capsule can already exist before onPluginAdded returns. Match its exact
            // parsed composition by identity in the native cache, not by size or appearance.
            assetStyle(view,null);
            Object current=Reflect.call(composition,view);
            if(current!=null)for(String json:assets.get(view.getResources()).keySet())
                if(Reflect.call(cacheGet,cache,"lottie_"+json.hashCode())==current){models.put(view,json);break;}
        }
    }
    private boolean eligible(ImageView view){
        return MediaWaveViewVisibility.eligible(view,false,feature::visible);
    }
    static boolean capsule(ImageView view){
        if(view==null || !view.isAttachedToWindow())return false;
        for(View node=view;node!=null;node=node.getParent() instanceof View parent?parent:null)
            if(node.getClass().getSimpleName().equals("CapsuleView"))return true;
        return false;
    }
    boolean present(){
        tail.clear(); // A newly validated presentation takes ownership from an outgoing snapshot.
        visible(); // Bootstrap models before preparing even temporarily hidden native nodes.
        for(Binding binding:new ArrayList<>(active.values()))if(!capsule(binding.view)
                || !matches(binding.view,binding.original))restoreView(binding.view);
        for(ImageView view:new ArrayList<>(models.keySet())){
            if(!capsule(view) || assetStyle(view,models.get(view))==null)continue;
            Drawable original=view.getDrawable();
            // A composition must have finished loading; never replace an image or fallback drawable.
            if(original==null || original!=Reflect.get(lottie,view) || !(original instanceof Animatable animation))continue;
            if(!active.containsKey(original) && bind(view,original)!=null){view.invalidate();feature.pixels(view);}
        }
        return !active.isEmpty();
    }
    private boolean matches(ImageView view,Drawable drawable){
        return view.getDrawable()==drawable && drawable==Reflect.get(lottie,view) && assetStyle(view,models.get(view))!=null;
    }
    private Binding bind(ImageView view,Drawable drawable){
        if(!lottie.getDeclaringClass().isInstance(view) || !matches(view,drawable) || !(drawable instanceof Animatable animation))return null;
        active.entrySet().removeIf(entry->entry.getValue().view==view && entry.getKey()!=drawable);
        MediaWaveStyle style=assetStyle(view,models.get(view));
        Binding binding=new Binding(view,drawable,animation.isRunning(),style);active.put(drawable,binding);
        Reflect.call(pause,drawable);return binding;
    }
    void invalidate(){
        for(Binding binding:active.values()){
            binding.view.invalidate();feature.pixels(binding.view);
        }
    }
    boolean activeVisible(){for(Binding binding:active.values())if(eligible(binding.view))return true;return false;}
    void restore(){
        visibilityWake.clear();
        tail.clear();
        for(Binding binding:new ArrayList<>(active.values()))restoreView(binding.view);
    }
    void retire(MediaWaveLevels levels){
        for(Binding binding:new ArrayList<>(active.values())){
            tail.hold(binding.view,levels);
            if(tail.frame(binding.view)==null){restoreView(binding.view);continue;}
            binding.view.invalidate();feature.pixels(binding.view);
        }
    }
    private void restoreView(ImageView view){
        restoreView(view,true);
    }
    private void restoreView(ImageView view,boolean forgetTail){
        if(forgetTail)tail.forget(view);
        for(var entry:new ArrayList<>(active.entrySet()))if(entry.getValue().view==view){
            Binding binding=entry.getValue();active.remove(entry.getKey());
            if(view.isAttachedToWindow() && view.getDrawable()==binding.original){
                if(binding.wasRunning && feature.resumeOem() && binding.original instanceof Animatable animation)animation.start();
                view.invalidate();feature.pixels(view);
            }
        }
    }
    @android.annotation.SuppressLint("DiscouragedApi")
    private MediaWaveStyle assetStyle(ImageView view,String json){
        Map<String,MediaWaveStyle> known=assets.get(view.getResources());
        if(known==null){
            known=new LinkedHashMap<>();
            for(String name:new String[]{"voice_print_dark","voice_print_dark_15hz"}){
                int id=view.getResources().getIdentifier(name,"raw","com.oplus.systemui.plugins");
                if(id==0)continue;
                try(var input=view.getResources().openRawResource(id)){
                    byte[] bytes=input.readNBytes(262145);if(bytes.length>262144)continue;
                    String value=new String(bytes,StandardCharsets.UTF_8);MediaWaveStyle style=MediaWaveStyle.from(new JSONObject(value));
                    if(style!=null)known.put(value,style);
                }catch(Exception ignored){/* A firmware asset mismatch preserves the OEM waveform. */}
            }
            assets.put(view.getResources(),known);
        }
        return known.get(json);
    }
    private final class Binding{
        final ImageView view;final Drawable original;final boolean wasRunning;final MediaWaveStyle style;
        Binding(ImageView view,Drawable original,boolean running,MediaWaveStyle style){this.view=view;this.original=original;wasRunning=running;this.style=style;}
        void draw(Canvas canvas,Drawable drawable){
            Rect bounds=drawable.getBounds();if(bounds.isEmpty())return;
            float sx=bounds.width()/style.width(),sy=bounds.height()/style.height();
            paint.setColorFilter(drawable.getColorFilter()!=null?drawable.getColorFilter():view.getColorFilter());
            MediaWaveLevels frozen=tail.frame(view);
            for(int i=0;i<MediaWaveLevels.COUNT;i++){
                var bar=style.bars().get(i);
                int tint=view.getImageTintList()==null?Color.rgb(Color.red(bar.color()),Color.green(bar.color()),Color.blue(bar.color()))
                        :view.getImageTintList().getColorForState(view.getDrawableState(),bar.color()|0xff000000);
                paint.setColor(tint);paint.setAlpha(bar.alpha(tint,drawable.getAlpha()));
                float width=bar.width()*sx,height=bar.height(frozen==null?feature.level(i):frozen.at(i))*sy;
                float left=bounds.left+bar.x()*sx-width/2,center=bounds.top+bar.y()*sy;
                canvas.drawRoundRect(left,center-height/2,left+width,center+height/2,width/2,bar.width()*sy/2,paint);
            }
            feature.waveformDrawn(frozen!=null);
        }
    }
    @Override public void close(){
        visibilityWake.clear();enabled(false);for(var hook:hooks)hook.unhook();hooks.clear();models.clear();assets.clear();roots.clear();
    }
}
