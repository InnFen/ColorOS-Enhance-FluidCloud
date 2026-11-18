package dev.howard.colorosenhance.ui;

import android.app.Application;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import dev.lsposed.fluidcloud.MediaWaveBands;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class WaveBandAxisViewTest {
    @Test public void draggingToTheMiddleUsesLogFrequencyAndMovesOnlyOneBoundary() {
        WaveBandAxisView axis = axis();
        axis.setBands(new MediaWaveBands(List.of(20, 40, 70, 100, 2500, 5000, 24000)));
        var changed = new AtomicReference<MediaWaveBands>();
        axis.setOnBandsChanged(changed::set);

        drag(axis, xAt(axis, 100), axis.getWidth() / 2f);

        // The middle of a log axis is the geometric mean of its endpoints.
        assertEquals(List.of(20, 40, 70, 693, 2500, 5000, 24000), changed.get().edges());
    }

    @Test public void draggingPastANeighborKeepsTheSixBandsInOrder() {
        WaveBandAxisView axis = axis();
        var changed = new AtomicReference<MediaWaveBands>();
        axis.setOnBandsChanged(changed::set);

        drag(axis, xAt(axis, 125), xAt(axis, 1000));

        assertEquals(List.of(40, 314, 315, 800, 2000, 5000, 16000), changed.get().edges());
    }

    @Test public void repeatedTapsSelectOverlappingHandlesForTheNextDrag() {
        WaveBandAxisView axis = axis();
        axis.setBands(new MediaWaveBands(List.of(40, 41, 315, 800, 2000, 5000, 16000)));
        var changed = new AtomicReference<MediaWaveBands>();
        axis.setOnBandsChanged(changed::set);
        float overlap = xAt(axis, 40);

        tap(axis, overlap);
        tap(axis, overlap);
        assertNull(changed.get());
        drag(axis, overlap, xAt(axis, 1000));

        assertEquals(List.of(40, 314, 315, 800, 2000, 5000, 16000), changed.get().edges());
    }

    @Test public void verticalScrollingAndDisabledControlsDoNotEditTheDraft() {
        WaveBandAxisView axis = axis();
        var changed = new AtomicReference<MediaWaveBands>();
        axis.setOnBandsChanged(changed::set);
        long start = SystemClock.uptimeMillis();
        float x = xAt(axis, 125);
        float y = axisY(axis);
        assertTrue(touch(axis, start, 0, MotionEvent.ACTION_DOWN, x, y));
        assertFalse(touch(axis, start, 30, MotionEvent.ACTION_MOVE, x, y + 60));
        assertNull(changed.get());

        axis.setEnabled(false);
        assertFalse(touch(axis, start, 60, MotionEvent.ACTION_DOWN, x, y));
        assertNull(changed.get());
    }

    @Test public void programmaticUpdatesRefreshTheAccessibleRangesWithoutSendingEdits() {
        WaveBandAxisView axis = axis();
        var changed = new AtomicReference<MediaWaveBands>();
        axis.setOnBandsChanged(changed::set);
        axis.setBands(new MediaWaveBands(List.of(20, 90, 250, 1000, 3000, 9000, 24000)));
        assertNull(changed.get());

        AccessibilityNodeInfo node = axis.createAccessibilityNodeInfo();
        String description = node.getContentDescription().toString();
        assertTrue(description.contains("第 1 柱 20–90 Hz"));
        assertTrue(description.contains("第 6 柱 9000–24000 Hz"));

        axis.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(List.of(21, 90, 250, 1000, 3000, 9000, 24000), changed.get().edges());
    }

    private static WaveBandAxisView axis() {
        WaveBandAxisView axis = new WaveBandAxisView(RuntimeEnvironment.getApplication());
        axis.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        axis.layout(0, 0, axis.getMeasuredWidth(), axis.getMeasuredHeight());
        return axis;
    }

    private static float xAt(WaveBandAxisView axis, int frequency) {
        float inset = 16 * axis.getResources().getDisplayMetrics().density;
        double progress = Math.log(frequency / 20d) / Math.log(24000 / 20d);
        return inset + (float) (progress * (axis.getWidth() - 2 * inset));
    }

    private static float axisY(WaveBandAxisView axis) {
        var metrics = axis.getResources().getDisplayMetrics();
        return 30 * metrics.density + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, metrics);
    }

    private static void drag(WaveBandAxisView axis, float from, float to) {
        long start = SystemClock.uptimeMillis();
        float y = axisY(axis);
        assertTrue(touch(axis, start, 0, MotionEvent.ACTION_DOWN, from, y));
        assertTrue(touch(axis, start, 30, MotionEvent.ACTION_MOVE, to, y));
        assertTrue(touch(axis, start, 60, MotionEvent.ACTION_UP, to, y));
    }

    private static void tap(WaveBandAxisView axis, float x) {
        long start = SystemClock.uptimeMillis();
        float y = axisY(axis);
        assertTrue(touch(axis, start, 0, MotionEvent.ACTION_DOWN, x, y));
        assertTrue(touch(axis, start, 20, MotionEvent.ACTION_UP, x, y));
    }

    private static boolean touch(WaveBandAxisView axis, long start, long offset, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, start + offset, action, x, y, 0);
        try {
            return axis.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }
}
