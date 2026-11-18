package com.howard.simpinautounlock.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AsyncResultDecoderTest {
    @Test
    public void decodesSuccessAndRemainingAttempts() {
        AsyncResultDecoder.Decoded decoded =
                new AsyncResultDecoder().decode(new FakeAsyncResult(null, new int[] {3}));

        assertTrue(decoded.readable());
        assertTrue(decoded.success());
        assertEquals(3, decoded.remainingAttempts());
    }

    @Test
    public void decodesFailureWithoutRemainingAttempts() {
        AsyncResultDecoder.Decoded decoded = new AsyncResultDecoder().decode(
                new FakeAsyncResult(new IllegalStateException(), null));

        assertTrue(decoded.readable());
        assertFalse(decoded.success());
        assertEquals(-1, decoded.remainingAttempts());
    }

    @Test
    public void decodesBoxedRemainingAttempts() {
        AsyncResultDecoder.Decoded decoded =
                new AsyncResultDecoder().decode(new FakeAsyncResult(null, Integer.valueOf(2)));

        assertTrue(decoded.readable());
        assertTrue(decoded.success());
        assertEquals(2, decoded.remainingAttempts());
    }

    @Test
    public void unsupportedResultShapeHasNoRemainingAttempts() {
        AsyncResultDecoder.Decoded decoded =
                new AsyncResultDecoder().decode(new FakeAsyncResult(null, "2"));

        assertTrue(decoded.readable());
        assertTrue(decoded.success());
        assertEquals(-1, decoded.remainingAttempts());
    }

    @Test
    public void missingFieldsAreUnreadable() {
        AsyncResultDecoder.Decoded decoded = new AsyncResultDecoder().decode(new Object());

        assertFalse(decoded.readable());
        assertFalse(decoded.success());
        assertEquals(-1, decoded.remainingAttempts());
    }

    private static final class FakeAsyncResult {
        public final Throwable exception;
        public final Object result;

        FakeAsyncResult(Throwable exception, Object result) {
            this.exception = exception;
            this.result = result;
        }
    }
}
