package com.howard.simpinautounlock.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class PinStorageResultPolicyTest {
    @Test
    public void preservesNullWithoutConsultingCoordinator() throws Throwable {
        List<PinStorageTraceEvent> events = new ArrayList<>();

        Object result = PinStorageResultPolicy.resolve(
                null, events::add, ignored -> failIfCalled());

        assertNull(result);
        assertEquals(
                List.of(PinStorageTraceEvent.GET_PIN_ORIGINAL_UNAVAILABLE),
                events);
    }

    @Test
    public void preservesNonStringWithoutConsultingCoordinator() throws Throwable {
        Object original = new Object();

        List<PinStorageTraceEvent> events = new ArrayList<>();

        Object result = PinStorageResultPolicy.resolve(
                original, events::add, ignored -> failIfCalled());

        assertSame(original, result);
        assertEquals(
                List.of(PinStorageTraceEvent.GET_PIN_ORIGINAL_UNAVAILABLE),
                events);
    }

    @Test
    public void preservesNonEmptyStringWithoutConsultingCoordinator() throws Throwable {
        String original = new String("cached");

        List<PinStorageTraceEvent> events = new ArrayList<>();

        Object result = PinStorageResultPolicy.resolve(
                original, events::add, ignored -> failIfCalled());

        assertSame(original, result);
        assertEquals(
                List.of(PinStorageTraceEvent.GET_PIN_ORIGINAL_PRESENT),
                events);
    }

    @Test
    public void resolvesEmptyStringThroughCoordinator() throws Throwable {
        List<PinStorageTraceEvent> events = new ArrayList<>();

        Object result = PinStorageResultPolicy.resolve("", events::add, original -> "1234");

        assertEquals("1234", result);
        assertEquals(
                List.of(
                        PinStorageTraceEvent.GET_PIN_ORIGINAL_EMPTY,
                        PinStorageTraceEvent.MODULE_PIN_RETURNED),
                events);
    }

    @Test
    public void recordsWhenCoordinatorDoesNotReturnPin() throws Throwable {
        List<PinStorageTraceEvent> events = new ArrayList<>();

        Object result = PinStorageResultPolicy.resolve("", events::add, original -> "");

        assertEquals("", result);
        assertEquals(
                List.of(
                        PinStorageTraceEvent.GET_PIN_ORIGINAL_EMPTY,
                        PinStorageTraceEvent.MODULE_PIN_NOT_RETURNED),
                events);
    }

    @Test
    public void classifiesRuntimeAsLazyWhenItDoesNotExist() {
        assertEquals(
                PinStorageTraceEvent.PHONE_RUNTIME_INIT_LAZY,
                PinStorageResultPolicy.runtimeEvent(false));
    }

    @Test
    public void classifiesRuntimeAsReusedWhenItAlreadyExists() {
        assertEquals(
                PinStorageTraceEvent.PHONE_RUNTIME_REUSED,
                PinStorageResultPolicy.runtimeEvent(true));
    }

    private static Object failIfCalled() {
        throw new AssertionError("coordinator must not be consulted");
    }
}
