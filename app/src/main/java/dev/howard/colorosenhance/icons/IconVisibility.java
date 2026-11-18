package dev.howard.colorosenhance.icons;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

/** No OEM holder, blocklist, animation, or pipeline state is changed. */
final class IconVisibility {
    private final Map<ViewGroup, String> groups = new WeakHashMap<>();
    private final Map<ViewGroup, NativeIconPolicy> policies = new WeakHashMap<>();
    private final Map<View, Icon> icons = new WeakHashMap<>();
    private final Map<ViewGroup, Boolean> drawGroups = new WeakHashMap<>();
    private final Set<ViewGroup> keyguardDrawGroups=Collections.newSetFromMap(new WeakHashMap<>());
    private NativeIconPolicy keyguardPolicy=new NativeIconPolicy(List.of(),false);
    private final Set<View> mirrors = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> panelClocks = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ViewGroup> panelClockParents = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<View, SpecialDraw> specialDraws = new WeakHashMap<>();
    private final Map<View, Special> specials = new WeakHashMap<>();
    private final Supplier<StatusIconConfig> config;
    private HomeIconOrder ordering;
    private StatusIconConfig.BluetoothState bluetooth = StatusIconConfig.BluetoothState.unknown();
    private boolean applying;
    private boolean seamless;
    private float fraction;
    private float keyguardFraction;
    private final ThreadLocal<Frame> drawing = new ThreadLocal<>();
    private final Map<ViewGroup,IconSpaceContinuity> spacing=new WeakHashMap<>();
    private record Icon(String slot, Method visible, Method blocked, Method state) {}
    private record SpecialDraw(String slot, WeakReference<View> source, String location) {}
    static final class Frame {
        final ViewGroup parent;
        final Map<View,IconDrawPlan.Transform> transforms;
        final Frame previous;
        Frame(ViewGroup parent, Map<View,IconDrawPlan.Transform> transforms, Frame previous) {
            this.parent=parent;this.transforms=transforms;this.previous=previous;
        }
    }
    private static final class Special {
        final String slot,location;
        int nativeVisibility;
        boolean overridden;
        Special(String slot, int nativeVisibility,String location) { this.slot = slot; this.nativeVisibility = nativeVisibility;this.location=location; }
    }
    IconVisibility(Supplier<StatusIconConfig> config) { this.config = config; }
    void ordering(HomeIconOrder ordering){this.ordering=ordering;ordering.pixels(this::invalidatePresentation);}
    private void invalidatePresentation(){
        for(ViewGroup group:new ArrayList<>(drawGroups.keySet()))group.invalidate();
        for(View mirror:new ArrayList<>(mirrors))mirror.invalidate();
    }
    void group(ViewGroup group, String location) {
        group(group, location, new NativeIconPolicy(List.of(),true));
    }
    void group(ViewGroup group, String location, NativeIconPolicy policy) {
        groups.put(group, location);policies.put(group,policy);
        if (Set.of("HOME","QS","SHADE_CARRIER_GROUP","KEYGUARD").contains(location)) drawGroups.put(group, true);
        if("KEYGUARD".equals(location)){keyguardDrawGroups.add(group);keyguardPolicy=policy;}
    }
    void icon(View view, String slot, Method visible, Method blocked) {
        icons.put(view,new Icon(slot,visible,blocked,IconReflection.optional(view.getClass(),"getVisibleState")));
    }
    void seamless(boolean ready) { seamless=ready;refresh(); }
    void mirror(View view, boolean home) { if(home)mirrors.add(view);else mirrors.remove(view); }
    void fraction(float value) {
        if (!Float.isFinite(value)) return;
        float next=Math.max(0,Math.min(1,value));
        if (fraction==next) return;
        fraction=next;
        if (config.get().enabled()) {
            for (ViewGroup group : new ArrayList<>(drawGroups.keySet())) group.invalidate();
            // The status bar window may stop drawing while the shade is open. Its OnDrawListener
            // then cannot invalidate the OEM mirror's hardware display list for the next fraction.
            for (View mirror : new ArrayList<>(mirrors)) mirror.invalidate();
            for (View clock : new ArrayList<>(panelClocks)) clock.invalidate();
            for (ViewGroup parent : new ArrayList<>(panelClockParents)) parent.invalidate();
        }
    }
    void keyguardFraction(float value) {
        if(!Float.isFinite(value))return;
        float next=Math.max(0,Math.min(1,value));
        if(keyguardFraction==next)return;
        keyguardFraction=next;
        if(config.get().enabled()) {
            for(ViewGroup group:new ArrayList<>(keyguardDrawGroups))group.invalidate();
            for(View mirror:new ArrayList<>(mirrors))mirror.invalidate();
        }
    }
    void bluetooth(StatusIconConfig.BluetoothState state) { bluetooth = state; refresh(); }
    boolean visible(View view, String slot, boolean nativeVisible) {
        if (!nativeVisible || !(view.getParent() instanceof ViewGroup parent)) return nativeVisible;
        if("KEYGUARD".equals(groups.get(parent))) {
            if(!config.get().appliesTo("KEYGUARD") || seamless)return nativeVisible;
            NativeIconPolicy policy=policies.get(parent);
            if(policy!=null && policy.userHidden(slot))return false;
        }
        if (!stockAllows(parent,view,slot)) return nativeVisible;
        return seamless ? !config.get().restrictsBluetooth(slot, groups.get(parent), bluetooth)
                : !config.get().restricts(slot, groups.get(parent), bluetooth);
    }
    void special(View view, String slot) {
        special(view,slot,"HOME");
    }
    void special(View view,String slot,String location) {
        specials.computeIfAbsent(view, key -> new Special(slot, view.getVisibility(),location));
        registerDraw(view,slot,location);
        applySpecial(view, specials.get(view));
    }
    void panelBattery(View icons, View battery) {
        panelBattery(icons,battery,"QS");
    }
    void panelBattery(View icons,View battery,String location) {
        // Header battery and icons need not share a LinearLayout on newer ColorOS.
        View branch=icons;
        while(branch.getParent() instanceof ViewGroup parent) {
            View target=battery;
            while(target.getParent()!=parent && target.getParent() instanceof ViewGroup ancestor)target=ancestor;
            if(target.getParent()==parent && target!=branch) {
                specialDraws.put(target,new SpecialDraw("battery",new WeakReference<>(battery),location));
                if("KEYGUARD".equals(location))keyguardDrawGroups.add(parent);
                drawGroups.put(parent,true);return;
            }
            branch=parent;
        }
    }
    void panelClock(View clock) {
        if(clock==null)return;
        panelClocks.add(clock);
        // The additional layer alpha is recorded by the parent's drawChild, not the TextView.
        // Invalidating only the child's render node leaves the parent's first alpha cached.
        if(clock.getParent() instanceof ViewGroup parent)panelClockParents.add(parent);
    }
    boolean reveal(View child) {
        // The notification clock scales from a small status clock into a large header clock.
        // A horizontal icon-sized reveal cuts different digits on these two native drawings.
        SpecialDraw special=specialDraws.get(child);
        return !panelClocks.contains(child) && (special==null || !"clock".equals(special.slot));
    }
    private void registerDraw(View view, String slot,String location) {
        View target=view;
        while (target.getParent() instanceof ViewGroup parent) {
            if (parent instanceof LinearLayout layout && layout.getOrientation()==LinearLayout.HORIZONTAL) {
                specialDraws.put(target,new SpecialDraw(slot,new WeakReference<>(view),location));
                drawGroups.put(parent,!"clock".equals(slot));
                if("KEYGUARD".equals(location))keyguardDrawGroups.add(parent);
                break;
            }
            target=parent;
        }
    }
    int visibility(View view, int requested) {
        if (applying) return requested;
        Special special = specials.get(view);
        if (special == null) return requested;
        special.nativeVisibility = requested;
        special.overridden = !seamless && hideSpecial(special);
        return special.overridden ? View.GONE : requested;
    }
    private void applySpecial(View view, Special state) {
        boolean hide = !seamless && hideSpecial(state);
        if (!hide && !state.overridden) return;
        if (hide && !state.overridden) state.nativeVisibility = view.getVisibility();
        state.overridden = hide;
        applying = true;
        try { view.setVisibility(hide ? View.GONE : state.nativeVisibility); }
        finally { applying = false; }
    }
    private boolean hideSpecial(Special state) {
        StatusIconConfig cfg=config.get();
        return cfg.restricts(state.slot,state.location,bluetooth) || (cfg.appliesTo(state.location)
                && "KEYGUARD".equals(state.location) && keyguardPolicy.userHidden(state.slot));
    }
    void refresh() {
        // Callers run on the main looper, including preference changes.
        for (var entry : new ArrayList<>(specials.entrySet())) applySpecial(entry.getKey(), entry.getValue());
        for (ViewGroup group : new ArrayList<>(groups.keySet())) group.requestLayout();
        for (ViewGroup group : new ArrayList<>(drawGroups.keySet())) group.invalidate();
        for (View mirror : new ArrayList<>(mirrors)) mirror.invalidate();
        for (View clock : new ArrayList<>(panelClocks)) clock.invalidate();
        for (ViewGroup parent : new ArrayList<>(panelClockParents)) parent.invalidate();
    }
    private boolean stockAllows(ViewGroup parent, View view, String slot) {
        NativeIconPolicy policy=policies.get(parent);
        if (policy!=null && !policy.allows(slot)) return false;
        Icon icon=icons.get(view);
        try { return icon==null || !Boolean.TRUE.equals(icon.blocked.invoke(view)); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }
    Frame begin(ViewGroup parent) {
        StatusIconConfig cfg=config.get();
        Boolean end=drawGroups.get(parent);
        boolean keyguard=keyguardDrawGroups.contains(parent);
        if (!seamless || !cfg.enabled() || end==null || (keyguard ? !cfg.keyguardFollow() : cfg.shadeOnly().isEmpty() && cfg.bluetoothMode()==0)) return null;
        boolean nativeSpacing=!keyguard && parent.getWidth()>0 && parent instanceof LinearLayout && Boolean.TRUE.equals(end);
        List<View> children=new ArrayList<>();List<IconDrawPlan.Cell> cells=new ArrayList<>();List<Boolean> allocated=new ArrayList<>();
        for (int i=0;i<parent.getChildCount();i++) {
            View child=parent.getChildAt(i);
            boolean selected=false, occupiesSpace=true, alwaysHidden=false;
            if (child.getVisibility()==View.VISIBLE && child.getWidth()>0) {
                Icon icon=icons.get(child);
                NativeIconPolicy policy=policies.get(parent);
                boolean systemHidden=keyguard && policy!=null && policy.userHidden(icon==null?"":icon.slot);
                boolean bluetoothHidden=icon!=null && stockAllows(parent,child,icon.slot)
                        && cfg.restrictsBluetooth(icon.slot,keyguard?"KEYGUARD":groups.get(parent),bluetooth);
                if (icon!=null && (systemHidden || bluetoothHidden || (cfg.shadeOnly().contains(icon.slot) && stockAllows(parent,child,icon.slot)))) {
                    try {
                        boolean nativeVisible=Boolean.TRUE.equals(icon.visible.invoke(child));
                        boolean hiding=icon.state!=null && Integer.valueOf(2).equals(icon.state.invoke(child));
                        // Capsule occlusion changes the draw target after native layout has
                        // allocated the icon's cell. State 2 does not mean that width vanished:
                        // keep both masking and compaction through the disappearance animation.
                        // Permanently hidden keyguard items retain their separate native policy.
                        alwaysHidden=systemHidden || bluetoothHidden;
                        selected=nativeVisible || alwaysHidden;
                        occupiesSpace=alwaysHidden ? !hiding : nativeVisible;
                    }
                    catch (ReflectiveOperationException | RuntimeException unavailable) { /* Keep native rendering. */ }
                }
                SpecialDraw special=specialDraws.get(child);
                if (special!=null && (cfg.shadeOnly().contains(special.slot)
                        || (keyguard && keyguardPolicy.userHidden(special.slot)))) {
                    View source=special.source.get();
                    selected=source!=null && source.getVisibility()==View.VISIBLE;
                    alwaysHidden=keyguard && keyguardPolicy.userHidden(special.slot);
                }
            }
            float width=child.getVisibility()==View.VISIBLE && occupiesSpace ? child.getWidth() : 0;
            if (width>0 && icons.containsKey(child)) width+=child.getPaddingStart()+child.getPaddingEnd();
            if (child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margin && width>0)
                width+=margin.leftMargin+margin.rightMargin;
            // Native status icons all lay out at x=0 and animate translationX when a
            // slot (e.g. VPN) changes. Those animated coordinates can cross or coincide;
            // they do not change which side of a hidden cell a neighbor belongs to.
            float order=parent instanceof LinearLayout ? (parent.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL ? -i : i) : child.getLeft();
            children.add(child);cells.add(new IconDrawPlan.Cell(order,width,selected,alwaysHidden));
            boolean nativeCell=width>0;
            Icon known=icons.get(child);
            if(nativeSpacing && nativeCell && known!=null)try{nativeCell=Boolean.TRUE.equals(known.visible.invoke(child)) && stockAllows(parent,child,known.slot);}
            catch(ReflectiveOperationException|RuntimeException unavailable){nativeCell=false;}
            allocated.add(nativeCell);
        }
        boolean right=rightAnchored(parent);
        var plan=IconDrawPlan.at(cells,right,keyguard?keyguardFraction:fraction);
        if(nativeSpacing)
            plan=spacing.computeIfAbsent(parent,unused->new IconSpaceContinuity()).apply(parent,cells,allocated,right,fraction,plan,
                    ()->{for(View mirror:new ArrayList<>(mirrors))mirror.invalidate();});
        Map<View,IconDrawPlan.Transform> transforms=new IdentityHashMap<>();
        for (int i=0;i<children.size();i++) transforms.put(children.get(i),plan.get(i));
        Frame frame=new Frame(parent,transforms,drawing.get());drawing.set(frame);return frame;
    }
    void end(Frame frame) { if (frame!=null) { if(frame.previous==null)drawing.remove();else drawing.set(frame.previous); } }
    boolean rightAnchored(ViewGroup parent) {
        Boolean end=drawGroups.get(parent);
        return end!=null && end != (parent.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL);
    }
    IconDrawPlan.Transform transform(ViewGroup parent, View child) {
        if (seamless && config.get().enabled() && config.get().shadeOnly().contains("clock")) {
            if (panelClocks.contains(child)) return new IconDrawPlan.Transform(0,fraction);
        }
        Frame frame=drawing.get();
        var nativeTransform=frame!=null && frame.parent==parent ? frame.transforms.getOrDefault(child,IconDrawPlan.Transform.NONE)
                : IconDrawPlan.Transform.NONE;
        Float ordered=ordering==null?null:ordering.offset(child,fraction);
        Float alpha=ordering==null?null:ordering.alpha(child);
        // Clock/battery wrappers can compact the entire icon container. Its children
        // already have their ordered targets, so that ancestor shift would count twice.
        if(ordered==null && nativeTransform.dx()!=0 && ordering!=null && ordering.containsOrderedGroup(child))
            ordered=0f;
        return ordered==null?nativeTransform:new IconDrawPlan.Transform(ordered,nativeTransform.alpha()*(alpha==null?1:alpha));
    }
    IconDrawPlan.Transform mirrored(View source) {
        // OEM fake frames draw the source directly, bypassing its parent's drawChild.
        if (source==null || !(source.getParent() instanceof ViewGroup parent)) return IconDrawPlan.Transform.NONE;
        Frame frame=begin(parent);
        try {
            var base=transform(parent,source);
            Float ordered=ordering==null?null:ordering.mirroredOffset(source,fraction);
            return ordered==null?base:new IconDrawPlan.Transform(ordered,base.alpha());
        }
        finally { end(frame); }
    }
}
