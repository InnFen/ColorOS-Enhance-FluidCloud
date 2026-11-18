package dev.lsposed.fluidcloud;

import android.graphics.Rect;
import android.graphics.Region;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Geometry adapter for expandable instant capsules, independent of ordinary CardView. */
final class NativeInstantCameraCard {
    final Class<?> type;
    final Method state, position, draw, detach, blur, touchRegion;
    private final Method expanded, current;
    private final NativeCameraCard cards;
    private final WeakHashMap<FrameLayout, Owned> owned = new WeakHashMap<>();
    private final WeakHashMap<ViewGroup, Boolean> containers = new WeakHashMap<>();
    interface TouchWriter { boolean write(ViewGroup container, Region region); }
    private final TouchWriter touchWriter;
    private final WeakHashMap<ViewGroup, Region> writtenRegions = new WeakHashMap<>();
    private final WeakHashMap<ViewGroup, android.view.ViewTreeObserver.OnPreDrawListener> touchChecks = new WeakHashMap<>();
    private boolean preparingScene;
    private String lastNote;
    private final java.util.Set<View> remeasure = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private record Owned(java.lang.ref.WeakReference<ViewGroup> container, int nativeTop, int appliedTop,
                         int nativePadding, int appliedPadding, int inset, Rect camera, int anchorDy) { }
    NativeInstantCameraCard(NativeCameraCard.Lookup lookup, NativeCameraCard cards) throws ReflectiveOperationException {
        this(lookup, cards, (container, region) -> {
            var surface = container.getRootSurfaceControl();
            if (surface == null) return false;
            surface.setTouchableRegion(new Region(region));
            return true;
        });
    }
    NativeInstantCameraCard(NativeCameraCard.Lookup lookup, NativeCameraCard cards, TouchWriter touchWriter)
            throws ReflectiveOperationException {
        this.cards = cards;
        this.touchWriter = touchWriter;
        type = lookup.load(NativeCameraCard.VIEWS + "InstantContainer");
        if (!FrameLayout.class.isAssignableFrom(type)) throw new NoSuchMethodException("Instant container type");
        Class<?> model = lookup.load(cards.legacy ? "h5.n" : "com.oplus.systemui.plugins.seedling.card.ui.model.n");
        state = Reflect.method(type, "k", void.class, model, boolean.class, boolean.class);
        position = Reflect.method(type, cards.legacy ? "q" : "s", void.class);
        draw = Reflect.method(type, "dispatchDraw", void.class, android.graphics.Canvas.class);
        detach = Reflect.method(type, "onDetachedFromWindow", void.class);
        expanded = Reflect.method(type, "l", boolean.class);
        current = Reflect.method(type, "getInstantView", View.class);
        blur = Reflect.method(type, cards.legacy ? "o" : "p", void.class,
                View.class, Rect.class, float.class, int.class, int.class);
        Method region = null;
        for (Method method : type.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.getReturnType() != Region.class
                    || !java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{type})) continue;
            if (region != null) throw new NoSuchMethodException("Ambiguous instant input region");
            method.setAccessible(true); region = method;
        }
        if (region == null) throw new NoSuchMethodException("Instant input region contract");
        touchRegion = region;
        // Never hook an inherited framework dispatchDraw when a firmware changes.
        if (draw.getDeclaringClass() != type || detach.getDeclaringClass() != type)
            throw new NoSuchMethodException("Instant lifecycle overrides");
    }
    void afterStateChange(ViewGroup container, boolean enabled) {
        nativeChanged(container);
        View value = enabled ? (View) Reflect.call(current, container) : null;
        var keyguard = container.getContext().getSystemService(android.app.KeyguardManager.class);
        if (value instanceof FrameLayout view && (boolean) Reflect.call(expanded, container)
                && container.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                && keyguard != null && !keyguard.isKeyguardLocked()
                && view.getParent() == container && view.getLayoutParams() instanceof FrameLayout.LayoutParams params) {
            Owned old = owned.get(view);
            Rect camera = cards.cameraAnchor(container);
            // Direct appearances can attach a recycled SDK root via addView(view),
            // omitting the large-card LayoutParams. Manual expansion uses the OEM
            // position updater. Run that same updater before measuring the new
            // scene, never invent a status-bar offset or capture our applied margin
            // as a fresh native baseline.
            boolean nativeMargin = old == null || params.topMargin != old.appliedTop();
            if (nativeMargin && (params.topMargin <= 0 || camera != null && !camera.isEmpty()
                    && params.topMargin + container.getTranslationY() < camera.top))
                Reflect.call(position, container);
        }
        update(container, enabled);
    }
    void beforeAppearScene(ViewGroup container, boolean enabled) {
        if (!enabled || preparingScene) return;
        preparingScene = true;
        try {
            afterStateChange(container, true);
            View value = (View) Reflect.call(current, container);
            // The new instant-appear builder snapshots measured height into its
            // SizeFragment. A requested layout alone arrives too late: it leaves
            // that whole animation cropping to the old unpadded height.
            if (!owned.containsKey(value) || !value.isLayoutRequested()
                    || container.getWidth() <= 0 || container.getHeight() <= 0) return;
            container.measure(View.MeasureSpec.makeMeasureSpec(container.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(container.getHeight(), View.MeasureSpec.EXACTLY));
            container.layout(container.getLeft(), container.getTop(), container.getRight(), container.getBottom());
            update(container, true);
        } finally { preparingScene = false; }
    }
    void update(ViewGroup container, boolean enabled) {
        containers.put(container, enabled);
        for (var entry : new ArrayList<>(owned.entrySet())) {
            FrameLayout view = entry.getKey(); Owned old = entry.getValue();
            if (view != null && old.container().get() == container
                    && (!enabled || view.getParent() != container)) release(view);
        }
        if (!enabled) { restoreTouch(container); return; }
        View value = (View) Reflect.call(current, container);
        // SDK custom views that do not honor FrameLayout padding are left native.
        if (!(boolean) Reflect.call(expanded, container) || !(value instanceof FrameLayout view)
                || view.getParent() != container || !(view.getLayoutParams() instanceof FrameLayout.LayoutParams params)) return;
        Owned old = owned.get(view);
        int nativeTop = old != null && params.topMargin == old.appliedTop() ? old.nativeTop() : params.topMargin;
        int nativePadding = old != null && view.getPaddingTop() == old.appliedPadding() ? old.nativePadding() : view.getPaddingTop();
        NativeCameraCard.Plan plan = cards.instantGeometry(container, nativeTop + container.getTranslationY());
        Rect camera = cards.cameraAnchor(container);
        if (plan == null || camera == null || camera.isEmpty()) { release(view); return; }
        // The camera stays the capsule; the morph moves toward the (offset) card top as it expands.
        int anchorDy = plan.anchorDy();
        int top = Math.round(plan.translationY() - container.getTranslationY());
        int padding = nativePadding + plan.inset();
        int lead = -1;
        if (plan.target() >= 0) {
            // Pull the content up: its first visible element goes to the target below the camera.
            lead = CameraAvoidance.lead(view, view.getPaddingTop());
            if (lead >= 0) lead += cards.trimmer.trimmed(view);
            padding = Math.min(padding, CameraAvoidance.boxTop(plan.target(), lead));
            cards.trimmer.apply(view, false, lead < 0 ? 0 : Math.max(0, lead - plan.target()));
            if (lead < 0 && remeasure.add(view)) view.post(() -> {
                remeasure.remove(view);
                if (Boolean.TRUE.equals(containers.get(container)) && owned.containsKey(view)
                        && CameraAvoidance.lead(view, view.getPaddingTop()) >= 0) update(container, true);
            });
        }
        else cards.trimmer.restore(view);
        int inset = Math.max(1, padding - nativePadding);
        String note = "Instant camera card: native top padding " + nativePadding + "px, content lead " + lead
                + "px, padding " + padding + "px, target " + plan.target() + "px";
        if (!note.equals(lastNote)) { lastNote = note; try { cards.onInfo.accept(note); } catch (RuntimeException ignored) { } }
        owned.put(view, new Owned(new java.lang.ref.WeakReference<>(container), nativeTop, top,
                nativePadding, padding, inset, new Rect(camera), anchorDy));
        if (params.topMargin != top) { params.topMargin = top; view.setLayoutParams(params); }
        if (view.getPaddingTop() != padding)
            view.setPadding(view.getPaddingLeft(), padding, view.getPaddingRight(), view.getPaddingBottom());
        cards.prepareInstant(view, inset, camera, anchorDy);
        observeTouch(container);
        // Keep outgoing native views adapted while their own disappear animation
        // runs. A replacement small capsule must never receive this geometry.
        for (var entry : owned.entrySet()) if (entry.getKey() != view && entry.getKey().getParent() == container)
            cards.prepareInstant(entry.getKey(), entry.getValue().inset(), entry.getValue().camera(),
                    entry.getValue().anchorDy());
    }
    boolean owns(View view) { return owned.containsKey(view); }
    void nativeChanged(ViewGroup container) { writtenRegions.remove(container); }
    Region inputRegion(ViewGroup container, Region nativeRegion, boolean enabled) {
        if (!enabled || !(boolean) Reflect.call(expanded, container)) return nativeRegion;
        Region result = new Region();
        View active = (View) Reflect.call(current, container);
        for (var entry : owned.entrySet()) {
            FrameLayout view = entry.getKey();
            if (view == null || view.getParent() != container || entry.getValue().container().get() != container
                    || view.getVisibility() != View.VISIBLE || view != active && view.getAlpha() <= 0) continue;
            Rect bounds = cards.instantTouchBounds(view);
            if (bounds != null) result.op(bounds, Region.Op.UNION);
        }
        return result.isEmpty() ? nativeRegion : result;
    }
    private void observeTouch(ViewGroup container) {
        if (touchChecks.containsKey(container)) return;
        var weak = new java.lang.ref.WeakReference<>(container);
        android.view.ViewTreeObserver.OnPreDrawListener check = () -> {
            ViewGroup view = weak.get();
            if (view == null) return true;
            try { syncTouch(view); }
            catch (RuntimeException | LinkageError error) {
                stopTouch(view);
                try { cards.onFailure.accept(error); } catch (RuntimeException | LinkageError ignored) { }
            }
            return true;
        };
        touchChecks.put(container, check);
        container.getViewTreeObserver().addOnPreDrawListener(check);
    }
    private void syncTouch(ViewGroup container) {
        // The native updater can still publish the outgoing small capsule while
        // the new large card is already drawing. Commit only this owned card's
        // current transformed region before that frame, never a full-screen one.
        Region region = inputRegion(container, null, Boolean.TRUE.equals(containers.get(container)));
        boolean adapted = region != null;
        if (region == null) region = (Region) Reflect.call(touchRegion, null, container);
        if (!region.equals(writtenRegions.get(container)) && touchWriter.write(container, region))
            writtenRegions.put(container, new Region(region));
        if (!adapted || !Boolean.TRUE.equals(containers.get(container)) || !(boolean) Reflect.call(expanded, container))
            stopTouch(container);
    }
    private void stopTouch(ViewGroup container) {
        var check = touchChecks.remove(container);
        if (check != null && container.getViewTreeObserver().isAlive()) container.getViewTreeObserver().removeOnPreDrawListener(check);
        writtenRegions.remove(container);
    }
    private void restoreTouch(ViewGroup container) {
        try {
            if (touchChecks.containsKey(container)) {
                Region nativeRegion = (Region) Reflect.call(touchRegion, null, container);
                touchWriter.write(container, nativeRegion);
            }
        } finally { stopTouch(container); }
    }
    void release(FrameLayout view) {
        Owned old = owned.remove(view);
        cards.trimmer.restore(view);
        if (old == null) return;
        cards.releaseInstant(view);
        if (view.getLayoutParams() instanceof FrameLayout.LayoutParams params && params.topMargin == old.appliedTop()) {
            params.topMargin = old.nativeTop(); view.setLayoutParams(params);
        }
        if (view.getPaddingTop() == old.appliedPadding())
            view.setPadding(view.getPaddingLeft(), old.nativePadding(), view.getPaddingRight(), view.getPaddingBottom());
    }
    void detached(ViewGroup container) {
        for (var entry : new ArrayList<>(owned.entrySet()))
            if (entry.getKey() != null && entry.getValue().container().get() == container) release(entry.getKey());
        containers.remove(container);
        stopTouch(container);
    }
    void refresh(boolean enabled) {
        for (ViewGroup container : new ArrayList<>(containers.keySet())) if (container != null) update(container, enabled);
    }
    void restore() {
        for (FrameLayout view : new ArrayList<>(owned.keySet())) if (view != null) release(view);
        for (ViewGroup container : new ArrayList<>(touchChecks.keySet())) if (container != null) restoreTouch(container);
        containers.clear();
    }
}
