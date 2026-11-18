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
        }
        return true;
    }

    /** A live card in another window appeared, moved or went away: redraw so the clock can make room. */
    void floatingCardsChanged() {
        if (clock != null) handler.post(root::invalidate);
    }

    /** Settings changed: undo disabled features right away, then rescan. Called from any thread. */
    void configChanged() {
        handler.post(() -> {
            Config cfg = m.config;
            if (!cfg.clock && clock != null) { clock.release(); clock = null; }
            if (!cfg.shortcuts && shortcuts != null) { shortcuts.release(); shortcuts = null; }
            Fonts.clear();
            lastDump = 0;
            schedule();
        });
    }

    private void schedule() {
        if (scheduled) return;
        scheduled = true;
        handler.postDelayed(scanTask, clock == null ? FAST_SCAN_DELAY_MS : SCAN_DELAY_MS);
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
            return;
        }

        Set<View> skip = new HashSet<>();
        if (clock != null) skip.add(clock.view);
        List<View> tree = Views.visibleTree(root, skip);
        Palette palette = Palette.of(root.getContext(), cfg.shortcutTone);

        // Digit keys (bouncer / PIN pad) are off limits.
        Set<View> keypads = findKeypads(tree);

        View clockView = findClock(tree, screenW, screenH, keypads);
        boolean keyguardWindow = clockView != null || aod || KEYGUARD_TITLE.matcher(title).matches();

        if (cfg.clock && clockView != null) {
            if (clock == null) clock = new ClockController(m, root, aod);
            TextView date = findDate(tree, clockView);
            clock.setObstacles(cfg.clockAvoid ? findObstacles(tree, clockView, date, screenW, screenH)
                    : java.util.Collections.emptyList());
            clock.bind(clockView, date, cfg, palette);
        } else if (clock != null && cfg.clock && clock.clock() != null && !clock.clock().isAttachedToWindow()) {
            clock.release();
            clock = null;
        }

        // Shortcut buttons are (re)detected only while the lock screen itself is showing, never over the bouncer.
        // The control center can open over the lock screen in the same window, so ask for a clock that is clearly
        // showing (not faded behind a panel) and a locked keyguard; no clock means no restyling at all.
        boolean lockscreenShowing = clockView != null && effectivelyVisible(clockView, 0.9f) && keyguardLocked();
        if (cfg.shortcuts && !aod && keyguardWindow && lockscreenShowing && keypads.isEmpty()) {
            if (shortcuts == null) shortcuts = new ShortcutStyler(m);
            shortcuts.apply(tree, screenW, screenH, palette, keypads);
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
        for (View v : tree) {
            if (!(v instanceof TextView)) continue;
            TextView tv = (TextView) v;
            if (tv.getTextSize() < minSize) continue;
            if (insideAny(tv, keypads)) continue;
            Rect r = Views.screen(tv);
            if (r.centerY() > screenH * (aod ? 0.8f : 0.6f)) continue;
            String text = Views.text(tv).trim();
            if (text.length() <= 12 && (ClockController.TIME.matcher(text).find() || STACKED.matcher(text).matches())) {
                // A clock that is actually showing beats a bigger one that is faded out (ColorOS keeps several).
                boolean shown = effectivelyVisible(tv), bestShown = best != null && effectivelyVisible(best);
                if (best == null || (shown && !bestShown)
                        || (shown == bestShown && tv.getTextSize() > best.getTextSize())) best = tv;
            } else if (SPLIT_DIGITS.matcher(text).matches()) {
                split.computeIfAbsent(tv.getParent(), k -> new ArrayList<>()).add(tv);
            }
        }
        if (best != null) return best;
        for (Map.Entry<ViewParent, List<TextView>> e : split.entrySet()) {
            if (e.getValue().size() >= 2 && e.getKey() instanceof View && e.getKey() != root) return (View) e.getKey();
        }
        // Custom-drawn clocks: a big view whose class name says "clock".
        View custom = null;
        for (View v : tree) {
            if (v instanceof TextView || v == root) continue;
            String name = v.getClass().getName().toLowerCase(Locale.ROOT);
            if (!name.contains("clock")) continue;
            if (v.getWidth() < screenW * 0.4f || v.getHeight() < Views.dp(root, 60)) continue;
            if (Views.screen(v).centerY() > screenH * 0.5f) continue;
            if (custom == null || v.getWidth() * v.getHeight() < custom.getWidth() * custom.getHeight()) custom = v;
        }
        return custom;
    }

    /**
     * Notification rows and cards in the keyguard window: wide boxes with a background (or a notification-ish
     * class name) that are not part of the clock. The clock controller keeps itself above the highest one.
     */
    private List<View> findObstacles(List<View> tree, View clockView, TextView date, int screenW, int screenH) {
        List<View> out = new ArrayList<>();
        float minH = Views.dp(root, 36);
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
            if (r.top > screenH * 0.85f) continue;
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
