package dev.lsposed.fluidcloud;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Excludes a native capsule's gesture only from the synchronous OEM click-top helper. */
final class CapsuleClickTopGuard {
    private final WeakHashMap<View, Gesture> gestures = new WeakHashMap<>();
    private final ThreadLocal<Gesture> current = new ThreadLocal<>();
    private final Predicate<View> capsule;
    private record Gesture(long downTime, int device, int source, int pointer, boolean owned) {
        boolean matches(MotionEvent event) {
            return event != null && event.getPointerCount() == 1 && downTime == event.getDownTime()
                    && device == event.getDeviceId() && source == event.getSource()
                    && pointer == event.getPointerId(0);
        }
    }
    CapsuleClickTopGuard() {
        this(view -> view.getClass().getName().equals(
                "com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView") || mini(view));
    }
    CapsuleClickTopGuard(Predicate<View> capsule) { this.capsule = capsule; }
    private static boolean mini(View view) {
        if (view.getId() == View.NO_ID) return false;
        try { return "mimi_view".equals(view.getResources().getResourceEntryName(view.getId())); }
        catch (android.content.res.Resources.NotFoundException ignored) { return false; }
    }
    interface Dispatch { Object call() throws Throwable; }
    Object dispatch(View bar, MotionEvent event, boolean enabled, Dispatch original) throws Throwable {
        Gesture previous = current.get();
        Gesture active = null;
        if (!enabled || event == null) gestures.remove(bar);
        else {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                active = new Gesture(event.getDownTime(), event.getDeviceId(), event.getSource(),
                        event.getPointerId(0), event.getPointerCount() == 1
                        && hits(bar, event.getRawX(), event.getRawY()));
                gestures.put(bar, active);
            } else {
                active = gestures.get(bar);
                if (active == null || !active.matches(event) || action == MotionEvent.ACTION_CANCEL
                        || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
                    gestures.remove(bar);
                    active = null;
                }
            }
        }
        current.set(active);
        try { return original.call(); }
        finally {
            if (previous == null) current.remove(); else current.set(previous);
            if (event == null || event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) gestures.remove(bar);
        }
    }
    boolean suppress(MotionEvent event) {
        Gesture active = current.get();
        return active != null && active.owned && active.matches(event)
                && (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_UP);
    }
    private boolean hits(View view, float x, float y) {
        if (!view.isAttachedToWindow() || !view.isShown() || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) return false;
        if (capsule.test(view)) {
            Rect rect = new Rect();
            if (view.getGlobalVisibleRect(rect)) {
                int[] screen = new int[2], window = new int[2];
                view.getRootView().getLocationOnScreen(screen);
                view.getRootView().getLocationInWindow(window);
                rect.offset(screen[0] - window[0], screen[1] - window[1]);
                if (x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom) return true;
            }
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            if (hits(group.getChildAt(i), x, y)) return true;
        }
        return false;
    }
}
