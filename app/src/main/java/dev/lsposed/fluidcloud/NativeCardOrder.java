package dev.lsposed.fluidcloud;

import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Orders a presentation copy before native DiffUtil, never the business-state list. */
final class NativeCardOrder {
    final Method update, launch;
    private final Field data, selectedKey, contentState, viewModel;
    private final Class<?> capsuleContainer;
    private final Handler main;
    private final BooleanSupplier enabled;
    private final Set<View> owners = Collections.newSetFromMap(new WeakHashMap<>());
    private final WeakHashMap<Class<?>, Field> keys = new WeakHashMap<>();
    private final Selection selection = new Selection();
    private String presented;
    private boolean expanded, queued, closed, applied;
    java.util.function.Consumer<Throwable> failed = error -> { };
    java.util.function.Consumer<String> trace = message -> { };
    /** Capsule state changes during launch belong to the next presentation. */
    static final class Selection {
        private String selected, pinned;
        private boolean launching;
        boolean change(String value) {
            if (java.util.Objects.equals(selected,value)) return false;
            selected=value;return true;
        }
        String key(boolean enabled) { return launching?pinned:enabled?selected:null; }
        boolean begin(boolean enabled, String visiblePriority) {
            if (!enabled || launching) return false;
            launching=true;pinned=visiblePriority;return true;
        }
        boolean pinned() { return launching; }
        void end() { launching=false;pinned=null; }
    }
    private final Runnable refresh = this::refreshNow;
    private final Runnable launchTimeout=()->{
        if(selection.pinned()){selection.end();trace("launch guard expired; native state released");schedule();}
    };
    private void refreshNow() {
        queued = false;
        if (!expanded || closed || selection.pinned()) return;
        try { for (View owner : new ArrayList<>(owners)) if (owner.isAttachedToWindow()) Reflect.call(update, owner); }
        catch (RuntimeException | LinkageError error) { close(); failed.accept(error); }
    }
    NativeCardOrder(NativeCapsuleAppearance.Lookup lookup, boolean legacy, Handler main, BooleanSupplier enabled)
            throws ReflectiveOperationException {
        Class<?> container = lookup.load(NativeCameraCard.VIEWS + "CardContainer");
        capsuleContainer = lookup.load(NativeCapsuleAppearance.VIEWS + "CapsuleContainer");
        selectedKey = Reflect.field(capsuleContainer, legacy ? "w" : "l", String.class);
        contentState = Reflect.field(capsuleContainer, legacy ? "x" : "m", Enum.class);
        viewModel=Reflect.uniqueField(container,field->field.getDeclaringClass()==container && Reflect.instance(field)
                && NativeCardLaunch.find(field.getType()).isPresent());
        launch=NativeCardLaunch.find(viewModel.getType()).orElseThrow(()->new NoSuchMethodException("Native card launch entry"));
        NativeCardList list=NativeCardList.of(container);data=list.data;update=list.update;
        this.main = main; this.enabled = enabled;
    }
    static <T> List<T> prioritize(List<T> source, String selected, Function<T, String> key) {
        if (selected == null || selected.isEmpty() || source.size() < 2) return source;
        int match = -1;
        for (int i = 0; i < source.size(); i++) if (selected.equals(key.apply(source.get(i)))) {
            if (match >= 0) return source;
            match = i;
        }
        if (match <= 0) return source;
        var copy = new ArrayList<>(source);
        copy.add(0, copy.remove(match));
        return copy;
    }
    AutoCloseable beforeUpdate(View owner) throws IllegalAccessException {
        owners.add(owner);
        Object original = Reflect.get(data, owner);
        if (closed || !enabled.getAsBoolean() && !selection.pinned() || !(original instanceof List<?> items)) { applied=false;presented=null; return () -> { }; }
        String priority=selection.key(enabled.getAsBoolean());
        List<?> sorted = prioritize(items, priority, item -> {
            if (item == null) return null;
            Field field = keys.get(item.getClass());
            if (field == null) try { field = Reflect.field(item.getClass(), "a", String.class); keys.put(item.getClass(), field); }
            catch (NoSuchFieldException unavailable) { return null; }
            return (String) Reflect.get(field, item);
        });
        presented=priority;
        if (sorted == original) return () -> { };
        applied=true;
        trace("presentation reordered; launchPinned="+selection.pinned());
        data.set(owner, sorted);
        return () -> { if (Reflect.get(data, owner) == sorted) data.set(owner, original); };
    }
    void selection(View root) {
        if (capsuleContainer.isInstance(root)) {
            Object state=Reflect.get(contentState,root);
            String key = state instanceof Enum<?> value && value.name().equals("NORMAL")
                    ? (String) Reflect.get(selectedKey, root) : null;
            if (selection.change(key)) { trace("selection changed; expanded="+expanded+" launchPinned="+selection.pinned());schedule(); }
            return;
        }
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) selection(group.getChildAt(i));
    }
    boolean beforeLaunch(Object model) {
        if (closed || !expanded || !enabled.getAsBoolean() || selection.pinned()) return false;
        boolean owner=false;
        for(View view:owners)if(view.isAttachedToWindow() && Reflect.get(viewModel,view)==model){owner=true;break;}
        if(!owner || !selection.begin(true,presented))return false;
        main.removeCallbacks(refresh);queued=false;
        main.removeCallbacks(launchTimeout);main.postDelayed(launchTimeout,5000);
        trace("native app launch began; display order pinned");return true;
    }
    void afterLaunch(boolean accepted) {
        if(accepted)return;
        releaseLaunch();trace("native launch rejected; order released");schedule();
    }
    boolean deferUpdate(View owner) {
        // Keep the native launcher controller attached to the same visible
        // holder until the window is removed. The incoming business state is
        // already stored by OEM; only its adapter update is deferred.
        return !closed && expanded && selection.pinned() && owner.isAttachedToWindow();
    }
    private void releaseLaunch(){main.removeCallbacks(launchTimeout);selection.end();}
    void expanded(boolean value) { expanded = value; if (value) schedule();else releaseLaunch(); }
    void schedule() { if (!closed && expanded && !queued && !selection.pinned() && (enabled.getAsBoolean() || applied)) { queued = true; main.post(refresh); } }
    private void trace(String message){if(android.util.Log.isLoggable("COSCardOrder",android.util.Log.DEBUG))trace.accept("Card priority: "+message);}
    void close() { closed = true; main.removeCallbacks(refresh);releaseLaunch(); owners.clear(); keys.clear();presented=null;selection.change(null); }
}
