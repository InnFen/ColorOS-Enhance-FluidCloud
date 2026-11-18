package dev.lsposed.fluidcloud;

import android.view.Choreographer;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Shares an expensive read only inside one actual Choreographer callback frame. */
final class FrameBoolean {
    private static final Method FRAME_TIME = frameMethod();
    private final LongSupplier clock;
    private long previous = Long.MIN_VALUE;
    private boolean value;
    FrameBoolean() { this(() -> {
        if (FRAME_TIME == null) return Long.MIN_VALUE;
        try { return (long) Reflect.call(FRAME_TIME, Choreographer.getInstance()); }
        catch (IllegalStateException outsideFrame) { return Long.MIN_VALUE; }
    }); }
    private static Method frameMethod() {
        try { return Reflect.method(Choreographer.class,"getFrameTimeNanos",long.class); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    FrameBoolean(LongSupplier clock) { this.clock = clock; }
    boolean get(BooleanSupplier read) {
        long frame = clock.getAsLong();
        if (frame == Long.MIN_VALUE || frame != previous) {
            boolean current = read.getAsBoolean();
            value = current; previous = frame;
        }
        return value;
    }
    void invalidate() { previous = Long.MIN_VALUE; }
}
