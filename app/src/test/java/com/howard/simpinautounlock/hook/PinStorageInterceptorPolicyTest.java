package com.howard.simpinautounlock.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class PinStorageInterceptorPolicyTest {
    @Test
    public void normalPathRunsPreludeOriginalAndPostProcessorInOrder() throws Throwable {
        List<String> order = new ArrayList<>();

        Object result = PinStorageInterceptorPolicy.intercept(
                () -> {
                    order.add("slot");
                    return 1;
                },
                slotId -> order.add("entered-" + slotId),
                () -> {
                    order.add("original");
                    return "";
                },
                (slotId, original) -> {
                    order.add("module-" + slotId);
                    return "module-pin";
                },
                failure -> fail("failure reporter must not be called"));

        assertEquals("module-pin", result);
        assertEquals(List.of("slot", "entered-1", "original", "module-1"), order);
    }

    @Test
    public void slotReadFailureCallsOriginalOnceAndSkipsPostProcessor() throws Throwable {
        int[] originalCalls = {0};
        int[] postProcessorCalls = {0};
        Object nativeResult = new Object();
        IllegalStateException preludeFailure = new IllegalStateException("slot unavailable");
        List<Throwable> failures = new ArrayList<>();

        Object result = PinStorageInterceptorPolicy.intercept(
                () -> {
                    throw preludeFailure;
                },
                ignored -> {},
                () -> {
                    originalCalls[0]++;
                    return nativeResult;
                },
                (slotId, original) -> {
                    postProcessorCalls[0]++;
                    return new Object();
                },
                failures::add);

        assertSame(nativeResult, result);
        assertEquals(1, originalCalls[0]);
        assertEquals(0, postProcessorCalls[0]);
        assertEquals(List.of(preludeFailure), failures);
    }

    @Test
    public void entryLogFailureCallsOriginalOnceAndSkipsPostProcessor() throws Throwable {
        int[] originalCalls = {0};
        int[] postProcessorCalls = {0};
        Object nativeResult = new Object();
        IllegalStateException preludeFailure = new IllegalStateException("log unavailable");
        List<Throwable> failures = new ArrayList<>();

        Object result = PinStorageInterceptorPolicy.intercept(
                () -> 0,
                ignored -> {
                    throw preludeFailure;
                },
                () -> {
                    originalCalls[0]++;
                    return nativeResult;
                },
                (slotId, original) -> {
                    postProcessorCalls[0]++;
                    return new Object();
                },
                failures::add);

        assertSame(nativeResult, result);
        assertEquals(1, originalCalls[0]);
        assertEquals(0, postProcessorCalls[0]);
        assertEquals(List.of(preludeFailure), failures);
    }

    @Test
    public void postProcessorFailureReturnsOriginalWithoutCallingItAgain() throws Throwable {
        int[] originalCalls = {0};
        Object nativeResult = new Object();
        IllegalStateException postProcessorFailure =
                new IllegalStateException("module unavailable");
        List<Throwable> failures = new ArrayList<>();

        Object result = PinStorageInterceptorPolicy.intercept(
                () -> 0,
                ignored -> {},
                () -> {
                    originalCalls[0]++;
                    return nativeResult;
                },
                (slotId, original) -> {
                    throw postProcessorFailure;
                },
                failures::add);

        assertSame(nativeResult, result);
        assertEquals(1, originalCalls[0]);
        assertEquals(List.of(postProcessorFailure), failures);
    }

    @Test
    public void originalFailurePropagatesWithoutRetry() {
        int[] originalCalls = {0};
        IllegalStateException failure = new IllegalStateException("framework failure");

        try {
            PinStorageInterceptorPolicy.intercept(
                    () -> 0,
                    ignored -> {},
                    () -> {
                        originalCalls[0]++;
                        throw failure;
                    },
                    (slotId, original) -> {
                        throw new AssertionError("post processor must not be called");
                    },
                    ignored -> fail("framework failure must not be reported as hook failure"));
            fail("framework failure must propagate");
        } catch (Throwable actual) {
            assertSame(failure, actual);
        }

        assertEquals(1, originalCalls[0]);
    }
}
