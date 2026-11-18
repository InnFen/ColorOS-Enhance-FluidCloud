package dev.lsposed.fluidcloud;

import android.app.KeyguardManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Changes native measured geometry before its own appear/disappear scenes are built. */
final class NativeCameraCard {
    static final String VIEWS = "com.oplus.systemui.plugins.seedling.card.ui.view.";
    interface Lookup { Class<?> load(String name) throws ClassNotFoundException; }
    final Class<?> containerType, cardType, rootType;
    final Method layout, measure, cardLayout, intrinsicHeight, centers, detach, rootUpdate, windowAdd, blurBounds, outline, foreground, cardAlpha;
    final boolean legacy;
    private final Field capsuleRect, content, outlineRect;
    final Field windowRoot;
    private final Method adapterPosition, layoutPosition;
    private final Method setFrame;
    private final Class<?> mediaBackgroundType;
    private final Class<?> pageBackgroundType;
    private final Class<?> blurBackgroundType;
    private final Method blurAlpha;
    private final java.util.function.BiFunction<ViewGroup, Float, Plan> geometryOverride;
    private final WeakHashMap<ViewGroup, Position> positions = new WeakHashMap<>();
    private final WeakHashMap<View, Margin> margins = new WeakHashMap<>();
    private final WeakHashMap<View, Backdrop> backgrounds = new WeakHashMap<>();
    private final WeakHashMap<ViewGroup, Clip> clips = new WeakHashMap<>();
    private final WeakHashMap<View, Integer> stablePositions = new WeakHashMap<>();
    private final WeakHashMap<View, Rect> morphMasks = new WeakHashMap<>();
    private final WeakHashMap<View, Decorations> decorations = new WeakHashMap<>();
    private final WeakHashMap<View, java.lang.ref.WeakReference<CameraHeaderSurface>> headers = new WeakHashMap<>();
    private final WeakHashMap<View, java.lang.ref.WeakReference<CameraMaterialSurface>> materialSurfaces = new WeakHashMap<>();
    private final WeakHashMap<View, java.lang.ref.WeakReference<CameraCardForeground>> foregrounds = new WeakHashMap<>();
    private final WeakHashMap<View, External> external = new WeakHashMap<>();
    private Class<?> externalParentType;
    private final WeakHashMap<ViewGroup, android.view.ViewTreeObserver.OnPreDrawListener> ownershipChecks = new WeakHashMap<>();
    private final WeakHashMap<View, String> frameTraces = new WeakHashMap<>();
    java.util.function.Consumer<Throwable> onFailure = error -> { };
    /** dp between the camera cutout and the first visible content; negative keeps the original geometry. */
    java.util.function.IntSupplier gapDp = () -> -1;
    /** Card top like the theme dimen offset_between_status_bar_and_card, in dp; MIN_VALUE follows the capsule top. */
    java.util.function.IntSupplier topOffsetDp = () -> Integer.MIN_VALUE;
    final CameraAvoidance.Trimmer trimmer = new CameraAvoidance.Trimmer();
    java.util.function.Consumer<String> onInfo = message -> { };
    private String lastAvoidLog;
    private final NativeCardMaterial materials = new NativeCardMaterial();
    private final WeakHashMap<View, Elevation> elevations = new WeakHashMap<>();
    private final java.util.Set<View> mutedBlur = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private boolean aligning;
    private record Position(float nativeY, float appliedY, int inset, int target) {
        Position(float nativeY, float appliedY, int inset) { this(nativeY, appliedY, inset, -1); }
    }
    /** Header actually applied to each first card, which may differ from the container plan once its content is measured. */
    private final WeakHashMap<View, Integer> cardInsets = new WeakHashMap<>();
    private final WeakHashMap<View, Integer> leads = new WeakHashMap<>();
    private record Margin(int nativeTop, int appliedTop) { }
    private record Clip(boolean children, boolean padding, boolean outline, boolean ownsOutline) { }
    private record Backdrop(Rect nativeBounds, Rect appliedBounds, boolean fillsParent) { }
    private record Elevation(float nativeValue, float appliedValue) { }
    private record Decorations(CardHeaderDrawable background, CardHeaderDrawable foreground) { }
    private record MaterialOutline(android.view.ViewOutlineProvider provider, Rect mask, int width, int height) { }
    private record External(int inset, Rect camera) { }
    /** target: px from the card top to the first visible content, or -1 for the original geometry. */
    record Plan(float translationY, int inset, int target) {
        Plan(float translationY, int inset) { this(translationY, inset, -1); }
    }
    record Morph(Rect mask, float translationY) { }

    NativeCameraCard(ClassLoader loader) throws ReflectiveOperationException {
        this(name -> Reflect.type(loader, name));
    }
    NativeCameraCard(Lookup lookup) throws ReflectiveOperationException {
        this(lookup, null);
    }
    NativeCameraCard(Lookup lookup, java.util.function.BiFunction<ViewGroup, Float, Plan> geometryOverride)
            throws ReflectiveOperationException {
        this.geometryOverride = geometryOverride;
        containerType = lookup.load(VIEWS + "CardContainer");
        cardType = lookup.load(VIEWS + "CardView");
        Class<?> root; boolean old;
        try { root = lookup.load(VIEWS + "e"); old = false; }
        catch (ClassNotFoundException missing) { root = lookup.load("i5.d"); old = true; }
        legacy = old; rootType = root;
        if (!ViewGroup.class.isAssignableFrom(containerType) || !FrameLayout.class.isAssignableFrom(cardType)
                || !FrameLayout.class.isAssignableFrom(rootType)) throw new NoSuchMethodException("Card view types");
        capsuleRect = Reflect.field(rootType, legacy ? "s" : "h", Rect.class);
        content = Reflect.field(cardType, legacy ? "s" : "i", View.class);
        layout = Reflect.method(containerType, legacy ? "i" : "k", void.class);
        centers = Reflect.method(containerType, "getItemViewsCenter", List.class);
        Reflect.method(rootType, "getCardStartLocation", int.class);
        Reflect.method(rootType, "getCardContainer", containerType);
        adapterPosition = Reflect.method(containerType, "getChildAdapterPosition", int.class, View.class);
        layoutPosition = Reflect.method(containerType, "getChildLayoutPosition", int.class, View.class);
        setFrame = Reflect.method(View.class, "setLeftTopRightBottom", void.class, int.class, int.class, int.class, int.class);
        measure = Reflect.method(cardType, "onMeasure", void.class, int.class, int.class);
        cardLayout = Reflect.method(cardType, "onLayout", void.class, boolean.class, int.class, int.class, int.class, int.class);
        intrinsicHeight = Reflect.method(cardType, "getIntrinsicHeight", int.class);
        blurBounds = Reflect.method(cardType, "setLeftTopRightBottomForBlur", void.class, Rect.class);
        Class<?> outlineType = lookup.load(legacy ? "j6.h" : "com.oplus.systemui.plugins.common.util.j");
        if (!android.view.ViewOutlineProvider.class.isAssignableFrom(outlineType)) outlineType = PluginClasses.outline(cardType);
        Field rect;
        try{rect=Reflect.field(outlineType,legacy?"c":"a",Rect.class);}
        catch(NoSuchFieldException missing){rect=Reflect.uniqueField(outlineType,field->Reflect.instance(field)&&field.getType()==Rect.class);}
        outlineRect=rect;
        outline = Reflect.method(outlineType, "getOutline", void.class, View.class, android.graphics.Outline.class);
        foreground = Reflect.method(cardType, "dispatchDraw", void.class, android.graphics.Canvas.class);
        cardAlpha = Reflect.method(cardType, "setAlpha", void.class, float.class);
        blurBackgroundType = lookup.load(VIEWS + "CardBackgroundView");
        blurAlpha = Reflect.method(blurBackgroundType, "setBlurRadiusFollowAlpha", void.class, float.class);
        detach = Reflect.method(containerType, "onDetachedFromWindow", void.class);
        Method update;
        try{update=Reflect.method(rootType,legacy?"e":"d",void.class);}
        catch(NoSuchMethodException changed){update=Reflect.signature(rootType,void.class,String.class);}
        rootUpdate=update;
        windowAdd = Reflect.method(lookup.load(legacy ? "l5.h"
                : "com.oplus.systemui.plugins.seedling.card.window.i"), "a", void.class, boolean.class);
        windowRoot = Reflect.field(windowAdd.getDeclaringClass(), legacy ? "f" : "g", rootType);
        Class<?> media;
        try { media = lookup.load("com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView"); }
        catch (ClassNotFoundException missing) { media = null; }
        mediaBackgroundType = media != null && View.class.isAssignableFrom(media) ? media : null;
        Class<?> page;
        try { page = lookup.load("com.oplus.systemui.plugins.shared.template.page.view.PageBackgroundView"); }
        catch (ClassNotFoundException missing) { page = null; }
        pageBackgroundType = page != null && FrameLayout.class.isAssignableFrom(page) ? page : null;
    }

    /** The header grows by the same amount the native container moves up: content stays clear. */
    static Plan plan(Rect capsule, Rect cutout, int width, int statusHeight,
                     float nativeY, int divider, boolean portrait, boolean unlocked) {
        return plan(capsule, cutout, width, statusHeight, nativeY, divider, portrait, unlocked, -1, -1);
    }
    /**
     * The card top still moves to the capsule top (camera anchor). With {@code gapPx >= 0}
     * the content is pulled up so its first visible element sits {@code gapPx} below the
     * camera cutout; the header never exceeds the original space.
     */
    static Plan plan(Rect capsule, Rect cutout, int width, int statusHeight,
                     float nativeY, int divider, boolean portrait, boolean unlocked, int gapPx, int manualInset) {
        Plan full = fullPlan(capsule, cutout, width, statusHeight, nativeY, divider, portrait, unlocked, manualInset);
        if (full == null || gapPx < 0) return full;
        // Theme-style offset: the stock card moves up as a whole and only grows by the gap.
        if (manualInset > 0) return new Plan(full.translationY(), Math.max(1, gapPx), -1);
        int cardTop = Math.round(nativeY + divider) - full.inset();
        int target = Math.min(full.inset() + Math.max(0, gapPx), cutout.bottom - cardTop + gapPx);
        int header = Math.max(1, Math.min(full.inset(), target));
        return new Plan(full.translationY(), header, Math.max(1, target));
    }
    private static Plan fullPlan(Rect capsule, Rect cutout, int width, int statusHeight,
                     float nativeY, int divider, boolean portrait, boolean unlocked, int manualInset) {
        if (!portrait || !unlocked || capsule == null || cutout == null || capsule.isEmpty()
                || cutout.isEmpty() || width <= 0 || statusHeight <= 0 || !Float.isFinite(nativeY)
                || divider < 0 || capsule.top < 0 || capsule.bottom > statusHeight * 2
                || capsule.height() > statusHeight || cutout.left <= 0 || cutout.right >= width
                || Math.abs(cutout.exactCenterX() - width / 2f) > width * .15f
                || !capsule.contains(cutout.centerX(), capsule.centerY())) return null;
        float nativeTop = nativeY + divider;
        int inset = manualInset > 0 ? manualInset : Math.round(nativeTop - capsule.top);
        if (inset <= 0 || inset > statusHeight * 2 || capsule.top >= cutout.bottom) return null;
        return new Plan(nativeY - inset, inset);
    }

    private ViewGroup root(ViewGroup container) {
        ViewParent parent = container.getParent();
        return rootType.isInstance(parent) ? (ViewGroup) parent : null;
    }
    private Plan geometry(ViewGroup container, float nativeY) {
        return geometry(container, nativeY, -1);
    }
    Plan instantGeometry(ViewGroup container, float nativeY) { return geometry(container, nativeY, 0); }
    Rect cameraAnchor(ViewGroup container) {
        ViewGroup root = root(container);
        return root == null ? null : new Rect((Rect) Reflect.get(capsuleRect, root));
    }
    private Plan geometry(ViewGroup container, float nativeY, int dividerOverride) {
        if (geometryOverride != null) return geometryOverride.apply(container, nativeY);
        ViewGroup root = root(container);
        if (root == null || container.getDisplay() == null
                || container.getDisplay().getDisplayId() != Display.DEFAULT_DISPLAY) return null;
        DisplayCutout cutout = container.getDisplay().getCutout();
        var resources = container.getResources();
        int dividerId = resources.getIdentifier("capsule_card_divider_height", "dimen", "com.oplus.systemui.plugins");
        int statusId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (dividerId == 0 || statusId == 0) return null;
        KeyguardManager keyguard = container.getContext().getSystemService(KeyguardManager.class);
        Rect capsule = (Rect) Reflect.get(capsuleRect, root);
        Rect cameraRect = cutout == null ? null : cutout.getBoundingRectTop();
        int width = resources.getDisplayMetrics().widthPixels, status = resources.getDimensionPixelSize(statusId);
        int divider = dividerOverride < 0 ? resources.getDimensionPixelSize(dividerId) : dividerOverride;
        boolean portrait = resources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        boolean unlocked = keyguard != null && !keyguard.isKeyguardLocked();
        int dp = gapDp.getAsInt();
        float density = resources.getDisplayMetrics().density;
        int gapPx = dp < 0 ? -1 : Math.round(dp * density);
        int manualInset = manualInset(resources, density);
        Plan plan = plan(capsule, cameraRect, width, status, nativeY, divider, portrait, unlocked, gapPx, manualInset);
        if (plan != null) logAvoidance(capsule, cameraRect, plan, nativeY, dp, density);
        return plan;
    }
    /**
     * How far the theme dimen offset_between_status_bar_and_card would move the card when set
     * to the chosen dp, measured from the value this firmware actually uses; -1 when not chosen
     * or the dimen is not found.
     */
    private int manualInset(android.content.res.Resources resources, float density) {
        int offset = topOffsetDp.getAsInt();
        if (offset == Integer.MIN_VALUE) return -1;
        int id = 0;
        for (String pkg : new String[]{"com.oplus.systemui.plugins", "com.android.systemui"}) {
            id = resources.getIdentifier("offset_between_status_bar_and_card", "dimen", pkg);
            if (id != 0) break;
        }
        String note;
        int inset = -1;
        if (id == 0) note = "Card top offset: dimen offset_between_status_bar_and_card not found, following capsule top";
        else {
            float nativeOffset = resources.getDimension(id);
            inset = Math.round(nativeOffset - offset * density);
            note = String.format(java.util.Locale.ROOT, "Card top offset: native %.1fdp -> %ddp, card moves up %dpx",
                    nativeOffset / density, offset, inset);
            if (inset <= 0) inset = -1;
        }
        if (!note.equals(lastOffsetLog)) { lastOffsetLog = note; try { onInfo.accept(note); } catch (RuntimeException ignored) { } }
        return inset;
    }
    private String lastOffsetLog;
    /** One log line per distinct geometry, so the actual dp values can be read in the LSPosed log. */
    private void logAvoidance(Rect capsule, Rect camera, Plan plan, float nativeY, int dp, float density) {
        if (capsule == null || camera == null || density <= 0) return;
        // translationY = nativeY - original header space, see fullPlan().
        int original = Math.round(nativeY - plan.translationY());
        String message = String.format(java.util.Locale.ROOT,
                "Camera card avoidance gap %ddp: original header %dpx (%.1fdp), header %dpx (%.1fdp), content target %dpx (%.1fdp); camera bottom %.1fdp below capsule top",
                dp, original, original / density, plan.inset(), plan.inset() / density,
                plan.target(), plan.target() / density, (camera.bottom - capsule.top) / density);
        if (message.equals(lastAvoidLog)) return;
        lastAvoidLog = message;
        try { onInfo.accept(message); } catch (RuntimeException ignored) { }
    }
    void afterNativeLayout(ViewGroup container, boolean enabled) {
        // The original method has just written a fresh OEM value; never subtract twice.
        float nativeY = container.getTranslationY();
        Plan plan = enabled ? geometry(container, nativeY) : null;
        positions.put(container, new Position(nativeY, plan == null ? nativeY : plan.translationY(),
                plan == null ? 0 : plan.inset(), plan == null ? -1 : plan.target()));
        if (plan != null) container.setTranslationY(plan.translationY());
        updateChildren(container, enabled);
        if (!ownershipChecks.containsKey(container)) {
            android.view.ViewTreeObserver.OnPreDrawListener check = () -> {
                try { return reconcileOrder(container); }
                catch (RuntimeException | LinkageError error) {
                    try { restore(); } catch (RuntimeException | LinkageError cleanup) { error.addSuppressed(cleanup); }
                    try { onFailure.accept(error); } catch (RuntimeException | LinkageError ignored) { }
                    return true;
                }
            };
            ownershipChecks.put(container, check);
            container.getViewTreeObserver().addOnPreDrawListener(check);
        }
    }
    private boolean reconcileOrder(ViewGroup container) {
        Position position = positions.get(container);
        if (position == null) return true;
        boolean changed = false;
        for (int i = 0; i < container.getChildCount(); i++) {
            View card = container.getChildAt(i);
            if (!cardType.isInstance(card)) continue;
            View body = (View) Reflect.get(content, card);
            int before = body != null && body.getLayoutParams() instanceof FrameLayout.LayoutParams p ? p.topMargin : 0;
            int inset = itemPosition(container, card) == 0 ? position.inset() : 0;
            adjustContent(card, inset, inset == 0 ? -1 : position.target());
            if (inset == 0) { morphMasks.remove(card); restoreSurface(card); restoreBackgrounds(card); }
            else {
                // Child-only invalidations can reuse CardView's display list,
                // bypassing its dispatchDraw. Reconcile before any RenderNode is
                // recorded, including SDK replacement, clipping and shader size.
                boolean pending = card.isLayoutRequested();
                beforeDraw(card, true);
                if (!pending && card.isLayoutRequested()) changed = true;
            }
            if (body != null && body.getLayoutParams() instanceof FrameLayout.LayoutParams p && before != p.topMargin) changed = true;
        }
        // RecyclerView can move an unchanged holder without measuring it. Do not
        // submit the old header on a later item while its new layout is pending.
        return !changed;
    }
    void beforeMeasure(View card, boolean enabled) {
        ViewParent parent = card.getParent();
        ViewGroup container = containerType.isInstance(parent) ? (ViewGroup) parent : null;
        if (container == null) {
            // A scrapped holder can be measured between detach/reattach. Absence of
            // its parent is not a confirmed move away from the first list slot.
            if (!enabled) adjustContent(card, 0, -1);
            return;
        }
        Position position = positions.get(container);
        if (position != null) {
            Plan plan = enabled ? geometry(container, position.nativeY()) : null;
            position = new Position(position.nativeY(), plan == null ? position.nativeY() : plan.translationY(),
                    plan == null ? 0 : plan.inset(), plan == null ? -1 : plan.target());
            positions.put(container, position);
            if (container.getTranslationY() != position.appliedY()) container.setTranslationY(position.appliedY());
        }
        int index = enabled ? itemPosition(container, card) : -1;
        boolean first = enabled && position != null && index == 0;
        adjustContent(card, first ? position.inset() : 0, first ? position.target() : -1);
    }
    int intrinsicHeight(View card, int nativeHeight, boolean enabled) {
        if (!enabled || nativeHeight <= 0 || !cardType.isInstance(card)) return nativeHeight;
        View body = (View) Reflect.get(content, card);
        Margin margin = margins.get(body);
        if (body == null || margin == null || !(body.getLayoutParams() instanceof FrameLayout.LayoutParams params)
                || params.topMargin != margin.appliedTop()) return nativeHeight;
        return Math.addExact(nativeHeight, margin.appliedTop() - margin.nativeTop());
    }
    private int itemPosition(Object container, View card) {
        int index = (int) Reflect.call(adapterPosition, container, card);
        if (index < 0) index = (int) Reflect.call(layoutPosition, container, card);
        if (index >= 0) { stablePositions.put(card, index); return index; }
        // DiffUtil invalidates adapter positions before the existing holder is laid
        // out or removed. Keep that holder's geometry until a real new slot is known.
        return stablePositions.getOrDefault(card, -1);
    }
    /** All native branches, including interruption scenes, share one camera-top anchor. */
    static Morph morph(Rect mask, Rect camera, float scaleX, float scaleY, float pivotY,
                       float containerY, int cardTop, int width, int height) {
        if (mask == null || mask.isEmpty() || camera == null || camera.isEmpty()
                || !Float.isFinite(scaleX) || !Float.isFinite(scaleY) || scaleX <= .01f || scaleY <= .01f
                || !Float.isFinite(pivotY) || !Float.isFinite(containerY) || width <= 0 || height <= 0) return null;
        int w = Math.min(width, Math.max(mask.width(), (int) Math.ceil(Math.max(camera.width(), camera.height()) / scaleX)));
        int h = Math.min(height, Math.max(mask.height(), (int) Math.ceil(camera.height() / scaleY)));
        int left = Math.max(0, Math.min(width - w, Math.round(mask.exactCenterX() - w / 2f)));
        int top = Math.max(0, Math.min(height - h, Math.round(mask.exactCenterY() - h / 2f)));
        Rect adjusted = new Rect(left, top, left + w, top + h);
        float translation = camera.top - containerY - cardTop - pivotY + (pivotY - top) * scaleY;
        return new Morph(adjusted, translation);
    }
    private Rect cameraFor(View card, boolean enabled) {
        External added = enabled ? external.get(card) : null;
        if (added != null) return new Rect(added.camera());
        if (!enabled || !cardType.isInstance(card) || !containerType.isInstance(card.getParent())) return null;
        ViewGroup container = (ViewGroup) card.getParent(); Position position = positions.get(container);
        if (position == null || position.inset() <= 0 || itemPosition(container, card) != 0) return null;
        ViewGroup root = root(container);
        return root == null ? null : new Rect((Rect) Reflect.get(capsuleRect, root));
    }
    private Morph morphFor(View card, Rect mask, boolean enabled) {
        Rect camera = cameraFor(card, enabled);
        if (camera == null) return null;
        View parent = (View) card.getParent();
        return morph(mask, camera, card.getScaleX(), card.getScaleY(), card.getPivotY(),
                parent.getTranslationY() - parent.getScrollY(), card.getTop(), card.getWidth(), card.getHeight());
    }
    void beforeBlurBounds(View card, Rect mask, boolean enabled) {
        prepareMorphBounds(card, mask, enabled, true);
    }
    private void prepareMorphBounds(View card, Rect mask, boolean enabled, boolean blur) {
        Morph morph = morphFor(card, mask, enabled);
        if (morph == null) { morphMasks.remove(card); return; }
        // Blur and outline are separate native Rects; adapt both at their own entry.
        boolean enlarged = !mask.equals(morph.mask());
        morphMasks.put(card, new Rect(morph.mask()));
        mask.set(morph.mask());
        // The native size fragment keeps blur one local pixel inside its outline.
        // Clamping both independently to the camera size must not erase that guard.
        if (blur && enlarged && card.getScaleX() != 1f && mask.width() > 2 && mask.height() > 2) mask.inset(1, 1);
    }
    void beforeOutline(Object provider, View card, boolean enabled) {
        Rect rect = (Rect) Reflect.get(outlineRect, provider);
        if (rect != null) prepareMorphBounds(card, rect, enabled, false);
    }
    Rect outlineBounds(Object provider) {
        Rect rect = (Rect) Reflect.get(outlineRect, provider);
        return rect == null ? null : new Rect(rect);
    }
    void restoreOutlineBounds(Object provider, Rect original) {
        Rect rect = (Rect) Reflect.get(outlineRect, provider);
        if (rect != null && original != null) rect.set(original);
    }
    private Rect activeMorphMask(View card) {
        Rect mask = morphMasks.get(card);
        if (external.containsKey(card) && (mask == null
                || outline.getDeclaringClass().isInstance(card.getOutlineProvider())
                && outlineBounds(card.getOutlineProvider()) == null)) {
            // An instant card can appear directly, without a preceding capsule
            // morph. Its scale/translation scene writes before SizeFragment, and
            // cleanup replaces the outline with an unbounded provider. Both use
            // the whole card: neither a missing nor a stale clip is the origin.
            return new Rect(0, 0, card.getWidth(), card.getHeight());
        }
        return mask;
    }
    float translation(View card, float nativeY, boolean enabled) {
        if (aligning) return nativeY;
        Morph morph = morphFor(card, activeMorphMask(card), enabled);
        if (morph != null && cardType.isInstance(card) && card.getScaleX() == 1f && card.getScaleY() == 1f
                && outline.getDeclaringClass().isInstance(card.getOutlineProvider())
                && outlineBounds(card.getOutlineProvider()) == null) {
            // COUI RecyclerView overscroll is a parent RenderNode translation,
            // not a changed child top. A reversed dismissal can leave it behind.
            // Compensate that scroll only; preserve real list layout/scrolling.
            return ((View) card.getParent()).getScrollY();
        }
        return morph == null ? nativeY : morph.translationY();
    }
    void align(View card, boolean enabled) {
        float y = translation(card, card.getTranslationY(), enabled);
        if (y != card.getTranslationY()) {
            aligning = true;
            try { card.setTranslationY(y); } finally { aligning = false; }
        }
        updateSurface(card, enabled);
    }
    static float surfaceBlend(Rect mask, Rect camera, float scaleX, float scaleY) {
        if (mask == null || camera == null || camera.isEmpty()) return 1;
        float diameter = Math.max(camera.width(), camera.height());
        float size = Math.max(mask.width() * scaleX, mask.height() * scaleY);
        float fraction = Math.max(0, Math.min(1, (size / diameter - 1)));
        return fraction * fraction * (3 - 2 * fraction);
    }
    private float surfaceBlend(View card, boolean enabled) {
        Rect camera = cameraFor(card, enabled);
        return camera == null ? 1 : surfaceBlend(activeMorphMask(card), camera, card.getScaleX(), card.getScaleY());
    }
    private View blurBackground(View card) {
        if (card instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            if (blurBackgroundType.isInstance(group.getChildAt(i))) return group.getChildAt(i);
        return null;
    }
    void updateSurface(View card, boolean enabled) {
        float blend = surfaceBlend(card, enabled);
        updateMaterialOutline(card);
        var reference = headers.get(card);
        CameraHeaderSurface header = reference == null ? null : reference.get();
        if (header != null) { sizePaintLayer(header, card); header.update(blend); }
        if (blend >= 1) { restoreSurface(card); return; }
        float current = card.getElevation(); Elevation previous = elevations.get(card);
        float nativeValue = previous != null && current == previous.appliedValue() ? previous.nativeValue() : current;
        float value = nativeValue * blend;
        elevations.put(card, new Elevation(nativeValue, value));
        if (current != value) card.setElevation(value);
        View background = blurBackground(card);
        if (background != null) {
            mutedBlur.add(card);
            Reflect.call(blurAlpha, background, card.getAlpha() * blend);
        }
    }
    void drawSurface(View card, android.graphics.Canvas canvas, boolean enabled) {
        float blend = surfaceBlend(card, enabled);
        if (blend < 1) canvas.drawColor(android.graphics.Color.argb(Math.round((1 - blend) * 255), 0, 0, 0));
        // At rest the native material owns the entire surface. A permanent
        // black header would split template highlights even below the OEM glow.
    }
    void beforeDraw(View card, boolean enabled) {
        if (!enabled) return;
        reconcileMorph(card);
        // Native page/holder replacement and size animations can reset decoration
        // bounds after the card's onLayout callback has already completed.
        afterLayout(card, true);
        for (var entry : backgrounds.entrySet()) {
            View background = entry.getKey();
            if (background != null && within(background, card)) updateMaterial(background, entry.getValue().appliedBounds());
        }
        if (android.util.Log.isLoggable("COSCameraCard", android.util.Log.DEBUG)) traceFrame(card);
    }
    private void reconcileMorph(View card) {
        var provider = card.getOutlineProvider();
        if (!outline.getDeclaringClass().isInstance(provider) || cameraFor(card, true) == null) return;
        Rect nativeMask = outlineBounds(provider);
        if (nativeMask == null) {
            // OEM cleanup replaces the animated provider with an unbounded one.
            // A cached inset from the last frame is not the next scene's origin.
            if (card.getScaleX() != 1f || card.getScaleY() != 1f) return;
            nativeMask = new Rect(0, 0, card.getWidth(), card.getHeight());
        }
        prepareMorphBounds(card, nativeMask, true, false);
        align(card, true);
    }
    private void traceFrame(View card) {
        StringBuilder trace = new StringBuilder("mask=").append(morphMasks.get(card))
                .append(" camera=").append(cameraFor(card, true));
        if (card.getParent() instanceof ViewGroup container) trace.append(" position=").append(positions.get(container));
        android.view.ViewOutlineProvider provider = card.getOutlineProvider();
        if (outline.getDeclaringClass().isInstance(provider)) trace.append(" outline=").append(Reflect.get(outlineRect, provider));
        traceNode(card, trace);
        if (card.getParent() instanceof View parent) traceNode(parent, trace);
        View background = blurBackground(card);
        if (background != null) traceNode(background, trace);
        View body = (View) Reflect.get(content, card);
        if (body != null) {
            traceNode(body, trace);
            if (body instanceof ViewGroup group) for (int i = 0; i < Math.min(group.getChildCount(), 4); i++)
                traceNode(group.getChildAt(i), trace);
        }
        for (View view : backgrounds.keySet()) if (view != null && within(view, card)) traceNode(view, trace);
        String value = trace.toString();
        if (!value.equals(frameTraces.put(card, value))) android.util.Log.i("COSCameraFrame", value);
    }
    private static void traceNode(View view, StringBuilder trace) {
        android.graphics.Matrix matrix = new android.graphics.Matrix(); view.transformMatrixToGlobal(matrix);
        float[] origin = {0,0}; matrix.mapPoints(origin);
        trace.append(" | ").append(view.getClass().getSimpleName()).append('#').append(System.identityHashCode(view))
                .append(' ').append(view.getLeft()).append(',').append(view.getTop()).append('-').append(view.getRight()).append(',').append(view.getBottom())
                .append(" screen=").append(origin[0]).append(',').append(origin[1])
                .append(" s=").append(view.getScaleX()).append(',').append(view.getScaleY())
                .append(" t=").append(view.getTranslationX()).append(',').append(view.getTranslationY())
                .append(" scroll=").append(view.getScrollX()).append(',').append(view.getScrollY())
                .append(" a=").append(view.getAlpha()).append(" clip=").append(view.getClipToOutline()).append('/').append(view.getClipBounds());
    }
    private void updateMaterial(View background, Rect bounds) {
        materials.resize(background.getBackground(), bounds.width(), bounds.height());
        materials.resize(background.getForeground(), bounds.width(), bounds.height());
    }
    private void restoreSurface(View card) {
        Elevation elevation = elevations.remove(card);
        if (elevation != null && card.getElevation() == elevation.appliedValue()) card.setElevation(elevation.nativeValue());
        if (mutedBlur.remove(card)) {
            View background = blurBackground(card);
            if (background != null) Reflect.call(blurAlpha, background, card.getAlpha());
        }
    }
    private void restoreSurfaces(View ancestor) {
        var cards = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<View, Boolean>());
        cards.addAll(elevations.keySet()); cards.addAll(mutedBlur);
        for (View card : cards) if (card != null && (ancestor == null || within(card, ancestor))) restoreSurface(card);
    }
    private void updateChildren(ViewGroup container, boolean enabled) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View card = container.getChildAt(i);
            if (cardType.isInstance(card)) beforeMeasure(card, enabled);
        }
    }
    private void adjustContent(View card, int inset, int target) {
        View child = (View) Reflect.get(content, card);
        if (child == null || !(child.getLayoutParams() instanceof FrameLayout.LayoutParams params)) return;
        Margin previous = margins.get(child);
        int nativeTop = previous != null && params.topMargin == previous.appliedTop()
                ? previous.nativeTop() : params.topMargin;
        int top = nativeTop + inset;
        if (inset > 0 && target >= 0) {
            // Place the first visible element at the target, never lower than the original.
            Integer lead = leads.get(card);
            top = Math.min(nativeTop + inset, CameraAvoidance.boxTop(target, lead == null ? -1 : lead));
            // Whatever the margin cannot absorb comes out of the content's own top spacing.
            if (child instanceof ViewGroup body) trimmer.apply(body, true, lead == null ? 0 : Math.max(0, lead - target));
        } else if (child instanceof ViewGroup body) trimmer.restore(body);
        if (params.topMargin != top) {
            params.topMargin = top;
            child.setLayoutParams(params);
        }
        if (inset == 0) { margins.remove(child); cardInsets.remove(card); }
        else {
            margins.put(child, new Margin(nativeTop, top));
            cardInsets.put(card, Math.max(1, top - nativeTop));
        }
    }
    void afterLayout(View card, boolean enabled) {
        ViewParent parent = card.getParent();
        if (enabled && !containerType.isInstance(parent) && stablePositions.containsKey(card)) return;
        Position position = parent instanceof ViewGroup group ? positions.get(group) : null;
        int inset = enabled && position != null && itemPosition(parent, card) == 0
                ? position.inset() : 0;
        View child = (View) Reflect.get(content, card);
        if (inset > 0 && child instanceof ViewGroup body) {
            int measured = CameraAvoidance.lead(body, 0);
            int lead = measured < 0 ? -1 : measured + trimmer.trimmed(body);
            if (lead >= 0 && !Integer.valueOf(lead).equals(leads.put(card, lead)) && position.target() >= 0) {
                onInfo.accept("Camera card content lead " + lead + "px, target " + position.target() + "px");
                card.post(card::requestLayout);
            }
            inset = cardInsets.getOrDefault(card, inset);
        }
        if (inset <= 0) { restoreBackgrounds(card); removeHeader(card); return; }
        ArrayList<View> layers = new ArrayList<>();findDecorativeBackgrounds(child, child, layers);
        for (View background : layers) {
            extendBackground(card, background, inset);
            decorateLayer(background, card, 0);
        }
        extendContainerDecorations(child, child, card, inset);
        extendStrokes(child, child, card, inset);
        syncMaterials((ViewGroup) card, child);
        syncForeground(card);
        ensureHeader((ViewGroup) card);
    }
    boolean handles(View card) {
        if (cardType.isInstance(card)) return true;
        return !external.isEmpty() && card.getParent() != null
                && card.getParent().getClass() == externalParentType && external.containsKey(card);
    }
    void prepareInstant(FrameLayout card, int inset, Rect camera) {
        if (card.getParent() == null) return;
        externalParentType = card.getParent().getClass();
        external.put(card, new External(inset, new Rect(camera)));
        // The OEM root blur already spans the padded root. Extend only known
        // decoration inside its content; text and button hit targets stay native.
        ArrayList<View> layers = new ArrayList<>();
        for (int i = 0; i < card.getChildCount(); i++) {
            View body = card.getChildAt(i);
            if (body instanceof CameraHeaderSurface) continue;
            findDecorativeBackgrounds(body, body, layers);
            extendContainerDecorations(body, body, card, inset);
            extendStrokes(body, body, card, inset);
        }
        for (View background : layers) { extendBackground(card, background, inset); decorateLayer(background, card, 0); }
        syncMaterials(card, null);
        syncForeground(card);
        ensureHeader(card);
    }
    private void syncForeground(View card) {
        var reference = foregrounds.get(card);
        CameraCardForeground previous = reference == null ? null : reference.get();
        var current = card.getForeground();
        if (previous != null && current == previous) { previous.bind(); return; }
        if (previous != null) previous.release();
        foregrounds.remove(card);
        // Only the exact black cover constructed by the OEM scene builders.
        // Gradients, ripple/spotlight effects and SDK custom foregrounds remain.
        if (current == null || current.getClass() != android.graphics.drawable.ColorDrawable.class
                || (((android.graphics.drawable.ColorDrawable) current).getColor() & 0x00ffffff) != 0) return;
        var proxy = new CameraCardForeground(current);
        card.setForeground(proxy); proxy.bind();
        foregrounds.put(card, new java.lang.ref.WeakReference<>(proxy));
    }
    private void syncMaterials(ViewGroup card, View body) {
        var paints = new ArrayList<CameraMaterialSurface.Paint>();
        for (int i = 0; i < card.getChildCount(); i++) collectMaterials(card.getChildAt(i), body, paints);
        var reference = materialSurfaces.get(card);
        CameraMaterialSurface surface = reference == null ? null : reference.get();
        if (paints.isEmpty()) { removeMaterialSurface(card); return; }
        if (surface == null) {
            surface = new CameraMaterialSurface(card.getContext(), card);
            materialSurfaces.put(card, new java.lang.ref.WeakReference<>(surface));
        }
        // Native blur stays underneath; SDK content and the card's own spotlight
        // stay above. Zero layout size keeps instant WRAP_CONTENT measurement native.
        int index = body == null ? 0 : card.indexOfChild(body);
        if (surface.getParent() == null) card.addView(surface, Math.max(0, index), new FrameLayout.LayoutParams(0, 0));
        surface.update(paints);
        updateMaterialOutline(card);
    }
    private void updateMaterialOutline(View card) {
        var reference = materialSurfaces.get(card);
        CameraMaterialSurface surface = reference == null ? null : reference.get();
        if (surface == null) return;
        sizePaintLayer(surface, card);
        // Native scale/size fragments can reuse the parent's recorded display
        // list. Its material child has an independent RenderNode outline: keep
        // that clip in the same frame, rather than waiting for dispatchDraw at
        // the end of the scene to reveal the full contour.
        Rect mask = activeMorphMask(card);
        Rect material = mask == null ? new Rect(0, 0, card.getWidth(), card.getHeight()) : new Rect(mask);
        surface.updateOutline(new MaterialOutline(card.getOutlineProvider(), mask == null ? null : new Rect(mask),
                card.getWidth(), card.getHeight()), material);
    }
    private void collectMaterials(View view, View body, List<CameraMaterialSurface.Paint> paints) {
        if (view instanceof CameraMaterialSurface || view instanceof CameraHeaderSurface || view.getVisibility() != View.VISIBLE) return;
        Decorations pair = decorations.get(view);
        // The body's blur/alpha animates the controls, not the independently sized
        // card material. Preserve a decorative layer's own SDK crossfade alpha.
        float alpha = view == body ? 1f : Math.max(0f, Math.min(1f, view.getAlpha()));
        if (pair != null && pair.background() != null) paints.add(new CameraMaterialSurface.Paint(pair.background(), alpha));
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            collectMaterials(group.getChildAt(i), body, paints);
        // Only empty full-page decoration foregrounds move below the controls.
        // A content-bearing SDK foreground can be a spotlight and remains native.
        if (pair != null && pair.foreground() != null && backgrounds.containsKey(view))
            paints.add(new CameraMaterialSurface.Paint(pair.foreground(), alpha));
    }
    private void removeMaterialSurface(View card) {
        var reference = materialSurfaces.remove(card);
        CameraMaterialSurface surface = reference == null ? null : reference.get();
        if (surface != null) {
            surface.release();
            if (surface.getParent() instanceof ViewGroup parent) parent.removeView(surface);
        }
    }
    void releaseInstant(View card) {
        external.remove(card); morphMasks.remove(card); restoreSurface(card); restoreBackgrounds(card);
    }
    Rect instantTouchBounds(View card) {
        if (!external.containsKey(card) || card.getWidth() <= 0 || card.getHeight() <= 0) return null;
        Rect mask = activeMorphMask(card);
        var bounds = new android.graphics.RectF(mask == null ? new Rect(0, 0, card.getWidth(), card.getHeight()) : mask);
        var matrix = new android.graphics.Matrix();
        card.transformMatrixToGlobal(matrix); matrix.mapRect(bounds);
        int[] window = new int[2]; card.getRootView().getLocationOnScreen(window);
        bounds.offset(-window[0], -window[1]);
        if (!Float.isFinite(bounds.left) || !Float.isFinite(bounds.top)
                || !Float.isFinite(bounds.right) || !Float.isFinite(bounds.bottom)) return null;
        Rect result = new Rect(); bounds.roundOut(result);
        View root = card.getRootView();
        return result.isEmpty() || !result.intersect(0, 0, root.getWidth(), root.getHeight()) ? null : result;
    }
    private void ensureHeader(ViewGroup card) {
        var reference = headers.get(card);
        CameraHeaderSurface header = reference == null ? null : reference.get();
        if (header == null) {
            var owner = new java.lang.ref.WeakReference<View>(card);
            header = new CameraHeaderSurface(card.getContext(), canvas -> {
                View view = owner.get();
                if (view != null) drawSurface(view, canvas, true);
            });
            headers.put(card, new java.lang.ref.WeakReference<>(header));
        }
        // Ordinary CardView measures its body explicitly, but instant templates
        // use FrameLayout's WRAP_CONTENT measurement. A MATCH_PARENT default View
        // consumes the full AT_MOST screen height and stretches those cards.
        // Keep this paint-only child out of measurement; size it from the owner
        // immediately before drawing instead.
        if (header.getParent() == null) card.addView(header, new FrameLayout.LayoutParams(0, 0));
        if (header.getParent() != card) return;
        if (card.indexOfChild(header) != card.getChildCount() - 1) header.bringToFront();
        sizePaintLayer(header, card);
        header.update(surfaceBlend(card, true));
    }
    private static void sizePaintLayer(View layer, View card) {
        if (layer.getWidth() != card.getWidth() || layer.getHeight() != card.getHeight())
            layer.measure(View.MeasureSpec.makeMeasureSpec(card.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(card.getHeight(), View.MeasureSpec.EXACTLY));
        // A layout-only pass can apply the SDK root's content padding without
        // changing our prepared size. These paint planes belong at the card's
        // origin, including while its recorded display list is being reused.
        if (layer.getLeft() != 0 || layer.getTop() != 0
                || layer.getRight() != card.getWidth() || layer.getBottom() != card.getHeight())
            layer.layout(0, 0, card.getWidth(), card.getHeight());
    }
    private void removeHeader(View card) {
        var reference = headers.remove(card);
        CameraHeaderSurface header = reference == null ? null : reference.get();
        if (header != null && header.getParent() instanceof ViewGroup parent) parent.removeView(header);
    }
    private void extendContainerDecorations(View view, View body, View card, int inset) {
        if (view == null) return;
        if (view instanceof FrameLayout group && view.getClass() == FrameLayout.class
                && group.getChildCount() > 0 && fullPageBounds(view, body)
                && !view.isClickable() && !view.isLongClickable() && !view.isFocusable()) {
            decorateLayer(view, card, inset);
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            extendContainerDecorations(group.getChildAt(i), body, card, inset);
    }
    /**
     * The light outline drawn around cards on dark backgrounds is a stroke foreground
     * (ColorOS GradientDrawable on the shared page, or the SDK "cardStroke" layer) sized to
     * the native content. Wrap it so it is drawn over the camera header as well; only the
     * drawable is extended, never the view's frame, children or touch targets.
     */
    private void extendStrokes(View view, View body, View card, int inset) {
        if (view == null || view instanceof CameraHeaderSurface || view instanceof CameraMaterialSurface) return;
        // Layers whose frame already moved into the header are handled by extendBackground.
        if (!backgrounds.containsKey(view) && fullPageBounds(view, body)) {
            boolean strokeHost = "cardStroke".equals(view.getTag());
            android.graphics.drawable.Drawable foreground = view.getForeground();
            if (foreground instanceof CardHeaderDrawable wrapper) foreground = wrapper.nativeDrawable;
            if (strokeHost) decorateLayer(view, card, inset);
            else if (foreground instanceof android.graphics.drawable.GradientDrawable) decorateForeground(view, card, inset);
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            extendStrokes(group.getChildAt(i), body, card, inset);
    }
    private void decorateForeground(View view, View card, int inset) {
        Decorations previous = decorations.get(view);
        CardHeaderDrawable fg = decorate(view.getForeground(), previous == null ? null : previous.foreground(), inset);
        if (fg == null) return;
        decorations.put(view, new Decorations(previous == null ? null : previous.background(), fg));
        if (view.getForeground() != fg) view.setForeground(fg);
        fg.bind();
        unclip(view, card);
    }
    private void decorateLayer(View view, View card, int inset) {
        Decorations previous = decorations.get(view);
        CardHeaderDrawable bg = decorate(view.getBackground(), previous == null ? null : previous.background(), inset);
        CardHeaderDrawable fg = decorate(view.getForeground(), previous == null ? null : previous.foreground(), inset);
        if (bg != null || fg != null) {
            decorations.put(view, new Decorations(bg, fg));
            if (bg != null && view.getBackground() != bg) view.setBackground(bg);
            if (fg != null && view.getForeground() != fg) view.setForeground(fg);
            // View's setters clear the old drawable callback after wrapper creation.
            if (bg != null) bg.bind();
            if (fg != null) fg.bind();
            unclip(view, card);
        }
    }
    private CardHeaderDrawable decorate(android.graphics.drawable.Drawable current, CardHeaderDrawable previous, int inset) {
        if (current == previous && previous != null) { previous.inset(inset); return previous; }
        if (previous != null) previous.release();
        return current == null ? null : new CardHeaderDrawable(current, inset, materials);
    }
    private boolean fullPageBounds(View view, View body) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        for (View current = view; current != body;) {
            if (current.getLeft() != 0 || current.getTop() != 0 || current.getWidth() != body.getWidth()
                    || current.getHeight() != body.getHeight() || !(current.getParent() instanceof View ancestor)) return false;
            current = ancestor;
        }
        return true;
    }
    private void unclip(View view, View card) {
        for (View current = view; current != null; current = current.getParent() instanceof View parent ? parent : null) {
            if (current == null) break;
            if (current instanceof ViewGroup group) {
                clips.putIfAbsent(group, new Clip(group.getClipChildren(), group.getClipToPadding(), group.getClipToOutline(), current != card));
                if (group.getClipChildren()) group.setClipChildren(false);
                if (group.getClipToPadding()) group.setClipToPadding(false);
                if (current != card && group.getClipToOutline()) group.setClipToOutline(false);
            }
            if (current == card) break;
        }
    }
    private void extendBackground(View card, View background, int inset) {
        // Extend only the native decorative layer, never its text or touch targets.
        Rect bounds = new Rect(background.getLeft(), background.getTop(), background.getRight(), background.getBottom());
        Backdrop previous = backgrounds.get(background);
        View parent = background.getParent() instanceof View view ? view : null;
        boolean fillsParent = previous != null ? previous.fillsParent()
                : parent != null && bounds.left == 0 && bounds.top == 0
                && bounds.width() == parent.getWidth() && bounds.height() == parent.getHeight();
        if (fillsParent && parent != null && parent.getWidth() > 0 && parent.getHeight() > 0)
            bounds = new Rect(0, 0, parent.getWidth(), parent.getHeight());
        else if (previous != null && bounds.equals(previous.appliedBounds())) bounds = previous.nativeBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        backgrounds.put(background, new Backdrop(new Rect(bounds),
                new Rect(bounds.left, bounds.top - inset, bounds.right, bounds.bottom), fillsParent));
        unclip(background, card);
        int height = bounds.height() + inset;
        if (background.getMeasuredWidth() != bounds.width() || background.getMeasuredHeight() != height)
            background.measure(View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        // SDK LayoutTransition animates top/bottom during a content update. Its
        // negative intermediate top is not a new native baseline. Use the same
        // frame setter as the OEM blur layer: do not start another layout tween
        // merely by extending an empty decorative view into the camera header.
        Reflect.call(setFrame, background, bounds.left, bounds.top - inset, bounds.right, bounds.bottom);
        updateMaterial(background, backgrounds.get(background).appliedBounds());
    }
    private void findDecorativeBackgrounds(View view, View body, List<View> result) {
        if (view == null) return;
        if (mediaBackgroundType != null && mediaBackgroundType.isInstance(view)
                || isFullPageDecoration(view, body)) { result.add(view); return; }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            findDecorativeBackgrounds(group.getChildAt(i), body, result);
    }
    private boolean isFullPageDecoration(View view, View body) {
        // A shared renderer can change its class names independently of SystemUI.
        // Only empty layout layers are generic candidates: never text, images,
        // custom-drawn content or interactive children that happen to fill a page.
        if (view == body || !(view instanceof FrameLayout group)
                || view.getClass() != FrameLayout.class && (pageBackgroundType == null || !pageBackgroundType.isInstance(view))
                || group.getChildCount() != 0
                || view.isClickable() || view.isLongClickable() || view.isFocusable()
                || view.hasOnClickListeners() || view.hasFocusable()
                || view.getBackground() == null && view.getForeground() == null) return false;
        Rect bounds = new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
        Backdrop previous = backgrounds.get(view);
        if (previous != null && previous.fillsParent() && view.getParent() instanceof View parent)
            bounds = new Rect(0, 0, parent.getWidth(), parent.getHeight());
        else if (previous != null && bounds.equals(previous.appliedBounds())) bounds = previous.nativeBounds();
        if (!(view.getParent() instanceof View parent) || bounds.left != 0 || bounds.top != 0
                || bounds.width() <= 0 || bounds.height() <= 0
                || bounds.width() != parent.getWidth() || bounds.height() != parent.getHeight()) return false;
        for (View current = parent; current != body;) {
            if (current.getLeft() != 0 || current.getTop() != 0 || current.getWidth() != body.getWidth()
                    || current.getHeight() != body.getHeight()
                    || !(current.getParent() instanceof View ancestor)) return false;
            current = ancestor;
        }
        return true;
    }
    private View findMediaBackground(View view) {
        if (view == null || mediaBackgroundType == null) return null;
        if (mediaBackgroundType.isInstance(view)) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = findMediaBackground(group.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private static boolean within(View view, View ancestor) {
        for (View current = view; current != null; current = current.getParent() instanceof View p ? p : null)
            if (current == ancestor) return true;
        return false;
    }
    private void restoreBackgrounds(View card) {
        for (View owner : new ArrayList<>(materialSurfaces.keySet()))
            if (owner != null && (card == null || within(owner, card))) removeMaterialSurface(owner);
        for (View owner : new ArrayList<>(foregrounds.keySet())) {
            if (owner == null || card != null && !within(owner, card)) continue;
            var reference = foregrounds.remove(owner);
            CameraCardForeground proxy = reference == null ? null : reference.get();
            if (proxy != null) {
                proxy.release();
                if (owner.getForeground() == proxy) owner.setForeground(proxy.getDrawable());
            }
        }
        for (View owner : new ArrayList<>(headers.keySet()))
            if (owner != null && (card == null || within(owner, card))) removeHeader(owner);
        for (var entry : new ArrayList<>(decorations.entrySet())) {
            View view = entry.getKey(); Decorations pair = entry.getValue();
            if (view == null || card != null && !within(view, card)) continue;
            if (pair.background() != null) {
                pair.background().release();
                if (view.getBackground() == pair.background()) view.setBackground(pair.background().nativeDrawable);
            }
            if (pair.foreground() != null) {
                pair.foreground().release();
                if (view.getForeground() == pair.foreground()) view.setForeground(pair.foreground().nativeDrawable);
            }
            decorations.remove(view);
        }
        for (var entry : new ArrayList<>(backgrounds.entrySet())) {
            View background = entry.getKey(); Backdrop backdrop = entry.getValue();
            if (background == null || card != null && !within(background, card)) continue;
            materials.restore(background.getBackground());materials.restore(background.getForeground());
            Rect current = new Rect(background.getLeft(), background.getTop(), background.getRight(), background.getBottom());
            Rect bounds = backdrop.nativeBounds();
            if (current.equals(backdrop.appliedBounds())) {
                background.measure(View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.EXACTLY));
                background.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
            }
            backgrounds.remove(background);
        }
        for (var entry : new ArrayList<>(clips.entrySet())) {
            ViewGroup group = entry.getKey(); Clip clip = entry.getValue();
            if (group == null || card != null && !within(group, card)) continue;
            if (!group.getClipChildren()) group.setClipChildren(clip.children());
            if (!group.getClipToPadding()) group.setClipToPadding(clip.padding());
            if (clip.ownsOutline() && !group.getClipToOutline()) group.setClipToOutline(clip.outline());
            clips.remove(group);
        }
    }
    void refresh(boolean enabled) {
        for (ViewGroup container : new ArrayList<>(positions.keySet())) {
            if (container == null) continue;
            // Re-enter the OEM updater; its hook will obtain a fresh native baseline.
            Reflect.call(layout, container);
            container.requestLayout();
        }
        if (!enabled) { restoreSurfaces(null); restoreBackgrounds(null); restoreMargins(); }
    }
    void detached(ViewGroup container) {
        var check = ownershipChecks.remove(container);
        if (check != null && container.getViewTreeObserver().isAlive()) container.getViewTreeObserver().removeOnPreDrawListener(check);
        Position previous = positions.remove(container);
        if (previous != null && container.getTranslationY() == previous.appliedY())
            container.setTranslationY(previous.nativeY());
        updateChildren(container, false);
        restoreSurfaces(container);
        for (int i = 0; i < container.getChildCount(); i++) {
            stablePositions.remove(container.getChildAt(i)); morphMasks.remove(container.getChildAt(i));
        }
        restoreBackgrounds(container);
    }
    void restore() {
        for (ViewGroup container : new ArrayList<>(positions.keySet())) if (container != null) detached(container);
        restoreMargins();
        restoreBackgrounds(null);
        materials.restore();
        stablePositions.clear();
        morphMasks.clear();
        frameTraces.clear();
        external.clear();
        restoreSurfaces(null);
    }
    String describe(Object owner) {
        if (owner instanceof ViewGroup group && group.getClass() == externalParentType) {
            StringBuilder result = new StringBuilder("instant");
            describeTree(group, result, 0, new int[]{60});
            return result.toString();
        }
        View card = cardType.isInstance(owner) ? (View) owner : null;
        ViewGroup container = containerType.isInstance(owner) ? (ViewGroup) owner
                : card != null && containerType.isInstance(card.getParent()) ? (ViewGroup) card.getParent() : null;
        if (container == null) return "detached";
        Position position = positions.get(container);
        ViewGroup root = root(container);
        StringBuilder result = new StringBuilder("listY=").append(container.getTranslationY())
                .append(" stored=").append(position).append(" layoutPending=").append(container.isLayoutRequested())
                .append(" anchor=").append(root == null ? null : Reflect.get(capsuleRect, root));
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i); if (!cardType.isInstance(child)) continue;
            View body = (View) Reflect.get(content, child);
            result.append(" card[").append(i).append("] pos=").append(Reflect.call(adapterPosition, container, child))
                    .append('/').append(Reflect.call(layoutPosition, container, child))
                    .append(" top=").append(child.getTop()).append(" h=").append(child.getHeight())
                    .append('/').append(child.getMeasuredHeight()).append(" ty=").append(child.getTranslationY())
                    .append(" scale=").append(child.getScaleY())
                    .append(" margin=").append(body != null && body.getLayoutParams() instanceof FrameLayout.LayoutParams p ? p.topMargin : -1);
            describeTree(body, result, 0, new int[]{60});
        }
        return result.toString();
    }
    String describeInstantFrame(View view) {
        var reference = materialSurfaces.get(view);
        var surface = reference == null ? null : reference.get();
        return "size=" + view.getWidth() + "x" + view.getHeight() + " scale=" + view.getScaleY()
                + " alpha=" + view.getAlpha() + " mask=" + activeMorphMask(view)
                + " material=" + (surface == null ? "null" : surface.describe());
    }
    private static void describeTree(View view, StringBuilder result, int depth, int[] remaining) {
        if (view == null || depth > 6 || remaining[0]-- <= 0) return;
        result.append(" {d=").append(depth).append(' ').append(view.getClass().getName())
                .append(' ').append(view.getLeft()).append(',').append(view.getTop()).append('-')
                .append(view.getRight()).append(',').append(view.getBottom()).append(" pad=").append(view.getPaddingTop())
                .append(" bg=").append(view.getBackground() == null ? "null" : view.getBackground().getClass().getName())
                .append(" fg=").append(view.getForeground() == null ? "null" : view.getForeground().getClass().getName()).append('}');
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            describeTree(group.getChildAt(i), result, depth + 1, remaining);
    }
    private void restoreMargins() {
        for (var entry : new ArrayList<>(margins.entrySet())) {
            View child = entry.getKey(); Margin margin = entry.getValue();
            if (child != null && child.getLayoutParams() instanceof FrameLayout.LayoutParams params
                    && params.topMargin == margin.appliedTop()) {
                params.topMargin = margin.nativeTop(); child.setLayoutParams(params);
            }
        }
        margins.clear(); cardInsets.clear(); leads.clear(); trimmer.clear();
    }
}
