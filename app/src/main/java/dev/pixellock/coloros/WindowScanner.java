package dev.pixellock.coloros;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One per SystemUI window. After layout passes (throttled) it looks for the lock screen clock, the date line and
 * the two bottom shortcut buttons, and hands them to the controllers. It never touches the bouncer / PIN pad:
 * anything containing digit keys is left alone, so another module's keypad keeps working.
 */
final class WindowScanner implements ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener,
        View.OnAttachStateChangeListener {
    private static final long SCAN_DELAY_MS = 150;
    /** While no clock is bound yet (e.g. right after the screen turns on), look again sooner. */
    private static final long FAST_SCAN_DELAY_MS = 32;
    private static final Pattern AOD_TITLE = Pattern.compile("(?i).*(aod|doze|alwayson|always_on).*");
    private static final Pattern KEYGUARD_TITLE =
            Pattern.compile("(?i).*(notificationshade|keyguard|lockscreen|lock_screen|statusbar_expanded|aod|doze).*");
    private static final Pattern DATE_HINT =
            Pattern.compile("(?i).*(月|日|周|週|星期|mon|tue|wed|thu|fri|sat|sun|\\d{1,2}/\\d{1,2}).*");
    private static final Pattern SPLIT_DIGITS = Pattern.compile("\\d{1,2}");
    private static final Pattern STACKED = Pattern.compile("\\s*\\d{1,2}\\s*\\n\\s*\\d{2}\\s*");

    private final HookEntry m;
    private final ViewGroup root;
    private final String title;
    private final boolean aod;
    private final Handler handler;
    private long lastInvisibleScan;
    private boolean cardsRegistered;
    private String lastObstacles = "";
    private boolean scheduled;
    private boolean listening;
    private long lastDump;
    private ClockController clock;
    private View inPlace;
    /** Clock views whose in-place drawing never ran: they get the overlay instead. */
    private final Set<View> overlayOnly = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private String lastCandidates = "";
    private String lastGate = "";
    private ShortcutStyler shortcuts;
    private String lastMiss = "";
    private final Runnable scanTask = () -> { scheduled = false; scanSafely(); };

    WindowScanner(HookEntry m, ViewGroup root, String title) {
        this.m = m;
        this.root = root;
        this.title = title == null ? "" : title;
        aod = m.inAodApp() || AOD_TITLE.matcher(this.title).matches();
        Looper looper = Looper.myLooper();
        handler = new Handler(looper != null ? looper : Looper.getMainLooper());
    }

    void start() {
        root.addOnAttachStateChangeListener(this);
        if (root.isAttachedToWindow()) onViewAttachedToWindow(root);
    }

    @Override public void onViewAttachedToWindow(View v) {
        if (listening) return;
        listening = true;
        ViewTreeObserver vto = root.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(this);
        vto.addOnPreDrawListener(this);
        schedule();
    }

    @Override public void onViewDetachedFromWindow(View v) {
        if (!listening) return;
        listening = false;
        ViewTreeObserver vto = root.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.removeOnGlobalLayoutListener(this);
            vto.removeOnPreDrawListener(this);
        }
        handler.removeCallbacks(scanTask);
        scheduled = false;
        clearCards();
    }

    @Override public void onGlobalLayout() { schedule(); }

    @Override public boolean onPreDraw() {
        if (clock != null) {
            try { clock.sync(); } catch (Throwable t) { m.error("Clock sync failed", t); }
            // ColorOS may switch to another clock view (e.g. on screen-on) without a layout pass: look again.
            View bound = clock.clock();
            long now = SystemClock.uptimeMillis();
            if ((bound == null || !bound.isShown()) && now - lastInvisibleScan > 500) {
                lastInvisibleScan = now;
                schedule();
            }
        } else if (inPlace != null) {
            long now = SystemClock.uptimeMillis();
            if (!inPlace.isShown() && now - lastInvisibleScan > 500) {
                lastInvisibleScan = now;
                schedule();
            }
        }
        return true;
    }

    /** A live card in another window appeared, moved or went away: redraw so the clock can make room. */
    void floatingCardsChanged() {
        if (clock != null) handler.post(root::invalidate);
        if (inPlace != null) handler.post(this::schedule);
    }

    private static boolean anyShown(List<View> views) {
        for (View v : views) if (Views.shownOnThisThread(v)) return true;
        return false;
    }

    /** Screen turned on or off (SystemUI only). Called on the main thread. */
    void screenChanged(boolean on) {
        handler.post(() -> {
            if (!on) {
                // The always-on display is the system's: no overlay of ours, ColorOS's clock and date as they are.
                if (clock != null) { clock.release(); clock = null; }
                if (inPlace != null) InPlaceClock.dozing(inPlace);
            }
            schedule();
        });
    }

    private boolean interactive() {
        try {
            android.os.PowerManager pm = root.getContext().getSystemService(android.os.PowerManager.class);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Settings changed: undo disabled features right away, then rescan. Called from any thread. */
    void configChanged() {
        handler.post(() -> {
            Config cfg = m.config;
            if (!cfg.clock && clock != null) { clock.release(); clock = null; }
            if (!cfg.clock && inPlace != null) { InPlaceClock.release(inPlace); inPlace = null; }
            if (!cfg.shortcuts && shortcuts != null) { shortcuts.release(); shortcuts = null; }
            Fonts.clear();
            lastDump = 0;
            schedule();
        });
    }

    private void schedule() {
        if (scheduled) return;
        scheduled = true;
        handler.postDelayed(scanTask, clock == null && inPlace == null ? FAST_SCAN_DELAY_MS : SCAN_DELAY_MS);
    }

    private void scanSafely() {
        try { scan(); } catch (Throwable t) { m.error("Scan failed in window " + title, t); }
    }

    private void scan() {
        if (!root.isAttachedToWindow() || root.getWindowVisibility() != View.VISIBLE) { clearCards(); return; }
        DisplayMetrics dm = root.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels, screenH = dm.heightPixels;
        Config cfg = m.config;
        // Full-screen windows hold the keyguard (or the AOD). Smaller SystemUI windows are only checked for live
        // cards the clock has to stay clear of.
        boolean fullScreen = root.getHeight() >= screenH * (aod ? 0.6f : 0.8f) && root.getWidth() >= screenW * 0.9f;
        if (!fullScreen) {
            if (!m.inAodApp() && cfg.clockAvoid) scanCards(screenW, screenH); else clearCards();
            return;
        }
        if (aod && !cfg.aod) {
            if (clock != null) { clock.release(); clock = null; }
            if (inPlace != null) { InPlaceClock.release(inPlace); inPlace = null; }
            return;
        }

        Set<View> skip = new HashSet<>();
        if (clock != null) skip.add(clock.view);
        List<View> tree = Views.visibleTree(root, skip);
        Palette palette = Palette.of(root.getContext(), cfg.shortcutTone);

        // Digit keys (bouncer / PIN pad) are off limits.
        Set<View> keypads = findKeypads(tree);

        logStyleClock(tree);
        View clockView = findClock(tree, screenW, screenH, keypads);
        boolean keyguardWindow = clockView != null || aod || KEYGUARD_TITLE.matcher(title).matches();

        // Screen off in SystemUI: the system AOD (or ColorOS's dozing keyguard) keeps its own clock untouched.
        // The ColorOS 17 plugin clock is also the panoramic AOD clock: it stays Pixel (in place) while dozing.
        boolean dozing = !m.inAodApp() && !interactive();
        boolean pluginClock = clockView != null && isPluginClock(clockView);
        if (dozing && clock != null) { clock.release(); clock = null; }
        if (cfg.clock && clockView != null && (!dozing || (pluginClock && cfg.aod))) {
            TextView date = findDate(tree, clockView);
            boolean compact = cfg.clockAvoid && (!findObstacles(tree, clockView, date, screenW, screenH).isEmpty()
                    || anyShown(m.floatingCards()));
            if (inPlace != null && inPlace == clockView && InPlaceClock.draws(inPlace) == 0
                    && InPlaceClock.boundFor(inPlace) > 2500 && effectivelyVisible(inPlace)) {
                // ColorOS never asked this view to draw through anything we hook: fall back to the overlay.
                m.info("In-place clock never drew in 2.5 s: " + Views.describe(inPlace)
                        + " | class chain " + classChain(inPlace) + " | switching to overlay for this view");
                overlayOnly.add(inPlace);
                InPlaceClock.release(inPlace);
                inPlace = null;
            }
            if (!overlayOnly.contains(clockView)) {
                // Normal case: ColorOS's clock view itself draws the Pixel clock, so only one clock exists.
                if (clock != null) { clock.release(); clock = null; }
                if (inPlace != null && inPlace != clockView) InPlaceClock.release(inPlace);
                // Check back once it should have drawn (see the fallback above).
                if (!InPlaceClock.isBound(clockView)) handler.postDelayed(this::schedule, 2700);
                inPlace = clockView;
                // In the always-on display ColorOS's layout is left as it is (no resizing), so nothing jumps.
                InPlaceClock.bind(m, inPlace, date, cfg, palette, compact, !aod || pluginClock, pluginClock);
            } else if (aod) {
                // No clock of our own on the always-on display: if its clock can't be restyled in place, leave it.
                if (inPlace != null) { InPlaceClock.release(inPlace); inPlace = null; }
            } else {
                if (inPlace != null) { InPlaceClock.release(inPlace); inPlace = null; }
                if (clock == null) clock = new ClockController(m, root, aod);
                clock.setObstacles(cfg.clockAvoid ? findObstacles(tree, clockView, date, screenW, screenH)
                        : java.util.Collections.emptyList());
                clock.bind(clockView, date, cfg, palette);
            }
        } else if (clock != null && cfg.clock && clock.clock() != null && !clock.clock().isAttachedToWindow()) {
            clock.release();
            clock = null;
        }

        // Shortcut buttons are (re)detected only while the lock screen itself is showing, never over the bouncer.
        // The control center can open over the lock screen in the same window, so ask for a clock that is clearly
        // showing (not faded behind a panel) and a locked keyguard; no clock means no restyling at all.
        if (cfg.shortcuts && !aod && keyguardWindow) {
            String gate;
            boolean locked = keyguardLocked();
            boolean clockShowing = clockView != null && effectivelyVisible(clockView, 0.5f);
            View panel = clockShowing ? null : openPanel(tree, screenW, screenH);
            if (!keypads.isEmpty()) gate = "paused: PIN pad on screen";
            else if (!locked) gate = "paused: keyguard not locked";
            else if (panel != null) gate = "paused: panel open " + panel.getClass().getName() + "#" + Views.id(panel);
            else if (clockView == null) gate = "paused: no lock screen clock found";
            else gate = "active (clock " + (clockShowing ? "showing" : "faded, no panel open") + ")";
            if (!gate.equals(lastGate)) { lastGate = gate; m.info("Shortcut styling " + gate); }
            if (ShortcutHook.active) {
                // ColorOS's own button update is hooked (ColorOS 17): the tree-based styler stays out.
                if (shortcuts != null) { shortcuts.release(); shortcuts = null; }
            } else if (gate.startsWith("active")) {
                if (shortcuts == null) shortcuts = new ShortcutStyler(m);
                shortcuts.apply(tree, screenW, screenH, palette, keypads);
            }
        }

        if (keyguardWindow && clockView == null && cfg.clock) {
            String miss = "No lock screen clock recognised in window '" + title + "' (" + tree.size()
                    + " views). Turn on the diagnostic switch and send the log.";
            if (!miss.equals(lastMiss)) { lastMiss = miss; m.info(miss); }
        }
        if (cfg.dump && keyguardWindow && SystemClock.uptimeMillis() - lastDump > 20_000) {
            lastDump = SystemClock.uptimeMillis();
            dump(screenW, screenH);
        }
    }

    /** Alpha product up the parents; true when the view would actually be seen. */
    private boolean effectivelyVisible(View v) { return effectivelyVisible(v, 0.3f); }

    private boolean keyguardLocked() {
        try {
            android.app.KeyguardManager km = root.getContext().getSystemService(android.app.KeyguardManager.class);
            return km == null || km.isKeyguardLocked();
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean effectivelyVisible(View v, float min) {
        if (!v.isShown()) return false;
        float alpha = 1f;
        View cur = v;
        while (cur != null) {
            alpha *= cur.getAlpha();
            if (cur != v) alpha *= cur.getTransitionAlpha();
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        return alpha > min;
    }

    /** A quick settings / control center panel that is actually showing over the lock screen. */
    private View openPanel(List<View> tree, int screenW, int screenH) {
        for (View v : tree) {
            if (v == root || v.getHeight() < screenH * 0.3f || v.getWidth() < screenW * 0.6f) continue;
            String name = (v.getClass().getName() + " " + Views.id(v)).toLowerCase(Locale.ROOT);
            if (!(name.contains("qs") || name.contains("quicksetting") || name.contains("controlcenter")
                    || name.contains("control_center") || name.contains("controlpanel") || name.contains("myfc"))) continue;
            Rect r = Views.screen(v);
            if (r.bottom <= 0 || r.top >= screenH || r.right <= 0 || r.left >= screenW) continue;
            if (effectivelyVisible(v, 0.3f)) return v;
        }
        return null;
    }

    private String lastStyleClock = "";

    /**
     * ColorOS 17: the clock style plugin's view is the child of ClockPluginContainer / KeyguardClockPluginContainer
     * (loaded from com.oplus.keyguard.clock.base, the style itself from com.oplus.keyguard.personality.clocks).
     * The same view is shown on the lock screen and, dimmed, as the panoramic AOD.
     */
    private View findPluginClock(List<View> tree) {
        View best = null;
        for (View v : tree) {
            if (!(v.getParent() instanceof View)) continue;
            View parent = (View) v.getParent();
            if (!classChain(parent).contains("ClockPluginContainer")) continue;
            if (v.getWidth() <= 0 || v.getHeight() <= 0) continue;
            boolean shown = effectivelyVisible(v, 0.05f);
            boolean bestShown = best != null && effectivelyVisible(best, 0.05f);
            if (best == null || (shown && !bestShown)
                    || (shown == bestShown && v.getWidth() * v.getHeight() > best.getWidth() * best.getHeight())) best = v;
        }
        return best;
    }

    private static boolean isPluginClock(View v) {
        return v.getParent() instanceof View && classChain((View) v.getParent()).contains("ClockPluginContainer");
    }

    /**
     * ColorOS 17 loads the lock screen clock from a plugin into OplusKeyguardStyleClock. Log what that container
     * holds (classes and sizes only, no text) whenever it changes, to see which view draws the digits.
     */
    private void logStyleClock(List<View> tree) {
        for (View v : tree) {
            if (!classChain(v).contains("OplusKeyguardStyleClock")) continue;
            StringBuilder sb = new StringBuilder();
            int[] n = {0};
            describeTree(v, 0, sb, n);
            String log = "Style clock container: " + sb;
            if (!log.equals(lastStyleClock)) {
                lastStyleClock = log;
                for (int i = 0; i < log.length(); i += 3500) m.info(log.substring(i, Math.min(log.length(), i + 3500)));
            }
            return;
        }
    }

    private void describeTree(View v, int depth, StringBuilder sb, int[] n) {
        if (n[0]++ > 120 || depth > 12) return;
        sb.append("\n");
        for (int i = 0; i < depth; i++) sb.append("  ");
        Rect r = Views.screen(v);
        sb.append(v.getClass().getName()).append(" #").append(Views.id(v)).append(' ').append(r.toShortString());
        if (v.getVisibility() != View.VISIBLE) sb.append(" vis=").append(v.getVisibility());
        if (v.getAlpha() != 1f) sb.append(" a=").append(String.format(Locale.ROOT, "%.2f", v.getAlpha()));
        if (v instanceof TextView) {
            String t = Views.text(v).trim();
            boolean safe = t.length() <= 12 && t.matches("[\\d:：∶ \\n]*");
            sb.append(" ts=").append(Math.round(((TextView) v).getTextSize()))
                    .append(safe ? " text='" + t.replace('\n', '|') + "'" : " text[" + t.length() + "]");
        }
        if (v instanceof android.view.SurfaceView) sb.append(" SURFACE");
        if (v instanceof android.view.TextureView) sb.append(" TEXTURE");
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) describeTree(g.getChildAt(i), depth + 1, sb, n);
        }
    }

    private static String classChain(View v) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> c = v.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (sb.length() > 0) sb.append(" < ");
            sb.append(c.getName());
            if (c == View.class) break;
        }
        return sb.toString();
    }

    private Set<View> findKeypads(List<View> tree) {
        Map<Character, View> digits = new HashMap<>();
        for (View v : tree) {
            if (!(v instanceof TextView)) continue;
            String t = Views.text(v).trim();
            if (t.length() == 1 && Character.isDigit(t.charAt(0))) digits.putIfAbsent(t.charAt(0), v);
        }
        Set<View> out = new HashSet<>();
        if (digits.size() >= 8) {
            View group = Views.commonAncestor(new ArrayList<>(digits.values()));
            if (group != null && group != root) out.add(group);
            else out.addAll(digits.values());
        }
        return out;
    }

    private View findClock(List<View> tree, int screenW, int screenH, Set<View> keypads) {
        float minSize = Views.dp(root, 36);
        TextView best = null;
        Map<ViewParent, List<TextView>> split = new HashMap<>();
        StringBuilder cands = new StringBuilder();
        for (View v : tree) {
            if (!(v instanceof TextView)) continue;
            TextView tv = (TextView) v;
            String text = Views.text(tv).trim();
            boolean timeLike = text.length() <= 12
                    && (ClockController.TIME.matcher(text).find() || STACKED.matcher(text).matches());
            boolean digits = SPLIT_DIGITS.matcher(text).matches();
            if (!timeLike && !digits) continue;
            String why = null;
            if (tv.getTextSize() < minSize) why = "small";
            else if (insideAny(tv, keypads)) why = "keypad";
            else if (Views.screen(tv).centerY() > screenH * (aod ? 0.8f : 0.6f)) why = "low";
            if (timeLike && tv.getTextSize() >= Views.dp(root, 20)) {
                cands.append(tv.getClass().getSimpleName()).append('#').append(Views.id(tv))
                        .append(" ts=").append(Math.round(tv.getTextSize()))
                        .append(effectivelyVisible(tv) ? " shown" : " faded")
                        .append(why == null ? "" : " skip:" + why).append("; ");
            }
            if (why != null) continue;
            if (timeLike) {
                // A clock that is actually showing beats a bigger one that is faded out (ColorOS keeps several).
                boolean shown = effectivelyVisible(tv), bestShown = best != null && effectivelyVisible(best);
                if (best == null || (shown && !bestShown)
                        || (shown == bestShown && tv.getTextSize() > best.getTextSize())) best = tv;
            } else {
                split.computeIfAbsent(tv.getParent(), k -> new ArrayList<>()).add(tv);
            }
        }
        View plugin = findPluginClock(tree);
        if (plugin != null) {
            String log = "Clock candidates in '" + title + "': plugin clock " + Views.describe(plugin)
                    + " | class chain " + classChain(plugin);
            if (!log.equals(lastCandidates)) { lastCandidates = log; m.info(log); }
            return plugin;
        }
        View found = best;
        if (found == null) {
            for (Map.Entry<ViewParent, List<TextView>> e : split.entrySet()) {
                if (e.getValue().size() >= 2 && e.getKey() instanceof View && e.getKey() != root) {
                    found = (View) e.getKey();
                    cands.append("digit group ").append(found.getClass().getSimpleName()).append('#')
                            .append(Views.id(found)).append("; ");
                    break;
                }
            }
        }
        if (found == null) {
            // Custom-drawn clocks: a big view whose class name says "clock".
            View custom = null;
            for (View v : tree) {
                if (v instanceof TextView || v == root) continue;
                String name = v.getClass().getName().toLowerCase(Locale.ROOT);
                if (!name.contains("clock")) continue;
                cands.append("custom ").append(v.getClass().getSimpleName()).append('#').append(Views.id(v))
                        .append(' ').append(v.getWidth()).append('x').append(v.getHeight())
                        .append(effectivelyVisible(v) ? " shown" : " faded").append("; ");
                if (v.getWidth() < screenW * 0.4f || v.getHeight() < Views.dp(root, 60)) continue;
                if (Views.screen(v).centerY() > screenH * 0.5f) continue;
                if (custom == null || v.getWidth() * v.getHeight() < custom.getWidth() * custom.getHeight()) custom = v;
            }
            found = custom;
        }
        String log = "Clock candidates in '" + title + "': " + (cands.length() == 0 ? "none" : cands)
                + "-> " + (found == null ? "none" : found.getClass().getName() + "#" + Views.id(found));
        if (!log.equals(lastCandidates)) { lastCandidates = log; m.info(log); }
        return found;
    }

    /**
     * Notification rows and cards in the keyguard window: wide boxes with a background (or a notification-ish
     * class name) that are not part of the clock. The clock controller keeps itself above the highest one.
     */
    private List<View> findObstacles(List<View> tree, View clockView, TextView date, int screenW, int screenH) {
        List<View> out = new ArrayList<>();
        float minH = Views.dp(root, 36);
        // Only things below the clock's top count (the status bar area above it never pushes the clock).
        int clockTop = Views.screen(clockView).top;
        for (View v : tree) {
            if (v == root || v == clockView || v == date) continue;
            if (v.getWidth() < screenW * 0.55f || v.getHeight() < minH || v.getHeight() > screenH * 0.45f) continue;
            if (Views.isAncestor(v, clockView) || Views.isAncestor(clockView, v)) continue;
            if (date != null && (Views.isAncestor(v, date) || Views.isAncestor(date, v))) continue;
            String name = v.getClass().getName().toLowerCase(Locale.ROOT);
            boolean hinted = name.contains("notification") || name.contains("card") || name.contains("seedling")
                    || name.contains("fluid") || name.contains("live") || name.contains("capsule");
            if (!hinted && !hasVisibleBackground(v)) continue;
            Rect r = Views.screen(v);
            if (r.top > screenH * 0.85f || r.top < clockTop) continue;
            if (!Views.shownOnThisThread(v)) continue;
            boolean nested = false;
            for (View o : out) if (Views.isAncestor(o, v)) { nested = true; break; }
            if (!nested) out.add(v);
        }
        StringBuilder sb = new StringBuilder();
        for (View o : out) sb.append(o.getClass().getSimpleName()).append('#').append(Views.id(o)).append(' ');
        String log = sb.toString();
        if (!log.equals(lastObstacles)) {
            lastObstacles = log;
            if (!out.isEmpty()) m.info("Clock keeps clear of: " + log);
        }
        return out;
    }

    private static boolean hasVisibleBackground(View v) {
        android.graphics.drawable.Drawable bg = v.getBackground();
        if (bg == null) return false;
        if (bg instanceof android.graphics.drawable.ColorDrawable) {
            return android.graphics.Color.alpha(((android.graphics.drawable.ColorDrawable) bg).getColor()) > 20;
        }
        return bg.getAlpha() > 20;
    }

    /** Live cards ("fluid cloud") in smaller SystemUI windows: wide boxes below the status bar. */
    private void scanCards(int screenW, int screenH) {
        List<View> found = new ArrayList<>();
        float minH = Views.dp(root, 36);
        for (View v : Views.visibleTree(root, java.util.Collections.emptySet())) {
            if (v.getWidth() < screenW * 0.55f || v.getHeight() < minH || v.getHeight() > screenH * 0.45f) continue;
            if (!hasVisibleBackground(v) || !Views.shownOnThisThread(v)) continue;
            if (Views.screen(v).top < screenH * 0.08f) continue;
            boolean nested = false;
            for (View o : found) if (Views.isAncestor(o, v)) { nested = true; break; }
            if (!nested) found.add(v);
        }
        if (found.isEmpty()) { clearCards(); return; }
        cardsRegistered = true;
        m.setFloatingCards(this, found);
    }

    private void clearCards() {
        if (!cardsRegistered) return;
        cardsRegistered = false;
        m.setFloatingCards(this, java.util.Collections.emptyList());
    }

    private TextView findDate(List<View> tree, View clockView) {
        Rect c = Views.screen(clockView);
        float clockText = clockView instanceof TextView ? ((TextView) clockView).getTextSize() : Float.MAX_VALUE;
        float maxDist = Math.max(c.height(), Views.dp(root, 80));
        TextView best = null;
        float bestDist = Float.MAX_VALUE;
        for (View v : tree) {
            if (!(v instanceof TextView) || v == clockView) continue;
            TextView tv = (TextView) v;
            String t = Views.text(tv).trim();
            if (t.isEmpty() || t.length() > 40 || !t.matches("(?s).*\\d.*") || !DATE_HINT.matcher(t).matches()) continue;
            if (ClockController.TIME.matcher(t).find()) continue;
            if (tv.getTextSize() >= clockText) continue;
            Rect r = Views.screen(tv);
            if (r.right < c.left || r.left > c.right) continue;
            float dist = r.bottom <= c.top ? c.top - r.bottom : r.top >= c.bottom ? r.top - c.bottom : 0;
            if (dist <= maxDist && dist < bestDist) { bestDist = dist; best = tv; }
        }
        return best;
    }

    private static boolean insideAny(View v, Set<View> groups) {
        for (View g : groups) if (Views.isSelfOrAncestor(g, v)) return true;
        return false;
    }

    /**
     * Writes the visible view tree to the LSPosed log. Only clock/date-like text is printed; any other text is
     * replaced by its length so notification contents never end up in the log.
     */
    private void dump(int screenW, int screenH) {
        StringBuilder sb = new StringBuilder();
        sb.append("View dump of window '").append(title).append("' screen ").append(screenW).append('x').append(screenH)
                .append(" density ").append(root.getResources().getDisplayMetrics().density).append('\n');
        int[] count = {0};
        dumpView(root, 0, sb, count);
        String all = sb.toString();
        int part = 1;
        for (int i = 0; i < all.length(); i += 3500) {
            m.info("[dump " + part++ + "] " + all.substring(i, Math.min(all.length(), i + 3500)));
        }
    }

    private void dumpView(View v, int depth, StringBuilder sb, int[] count) {
        if (count[0]++ > 700 || depth > 40) return;
        if (v.getVisibility() == View.GONE) return;
        Rect r = Views.screen(v);
        for (int i = 0; i < depth; i++) sb.append(' ');
        sb.append(v.getClass().getName()).append(" #").append(Views.id(v)).append(' ').append(r.toShortString());
        if (v.getVisibility() != View.VISIBLE) sb.append(" vis=").append(v.getVisibility());
        if (v.getAlpha() != 1f) sb.append(" a=").append(String.format(Locale.ROOT, "%.2f", v.getAlpha()));
        if (v.isClickable()) sb.append(" click");
        if (v.getBackground() != null) sb.append(" bg=").append(v.getBackground().getClass().getSimpleName());
        if (v instanceof TextView) {
            String t = Views.text(v).trim().replace('\n', '|');
            boolean safe = t.length() <= 24 && (ClockController.TIME.matcher(t).find() || DATE_HINT.matcher(t).matches()
                    || t.matches("[\\d:：∶ |]{1,8}"));
            sb.append(" ts=").append(Math.round(((TextView) v).getTextSize()));
            sb.append(safe ? " text='" + t + "'" : " text[" + t.length() + "]");
        }
        CharSequence cd = v.getContentDescription();
        if (cd != null && v.isClickable() && cd.length() <= 12) sb.append(" cd='").append(cd).append('\'');
        sb.append('\n');
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) dumpView(g.getChildAt(i), depth + 1, sb, count);
        }
    }
}
