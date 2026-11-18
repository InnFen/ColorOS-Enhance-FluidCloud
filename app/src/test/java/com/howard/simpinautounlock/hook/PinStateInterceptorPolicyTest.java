package com.howard.simpinautounlock.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class PinStateInterceptorPolicyTest {
    @Test public void activeDeferralSkipsVoidStatePublisher() throws Throwable {
        assertNull(PinStateInterceptorPolicy.intercept(() -> true, () -> {
            throw new AssertionError("must not publish deferred PIN_REQUIRED");
        }));
    }

    @Test public void unguardedStateIsPublishedExactlyOnce() throws Throwable {
        int[] calls = {0};
        Object nativeResult = new Object();
        assertSame(nativeResult, PinStateInterceptorPolicy.intercept(() -> false, () -> {
            calls[0]++;
            return nativeResult;
        }));
        assertEquals(1, calls[0]);
    }

    @Test public void unreadableMetadataStillPublishesNativeStateOnce() throws Throwable {
        int[] calls = {0};
        assertNull(PinStateInterceptorPolicy.intercept(() -> {
            throw new ReflectiveOperationException("metadata unavailable");
        }, () -> {
            calls[0]++;
            return null;
        }));
        assertEquals(1, calls[0]);
    }

    @Test public void frameworkExceptionPropagatesWithoutRetry() {
        int[] calls = {0};
        Throwable failure = new IllegalStateException("framework failure");
        try {
            PinStateInterceptorPolicy.intercept(() -> false, () -> {
                calls[0]++;
                throw failure;
            });
            fail("must propagate");
        } catch (Throwable actual) {
            assertSame(failure, actual);
        }
        assertEquals(1, calls[0]);
    }

    @Test public void disposedProfileIsNotRefreshed() throws Throwable {
        Object lock = new Object();
        assertFalse(PinStateInterceptorPolicy.refreshIfActive(lock, () -> true, () -> {
            throw new AssertionError("destroyed profile must never publish state");
        }));
    }

    @Test public void disposalCheckAndRefreshBothHoldTheProfileLock() throws Throwable {
        Object lock = new Object();
        int[] calls = {0};
        assertTrue(PinStateInterceptorPolicy.refreshIfActive(lock, () -> {
            assertTrue(Thread.holdsLock(lock));
            return false;
        }, () -> {
            assertTrue(Thread.holdsLock(lock));
            calls[0]++;
            return null;
        }));
        assertEquals(1, calls[0]);
    }
}
